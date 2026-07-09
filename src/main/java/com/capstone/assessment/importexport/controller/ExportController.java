package com.capstone.assessment.importexport.controller;

import com.capstone.assessment.importexport.service.ExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private static final MediaType EXCEL_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    // Generates an Excel file for item analysis results for teacher or principal reporting.
    @GetMapping("/item-analysis/{testId}")
    public ResponseEntity<byte[]> exportItemAnalysis(@PathVariable Long testId) {
        byte[] fileBytes = exportService.exportItemAnalysis(testId);

        return ResponseEntity.ok()
                .contentType(EXCEL_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=item-analysis-test-%d.xlsx".formatted(testId))
                .body(fileBytes);
    }

    // Generates an Excel file for least mastered skills based on backend analytics data.
    @GetMapping("/lms/{testId}")
    public ResponseEntity<byte[]> exportLmsReport(@PathVariable Long testId) {
        byte[] fileBytes = exportService.exportLmsReport(testId);

        return ResponseEntity.ok()
                .contentType(EXCEL_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=lms-report-test-%d.xlsx".formatted(testId))
                .body(fileBytes);
    }

    // Generates an Excel file containing checked student scores for one selected assessment.
    @GetMapping("/student-scores/{testId}")
    public ResponseEntity<byte[]> exportStudentScores(@PathVariable Long testId) {
        byte[] fileBytes = exportService.exportStudentScores(testId);

        return ResponseEntity.ok()
                .contentType(EXCEL_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=student-scores-test-%d.xlsx".formatted(testId))
                .body(fileBytes);
    }
}
