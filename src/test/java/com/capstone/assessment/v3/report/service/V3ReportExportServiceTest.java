package com.capstone.assessment.v3.report.service;

import com.capstone.assessment.v3.report.dto.V3ConsolidatedReportResponse;
import com.capstone.assessment.v3.report.dto.V3LearningCompetencyReportResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class V3ReportExportServiceTest {

    private final V3ReportExportService exportService = new V3ReportExportService();

    private static V3ConsolidatedReportResponse consolidatedReport() {
        return new V3ConsolidatedReportResponse(
                "principal_consolidated",
                Instant.parse("2026-09-24T02:00:00Z"),
                "TEACHER",
                "available",
                List.of(),
                List.of(new V3ConsolidatedReportResponse.GroupRow(
                        "910018",
                        "Heart Millan Añana",
                        6,
                        new BigDecimal("41.67"),
                        // Real test_results.performance_status codes, as returned by the repository.
                        List.of(
                                new V3ConsolidatedReportResponse.MasteryStatusCount("maintain", 2),
                                new V3ConsolidatedReportResponse.MasteryStatusCount("reteach", 3),
                                new V3ConsolidatedReportResponse.MasteryStatusCount("priority_intervention", 1)
                        ),
                        List.of(new V3ConsolidatedReportResponse.LeastMasteredSkill(
                                910120L, "Parts of Speech", BigDecimal.ZERO, "needs_support"))
                ))
        );
    }

    @Test
    void consolidatedExcelCountsStudentsPerPerformanceBand() throws Exception {
        byte[] bytes = exportService.exportConsolidatedExcel(consolidatedReport());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("Consolidated Summary");
            Row header = findRowStartingWith(sheet, "Group");
            Row data = findRowStartingWith(sheet, "Heart Millan Añana");
            assertEquals("Maintain", header.getCell(3).getStringCellValue());
            assertEquals("Priority Intervention", header.getCell(6).getStringCellValue());
            assertEquals(2, (int) data.getCell(3).getNumericCellValue(), "maintain");
            assertEquals(0, (int) data.getCell(4).getNumericCellValue(), "review");
            assertEquals(3, (int) data.getCell(5).getNumericCellValue(), "reteach");
            assertEquals(1, (int) data.getCell(6).getNumericCellValue(), "priority_intervention");
        }
    }

    @Test
    void consolidatedPdfRenders() {
        byte[] bytes = exportService.exportConsolidatedPdf(consolidatedReport());

        assertTrue(new String(bytes, 0, 5, StandardCharsets.US_ASCII).startsWith("%PDF"));
    }

    private static V3LearningCompetencyReportResponse learningCompetencyReport(int weakStudentCount) {
        List<V3LearningCompetencyReportResponse.WeakStudent> weakStudents = new ArrayList<>();
        for (int i = 0; i < weakStudentCount; i++) {
            weakStudents.add(new V3LearningCompetencyReportResponse.WeakStudent(
                    1000L + i, "Student " + i, "Rizal", 2, new BigDecimal("35.00"), "needs_support",
                    "priority_intervention", "Priority Intervention",
                    "Prioritize intervention for Parts of Speech and monitor affected learners."));
        }
        return new V3LearningCompetencyReportResponse(
                "learning_competency",
                Instant.parse("2026-09-25T02:00:00Z"),
                new V3LearningCompetencyReportResponse.Scope("SCHOOL-001", "SMART School", 1, "2026-2027",
                        11, "First Quarter", 7, "Grade 7", 3, "English"),
                "available",
                List.of(),
                List.of(new V3LearningCompetencyReportResponse.IncludedAssessment(1001L, "Quiz 1")),
                List.of(new V3LearningCompetencyReportResponse.RootCompetency(
                        910001L, "Grammar", new BigDecimal("52.50"), "needs_support",
                        List.of(
                                new V3LearningCompetencyReportResponse.Skill(910120L, 910130L, "Parts of Speech",
                                        weakStudentCount + 1, new BigDecimal("40.00"), "needs_support",
                                        "reteach", "Reteach",
                                        // Long on purpose: must wrap onto several lines, not run off the page.
                                        "Reteach Parts of Speech using a different strategy, guided practice, "
                                                + "worked examples and short formative checks before the next "
                                                + "summative assessment of the quarter.",
                                        weakStudentCount, weakStudents),
                                new V3LearningCompetencyReportResponse.Skill(910121L, 910131L, "Verb Tenses",
                                        weakStudentCount + 1, new BigDecimal("90.00"), "mastered",
                                        "maintain", "Maintain", "Maintain Verb Tenses with enrichment.",
                                        0, List.of())
                        ))),
                "MARIA L. SANTOS",
                "Teacher"
        );
    }

    /** Table rows are the ones numbered 1, 2, 3... in the first column. */
    private static List<Row> numberedRows(Sheet sheet) {
        List<Row> rows = new ArrayList<>();
        for (Row row : sheet) {
            if (row.getCell(0) != null && row.getCell(0).getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                rows.add(row);
            }
        }
        return rows;
    }

    @Test
    void learningCompetencyExcelListsEveryCompetencyAndOnlyWeakStudents() throws Exception {
        byte[] bytes = exportService.exportLearningCompetencyExcel(learningCompetencyReport(3));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet competencies = workbook.getSheet("Competencies");
            Row header = findRowStartingWith(competencies, "No.");
            assertEquals("Intervention", header.getCell(5).getStringCellValue());
            List<Row> competencyRows = numberedRows(competencies);
            assertEquals(2, competencyRows.size(), "one row per competency");
            // Least mastered first: Parts of Speech (40%) before Verb Tenses (90%).
            assertEquals("Parts of Speech", competencyRows.get(0).getCell(1).getStringCellValue());
            assertEquals("Reteach", competencyRows.get(0).getCell(5).getStringCellValue());
            assertEquals(3, numberedRows(workbook.getSheet("Affected Learners")).size(), "one row per weak student");
        }
    }

    @Test
    void learningCompetencyPdfRendersAcrossPageBreaks() throws Exception {
        byte[] bytes = exportService.exportLearningCompetencyPdf(learningCompetencyReport(80));

        assertTrue(new String(bytes, 0, 5, StandardCharsets.US_ASCII).startsWith("%PDF"));
        try (PDDocument document = Loader.loadPDF(bytes)) {
            assertTrue(document.getNumberOfPages() >= 3, "80 weak students cannot fit on fewer than 3 pages");
            // Line breaks from wrapping are irrelevant to the check.
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertTrue(text.contains("Reteach Parts of Speech using a different strategy"), text);
            assertTrue(text.contains("summative assessment of the quarter."), "long suggestion must wrap, not be cut");
            assertTrue(text.contains("Maria L. Santos"), "signed by the teacher who generated it");
        }
    }

    @Test
    void dateConductedShowsTheOpenDayOrTheOpenToCloseRangeInPhilippineTime() {
        // 16:30 UTC is already the next day (00:30) in Manila.
        assertEquals("Oct 1, 2026", V3ReportExportService.dateConducted(Instant.parse("2026-09-30T16:30:00Z"), null));
        assertEquals("Sep 30, 2026", V3ReportExportService.dateConducted(
                Instant.parse("2026-09-30T01:00:00Z"), Instant.parse("2026-09-30T08:00:00Z")));
        assertEquals("Sep 15 – 17, 2026", V3ReportExportService.dateConducted(
                Instant.parse("2026-09-15T01:00:00Z"), Instant.parse("2026-09-17T08:00:00Z")));
        assertEquals("Sep 30 – Oct 2, 2026", V3ReportExportService.dateConducted(
                Instant.parse("2026-09-30T01:00:00Z"), Instant.parse("2026-10-02T08:00:00Z")));
        assertEquals("Dec 30, 2026 – Jan 4, 2027", V3ReportExportService.dateConducted(
                Instant.parse("2026-12-30T01:00:00Z"), Instant.parse("2027-01-04T08:00:00Z")));
        assertEquals("N/A", V3ReportExportService.dateConducted(null, null));
    }

    @Test
    void personNamesPrintInProperCase() {
        assertEquals("Ana Marie Dela Cruz", V3ReportExportService.personName("ANA MARIE DELA CRUZ"));
        // Real mixed-case row from the live DB.
        assertEquals("Carlo Andrei Flores Millan Mendoza",
                V3ReportExportService.personName("CARLO ANDREI FLORES Millan MENDOZA"));
        assertEquals("Anne-Marie O'Brien", V3ReportExportService.personName("ANNE-MARIE O'BRIEN"));
        assertEquals("Juan Dela Cruz III", V3ReportExportService.personName("juan dela cruz iii"));
        assertEquals("Maria L. Santos", V3ReportExportService.personName("Maria L. Santos"));
        assertEquals("Heart Millan Añana", V3ReportExportService.personName("HEART MILLAN AÑANA"));
    }

    /** Guards against tables whose columns run past the right margin (or off the page). */
    @Test
    void everyPdfTableFitsInsideThePageMargins() throws Exception {
        float contentWidth = PDRectangle.A4.getWidth() - 2 * 40f;
        int checked = 0;
        for (Field field : V3ReportExportService.class.getDeclaredFields()) {
            if (field.getType() == float[].class && field.getName().endsWith("_WIDTHS")) {
                field.setAccessible(true);
                float total = 0;
                for (float width : (float[]) field.get(null)) {
                    total += width;
                }
                assertTrue(total <= contentWidth + 0.5f,
                        field.getName() + " is " + total + "pt wide but only " + contentWidth + "pt fits");
                checked++;
            }
        }
        assertTrue(checked >= 10, "expected to find the PDF column-width tables, found " + checked);
    }

    private static Row findRowStartingWith(Sheet sheet, String text) {
        for (Row row : sheet) {
            if (row.getCell(0) != null && text.equals(row.getCell(0).getStringCellValue())) {
                return row;
            }
        }
        return fail("No row starting with '" + text + "'");
    }
}
