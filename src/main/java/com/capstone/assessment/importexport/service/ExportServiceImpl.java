package com.capstone.assessment.importexport.service;

import com.capstone.assessment.analytics.dto.ItemAnalysisDto;
import com.capstone.assessment.analytics.dto.LmsDto;
import com.capstone.assessment.analytics.service.AnalyticsService;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;

@Service
public class ExportServiceImpl implements ExportService {

    private final AnalyticsService analyticsService;
    private final JdbcTemplate jdbcTemplate;

    public ExportServiceImpl(AnalyticsService analyticsService, JdbcTemplate jdbcTemplate) {
        this.analyticsService = analyticsService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public byte[] exportItemAnalysis(Long testId) {
        List<ItemAnalysisDto> itemAnalysis = analyticsService.getItemAnalysis(testId);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Item Analysis");
            CellStyle headerStyle = createHeaderStyle(workbook);

            createHeaderRow(sheet, headerStyle, "Test Part ID", "Item Number", "Correctness Percentage");

            int rowIndex = 1;
            for (ItemAnalysisDto item : itemAnalysis) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(item.testPartId());
                row.createCell(1).setCellValue(item.itemNumber());
                row.createCell(2).setCellValue(item.correctnessPercentage());
            }

            autoSizeColumns(sheet, 3);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to generate item analysis report.", exception);
        }
    }

    @Override
    public byte[] exportLmsReport(Long testId) {
        List<LmsDto> lmsReport = analyticsService.getLmsByTest(testId);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("LMS Report");
            CellStyle headerStyle = createHeaderStyle(workbook);

            createHeaderRow(sheet, headerStyle, "Competency ID", "Competency Name", "Mastery Rate", "Status");

            int rowIndex = 1;
            for (LmsDto item : lmsReport) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(item.competencyId());
                row.createCell(1).setCellValue(item.competencyName());
                row.createCell(2).setCellValue(item.masteryRate());
                row.createCell(3).setCellValue(item.status());
            }

            autoSizeColumns(sheet, 4);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to generate LMS report.", exception);
        }
    }

    @Override
    public byte[] exportStudentScores(Long testId) {
        List<StudentScoreExportRow> studentScores = getStudentScores(testId);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Student Scores");
            CellStyle headerStyle = createHeaderStyle(workbook);

            createHeaderRow(
                    sheet,
                    headerStyle,
                    "Student ID",
                    "LRN",
                    "Student Name",
                    "Gender",
                    "Grade Level",
                    "Section",
                    "Assessment Name",
                    "Test ID",
                    "Total Score",
                    "Max Score",
                    "Percentage",
                    "Status / Performance",
                    "Checked At"
            );

            int rowIndex = 1;
            for (StudentScoreExportRow score : studentScores) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(score.studentId());
                row.createCell(1).setCellValue(score.studentLrn());
                row.createCell(2).setCellValue(score.studentName());
                row.createCell(3).setCellValue(score.gender());
                row.createCell(4).setCellValue(score.gradeLevel());
                row.createCell(5).setCellValue(score.section());
                row.createCell(6).setCellValue(score.assessmentName());
                row.createCell(7).setCellValue(score.testId());
                row.createCell(8).setCellValue(score.totalScore());
                row.createCell(9).setCellValue(score.maxScore());
                row.createCell(10).setCellValue(score.percentage());
                row.createCell(11).setCellValue(score.performance());
                row.createCell(12).setCellValue(score.checkedAt() == null ? "" : score.checkedAt().toLocalDateTime().toString());
            }

            autoSizeColumns(sheet, 13);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to generate student scores report.", exception);
        }
    }

    private List<StudentScoreExportRow> getStudentScores(Long testId) {
        String sql = """
                SELECT st.student_id,
                       st.student_lrn,
                       CONCAT(st.first_name, ' ', st.last_name) AS student_name,
                       st.gender,
                       gl.grade_level_name,
                       sec.section_name,
                       t.test_name,
                       t.test_id,
                       tr.total_score,
                       COALESCE(max_score.max_score, 0) AS max_score,
                       CASE
                           WHEN COALESCE(max_score.max_score, 0) = 0 THEN 0
                           ELSE ROUND(tr.total_score * 100.0 / max_score.max_score, 2)
                       END AS percentage,
                       tr.checked_at
                FROM test_result tr
                JOIN student st ON st.student_id = tr.student_id
                JOIN `test` t ON t.test_id = tr.test_id
                JOIN `class` c ON c.class_id = t.class_id
                JOIN section sec ON sec.section_id = c.section_id
                JOIN grade_level gl ON gl.grade_level_id = sec.grade_level_id
                LEFT JOIN (
                    SELECT test_id,
                           SUM(number_of_items * points_per_item) AS max_score
                    FROM test_part
                    GROUP BY test_id
                ) max_score ON max_score.test_id = t.test_id
                WHERE tr.test_id = ?
                ORDER BY st.last_name, st.first_name, st.student_id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            double percentage = rs.getDouble("percentage");
            return new StudentScoreExportRow(
                    rs.getLong("student_id"),
                    rs.getString("student_lrn"),
                    rs.getString("student_name"),
                    rs.getString("gender"),
                    rs.getString("grade_level_name"),
                    rs.getString("section_name"),
                    rs.getString("test_name"),
                    rs.getLong("test_id"),
                    rs.getInt("total_score"),
                    rs.getInt("max_score"),
                    percentage,
                    getPerformanceLabel(percentage),
                    rs.getObject("checked_at", Timestamp.class)
            );
        }, testId);
    }

    private String getPerformanceLabel(double percentage) {
        if (percentage >= 80.0) {
            return "Mastered";
        }

        if (percentage >= 60.0) {
            return "Developing";
        }

        return "Needs Support";
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);

        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);
        return headerStyle;
    }

    private void createHeaderRow(Sheet sheet, CellStyle headerStyle, String... headers) {
        Row headerRow = sheet.createRow(0);

        for (int index = 0; index < headers.length; index++) {
            headerRow.createCell(index).setCellValue(headers[index]);
            headerRow.getCell(index).setCellStyle(headerStyle);
        }
    }

    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int index = 0; index < columnCount; index++) {
            sheet.autoSizeColumn(index);
        }
    }

    private record StudentScoreExportRow(
            Long studentId,
            String studentLrn,
            String studentName,
            String gender,
            String gradeLevel,
            String section,
            String assessmentName,
            Long testId,
            Integer totalScore,
            Integer maxScore,
            Double percentage,
            String performance,
            Timestamp checkedAt
    ) {
    }
}
