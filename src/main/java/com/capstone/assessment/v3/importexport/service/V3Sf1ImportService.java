package com.capstone.assessment.v3.importexport.service;

import com.capstone.assessment.common.exception.BadRequestException;
import com.capstone.assessment.importexport.dto.Sf1ImportPreviewResponse;
import com.capstone.assessment.importexport.dto.Sf1PreviewRowDto;
import com.capstone.assessment.importexport.service.Sf1ImportService;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.exception.V3FieldValidationException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3AuditService;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
import com.capstone.assessment.v3.importexport.dto.V3Sf1ImportResponse;
import com.capstone.assessment.v3.importexport.dto.V3Sf1PreviewResponse;
import com.capstone.assessment.v3.importexport.model.V3Sf1Models.EnrollmentContext;
import com.capstone.assessment.v3.importexport.model.V3Sf1Models.ImportHeader;
import com.capstone.assessment.v3.importexport.model.V3Sf1Models.ReferenceContext;
import com.capstone.assessment.v3.importexport.model.V3Sf1Models.StudentContext;
import com.capstone.assessment.v3.importexport.repository.V3Sf1ImportRepository;
import com.capstone.assessment.v3.school.dto.V3ClassResponse;
import com.capstone.assessment.v3.school.model.V3SchoolSetupModels.ClassContext;
import com.capstone.assessment.v3.school.repository.V3SchoolSetupRepository;
import com.capstone.assessment.v3.school.service.V3SchoolSetupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Profile("v3")
@Service
public class V3Sf1ImportService {

    private static final long MAX_FILE_BYTES = 10L * 1024L * 1024L;
    private static final Pattern LRN_PATTERN = Pattern.compile("^\\d{12}$");
    private static final Pattern SHA256_PATTERN = Pattern.compile("^[a-fA-F0-9]{64}$");
    private static final Set<String> TERMINAL_IMPORT_STATUSES = Set.of(
            "completed", "partial_success", "failed", "cancelled"
    );

    private final Sf1ImportService parser;
    private final V3Sf1ImportRepository repository;
    private final V3SchoolSetupRepository schoolRepository;
    private final V3SchoolSetupService schoolSetupService;
    private final V3AuditService auditService;
    private final Clock clock;

    @Autowired
    public V3Sf1ImportService(
            Sf1ImportService parser,
            V3Sf1ImportRepository repository,
            V3SchoolSetupRepository schoolRepository,
            V3SchoolSetupService schoolSetupService,
            V3AuditService auditService
    ) {
        this(parser, repository, schoolRepository, schoolSetupService, auditService, Clock.systemUTC());
    }

    V3Sf1ImportService(
            Sf1ImportService parser,
            V3Sf1ImportRepository repository,
            V3SchoolSetupRepository schoolRepository,
            V3SchoolSetupService schoolSetupService,
            V3AuditService auditService,
            Clock clock
    ) {
        this.parser = parser;
        this.repository = repository;
        this.schoolRepository = schoolRepository;
        this.schoolSetupService = schoolSetupService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public V3Sf1PreviewResponse preview(
            V3AuthenticatedUser principal,
            MultipartFile file,
            int academicYearId,
            int gradeLevelId,
            String requestedSectionName
    ) {
        requirePrincipal(principal);
        FilePayload payload = readFile(file);
        Sf1ImportPreviewResponse parsed = parse(file);
        ReferenceContext context = resolveContext(
                principal.schoolId(), academicYearId, gradeLevelId, requestedSectionName, parsed
        );
        ContextValidation contextValidation = validateDetectedContext(context, parsed);
        int previousImports = repository.countPreviousCompletedImports(
                principal.schoolId(), academicYearId, payload.hash(), null
        );

        Set<String> seenLrns = new HashSet<>();
        List<V3Sf1PreviewResponse.Row> rows = parsed.rows().stream()
                .map(row -> planRow(principal.schoolId(), academicYearId, context.existingClassId(), row, seenLrns))
                .toList();
        int validRows = (int) rows.stream().filter(row -> !"invalid".equals(row.plannedOutcome())).count();

        List<String> warnings = new ArrayList<>(contextValidation.warnings());
        if (previousImports > 0) {
            warnings.add("This file content was imported before. Only new or newly enrollable learners will be added.");
        }

        return new V3Sf1PreviewResponse(
                UUID.randomUUID().toString(),
                payload.fileName(),
                payload.hash(),
                previousImports > 0,
                previousImports,
                toPreviewContext(context),
                new V3Sf1PreviewResponse.DetectedContext(
                        parsed.detectedSchoolYear(), parsed.detectedGradeLevelName(), parsed.detectedSectionName()
                ),
                contextValidation.requiresOverride(),
                List.copyOf(warnings),
                rows.size(),
                validRows,
                rows.size() - validRows,
                rows
        );
    }

    @Transactional
    public V3Sf1ImportResponse confirm(
            V3AuthenticatedUser principal,
            MultipartFile file,
            String importUuid,
            int academicYearId,
            int gradeLevelId,
            String requestedSectionName,
            String expectedFileHash,
            boolean acceptContextMismatch,
            V3RequestMetadata metadata
    ) {
        requirePrincipal(principal);
        String normalizedUuid = normalizeUuid(importUuid);
        String normalizedExpectedHash = normalizeExpectedHash(expectedFileHash);
        FilePayload payload = readFile(file);
        if (!payload.hash().equals(normalizedExpectedHash)) {
            throw conflict("SF1_FILE_CHANGED",
                    "The selected file no longer matches the preview. Preview the SF1 file again.");
        }

        Sf1ImportPreviewResponse parsed = parse(file);
        ReferenceContext context = resolveContext(
                principal.schoolId(), academicYearId, gradeLevelId, requestedSectionName, parsed
        );
        ContextValidation contextValidation = validateDetectedContext(context, parsed);
        if (contextValidation.requiresOverride() && !acceptContextMismatch) {
            throw new V3FieldValidationException(
                    "The detected SF1 context differs from the selected class context.",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    Map.of("acceptContextMismatch",
                            "Review the warnings and explicitly accept the context mismatch before confirming.")
            );
        }

        ImportHeader existing = repository.lockImportByUuid(normalizedUuid).orElse(null);
        if (existing != null) {
            validateReplay(principal, existing, context, payload);
            if (!TERMINAL_IMPORT_STATUSES.contains(existing.importStatus())) {
                throw conflict("SF1_IMPORT_IN_PROGRESS",
                        "This import identifier is already being processed. Retry after it completes.");
            }
            return loadResponse(existing, true);
        }

        V3ClassResponse targetClass = schoolSetupService.createOrReuseClass(
                principal,
                academicYearId,
                gradeLevelId,
                context.sectionName(),
                metadata,
                false
        );
        int previousImports = repository.countPreviousCompletedImports(
                principal.schoolId(), academicYearId, payload.hash(), null
        );
        Instant startedAt = clock.instant();
        long importId = repository.insertImport(
                normalizedUuid,
                principal.schoolId(),
                academicYearId,
                targetClass.classId(),
                principal.userId(),
                payload.fileName(),
                payload.hash(),
                parsed.detectedSchoolYear(),
                parsed.detectedGradeLevelName(),
                parsed.detectedSectionName(),
                parsed.rows().size(),
                startedAt
        );

        MutableCounts counts = new MutableCounts();
        Set<String> seenLrns = new HashSet<>();
        for (Sf1PreviewRowDto row : parsed.rows()) {
            processRow(principal, targetClass, importId, row, seenLrns, counts);
        }

        String finalStatus = counts.successfulRows() == 0 && (counts.conflicts + counts.invalid > 0)
                ? "failed"
                : (counts.conflicts > 0 || counts.invalid > 0 ? "partial_success" : "completed");
        Instant completedAt = clock.instant();
        if (repository.completeImport(
                importId,
                finalStatus,
                counts.created,
                counts.updated,
                counts.unchanged,
                counts.conflicts,
                counts.invalid,
                completedAt
        ) != 1) {
            throw new IllegalStateException("The SF1 import summary could not be finalized.");
        }

        auditService.record(
                principal.userId(),
                "sf1.import.confirm",
                "sf1_imports",
                Long.toString(importId),
                "failed".equals(finalStatus) ? "failure" : "success",
                metadata,
                Map.of(
                        "importUuid", normalizedUuid,
                        "classId", targetClass.classId(),
                        "duplicateFile", previousImports > 0,
                        "createdStudents", counts.created,
                        "updatedStudents", counts.updated,
                        "unchangedStudents", counts.unchanged,
                        "conflictRows", counts.conflicts,
                        "invalidRows", counts.invalid
                ),
                completedAt
        );

        ImportHeader saved = repository.findImport(importId)
                .orElseThrow(() -> new IllegalStateException("The completed SF1 import could not be reloaded."));
        return loadResponse(saved, false);
    }

    private void processRow(
            V3AuthenticatedUser principal,
            V3ClassResponse targetClass,
            long importId,
            Sf1PreviewRowDto row,
            Set<String> seenLrns,
            MutableCounts counts
    ) {
        Instant processedAt = clock.instant();
        String validationMessage = rowValidationMessage(row);
        if (validationMessage != null) {
            insertOutcome(importId, row, null, null, "invalid", "INVALID_SF1_ROW",
                    validationMessage, processedAt);
            counts.invalid++;
            return;
        }
        if (!seenLrns.add(row.studentLrn())) {
            insertOutcome(importId, row, null, null, "invalid", "DUPLICATE_LRN_IN_FILE",
                    "The same LRN appears more than once in this file.", processedAt);
            counts.invalid++;
            return;
        }

        StudentContext student = repository.findStudentByLrn(row.studentLrn()).orElse(null);
        if (student == null) {
            int genderId = repository.findGenderId(row.gender())
                    .orElseThrow(() -> invalid("file", "The SF1 gender is not available in V3 reference data."));
            long addressId = repository.insertBlankSf1Address();
            long studentId = repository.insertStudent(
                    principal.schoolId(), addressId, genderId, row.studentLrn(),
                    cleanName(row.firstName()), cleanName(row.lastName())
            );
            long classListId = repository.insertClassMembership(
                    UUID.randomUUID().toString(), targetClass.classId(), studentId,
                    targetClass.academicYearId(), importId
            );
            insertOutcome(importId, row, studentId, classListId, "created", null,
                    "New learner and class enrollment created.", processedAt);
            counts.created++;
            return;
        }

        if (!principal.schoolId().equals(student.schoolId())) {
            insertOutcome(importId, row, null, null, "enrollment_conflict", "LRN_OWNED_BY_ANOTHER_SCHOOL",
                    "This LRN is already registered outside the authenticated school.", processedAt);
            counts.conflicts++;
            return;
        }
        if (!"active".equals(student.status())) {
            insertOutcome(importId, row, student.studentId(), null, "enrollment_conflict",
                    "STUDENT_NOT_ACTIVE",
                    "The existing learner record is not active and requires an authorized status review.",
                    processedAt);
            counts.conflicts++;
            return;
        }

        String dataWarning = studentDataWarning(student, row);
        EnrollmentContext activeEnrollment = repository.lockActiveEnrollment(
                student.studentId(), targetClass.academicYearId()
        ).orElse(null);
        if (activeEnrollment != null) {
            if (activeEnrollment.classId() == targetClass.classId()) {
                insertOutcome(importId, row, student.studentId(), activeEnrollment.classListId(),
                        "already_enrolled", dataWarning == null ? "DUPLICATE_ENROLLMENT_IGNORED" : dataWarning,
                        dataWarning == null
                                ? "Learner is already enrolled in this class; no duplicate was created."
                                : "Learner is already enrolled; differing identity values were not overwritten.",
                        processedAt);
                counts.unchanged++;
            } else {
                insertOutcome(importId, row, student.studentId(), activeEnrollment.classListId(),
                        "enrollment_conflict", "ACTIVE_CLASS_CONFLICT",
                        "Learner is already enrolled in another class for this academic year.", processedAt);
                counts.conflicts++;
            }
            return;
        }

        EnrollmentContext oldTargetMembership = repository.lockMembership(
                student.studentId(), targetClass.classId()
        ).orElse(null);
        long classListId;
        if (oldTargetMembership == null) {
            classListId = repository.insertClassMembership(
                    UUID.randomUUID().toString(), targetClass.classId(), student.studentId(),
                    targetClass.academicYearId(), importId
            );
        } else {
            if (repository.reactivateMembership(
                    oldTargetMembership.classListId(), importId, principal.userId(), processedAt
            ) != 1) {
                throw conflict("SF1_ENROLLMENT_STATE_CHANGED",
                        "A learner enrollment changed while the SF1 import was being confirmed.");
            }
            classListId = oldTargetMembership.classListId();
        }
        insertOutcome(importId, row, student.studentId(), classListId, "updated", dataWarning,
                dataWarning == null
                        ? "Existing learner enrolled in the selected class."
                        : "Existing learner enrolled; differing identity values were not overwritten.",
                processedAt);
        counts.updated++;
    }

    private V3Sf1PreviewResponse.Row planRow(
            String schoolId,
            int academicYearId,
            Long targetClassId,
            Sf1PreviewRowDto row,
            Set<String> seenLrns
    ) {
        String validationMessage = rowValidationMessage(row);
        if (validationMessage != null) {
            return previewRow(row, "invalid", "INVALID_SF1_ROW", validationMessage);
        }
        if (!seenLrns.add(row.studentLrn())) {
            return previewRow(row, "invalid", "DUPLICATE_LRN_IN_FILE",
                    "The same LRN appears more than once in this file.");
        }
        StudentContext student = repository.findStudentByLrn(row.studentLrn()).orElse(null);
        if (student == null) {
            return previewRow(row, "created", null, "New learner will be created and enrolled.");
        }
        if (!schoolId.equals(student.schoolId()) || !"active".equals(student.status())) {
            return previewRow(row, "enrollment_conflict", "LRN_OR_STATUS_CONFLICT",
                    "Existing learner ownership or status requires review; no data will be changed.");
        }
        EnrollmentContext enrollment = repository.findActiveEnrollment(student.studentId(), academicYearId)
                .orElse(null);
        if (enrollment == null) {
            String warning = studentDataWarning(student, row);
            return previewRow(row, "updated", warning,
                    warning == null
                            ? "Existing learner will be enrolled in the selected class."
                            : "Existing identity differs; stored identity will be preserved while enrolling.");
        }
        if (targetClassId != null && enrollment.classId() == targetClassId) {
            return previewRow(row, "already_enrolled", "DUPLICATE_ENROLLMENT_IGNORED",
                    "Learner is already enrolled in this class; no duplicate will be created.");
        }
        return previewRow(row, "enrollment_conflict", "ACTIVE_CLASS_CONFLICT",
                "Learner is already enrolled in another class for this academic year.");
    }

    private ReferenceContext resolveContext(
            String schoolId,
            int academicYearId,
            int gradeLevelId,
            String requestedSectionName,
            Sf1ImportPreviewResponse parsed
    ) {
        if (!schoolRepository.academicYearExists(schoolId, academicYearId)) {
            throw invalid("academicYearId", "The selected academic year does not belong to this school.");
        }
        if (!schoolRepository.gradeLevelExists(gradeLevelId)) {
            throw invalid("gradeLevelId", "The selected grade level does not exist.");
        }
        String sectionName = firstNonBlank(requestedSectionName, parsed.detectedSectionName());
        if (sectionName == null) {
            throw invalid("sectionName",
                    "Section name is required because it could not be detected from the SF1 file.");
        }
        sectionName = sectionName.trim().replaceAll("\\s+", " ");
        if (sectionName.length() > 50) {
            throw invalid("sectionName", "Section name must not exceed 50 characters.");
        }
        return repository.findReferenceContext(schoolId, academicYearId, gradeLevelId, sectionName)
                .orElseThrow(() -> invalid("classContext",
                        "The selected academic year or grade level is not available."));
    }

    private ContextValidation validateDetectedContext(
            ReferenceContext context,
            Sf1ImportPreviewResponse parsed
    ) {
        List<String> warnings = new ArrayList<>();
        compareDetected("school year", parsed.detectedSchoolYear(), context.academicYearName(), warnings);
        compareDetected("grade level", parsed.detectedGradeLevelName(), context.gradeLevelName(), warnings);
        compareDetected("section", parsed.detectedSectionName(), context.sectionName(), warnings);
        return new ContextValidation(!warnings.isEmpty(), List.copyOf(warnings));
    }

    private void compareDetected(
            String label,
            String detected,
            String selected,
            List<String> warnings
    ) {
        if (detected != null && !detected.isBlank()
                && !comparisonValue(detected).equals(comparisonValue(selected))) {
            warnings.add("Detected " + label + " '" + detected.trim()
                    + "' differs from selected '" + selected + "'.");
        }
    }

    private void validateReplay(
            V3AuthenticatedUser principal,
            ImportHeader existing,
            ReferenceContext context,
            FilePayload payload
    ) {
        if (!existing.schoolId().equals(principal.schoolId())
                || existing.uploadedByUserId() != principal.userId()) {
            throw conflict("SF1_IMPORT_UUID_CONFLICT",
                    "This import identifier is already owned by another operation.");
        }
        if (existing.academicYearId() != context.academicYearId()
                || !existing.sourceFileHash().equals(payload.hash())) {
            throw conflict("SF1_IMPORT_UUID_PAYLOAD_CHANGED",
                    "Retries must reuse the same import UUID with the same file and academic year.");
        }
        ClassContext target = schoolRepository.findClass(principal.schoolId(), existing.targetClassId())
                .orElseThrow(() -> conflict("SF1_IMPORT_TARGET_MISSING",
                        "The original import target class is no longer available."));
        if (target.gradeLevelId() != context.gradeLevelId()
                || !comparisonValue(target.sectionName()).equals(comparisonValue(context.sectionName()))) {
            throw conflict("SF1_IMPORT_UUID_PAYLOAD_CHANGED",
                    "Retries must reuse the same import UUID with the same class context.");
        }
    }

    private V3Sf1ImportResponse loadResponse(ImportHeader header, boolean replayed) {
        ClassContext target = schoolRepository.findClass(header.schoolId(), header.targetClassId())
                .orElseThrow(() -> new IllegalStateException("The SF1 target class could not be reloaded."));
        int priorCount = repository.countPreviousCompletedImports(
                header.schoolId(), header.academicYearId(), header.sourceFileHash(), header.sf1ImportId()
        );
        return new V3Sf1ImportResponse(
                header.sf1ImportId(),
                header.importUuid(),
                header.importStatus(),
                header.sourceFileName(),
                header.sourceFileHash(),
                priorCount > 0,
                priorCount,
                new V3Sf1ImportResponse.TargetClass(
                        target.classId(), target.academicYearId(), target.academicYearName(),
                        target.gradeLevelId(), target.gradeLevelName(), target.sectionId(), target.sectionName()
                ),
                header.totalRows(),
                header.createdStudents(),
                header.updatedStudents(),
                header.unchangedStudents(),
                header.conflictRows(),
                header.invalidRows(),
                header.startedAt(),
                header.completedAt(),
                replayed,
                repository.listImportRows(header.sf1ImportId())
        );
    }

    private void insertOutcome(
            long importId,
            Sf1PreviewRowDto row,
            Long studentId,
            Long classListId,
            String status,
            String warningCode,
            String message,
            Instant processedAt
    ) {
        repository.insertImportItem(
                importId,
                row.rowNumber(),
                row.studentLrn() == null || row.studentLrn().isBlank()
                        ? "MISSING-" + row.rowNumber()
                        : row.studentLrn(),
                rowHash(row),
                studentId,
                classListId,
                status,
                warningCode,
                truncate(message, 255),
                processedAt
        );
    }

    private V3Sf1PreviewResponse.Row previewRow(
            Sf1PreviewRowDto row,
            String outcome,
            String warningCode,
            String message
    ) {
        return new V3Sf1PreviewResponse.Row(
                row.rowNumber(), row.studentLrn(), row.firstName(), row.lastName(), row.gender(),
                row.status(), outcome, warningCode, message
        );
    }

    private String rowValidationMessage(Sf1PreviewRowDto row) {
        if (!"VALID".equals(row.status())) {
            return truncate(row.message(), 255);
        }
        if (row.studentLrn() == null || !LRN_PATTERN.matcher(row.studentLrn()).matches()) {
            return "LRN must contain exactly 12 digits.";
        }
        if (row.firstName() == null || row.firstName().isBlank() || row.firstName().trim().length() > 50) {
            return "First name is required and must not exceed 50 characters.";
        }
        if (row.lastName() == null || row.lastName().isBlank() || row.lastName().trim().length() > 50) {
            return "Last name is required and must not exceed 50 characters.";
        }
        if (row.gender() == null || !("male".equalsIgnoreCase(row.gender())
                || "female".equalsIgnoreCase(row.gender()))) {
            return "Gender must resolve to Male or Female.";
        }
        return null;
    }

    private String studentDataWarning(StudentContext student, Sf1PreviewRowDto row) {
        boolean differs = !comparisonValue(student.firstName()).equals(comparisonValue(row.firstName()))
                || !comparisonValue(student.lastName()).equals(comparisonValue(row.lastName()))
                || !comparisonValue(student.genderName()).equals(comparisonValue(row.gender()));
        return differs ? "STORED_IDENTITY_PRESERVED" : null;
    }

    private Sf1ImportPreviewResponse parse(MultipartFile file) {
        try {
            return parser.generatePreview(file);
        } catch (BadRequestException exception) {
            throw invalid("file", exception.getMessage());
        }
    }

    private FilePayload readFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("file", "SF1 Excel file is required.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw invalid("file", "SF1 Excel file must not exceed 10 MB.");
        }
        String fileName = safeFileName(file.getOriginalFilename());
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        if (!(lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls"))) {
            throw invalid("file", "Only .xlsx or .xls SF1 files are accepted.");
        }
        try {
            return new FilePayload(fileName, sha256(file.getBytes()));
        } catch (IOException exception) {
            throw invalid("file", "The SF1 file could not be read.");
        }
    }

    private String safeFileName(String originalName) {
        String name = originalName == null ? "sf1.xlsx" : originalName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isEmpty()) {
            name = "sf1.xlsx";
        }
        return truncate(name, 255);
    }

    private String normalizeUuid(String value) {
        try {
            return UUID.fromString(Objects.requireNonNull(value).trim()).toString();
        } catch (RuntimeException exception) {
            throw invalid("importUuid", "importUuid must be a valid UUID from the preview response.");
        }
    }

    private String normalizeExpectedHash(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!SHA256_PATTERN.matcher(normalized).matches()) {
            throw invalid("expectedFileHash", "expectedFileHash must be the SHA-256 value from preview.");
        }
        return normalized;
    }

    private String rowHash(Sf1PreviewRowDto row) {
        return sha256(String.join("|",
                Integer.toString(row.rowNumber()),
                nullToEmpty(row.studentLrn()),
                nullToEmpty(row.firstName()),
                nullToEmpty(row.lastName()),
                nullToEmpty(row.gender())
        ).getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private V3Sf1PreviewResponse.ImportContext toPreviewContext(ReferenceContext context) {
        return new V3Sf1PreviewResponse.ImportContext(
                context.academicYearId(), context.academicYearName(),
                context.gradeLevelId(), context.gradeLevelName(),
                context.sectionName(), context.existingClassId()
        );
    }

    private void requirePrincipal(V3AuthenticatedUser user) {
        if (user == null) {
            throw new V3AuthException("AUTHENTICATION_REQUIRED", "Authentication is required.",
                    HttpStatus.UNAUTHORIZED);
        }
        if (!"principal".equals(user.role())) {
            throw new V3AuthException("PRINCIPAL_REQUIRED", "Principal access is required.",
                    HttpStatus.FORBIDDEN);
        }
        if (!"active".equals(user.status()) || user.schoolId() == null || user.schoolId().isBlank()) {
            throw new V3AuthException("ACTIVE_SCHOOL_ACCOUNT_REQUIRED",
                    "An active principal account associated with a school is required.",
                    HttpStatus.FORBIDDEN);
        }
    }

    private V3FieldValidationException invalid(String field, String message) {
        Map<String, Object> errors = new LinkedHashMap<>();
        errors.put(field, message);
        return new V3FieldValidationException("The SF1 import request is invalid.",
                HttpStatus.BAD_REQUEST, errors);
    }

    private V3AuthException conflict(String code, String message) {
        return new V3AuthException(code, message, HttpStatus.CONFLICT);
    }

    private String cleanName(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private String comparisonValue(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record FilePayload(String fileName, String hash) {
    }

    private record ContextValidation(boolean requiresOverride, List<String> warnings) {
    }

    private static final class MutableCounts {
        private int created;
        private int updated;
        private int unchanged;
        private int conflicts;
        private int invalid;

        private int successfulRows() {
            return created + updated + unchanged;
        }
    }
}
