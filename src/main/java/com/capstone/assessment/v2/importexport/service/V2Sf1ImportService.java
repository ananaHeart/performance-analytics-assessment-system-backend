package com.capstone.assessment.v2.importexport.service;

import com.capstone.assessment.common.exception.BadRequestException;
import com.capstone.assessment.importexport.dto.Sf1ImportPreviewResponse;
import com.capstone.assessment.importexport.dto.Sf1PreviewRowDto;
import com.capstone.assessment.importexport.service.Sf1ImportService;
import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import com.capstone.assessment.v2.importexport.dto.V2Sf1ImportSummaryResponse;
import com.capstone.assessment.v2.importexport.dto.V2StudentRecordResponse;
import com.capstone.assessment.v2.importexport.repository.V2Sf1ImportRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Profile("v2")
@Service
public class V2Sf1ImportService {

    private static final String VALID_ROW_STATUS = "VALID";

    private final Sf1ImportService sf1Parser;
    private final V2Sf1ImportRepository repository;

    public V2Sf1ImportService(
            Sf1ImportService sf1Parser,
            V2Sf1ImportRepository repository
    ) {
        this.sf1Parser = sf1Parser;
        this.repository = repository;
    }

    public Sf1ImportPreviewResponse preview(MultipartFile file, V2AuthenticatedUser principal) {
        validatePrincipal(principal);
        return sf1Parser.generatePreview(file);
    }

    public List<V2StudentRecordResponse> listStudents(
            V2AuthenticatedUser principal,
            Integer academicYearId
    ) {
        validatePrincipal(principal);
        return repository.listStudents(principal.schoolId(), academicYearId);
    }

    @Transactional
    public V2Sf1ImportSummaryResponse confirm(
            MultipartFile file,
            V2AuthenticatedUser principal,
            Integer gradeLevelId
    ) {
        validatePrincipal(principal);

        if (gradeLevelId == null || !repository.gradeLevelExists(gradeLevelId)) {
            throw new BadRequestException("Select a valid grade level before importing the SF1 file.");
        }

        Sf1ImportPreviewResponse preview = sf1Parser.generatePreview(file);
        String detectedSchoolYear = trimToNull(preview.detectedSchoolYear());
        String detectedSection = trimToNull(preview.detectedSectionName());

        if (detectedSchoolYear == null) {
            throw new BadRequestException("School year could not be detected from the SF1 worksheet.");
        }
        if (detectedSection == null) {
            throw new BadRequestException("Section name could not be detected from the SF1 worksheet.");
        }

        int academicYearId = repository.findAcademicYearIdByName(detectedSchoolYear)
                .orElseThrow(() -> new BadRequestException(
                        "Detected school year " + detectedSchoolYear + " is not configured in the system."
                ));

        int sectionId = repository.findSectionId(detectedSection, gradeLevelId)
                .orElseGet(() -> repository.createSection(detectedSection, gradeLevelId));
        long classId = repository.findClassId(academicYearId, sectionId)
                .orElseGet(() -> repository.createClass(academicYearId, sectionId));

        int importedStudents = 0;
        int updatedStudents = 0;
        int enrolledStudents = 0;
        int skippedRows = 0;
        Set<String> processedLrns = new HashSet<>();

        for (Sf1PreviewRowDto row : preview.rows()) {
            if (!VALID_ROW_STATUS.equalsIgnoreCase(row.status())) {
                skippedRows++;
                continue;
            }

            String studentLrn = trimToNull(row.studentLrn());
            if (studentLrn == null || !processedLrns.add(studentLrn)) {
                skippedRows++;
                continue;
            }

            int genderId = repository.findGenderId(row.gender())
                    .orElseThrow(() -> new BadRequestException(
                            "Gender '" + row.gender() + "' is not available in the V2 gender reference table."
                    ));

            V2Sf1ImportRepository.ExistingStudent existingStudent =
                    repository.findStudentByLrn(studentLrn).orElse(null);

            long studentId;
            if (existingStudent == null) {
                long addressId = repository.createSf1Address();
                studentId = repository.createStudent(
                        principal.schoolId(),
                        addressId,
                        genderId,
                        studentLrn,
                        row.firstName(),
                        row.lastName()
                );
                importedStudents++;
            } else {
                if (!principal.schoolId().equals(existingStudent.schoolId())) {
                    throw new BadRequestException(
                            "LRN " + studentLrn + " is already registered under another school."
                    );
                }

                studentId = existingStudent.studentId();
                repository.updateStudent(
                        studentId,
                        genderId,
                        row.firstName(),
                        row.lastName()
                );
                updatedStudents++;
            }

            if (!repository.classMembershipExists(classId, studentId)) {
                repository.addStudentToClass(classId, studentId);
                enrolledStudents++;
            }
        }

        return new V2Sf1ImportSummaryResponse(
                detectedSchoolYear,
                detectedSection,
                academicYearId,
                gradeLevelId,
                sectionId,
                classId,
                importedStudents,
                updatedStudents,
                enrolledStudents,
                skippedRows,
                "SF1 import completed successfully."
        );
    }

    private void validatePrincipal(V2AuthenticatedUser principal) {
        if (principal == null) {
            throw new BadRequestException("Authenticated principal context is required.");
        }
        if (!"principal".equalsIgnoreCase(principal.role())) {
            throw new BadRequestException("Only the school principal may import SF1 student records.");
        }
        if (principal.schoolId() == null || principal.schoolId().isBlank()) {
            throw new BadRequestException("The principal account is not linked to a school.");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
