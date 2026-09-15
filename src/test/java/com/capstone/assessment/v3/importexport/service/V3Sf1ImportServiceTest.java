package com.capstone.assessment.v3.importexport.service;

import com.capstone.assessment.importexport.dto.Sf1ImportPreviewResponse;
import com.capstone.assessment.importexport.dto.Sf1PreviewRowDto;
import com.capstone.assessment.importexport.service.Sf1ImportService;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.exception.V3FieldValidationException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3AuditService;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class V3Sf1ImportServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");
    private static final V3AuthenticatedUser PRINCIPAL = new V3AuthenticatedUser(
            11L, "SCHOOL-001", "principal@example.com", "principal", "active", "session"
    );
    private static final String IMPORT_UUID = "10000000-0000-0000-0000-000000000001";

    private final Sf1ImportService parser = mock(Sf1ImportService.class);
    private final V3Sf1ImportRepository repository = mock(V3Sf1ImportRepository.class);
    private final V3SchoolSetupRepository schoolRepository = mock(V3SchoolSetupRepository.class);
    private final V3SchoolSetupService schoolSetupService = mock(V3SchoolSetupService.class);
    private final V3AuditService auditService = mock(V3AuditService.class);
    private V3Sf1ImportService service;

    @BeforeEach
    void setUp() {
        service = new V3Sf1ImportService(
                parser,
                repository,
                schoolRepository,
                schoolSetupService,
                auditService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(schoolRepository.academicYearExists("SCHOOL-001", 2026)).thenReturn(true);
        when(schoolRepository.gradeLevelExists(8)).thenReturn(true);
        when(repository.findReferenceContext("SCHOOL-001", 2026, 8, "Narra"))
                .thenReturn(Optional.of(referenceContext(null)));
    }

    @Test
    void previewWarnsAboutRepeatedContentWithoutBlockingNewLearners() {
        MockMultipartFile file = file();
        Sf1PreviewRowDto row = validRow(7, "100000000001", "Juan", "Dela Cruz", "male");
        when(parser.generatePreview(file)).thenReturn(parsed(List.of(row)));
        when(repository.countPreviousCompletedImports("SCHOOL-001", 2026, hash(file), null))
                .thenReturn(2);
        when(repository.findStudentByLrn(row.studentLrn())).thenReturn(Optional.empty());

        V3Sf1PreviewResponse response = service.preview(PRINCIPAL, file, 2026, 8, "Narra");

        assertTrue(response.duplicateFile());
        assertEquals(2, response.previousCompletedImportCount());
        assertEquals("created", response.rows().get(0).plannedOutcome());
        assertFalse(response.requiresContextOverride());
        assertTrue(response.warnings().stream().anyMatch(message -> message.contains("imported before")));
        verify(repository, never()).insertStudent(anyString(), anyLong(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    void confirmCreatesOnlyNewLearnerAndDoesNotDuplicateExistingEnrollment() {
        MockMultipartFile file = file();
        String fileHash = hash(file);
        Sf1PreviewRowDto newRow = validRow(7, "100000000001", "Juan", "Dela Cruz", "male");
        Sf1PreviewRowDto existingRow = validRow(8, "100000000002", "Ana", "Reyes", "female");
        when(parser.generatePreview(file)).thenReturn(parsed(List.of(newRow, existingRow)));
        when(repository.lockImportByUuid(IMPORT_UUID)).thenReturn(Optional.empty());
        when(schoolSetupService.createOrReuseClass(
                eq(PRINCIPAL), eq(2026), eq(8), eq("Narra"), isNull(), eq(false)
        )).thenReturn(targetClass());
        when(repository.countPreviousCompletedImports(anyString(), anyInt(), anyString(), any()))
                .thenReturn(1);
        when(repository.insertImport(
                eq(IMPORT_UUID), eq("SCHOOL-001"), eq(2026), eq(700L), eq(11L),
                eq("SF1_Grade8_Narra.xlsx"), eq(fileHash), eq("2025-2026"),
                eq("Grade 8"), eq("Narra"), eq(2), eq(NOW)
        )).thenReturn(900L);
        when(repository.findStudentByLrn(newRow.studentLrn())).thenReturn(Optional.empty());
        when(repository.findGenderId("male")).thenReturn(Optional.of(1));
        when(repository.insertBlankSf1Address()).thenReturn(300L);
        when(repository.insertStudent(
                "SCHOOL-001", 300L, 1, newRow.studentLrn(), "Juan", "Dela Cruz"
        )).thenReturn(400L);
        when(repository.insertClassMembership(
                anyString(), eq(700L), eq(400L), eq(2026), eq(900L)
        ))
                .thenReturn(500L);
        StudentContext existingStudent = new StudentContext(
                401L, "SCHOOL-001", existingRow.studentLrn(), "Ana", "Reyes", "Female", "active"
        );
        when(repository.findStudentByLrn(existingRow.studentLrn()))
                .thenReturn(Optional.of(existingStudent));
        when(repository.lockActiveEnrollment(401L, 2026))
                .thenReturn(Optional.of(new EnrollmentContext(501L, 700L, "Narra", "enrolled")));
        when(repository.completeImport(900L, "completed", 1, 0, 1, 0, 0, NOW)).thenReturn(1);
        when(repository.findImport(900L)).thenReturn(Optional.of(
                header("completed", fileHash, 1, 0, 1, 0, 0)
        ));
        when(schoolRepository.findClass("SCHOOL-001", 700L))
                .thenReturn(Optional.of(classContext()));
        when(repository.listImportRows(900L)).thenReturn(List.of());

        V3Sf1ImportResponse response = service.confirm(
                PRINCIPAL, file, IMPORT_UUID, 2026, 8, "Narra", fileHash, false, null
        );

        assertEquals("completed", response.importStatus());
        assertEquals(1, response.createdStudents());
        assertEquals(1, response.unchangedStudents());
        assertFalse(response.replayed());
        verify(repository).insertStudent(
                "SCHOOL-001", 300L, 1, newRow.studentLrn(), "Juan", "Dela Cruz"
        );
        verify(repository, never()).insertClassMembership(anyString(), eq(700L), eq(401L), anyInt(), anyLong());
    }

    @Test
    void completedRetryReturnsStoredImportWithoutWritingAgain() {
        MockMultipartFile file = file();
        String fileHash = hash(file);
        when(parser.generatePreview(file)).thenReturn(parsed(List.of()));
        ImportHeader existing = header("completed", fileHash, 0, 0, 0, 0, 0);
        when(repository.lockImportByUuid(IMPORT_UUID)).thenReturn(Optional.of(existing));
        when(schoolRepository.findClass("SCHOOL-001", 700L))
                .thenReturn(Optional.of(classContext()));
        when(repository.listImportRows(900L)).thenReturn(List.of());

        V3Sf1ImportResponse response = service.confirm(
                PRINCIPAL, file, IMPORT_UUID, 2026, 8, "Narra", fileHash, false, null
        );

        assertTrue(response.replayed());
        assertEquals(900L, response.sf1ImportId());
        verify(repository, never()).insertImport(
                anyString(), anyString(), anyInt(), anyLong(), anyLong(), anyString(),
                anyString(), any(), any(), any(), anyInt(), any()
        );
        verifyNoInteractions(schoolSetupService);
    }

    @Test
    void detectedContextMismatchRequiresExplicitOverride() {
        MockMultipartFile file = file();
        Sf1ImportPreviewResponse mismatch = new Sf1ImportPreviewResponse(
                "2025-2026", "Rizal", "Grade 7", 0, 0, 0, List.of()
        );
        when(parser.generatePreview(file)).thenReturn(mismatch);

        V3FieldValidationException exception = assertThrows(
                V3FieldValidationException.class,
                () -> service.confirm(
                        PRINCIPAL, file, IMPORT_UUID, 2026, 8, "Narra", hash(file), false, null
                )
        );

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatus());
        assertTrue(exception.getErrors().containsKey("acceptContextMismatch"));
        verifyNoInteractions(schoolSetupService);
    }

    @Test
    void changedFileAfterPreviewIsRejectedBeforeDatabaseWrites() {
        MockMultipartFile file = file();

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.confirm(
                        PRINCIPAL, file, IMPORT_UUID, 2026, 8, "Narra", "0".repeat(64), false, null
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("SF1_FILE_CHANGED", exception.getCode());
        verifyNoInteractions(parser, schoolSetupService);
    }

    @Test
    void teacherCannotUsePrincipalSf1Endpoints() {
        V3AuthenticatedUser teacher = new V3AuthenticatedUser(
                12L, "SCHOOL-001", "teacher@example.com", "teacher", "active", "session"
        );

        V3AuthException exception = assertThrows(
                V3AuthException.class,
                () -> service.preview(teacher, file(), 2026, 8, "Narra")
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verifyNoInteractions(parser, repository, schoolRepository, schoolSetupService);
    }

    private MockMultipartFile file() {
        return new MockMultipartFile(
                "file",
                "SF1_Grade8_Narra.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "fixture-content".getBytes(StandardCharsets.UTF_8)
        );
    }

    private Sf1ImportPreviewResponse parsed(List<Sf1PreviewRowDto> rows) {
        return new Sf1ImportPreviewResponse(
                "2025-2026", "Narra", "Grade 8", rows.size(), rows.size(), 0, rows
        );
    }

    private Sf1PreviewRowDto validRow(
            int row,
            String lrn,
            String firstName,
            String lastName,
            String gender
    ) {
        return new Sf1PreviewRowDto(row, lrn, firstName, lastName, gender, "VALID",
                "Preview row parsed successfully.");
    }

    private ReferenceContext referenceContext(Long existingClassId) {
        return new ReferenceContext(2026, "2025-2026", 8, "Grade 8", "Narra", existingClassId);
    }

    private V3ClassResponse targetClass() {
        return new V3ClassResponse(
                700L, 2026, "2025-2026", 80, 8, "Grade 8", "Narra", "active", 0, true
        );
    }

    private ClassContext classContext() {
        return new ClassContext(
                700L, "SCHOOL-001", 2026, "2025-2026", 80, 8,
                "Grade 8", "Narra", "active", 2
        );
    }

    private ImportHeader header(
            String status,
            String fileHash,
            int created,
            int updated,
            int unchanged,
            int conflicts,
            int invalid
    ) {
        return new ImportHeader(
                900L, IMPORT_UUID, "SCHOOL-001", 2026, 700L, 11L,
                "SF1_Grade8_Narra.xlsx", fileHash, status, created + updated + unchanged + conflicts + invalid,
                created, updated, unchanged, conflicts, invalid, NOW, NOW
        );
    }

    private String hash(MockMultipartFile file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.getBytes()));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
