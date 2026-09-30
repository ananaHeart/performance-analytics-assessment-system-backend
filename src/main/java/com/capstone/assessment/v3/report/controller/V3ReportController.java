package com.capstone.assessment.v3.report.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.report.dto.V3AssessmentResultsReportResponse;
import com.capstone.assessment.v3.report.dto.V3ConsolidatedReportResponse;
import com.capstone.assessment.v3.report.dto.V3ItemAnalysisReportResponse;
import com.capstone.assessment.v3.report.dto.V3LearningCompetencyReportResponse;
import com.capstone.assessment.v3.report.dto.V3ReportReferenceDataResponse;
import com.capstone.assessment.v3.report.dto.V3StudentPerformanceProfileResponse;
import com.capstone.assessment.v3.report.dto.V3SyncActivityReportResponse;
import com.capstone.assessment.v3.report.model.V3ReportModels.V3ReportGroupDimension;
import com.capstone.assessment.v3.report.service.V3ReportExportService;
import com.capstone.assessment.v3.report.service.V3ReportService;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Profile("v3")
@RestController
@RequestMapping("/api/v3/reports")
public class V3ReportController {

    private static final MediaType EXCEL_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final V3ReportService reportService;
    private final V3ReportExportService exportService;

    public V3ReportController(V3ReportService reportService, V3ReportExportService exportService) {
        this.reportService = reportService;
        this.exportService = exportService;
    }

    @GetMapping("/reference-data")
    public ResponseEntity<ApiResponse<V3ReportReferenceDataResponse>> referenceData(
            @AuthenticationPrincipal V3AuthenticatedUser user
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 report reference data retrieved successfully.",
                reportService.getReferenceData(user)
        ));
    }

    @GetMapping("/assessment-results")
    public ResponseEntity<ApiResponse<V3AssessmentResultsReportResponse>> assessmentResults(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @RequestParam long testId,
            @RequestParam long classAssignmentId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 assessment-results report generated successfully.",
                reportService.getAssessmentResults(user, testId, classAssignmentId)
        ));
    }

    @GetMapping("/assessment-results/excel")
    public ResponseEntity<byte[]> assessmentResultsExcel(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @RequestParam long testId,
            @RequestParam long classAssignmentId
    ) {
        var report = reportService.getAssessmentResults(user, testId, classAssignmentId);
        byte[] bytes = exportService.exportAssessmentResultsExcel(report);
        return ResponseEntity.ok()
                .contentType(EXCEL_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=class-record-%d-%d.xlsx".formatted(testId, classAssignmentId))
                .body(bytes);
    }

    @GetMapping("/assessment-results/pdf")
    public ResponseEntity<byte[]> assessmentResultsPdf(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @RequestParam long testId,
            @RequestParam long classAssignmentId
    ) {
        var report = reportService.getAssessmentResults(user, testId, classAssignmentId);
        byte[] bytes = exportService.exportAssessmentResultsPdf(report);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=class-record-%d-%d.pdf".formatted(testId, classAssignmentId))
                .body(bytes);
    }

    @GetMapping("/item-analysis")
    public ResponseEntity<ApiResponse<V3ItemAnalysisReportResponse>> itemAnalysis(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @RequestParam long testId,
            @RequestParam long classAssignmentId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 item-analysis report generated successfully.",
                reportService.getItemAnalysisReport(user, testId, classAssignmentId)
        ));
    }

    @GetMapping("/item-analysis/excel")
    public ResponseEntity<byte[]> itemAnalysisExcel(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @RequestParam long testId,
            @RequestParam long classAssignmentId
    ) {
        var report = reportService.getItemAnalysisReport(user, testId, classAssignmentId);
        byte[] bytes = exportService.exportItemAnalysisExcel(report);
        return ResponseEntity.ok()
                .contentType(EXCEL_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=item-analysis-%d-%d.xlsx".formatted(testId, classAssignmentId))
                .body(bytes);
    }

    @GetMapping("/item-analysis/pdf")
    public ResponseEntity<byte[]> itemAnalysisPdf(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @RequestParam long testId,
            @RequestParam long classAssignmentId
    ) {
        var report = reportService.getItemAnalysisReport(user, testId, classAssignmentId);
        byte[] bytes = exportService.exportItemAnalysisPdf(report);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=item-analysis-%d-%d.pdf".formatted(testId, classAssignmentId))
                .body(bytes);
    }

    @GetMapping("/consolidated")
    public ResponseEntity<ApiResponse<V3ConsolidatedReportResponse>> consolidated(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @RequestParam V3ReportGroupDimension groupBy,
            @RequestParam(required = false) Integer academicYearId,
            @RequestParam(required = false) Integer termPeriodId,
            @RequestParam(required = false) Integer gradeLevelId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long teacherUserId,
            @RequestParam(required = false) Integer subjectId,
            @RequestParam(required = false) Long testId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 consolidated report generated successfully.",
                reportService.getConsolidatedReport(
                        user, groupBy, academicYearId, termPeriodId, gradeLevelId,
                        classId, teacherUserId, subjectId, testId)
        ));
    }

    @GetMapping("/consolidated/excel")
    public ResponseEntity<byte[]> consolidatedExcel(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @RequestParam V3ReportGroupDimension groupBy,
            @RequestParam(required = false) Integer academicYearId,
            @RequestParam(required = false) Integer termPeriodId,
            @RequestParam(required = false) Integer gradeLevelId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long teacherUserId,
            @RequestParam(required = false) Integer subjectId,
            @RequestParam(required = false) Long testId
    ) {
        var report = reportService.getConsolidatedReport(
                user, groupBy, academicYearId, termPeriodId, gradeLevelId,
                classId, teacherUserId, subjectId, testId);
        byte[] bytes = exportService.exportConsolidatedExcel(report);
        return ResponseEntity.ok()
                .contentType(EXCEL_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=consolidated-report-%s.xlsx".formatted(groupBy.name().toLowerCase()))
                .body(bytes);
    }

    @GetMapping("/consolidated/pdf")
    public ResponseEntity<byte[]> consolidatedPdf(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @RequestParam V3ReportGroupDimension groupBy,
            @RequestParam(required = false) Integer academicYearId,
            @RequestParam(required = false) Integer termPeriodId,
            @RequestParam(required = false) Integer gradeLevelId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long teacherUserId,
            @RequestParam(required = false) Integer subjectId,
            @RequestParam(required = false) Long testId
    ) {
        var report = reportService.getConsolidatedReport(
                user, groupBy, academicYearId, termPeriodId, gradeLevelId,
                classId, teacherUserId, subjectId, testId);
        byte[] bytes = exportService.exportConsolidatedPdf(report);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=consolidated-report-%s.pdf".formatted(groupBy.name().toLowerCase()))
                .body(bytes);
    }

    @GetMapping("/learning-competency")
    public ResponseEntity<ApiResponse<V3LearningCompetencyReportResponse>> learningCompetency(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @RequestParam int termPeriodId,
            @RequestParam int gradeLevelId,
            @RequestParam int subjectId,
            @RequestParam(required = false) Long rootTagId,
            @RequestParam(required = false) Long skillId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 learning competency report generated successfully.",
                reportService.getLearningCompetencyReport(user, termPeriodId, gradeLevelId, subjectId, rootTagId, skillId)
        ));
    }

    @GetMapping("/learning-competency/excel")
    public ResponseEntity<byte[]> learningCompetencyExcel(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @RequestParam int termPeriodId,
            @RequestParam int gradeLevelId,
            @RequestParam int subjectId,
            @RequestParam(required = false) Long rootTagId,
            @RequestParam(required = false) Long skillId
    ) {
        var report = reportService.getLearningCompetencyReport(
                user, termPeriodId, gradeLevelId, subjectId, rootTagId, skillId);
        byte[] bytes = exportService.exportLearningCompetencyExcel(report);
        return ResponseEntity.ok()
                .contentType(EXCEL_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=learning-competency-%d-%d-%d.xlsx"
                                .formatted(termPeriodId, gradeLevelId, subjectId))
                .body(bytes);
    }

    @GetMapping("/learning-competency/pdf")
    public ResponseEntity<byte[]> learningCompetencyPdf(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @RequestParam int termPeriodId,
            @RequestParam int gradeLevelId,
            @RequestParam int subjectId,
            @RequestParam(required = false) Long rootTagId,
            @RequestParam(required = false) Long skillId
    ) {
        var report = reportService.getLearningCompetencyReport(
                user, termPeriodId, gradeLevelId, subjectId, rootTagId, skillId);
        byte[] bytes = exportService.exportLearningCompetencyPdf(report);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=learning-competency-%d-%d-%d.pdf"
                                .formatted(termPeriodId, gradeLevelId, subjectId))
                .body(bytes);
    }

    @GetMapping("/sync-activity")
    public ResponseEntity<ApiResponse<V3SyncActivityReportResponse>> syncActivity(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @RequestParam(required = false) Integer academicYearId,
            @RequestParam(required = false) Integer termPeriodId,
            @RequestParam(required = false) Long teacherUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 teacher sync activity report generated successfully.",
                reportService.getSyncActivityReport(user, academicYearId, termPeriodId, teacherUserId, from, to)
        ));
    }

    @GetMapping("/sync-activity/excel")
    public ResponseEntity<byte[]> syncActivityExcel(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @RequestParam(required = false) Integer academicYearId,
            @RequestParam(required = false) Integer termPeriodId,
            @RequestParam(required = false) Long teacherUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        var report = reportService.getSyncActivityReport(user, academicYearId, termPeriodId, teacherUserId, from, to);
        byte[] bytes = exportService.exportSyncActivityExcel(report);
        return ResponseEntity.ok()
                .contentType(EXCEL_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=teacher-sync-activity.xlsx")
                .body(bytes);
    }

    @GetMapping("/sync-activity/pdf")
    public ResponseEntity<byte[]> syncActivityPdf(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @RequestParam(required = false) Integer academicYearId,
            @RequestParam(required = false) Integer termPeriodId,
            @RequestParam(required = false) Long teacherUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        var report = reportService.getSyncActivityReport(user, academicYearId, termPeriodId, teacherUserId, from, to);
        byte[] bytes = exportService.exportSyncActivityPdf(report);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=teacher-sync-activity.pdf")
                .body(bytes);
    }

    @GetMapping("/student-performance-profile")
    public ResponseEntity<ApiResponse<V3StudentPerformanceProfileResponse>> studentPerformanceProfile(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @RequestParam long studentId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 student performance profile generated successfully.",
                reportService.getStudentPerformanceProfile(user, studentId)
        ));
    }

    @GetMapping("/student-performance-profile/excel")
    public ResponseEntity<byte[]> studentPerformanceProfileExcel(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @RequestParam long studentId
    ) {
        var report = reportService.getStudentPerformanceProfile(user, studentId);
        byte[] bytes = exportService.exportStudentPerformanceProfileExcel(report);
        return ResponseEntity.ok()
                .contentType(EXCEL_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=student-performance-%d.xlsx".formatted(studentId))
                .body(bytes);
    }

    @GetMapping("/student-performance-profile/pdf")
    public ResponseEntity<byte[]> studentPerformanceProfilePdf(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @RequestParam long studentId
    ) {
        var report = reportService.getStudentPerformanceProfile(user, studentId);
        byte[] bytes = exportService.exportStudentPerformanceProfilePdf(report);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=student-performance-%d.pdf".formatted(studentId))
                .body(bytes);
    }
}
