package com.capstone.assessment.v3.report.service;

import com.capstone.assessment.v3.report.dto.V3AssessmentResultsReportResponse;
import com.capstone.assessment.v3.report.dto.V3AssessmentResultsReportResponse.StudentResultRow;
import com.capstone.assessment.v3.report.dto.V3ConsolidatedReportResponse;
import com.capstone.assessment.v3.report.dto.V3ConsolidatedReportResponse.GroupRow;
import com.capstone.assessment.v3.report.dto.V3ConsolidatedReportResponse.LeastMasteredSkill;
import com.capstone.assessment.v3.report.dto.V3ItemAnalysisReportResponse;
import com.capstone.assessment.v3.report.dto.V3ItemAnalysisReportResponse.CompetencyMasteryRow;
import com.capstone.assessment.v3.report.dto.V3ItemAnalysisReportResponse.QuestionRow;
import com.capstone.assessment.v3.report.dto.V3LearningCompetencyReportResponse;
import com.capstone.assessment.v3.report.dto.V3StudentPerformanceProfileResponse;
import com.capstone.assessment.v3.report.dto.V3SyncActivityReportResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders the V3 report DTOs as Excel and PDF files.
 * Kept intentionally simple (school capstone, read by non-IT staff): a light
 * brand color, bold headers, cell borders/zebra striping and a page footer -
 * not a full corporate report template.
 */
@Profile("v3")
@Service
public class V3ReportExportService {

    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("MMMM d, uuuu h:mm a", Locale.ENGLISH).withZone(ZoneId.of("Asia/Manila"));

    // ---------- Shared visual theme ----------

    private static final float[] BRAND_RGB = {0.055f, 0.420f, 0.235f};
    private static final float[] ZEBRA_RGB = {0.933f, 0.965f, 0.945f};
    private static final float[] GRID_RGB = {0.80f, 0.85f, 0.82f};
    private static final byte[] BRAND_RGB_BYTES = {(byte) 14, (byte) 107, (byte) 60};
    private static final byte[] ZEBRA_RGB_BYTES = {(byte) 238, (byte) 247, (byte) 240};

    // ---------- Excel ----------

    // ---------- Shared teacher-report formatting ----------

    /** One date style everywhere, e.g. "Sep 30, 2026 10:24 AM". */
    private static final DateTimeFormatter REPORT_DATE_TIME =
            DateTimeFormatter.ofPattern("MMM d, uuuu h:mm a", Locale.ENGLISH).withZone(ZoneId.of("Asia/Manila"));
    private static final String NOT_AVAILABLE = "N/A";
    private static final String SCHOOL_FOOTER = "For School Use Only";
    /** Bands of the school's seeded performance rule sets (student_score / intervention). */
    private static final String PERFORMANCE_LEGEND = "Performance level:  Maintain 80% and above   ·   Review 60-79.99%"
            + "   ·   Reteach 40-59.99%   ·   Priority Intervention below 40%";

    /** 88.00 -> "88", 1.50 -> "1.5": scores read like scores, not like money. */
    private static String points(BigDecimal value) {
        return value == null ? NOT_AVAILABLE : value.stripTrailingZeros().toPlainString();
    }

    /** Every percentage in every report: exactly two decimals, e.g. "56.67%". */
    private static String percent(BigDecimal value) {
        return value == null ? NOT_AVAILABLE : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static String orNotAvailable(String value) {
        return value == null || value.isBlank() ? NOT_AVAILABLE : value;
    }

    private static final java.util.regex.Pattern ROMAN_NUMERAL = java.util.regex.Pattern.compile("[IVXivx]{1,4}\\.?");

    /** Person names are stored as typed (often ALL CAPS from SF1); reports print them in
     *  proper case - "ANA MARIE DELA CRUZ" -> "Ana Marie Dela Cruz" - keeping hyphenated and
     *  apostrophe names ("Anne-Marie", "O'Brien") and suffixes like "III" right. Display only. */
    static String personName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        StringBuilder out = new StringBuilder(name.length());
        for (String word : name.trim().split("\\s+")) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            if (ROMAN_NUMERAL.matcher(word).matches() && word.length() > 1) {
                out.append(word.toUpperCase(Locale.ROOT));
                continue;
            }
            boolean capitalizeNext = true;
            for (char ch : word.toLowerCase(Locale.ROOT).toCharArray()) {
                out.append(capitalizeNext ? Character.toUpperCase(ch) : ch);
                capitalizeNext = ch == '-' || ch == '\'';
            }
        }
        return out.toString();
    }

    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH);

    /** When the assessment was conducted: its open date, or open-close range when it closes on a
     *  later day - "Sep 30, 2026", "Sep 15 – 17, 2026", "Sep 30 – Oct 2, 2026",
     *  "Dec 30, 2026 – Jan 4, 2027". */
    static String dateConducted(Instant openAt, Instant closeAt) {
        if (openAt == null) {
            return NOT_AVAILABLE;
        }
        java.time.LocalDate open = openAt.atZone(REPORT_ZONE).toLocalDate();
        java.time.LocalDate close = closeAt == null ? null : closeAt.atZone(REPORT_ZONE).toLocalDate();
        if (close == null || !close.isAfter(open)) {
            return DAY_MONTH_YEAR.format(open);
        }
        if (close.getYear() == open.getYear() && close.getMonth() == open.getMonth()) {
            return DAY_MONTH.format(open) + " – " + close.getDayOfMonth() + ", " + close.getYear();
        }
        if (close.getYear() == open.getYear()) {
            return DAY_MONTH.format(open) + " – " + DAY_MONTH_YEAR.format(close);
        }
        return DAY_MONTH_YEAR.format(open) + " – " + DAY_MONTH_YEAR.format(close);
    }

    private static String gradeSection(String grade, String section) {
        if (grade == null || grade.isBlank()) return orNotAvailable(section);
        if (section == null || section.isBlank()) return grade;
        return grade + " - " + section;
    }

    /** Status meaning is carried by text color only: green good, amber/orange watch, red urgent. */
    private static java.awt.Color statusColor(String code) {
        if (code == null) return ReportPdfDocument.MUTED;
        return switch (code.toLowerCase(Locale.ROOT)) {
            case "maintain", "mastered" -> ReportPdfDocument.GOOD;
            case "review" -> ReportPdfDocument.AMBER;
            case "developing", "reteach" -> ReportPdfDocument.ORANGE;
            case "needs_support", "priority_intervention" -> ReportPdfDocument.BAD;
            default -> ReportPdfDocument.MUTED;
        };
    }

    private static List<ReportPdfDocument.Column> columns(String[] headers, float[] widths, boolean[] centered) {
        List<ReportPdfDocument.Column> columns = new java.util.ArrayList<>();
        for (int i = 0; i < headers.length; i++) {
            columns.add(centered[i]
                    ? ReportPdfDocument.Column.center(headers[i], widths[i])
                    : ReportPdfDocument.Column.left(headers[i], widths[i]));
        }
        return columns;
    }

    // ---------- Assessment Results / Class Record ----------

    private static final float[] CLASS_RECORD_WIDTHS = {28f, 195f, 62f, 62f, 70f, 98f};
    private static final String[] CLASS_RECORD_HEADERS =
            {"#", "Student Name", "Score", "Max Score", "Percentage", "Performance Level"};

    private List<String[]> classRecordDetailsLeft(V3AssessmentResultsReportResponse report) {
        return assessmentDetailsLeft(report.scope());
    }

    private List<String[]> classRecordDetailsRight(V3AssessmentResultsReportResponse report) {
        var scope = report.scope();
        return List.of(
                new String[]{"Term", orNotAvailable(scope.termName())},
                new String[]{"School Year", orNotAvailable(scope.academicYearName())},
                new String[]{"Date Conducted", dateConducted(scope.openAt(), scope.closeAt())},
                new String[]{"Generated On", REPORT_DATE_TIME.format(report.generatedAt())});
    }

    /** Class mean, highest, lowest and the share at the Maintain level - there is no
     *  pass/fail in this system, so no "passing rate". */
    private List<ReportPdfDocument.Card> classRecordCards(V3AssessmentResultsReportResponse report) {
        var summary = report.summary();
        List<StudentResultRow> verified = report.rows().stream()
                .filter(row -> "finalized".equals(row.resultStatus()) && row.earnedPoints() != null)
                .toList();
        StudentResultRow highest = verified.stream()
                .max(java.util.Comparator.comparing(StudentResultRow::earnedPoints)).orElse(null);
        StudentResultRow lowest = verified.stream()
                .min(java.util.Comparator.comparing(StudentResultRow::earnedPoints)).orElse(null);
        long maintain = verified.stream().filter(row -> "maintain".equals(row.performanceStatusCode())).count();
        String maintainShare = verified.isEmpty() ? NOT_AVAILABLE : percent(
                BigDecimal.valueOf(maintain * 100).divide(BigDecimal.valueOf(verified.size()), 2, java.math.RoundingMode.HALF_UP));
        return List.of(
                new ReportPdfDocument.Card("Students Checked",
                        summary.verifiedCount() + " of " + summary.studentCount(), "(results verified)"),
                new ReportPdfDocument.Card("Class Mean", points(summary.classMeanPoints()),
                        summary.classMeanPercentage() == null ? "No verified results" : "(" + percent(summary.classMeanPercentage()) + ")"),
                new ReportPdfDocument.Card("Highest Score", highest == null ? NOT_AVAILABLE : points(highest.earnedPoints()),
                        highest == null ? " " : "(" + percent(highest.percentage()) + ")"),
                new ReportPdfDocument.Card("Lowest Score", lowest == null ? NOT_AVAILABLE : points(lowest.earnedPoints()),
                        lowest == null ? " " : "(" + percent(lowest.percentage()) + ")"),
                new ReportPdfDocument.Card("At Maintain Level (80%+)", maintainShare,
                        "(" + maintain + " of " + verified.size() + " students)"));
    }

    private record ClassRecordLine(String number, String name, String score, BigDecimal scoreValue, String maximum,
                                   BigDecimal maximumValue, String percentage, BigDecimal percentageValue,
                                   String performance, java.awt.Color color) {
    }

    private List<ClassRecordLine> classRecordLines(V3AssessmentResultsReportResponse report) {
        List<ClassRecordLine> lines = new java.util.ArrayList<>();
        int number = 1;
        for (StudentResultRow row : report.rows()) {
            BigDecimal maximum = row.maximumPoints() != null ? row.maximumPoints() : report.summary().maximumPoints();
            String performance;
            java.awt.Color color;
            if ("finalized".equals(row.resultStatus())) {
                performance = orNotAvailable(row.performanceStatusLabel());
                color = statusColor(row.performanceStatusCode());
            } else if ("not_submitted".equals(row.resultStatus())) {
                performance = "Not submitted";
                color = ReportPdfDocument.MUTED;
            } else {
                performance = "Pending verification";
                color = ReportPdfDocument.MUTED;
            }
            lines.add(new ClassRecordLine(Integer.toString(number++), orNotAvailable(personName(row.fullName())),
                    points(row.earnedPoints()), row.earnedPoints(), points(maximum), maximum,
                    percent(row.percentage()), row.percentage(), performance, color));
        }
        return lines;
    }

    public byte[] exportAssessmentResultsExcel(V3AssessmentResultsReportResponse report) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var styles = ReportExcelSheet.newStyleCache();
            ReportExcelSheet sheet = new ReportExcelSheet(workbook, "Class Record",
                    new float[]{7f, 34f, 14f, 14f, 14f, 22f}, styles);
            sheet.header(report.scope().schoolName(), "ASSESSMENT RESULTS / CLASS RECORD");
            sheet.section("Assessment Details");
            List<String[]> details = new java.util.ArrayList<>(classRecordDetailsLeft(report));
            details.addAll(classRecordDetailsRight(report));
            sheet.details(details, List.of(), 1, 5, 0, 0);
            sheet.section("Class Summary");
            sheet.cards(classRecordCards(report), new int[][]{{1, 1}, {2, 2}, {3, 3}, {4, 4}, {5, 5}});
            sheet.section("Class Record");
            sheet.tableHeader(CLASS_RECORD_HEADERS);
            boolean[] centered = {true, false, true, true, true, true};
            for (ClassRecordLine line : classRecordLines(report)) {
                sheet.tableRow(new Object[]{
                        Integer.valueOf(line.number()), line.name(),
                        line.scoreValue() == null ? NOT_AVAILABLE : line.scoreValue(),
                        line.maximumValue() == null ? NOT_AVAILABLE : line.maximumValue(),
                        line.percentageValue() == null ? NOT_AVAILABLE : line.percentageValue(),
                        line.performance()
                }, new java.awt.Color[]{null, null, null, null, null, line.color()}, centered, "....%.");
            }
            sheet.skip(1);
            for (var warning : report.warnings()) {
                sheet.note("Note: " + warning.message());
            }
            sheet.note(PERFORMANCE_LEGEND);
            sheet.signature(personName(report.scope().teacherName()), "Teacher", 1);
            sheet.footer(SCHOOL_FOOTER);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate assessment-results Excel export.", e);
        }
    }

    // ---------- Item Analysis and Competency Mastery ----------

    private static final float[] ITEM_ANALYSIS_WIDTHS = {45f, 100f, 60f, 62f, 70f, 178f};
    private static final String[] ITEM_ANALYSIS_HEADERS =
            {"Item #", "Type", "Correct", "Incorrect", "Unanswered", "Skill / Competency"};
    private static final boolean[] ITEM_ANALYSIS_CENTERED = {true, false, true, true, true, false};
    private static final float[] MASTERY_WIDTHS = {215f, 75f, 65f, 70f, 90f};
    private static final String[] MASTERY_HEADERS =
            {"Competency / Skill", "Assessed Items", "Students", "Mastery %", "Status"};
    private static final boolean[] MASTERY_CENTERED = {false, true, true, true, true};
    private static final float[] STUDENTS_PER_SKILL_WIDTHS = {130f, 150f, 65f, 75f, 95f};
    private static final String[] STUDENTS_PER_SKILL_HEADERS =
            {"Competency / Skill", "Student", "Mastery %", "Status", "Recommendation"};
    private static final boolean[] STUDENTS_PER_SKILL_CENTERED = {false, false, true, true, true};
    private static final String MASTERY_LEGEND = "Mastery status:  Mastered 80% and above   ·   Developing 60-79.99%"
            + "   ·   Needs Support below 60%";
    private static final String RECOMMENDATION_LEGEND = "Recommendation:  Maintain 80% and above   ·   Review 60-79.99%"
            + "   ·   Reteach 40-59.99%   ·   Priority Intervention below 40%";

    private String questionTypeLabel(String code) {
        if (code == null) return NOT_AVAILABLE;
        return switch (code) {
            case "multiple_choice" -> "Multiple Choice";
            case "true_false" -> "True / False";
            case "identification" -> "Identification";
            case "enumeration" -> "Enumeration";
            case "essay" -> "Essay";
            default -> humanize(code);
        };
    }

    private static String masteryStatusLabel(String code) {
        if (code == null) return NOT_AVAILABLE;
        return switch (code) {
            case "mastered" -> "Mastered";
            case "developing" -> "Developing";
            case "needs_support" -> "Needs Support";
            case "insufficient_data" -> "No data";
            default -> code;
        };
    }

    private List<String[]> assessmentDetailsLeft(V3AssessmentResultsReportResponse.Scope scope) {
        return List.of(
                new String[]{"Teacher", orNotAvailable(personName(scope.teacherName()))},
                new String[]{"Grade & Section", gradeSection(scope.gradeLevelName(), scope.sectionName())},
                new String[]{"Subject", orNotAvailable(scope.subjectName())},
                new String[]{"Assessment", orNotAvailable(scope.testName())});
    }

    private List<String[]> itemAnalysisDetailsRight(V3ItemAnalysisReportResponse report) {
        return List.of(
                new String[]{"Term", orNotAvailable(report.scope().termName())},
                new String[]{"School Year", orNotAvailable(report.scope().academicYearName())},
                new String[]{"Date Conducted", dateConducted(report.scope().openAt(), report.scope().closeAt())},
                new String[]{"Generated On", REPORT_DATE_TIME.format(report.generatedAt())});
    }

    private List<ReportPdfDocument.Card> itemAnalysisCards(V3ItemAnalysisReportResponse report) {
        int enrolled = report.rows().isEmpty() ? 0 : report.rows().get(0).correctCount()
                + report.rows().get(0).incorrectCount() + report.rows().get(0).unansweredCount();
        int assessed = Math.max(
                report.rows().stream().mapToInt(row -> row.correctCount() + row.incorrectCount()).max().orElse(0),
                report.competencyMastery().stream().mapToInt(CompetencyMasteryRow::studentCount).max().orElse(0));
        long correct = report.rows().stream().mapToLong(QuestionRow::correctCount).sum();
        long answered = report.rows().stream().mapToLong(row -> row.correctCount() + row.incorrectCount()).sum();
        String correctRate = answered == 0 ? NOT_AVAILABLE : percent(BigDecimal.valueOf(correct * 100)
                .divide(BigDecimal.valueOf(answered), 2, java.math.RoundingMode.HALF_UP));
        long below = report.competencyMastery().stream()
                .filter(row -> row.masteryPercentage() != null
                        && row.masteryPercentage().compareTo(new BigDecimal("80")) < 0)
                .count();
        return List.of(
                new ReportPdfDocument.Card("Total Items", Integer.toString(report.rows().size()), "(questions)"),
                new ReportPdfDocument.Card("Students Assessed", Integer.toString(assessed), "(of " + enrolled + " enrolled)"),
                new ReportPdfDocument.Card("Overall Correct Rate", correctRate, "(all items)"),
                new ReportPdfDocument.Card("Competencies Below 80%", Long.toString(below),
                        "(of " + report.competencyMastery().size() + " competencies)"));
    }

    private static final BigDecimal MASTERY_LINE = new BigDecimal("80");
    private static final String NEEDS_SUPPORT_TITLE = "Students Needing Support per Skill";
    private static final String NEEDS_SUPPORT_NOTE =
            "Only students below 80% mastery are listed, least mastered skill first and lowest student first.";
    private static final String ALL_MASTERED = "All students mastered this skill";

    /** Section 3 lists skills least mastered first (skills with no results last). */
    private static List<CompetencyMasteryRow> skillsLeastMasteredFirst(V3ItemAnalysisReportResponse report) {
        return report.competencyMastery().stream()
                .sorted(java.util.Comparator.comparing(CompetencyMasteryRow::masteryPercentage,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();
    }

    /** Only the students below the 80% mastery line - a class of 30 would otherwise list
     *  everyone under every skill. Already sorted lowest first by the report service. */
    private static List<V3ItemAnalysisReportResponse.StudentSkillMastery> studentsBelowMastery(CompetencyMasteryRow skill) {
        return skill.students().stream()
                .filter(student -> student.masteryPercentage() != null
                        && student.masteryPercentage().compareTo(MASTERY_LINE) < 0)
                .toList();
    }

    private String joinSkillNames(List<Long> skillIds, Map<Long, String> skillNames) {
        if (skillIds.isEmpty()) {
            return "-";
        }
        return skillIds.stream()
                .map(id -> skillNames.getOrDefault(id, "#" + id))
                .collect(Collectors.joining(", "));
    }

    public byte[] exportItemAnalysisPdf(V3ItemAnalysisReportResponse report) {
        Map<Long, String> skillNames = report.competencyMastery().stream()
                .collect(Collectors.toMap(CompetencyMasteryRow::skillId, CompetencyMasteryRow::skillName, (a, b) -> a));
        try {
            ReportPdfDocument pdf = new ReportPdfDocument(report.scope().schoolName(),
                    "ITEM ANALYSIS AND COMPETENCY MASTERY", SCHOOL_FOOTER);
            pdf.section(null, "Assessment Details");
            pdf.details(assessmentDetailsLeft(report.scope()), itemAnalysisDetailsRight(report));
            pdf.gap(10);
            pdf.cards(itemAnalysisCards(report));
            pdf.gap(12);

            pdf.section(1, "Item Analysis");
            List<ReportPdfDocument.Cell[]> items = new java.util.ArrayList<>();
            int number = 1;
            for (QuestionRow row : report.rows()) {
                items.add(new ReportPdfDocument.Cell[]{
                        ReportPdfDocument.Cell.of(Integer.toString(number++)),
                        ReportPdfDocument.Cell.of(questionTypeLabel(row.questionTypeCode())),
                        ReportPdfDocument.Cell.of(Integer.toString(row.correctCount())),
                        ReportPdfDocument.Cell.of(Integer.toString(row.incorrectCount())),
                        ReportPdfDocument.Cell.of(Integer.toString(row.unansweredCount())),
                        ReportPdfDocument.Cell.of(joinSkillNames(row.skillIds(), skillNames))
                });
            }
            pdf.table(columns(ITEM_ANALYSIS_HEADERS, ITEM_ANALYSIS_WIDTHS, ITEM_ANALYSIS_CENTERED), items);
            pdf.gap(14);

            pdf.section(2, "Competency Mastery");
            List<ReportPdfDocument.Cell[]> mastery = new java.util.ArrayList<>();
            for (CompetencyMasteryRow row : report.competencyMastery()) {
                java.awt.Color color = statusColor(row.masteryStatusCode());
                mastery.add(new ReportPdfDocument.Cell[]{
                        ReportPdfDocument.Cell.of(orNotAvailable(row.skillName())),
                        ReportPdfDocument.Cell.of(Integer.toString(row.assessedItemCount())),
                        ReportPdfDocument.Cell.of(Integer.toString(row.studentCount())),
                        ReportPdfDocument.Cell.of(percent(row.masteryPercentage())),
                        ReportPdfDocument.Cell.colored(masteryStatusLabel(row.masteryStatusCode()), color)
                });
            }
            pdf.table(columns(MASTERY_HEADERS, MASTERY_WIDTHS, MASTERY_CENTERED), mastery);
            pdf.note(MASTERY_LEGEND);
            pdf.gap(10);

            pdf.section(3, NEEDS_SUPPORT_TITLE);
            List<ReportPdfDocument.Cell[]> students = new java.util.ArrayList<>();
            for (CompetencyMasteryRow skill : skillsLeastMasteredFirst(report)) {
                ReportPdfDocument.Cell label = new ReportPdfDocument.Cell(orNotAvailable(skill.skillName()), null, true);
                if (skill.students().isEmpty()) {
                    students.add(new ReportPdfDocument.Cell[]{label,
                            ReportPdfDocument.Cell.colored("No verified results yet", ReportPdfDocument.MUTED),
                            ReportPdfDocument.Cell.of(""), ReportPdfDocument.Cell.of(""), ReportPdfDocument.Cell.of("")});
                    continue;
                }
                List<V3ItemAnalysisReportResponse.StudentSkillMastery> below = studentsBelowMastery(skill);
                if (below.isEmpty()) {
                    students.add(new ReportPdfDocument.Cell[]{label,
                            ReportPdfDocument.Cell.colored(ALL_MASTERED, ReportPdfDocument.GOOD),
                            ReportPdfDocument.Cell.of(""), ReportPdfDocument.Cell.of(""), ReportPdfDocument.Cell.of("")});
                    continue;
                }
                boolean first = true;
                for (V3ItemAnalysisReportResponse.StudentSkillMastery student : below) {
                    java.awt.Color masteryColor = statusColor(student.masteryStatusCode());
                    students.add(new ReportPdfDocument.Cell[]{
                            first ? label : ReportPdfDocument.Cell.of(""),
                            ReportPdfDocument.Cell.of(orNotAvailable(personName(student.fullName()))),
                            ReportPdfDocument.Cell.colored(percent(student.masteryPercentage()), masteryColor),
                            ReportPdfDocument.Cell.colored(masteryStatusLabel(student.masteryStatusCode()), masteryColor),
                            ReportPdfDocument.Cell.colored(orNotAvailable(student.recommendationLabel()),
                                    statusColor(student.recommendationCode()))
                    });
                    first = false;
                }
            }
            pdf.table(columns(STUDENTS_PER_SKILL_HEADERS, STUDENTS_PER_SKILL_WIDTHS, STUDENTS_PER_SKILL_CENTERED),
                    students, true);
            pdf.note(NEEDS_SUPPORT_NOTE);
            pdf.note(RECOMMENDATION_LEGEND);
            for (var warning : report.warnings()) {
                pdf.note("Note: " + warning.message());
            }
            pdf.signature(personName(report.scope().teacherName()), "Teacher");
            return pdf.finish();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate item-analysis PDF export.", e);
        }
    }

    public byte[] exportItemAnalysisExcel(V3ItemAnalysisReportResponse report) {
        Map<Long, String> skillNames = report.competencyMastery().stream()
                .collect(Collectors.toMap(CompetencyMasteryRow::skillId, CompetencyMasteryRow::skillName, (a, b) -> a));
        List<String[]> details = new java.util.ArrayList<>(assessmentDetailsLeft(report.scope()));
        details.addAll(itemAnalysisDetailsRight(report));
        String school = report.scope().schoolName();
        String title = "ITEM ANALYSIS AND COMPETENCY MASTERY";
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var styles = ReportExcelSheet.newStyleCache();

            ReportExcelSheet items = new ReportExcelSheet(workbook, "Item Analysis",
                    new float[]{8f, 18f, 10f, 11f, 12f, 38f}, styles);
            items.header(school, title);
            items.section("Assessment Details");
            items.details(details, List.of(), 1, 5, 0, 0);
            items.cards(itemAnalysisCards(report), new int[][]{{1, 1}, {2, 3}, {4, 4}, {5, 5}});
            items.section("1  Item Analysis");
            items.tableHeader(ITEM_ANALYSIS_HEADERS);
            int number = 1;
            for (QuestionRow row : report.rows()) {
                items.tableRow(new Object[]{number++, questionTypeLabel(row.questionTypeCode()), row.correctCount(),
                        row.incorrectCount(), row.unansweredCount(), joinSkillNames(row.skillIds(), skillNames)},
                        null, ITEM_ANALYSIS_CENTERED, null);
            }
            items.signature(personName(report.scope().teacherName()), "Teacher", 1);
            items.footer(SCHOOL_FOOTER);

            ReportExcelSheet mastery = new ReportExcelSheet(workbook, "Competency Mastery",
                    new float[]{8f, 40f, 14f, 11f, 12f, 16f}, styles);
            mastery.header(school, title);
            mastery.section("Assessment Details");
            mastery.details(details, List.of(), 1, 5, 0, 0);
            mastery.section("2  Competency Mastery");
            mastery.tableHeader(new String[]{"#", "Competency / Skill", "Assessed Items", "Students", "Mastery %", "Status"});
            number = 1;
            for (CompetencyMasteryRow row : report.competencyMastery()) {
                java.awt.Color color = statusColor(row.masteryStatusCode());
                mastery.tableRow(new Object[]{number++, orNotAvailable(row.skillName()), row.assessedItemCount(),
                                row.studentCount(),
                                row.masteryPercentage() == null ? NOT_AVAILABLE : row.masteryPercentage(),
                                masteryStatusLabel(row.masteryStatusCode())},
                        new java.awt.Color[]{null, null, null, null, null, color},
                        new boolean[]{true, false, true, true, true, true}, "....%.");
            }
            mastery.skip(1);
            mastery.note(MASTERY_LEGEND);
            mastery.footer(SCHOOL_FOOTER);

            ReportExcelSheet perSkill = new ReportExcelSheet(workbook, "Needing Support",
                    new float[]{8f, 30f, 32f, 12f, 15f, 22f}, styles);
            perSkill.header(school, title);
            perSkill.section("Assessment Details");
            perSkill.details(details, List.of(), 1, 5, 0, 0);
            perSkill.section("3  " + NEEDS_SUPPORT_TITLE);
            perSkill.tableHeader(new String[]{"#", "Competency / Skill", "Student", "Mastery %", "Status", "Recommendation"});
            number = 1;
            for (CompetencyMasteryRow skill : skillsLeastMasteredFirst(report)) {
                int firstRow = -1;
                int lastRow = -1;
                if (skill.students().isEmpty()) {
                    perSkill.tableRow(new Object[]{"", orNotAvailable(skill.skillName()), "No verified results yet", "", "", ""},
                            new java.awt.Color[]{null, null, ReportPdfDocument.MUTED, null, null, null},
                            new boolean[]{true, false, false, true, true, true}, null);
                    continue;
                }
                List<V3ItemAnalysisReportResponse.StudentSkillMastery> rows = studentsBelowMastery(skill);
                if (rows.isEmpty()) {
                    perSkill.tableRow(new Object[]{"", orNotAvailable(skill.skillName()), ALL_MASTERED, "", "", ""},
                            new java.awt.Color[]{null, null, ReportPdfDocument.GOOD, null, null, null},
                            new boolean[]{true, false, false, true, true, true}, null);
                    continue;
                }
                for (V3ItemAnalysisReportResponse.StudentSkillMastery student : rows) {
                    java.awt.Color masteryColor = statusColor(student.masteryStatusCode());
                    int written = perSkill.tableRow(new Object[]{number++, orNotAvailable(skill.skillName()),
                                    orNotAvailable(personName(student.fullName())),
                                    student.masteryPercentage() == null ? NOT_AVAILABLE : student.masteryPercentage(),
                                    masteryStatusLabel(student.masteryStatusCode()),
                                    orNotAvailable(student.recommendationLabel())},
                            new java.awt.Color[]{null, null, null, masteryColor, masteryColor,
                                    statusColor(student.recommendationCode())},
                            new boolean[]{true, false, false, true, true, true}, "...%..");
                    if (firstRow < 0) firstRow = written;
                    lastRow = written;
                }
                perSkill.mergeColumn(firstRow, lastRow, 1);
            }
            perSkill.skip(1);
            perSkill.note(NEEDS_SUPPORT_NOTE);
            perSkill.note(RECOMMENDATION_LEGEND);
            for (var warning : report.warnings()) {
                perSkill.note("Note: " + warning.message());
            }
            perSkill.footer(SCHOOL_FOOTER);

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate item-analysis Excel export.", e);
        }
    }

    // ---------- Individual Student Performance Profile ----------

    private static final String CONFIDENTIAL_FOOTER = "Confidential – For Authorized School Use Only";
    private static final DateTimeFormatter REPORT_DATE =
            DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH).withZone(ZoneId.of("Asia/Manila"));
    private static final float[] ASSESSMENT_HISTORY_WIDTHS = {22f, 106f, 64f, 62f, 70f, 50f, 56f, 85f};
    private static final String[] ASSESSMENT_HISTORY_HEADERS =
            {"No.", "Assessment", "Subject", "Term", "Date Conducted", "Score", "Percentage", "Performance Level"};
    private static final float[] COMPETENCY_PERFORMANCE_WIDTHS = {28f, 327f, 75f, 85f};
    private static final String[] COMPETENCY_PERFORMANCE_HEADERS = {"No.", "Competency / Skill", "Mastery %", "Status"};
    private static final String NO_INTERVENTIONS_MESSAGE =
            "No skill currently needs intervention: every assessed skill is at the Maintain level.";

    private record ProfileHistoryLine(String number, String assessment, String subject, String term, String date,
                                      String score, BigDecimal percentage, String performance, java.awt.Color color) {
    }

    private List<ProfileHistoryLine> profileHistory(V3StudentPerformanceProfileResponse report) {
        List<ProfileHistoryLine> lines = new java.util.ArrayList<>();
        int number = 1;
        for (var row : report.assessmentResults()) {
            boolean finalized = "finalized".equals(row.resultStatus());
            String score = finalized && row.earnedPoints() != null && row.maximumPoints() != null
                    ? points(row.earnedPoints()) + " / " + points(row.maximumPoints())
                    : NOT_AVAILABLE;
            lines.add(new ProfileHistoryLine(Integer.toString(number++), orNotAvailable(row.testName()),
                    orNotAvailable(row.subjectName()), orNotAvailable(row.termName()),
                    dateConducted(row.openAt(), row.closeAt()),
                    score, finalized ? row.percentage() : null,
                    finalized ? orNotAvailable(humanize(row.performanceStatusCode())) : "Pending verification",
                    finalized ? statusColor(row.performanceStatusCode()) : ReportPdfDocument.MUTED));
        }
        return lines;
    }

    private List<String[]> profileDetailsLeft(V3StudentPerformanceProfileResponse report) {
        String role = report.preparedByRole() == null ? "Teacher" : report.preparedByRole();
        return List.of(
                new String[]{"Learner Name", orNotAvailable(personName(report.fullName()))},
                new String[]{"Grade & Section", gradeSection(report.gradeLevelName(), report.sectionName())},
                new String[]{"Teacher".equals(role) ? "Teacher" : "Prepared By",
                        orNotAvailable(personName(report.preparedByName()))});
    }

    private List<String[]> profileDetailsRight(V3StudentPerformanceProfileResponse report) {
        long verified = report.assessmentResults().stream().filter(row -> "finalized".equals(row.resultStatus())).count();
        return List.of(
                new String[]{"School Year", orNotAvailable(report.academicYearName())},
                new String[]{"Assessments", report.assessmentResults().size() + " taken, " + verified + " verified"},
                new String[]{"Generated On", REPORT_DATE_TIME.format(report.generatedAt())});
    }

    /** Rule-based, from the student's own mastery data - no invented narrative. */
    private List<String> profileStrengths(V3StudentPerformanceProfileResponse report) {
        List<String> strengths = report.competencyPerformance().stream()
                .filter(skill -> "mastered".equals(skill.masteryStatusCode()))
                .sorted(java.util.Comparator.comparing(V3StudentPerformanceProfileResponse.CompetencyPerformance::masteryPercentage).reversed())
                .map(skill -> orNotAvailable(skill.skillName()) + " (" + percent(skill.masteryPercentage()) + ")")
                .toList();
        return strengths.isEmpty() ? List.of("No competency has reached the Mastered level (80%) yet.") : strengths;
    }

    private List<String> profileAreasForImprovement(V3StudentPerformanceProfileResponse report) {
        List<String> areas = report.competencyPerformance().stream()
                .filter(skill -> "developing".equals(skill.masteryStatusCode()) || "needs_support".equals(skill.masteryStatusCode()))
                .sorted(java.util.Comparator.comparing(V3StudentPerformanceProfileResponse.CompetencyPerformance::masteryPercentage))
                .map(skill -> orNotAvailable(skill.skillName()) + " (" + percent(skill.masteryPercentage()) + ", "
                        + masteryStatusLabel(skill.masteryStatusCode()) + ")")
                .toList();
        return areas.isEmpty() ? List.of("No competency is below the Mastered level.") : areas;
    }

    private List<String> profileActions(V3StudentPerformanceProfileResponse report) {
        return report.interventions().stream()
                .map(item -> orNotAvailable(item.recommendationLabel()) + ": "
                        + (item.suggestion() == null || item.suggestion().isBlank()
                                ? orNotAvailable(item.skillName()) + " (" + percent(item.masteryPercentage()) + ")"
                                : item.suggestion() + " (" + percent(item.masteryPercentage()) + ")"))
                .toList();
    }

    private List<ReportPdfDocument.Card> profileCards(V3StudentPerformanceProfileResponse report) {
        BigDecimal earned = BigDecimal.ZERO;
        BigDecimal possible = BigDecimal.ZERO;
        int verified = 0;
        for (var row : report.assessmentResults()) {
            if ("finalized".equals(row.resultStatus()) && row.earnedPoints() != null && row.maximumPoints() != null) {
                earned = earned.add(row.earnedPoints());
                possible = possible.add(row.maximumPoints());
                verified++;
            }
        }
        // Weighted by points, like every other average in these reports.
        BigDecimal average = possible.signum() == 0 ? null
                : earned.multiply(BigDecimal.valueOf(100)).divide(possible, 2, java.math.RoundingMode.HALF_UP);
        long assessedSkills = report.competencyPerformance().stream()
                .filter(skill -> skill.masteryPercentage() != null).count();
        long mastered = report.competencyPerformance().stream()
                .filter(skill -> "mastered".equals(skill.masteryStatusCode())).count();
        String outlookCode = average == null ? null
                : average.compareTo(new BigDecimal("80")) >= 0 ? "mastered"
                : average.compareTo(new BigDecimal("60")) >= 0 ? "developing" : "needs_support";
        return List.of(
                new ReportPdfDocument.Card("Average Score", percent(average),
                        "(" + verified + (verified == 1 ? " verified assessment)" : " verified assessments)")),
                new ReportPdfDocument.Card("Competencies Mastered", mastered + " / " + assessedSkills, "(80% and above)"),
                new ReportPdfDocument.Card("Skills Needing Support", Integer.toString(report.interventions().size()),
                        "(below 80%)"),
                new ReportPdfDocument.Card("Overall Outlook",
                        outlookCode == null ? NOT_AVAILABLE : masteryStatusLabel(outlookCode),
                        "(based on average score)", outlookCode == null ? null : statusColor(outlookCode)));
    }

    public byte[] exportStudentPerformanceProfilePdf(V3StudentPerformanceProfileResponse report) {
        try {
            ReportPdfDocument pdf = new ReportPdfDocument(report.schoolName(),
                    "INDIVIDUAL STUDENT PERFORMANCE PROFILE", CONFIDENTIAL_FOOTER);
            pdf.section(1, "Student Information");
            pdf.details(profileDetailsLeft(report), profileDetailsRight(report));
            pdf.smallPrint("LRN: " + orNotAvailable(report.studentLrn()) + "     Confidential learner record");
            pdf.gap(10);

            pdf.section(2, "Assessment Performance Summary");
            List<ReportPdfDocument.Cell[]> history = new java.util.ArrayList<>();
            for (ProfileHistoryLine line : profileHistory(report)) {
                history.add(new ReportPdfDocument.Cell[]{
                        ReportPdfDocument.Cell.of(line.number()),
                        ReportPdfDocument.Cell.of(line.assessment()),
                        ReportPdfDocument.Cell.of(line.subject()),
                        ReportPdfDocument.Cell.of(line.term()),
                        ReportPdfDocument.Cell.of(line.date()),
                        ReportPdfDocument.Cell.of(line.score()),
                        ReportPdfDocument.Cell.of(percent(line.percentage())),
                        ReportPdfDocument.Cell.colored(line.performance(), line.color())
                });
            }
            if (history.isEmpty()) {
                pdf.note("No assessment result exists yet for this student.");
            } else {
                pdf.table(List.of(
                        ReportPdfDocument.Column.center("No.", ASSESSMENT_HISTORY_WIDTHS[0]),
                        ReportPdfDocument.Column.left("Assessment", ASSESSMENT_HISTORY_WIDTHS[1]),
                        ReportPdfDocument.Column.left("Subject", ASSESSMENT_HISTORY_WIDTHS[2]),
                        ReportPdfDocument.Column.left("Term", ASSESSMENT_HISTORY_WIDTHS[3]),
                        ReportPdfDocument.Column.centerWrap("Date Conducted", ASSESSMENT_HISTORY_WIDTHS[4]),
                        ReportPdfDocument.Column.center("Score", ASSESSMENT_HISTORY_WIDTHS[5]),
                        ReportPdfDocument.Column.center("Percentage", ASSESSMENT_HISTORY_WIDTHS[6]),
                        ReportPdfDocument.Column.centerWrap("Performance Level", ASSESSMENT_HISTORY_WIDTHS[7])),
                        history);
                pdf.note(PERFORMANCE_LEGEND);
            }
            pdf.gap(10);

            pdf.section(3, "Competency Performance");
            List<ReportPdfDocument.Cell[]> competencies = new java.util.ArrayList<>();
            int number = 1;
            for (var skill : report.competencyPerformance()) {
                java.awt.Color color = statusColor(skill.masteryStatusCode());
                competencies.add(new ReportPdfDocument.Cell[]{
                        ReportPdfDocument.Cell.of(Integer.toString(number++)),
                        ReportPdfDocument.Cell.of(orNotAvailable(skill.skillName())),
                        ReportPdfDocument.Cell.colored(percent(skill.masteryPercentage()), color),
                        ReportPdfDocument.Cell.colored(masteryStatusLabel(skill.masteryStatusCode()), color)
                });
            }
            if (competencies.isEmpty()) {
                pdf.note("No competency has been assessed yet for this student.");
            } else {
                pdf.table(columns(COMPETENCY_PERFORMANCE_HEADERS, COMPETENCY_PERFORMANCE_WIDTHS,
                        new boolean[]{true, false, true, true}), competencies);
                pdf.note(MASTERY_LEGEND);
            }
            pdf.gap(10);

            pdf.section(4, "Strengths and Areas for Improvement");
            pdf.twoLists("Strengths", profileStrengths(report), "Areas for Improvement", profileAreasForImprovement(report));
            pdf.gap(10);

            pdf.section(5, "Recommended Teacher Actions / Intervention Plan");
            List<String> actions = profileActions(report);
            if (actions.isEmpty()) {
                pdf.note(NO_INTERVENTIONS_MESSAGE);
            } else {
                pdf.gap(4);
                pdf.numberedList(actions);
            }
            pdf.gap(10);

            pdf.section(6, "Overall Learner Summary");
            pdf.cards(profileCards(report));
            for (var warning : report.warnings()) {
                pdf.note("Note: " + warning.message());
            }
            pdf.signature(personName(report.preparedByName()),
                    report.preparedByRole() == null ? "Teacher" : report.preparedByRole());
            return pdf.finish();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate student-performance-profile PDF export.", e);
        }
    }

    public byte[] exportStudentPerformanceProfileExcel(V3StudentPerformanceProfileResponse report) {
        List<String[]> details = new java.util.ArrayList<>(profileDetailsLeft(report));
        details.addAll(profileDetailsRight(report));
        String title = "INDIVIDUAL STUDENT PERFORMANCE PROFILE";
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var styles = ReportExcelSheet.newStyleCache();

            ReportExcelSheet profile = new ReportExcelSheet(workbook, "Student Profile",
                    new float[]{7f, 30f, 16f, 15f, 14f, 11f, 12f, 21f}, styles);
            profile.header(report.schoolName(), title);
            profile.section("1  Student Information");
            profile.details(details, List.of(), 1, 7, 0, 0);
            profile.note("LRN: " + orNotAvailable(report.studentLrn()) + "   (confidential learner record)");
            profile.skip(1);
            profile.section("2  Assessment Performance Summary");
            profile.tableHeader(ASSESSMENT_HISTORY_HEADERS);
            for (ProfileHistoryLine line : profileHistory(report)) {
                profile.tableRow(new Object[]{Integer.valueOf(line.number()), line.assessment(), line.subject(),
                                line.term(), line.date(), line.score(),
                                line.percentage() == null ? NOT_AVAILABLE : line.percentage(), line.performance()},
                        new java.awt.Color[]{null, null, null, null, null, null, null, line.color()},
                        new boolean[]{true, false, false, false, true, true, true, true}, "......%.");
            }
            profile.skip(1);
            profile.note(PERFORMANCE_LEGEND);
            profile.skip(1);
            profile.section("6  Overall Learner Summary");
            profile.cards(profileCards(report), new int[][]{{1, 1}, {2, 3}, {4, 5}, {6, 7}});
            profile.signature(personName(report.preparedByName()),
                    report.preparedByRole() == null ? "Teacher" : report.preparedByRole(), 1);
            profile.footer(CONFIDENTIAL_FOOTER);

            ReportExcelSheet competency = new ReportExcelSheet(workbook, "Competency Performance",
                    new float[]{7f, 50f, 13f, 16f}, styles);
            competency.header(report.schoolName(), title);
            competency.section("3  Competency Performance");
            competency.tableHeader(COMPETENCY_PERFORMANCE_HEADERS);
            int number = 1;
            for (var skill : report.competencyPerformance()) {
                java.awt.Color color = statusColor(skill.masteryStatusCode());
                competency.tableRow(new Object[]{number++, orNotAvailable(skill.skillName()),
                                skill.masteryPercentage() == null ? NOT_AVAILABLE : skill.masteryPercentage(),
                                masteryStatusLabel(skill.masteryStatusCode())},
                        new java.awt.Color[]{null, null, color, color},
                        new boolean[]{true, false, true, true}, "..%.");
            }
            competency.skip(1);
            competency.note(MASTERY_LEGEND);
            competency.skip(1);
            competency.section("4  Strengths");
            for (String strength : profileStrengths(report)) {
                competency.note("•  " + strength);
            }
            competency.skip(1);
            competency.section("4  Areas for Improvement");
            for (String area : profileAreasForImprovement(report)) {
                competency.note("•  " + area);
            }
            competency.footer(CONFIDENTIAL_FOOTER);

            ReportExcelSheet plan = new ReportExcelSheet(workbook, "Intervention Plan",
                    new float[]{7f, 22f, 34f, 12f, 60f}, styles);
            plan.header(report.schoolName(), title);
            plan.section("5  Recommended Teacher Actions / Intervention Plan");
            if (report.interventions().isEmpty()) {
                plan.note(NO_INTERVENTIONS_MESSAGE);
            } else {
                plan.tableHeader(new String[]{"#", "Recommendation", "Competency / Skill", "Mastery %", "Suggested Action"});
                number = 1;
                for (var item : report.interventions()) {
                    plan.tableRow(new Object[]{number++, orNotAvailable(item.recommendationLabel()),
                                    orNotAvailable(item.skillName()),
                                    item.masteryPercentage() == null ? NOT_AVAILABLE : item.masteryPercentage(),
                                    orNotAvailable(item.suggestion())},
                            new java.awt.Color[]{null, statusColor(item.recommendationCode()), null, null, null},
                            new boolean[]{true, true, false, true, false}, "...%.");
                }
            }
            plan.footer(CONFIDENTIAL_FOOTER);

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate student-performance-profile Excel export.", e);
        }
    }

    // ---------- Principal Consolidated Report ----------

    /** Despite the DTO field name "masteryStatusCounts", these counts are keyed by
     *  test_results.performance_status (the student_score rule set's bands), not by
     *  masteryStatusCode - looking up "mastered"/"developing" there always yields 0. */
    private static final String[] PERFORMANCE_BAND_CODES = {"maintain", "review", "reteach", "priority_intervention"};
    private static final String[] CONSOLIDATED_SUMMARY_EXCEL_COLUMNS =
            {"Group", "Students", "Mean %", "Maintain", "Review", "Reteach", "Priority Intervention"};
    private static final String[] CONSOLIDATED_SKILLS_EXCEL_COLUMNS =
            {"Group", "Competency / Skill", "Mastery %", "Status"};

    private int countForStatus(List<V3ConsolidatedReportResponse.MasteryStatusCount> counts, String status) {
        return counts.stream()
                .filter(c -> status.equals(c.performanceStatus()))
                .mapToInt(V3ConsolidatedReportResponse.MasteryStatusCount::count)
                .findFirst()
                .orElse(0);
    }

    public byte[] exportConsolidatedExcel(V3ConsolidatedReportResponse report) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet summarySheet = workbook.createSheet("Consolidated Summary");
            int rowIndex = writeTitleBlock(summarySheet,
                    "Consolidated Report - Grouped by " + humanize(report.groupedBy()),
                    "Generated " + DISPLAY_DATE.format(report.generatedAt()),
                    CONSOLIDATED_SUMMARY_EXCEL_COLUMNS.length);
            writeHeaderRow(summarySheet, rowIndex++, workbook, CONSOLIDATED_SUMMARY_EXCEL_COLUMNS);
            int summaryCount = 0;
            for (GroupRow group : report.groups()) {
                Row dataRow = summarySheet.createRow(rowIndex++);
                setCell(dataRow, 0, nullToEmpty(group.groupLabel()));
                setCell(dataRow, 1, group.studentCount());
                setCell(dataRow, 2, percentOrBlank(group.meanPercentage()));
                for (int i = 0; i < PERFORMANCE_BAND_CODES.length; i++) {
                    setCell(dataRow, 3 + i, countForStatus(group.masteryStatusCounts(), PERFORMANCE_BAND_CODES[i]));
                }
                applyBodyStyle(workbook, dataRow, CONSOLIDATED_SUMMARY_EXCEL_COLUMNS.length, summaryCount++ % 2 == 1);
            }
            summarySheet.createFreezePane(0, rowIndex - summaryCount);
            for (int i = 0; i < CONSOLIDATED_SUMMARY_EXCEL_COLUMNS.length; i++) {
                summarySheet.autoSizeColumn(i);
            }

            Sheet skillsSheet = workbook.createSheet("Least Mastered Skills");
            int skillsRowIndex = writeTitleBlock(skillsSheet,
                    "Consolidated Report - Grouped by " + humanize(report.groupedBy()),
                    "Generated " + DISPLAY_DATE.format(report.generatedAt()),
                    CONSOLIDATED_SKILLS_EXCEL_COLUMNS.length);
            writeHeaderRow(skillsSheet, skillsRowIndex++, workbook, CONSOLIDATED_SKILLS_EXCEL_COLUMNS);
            int skillsCount = 0;
            for (GroupRow group : report.groups()) {
                for (LeastMasteredSkill skill : group.leastMasteredSkills()) {
                    Row dataRow = skillsSheet.createRow(skillsRowIndex++);
                    setCell(dataRow, 0, nullToEmpty(group.groupLabel()));
                    setCell(dataRow, 1, nullToEmpty(skill.skillName()));
                    setCell(dataRow, 2, percentOrBlank(skill.masteryPercentage()));
                    setCell(dataRow, 3, humanize(skill.masteryStatusCode()));
                    applyBodyStyle(workbook, dataRow, CONSOLIDATED_SKILLS_EXCEL_COLUMNS.length, skillsCount++ % 2 == 1);
                }
            }
            skillsSheet.createFreezePane(0, skillsRowIndex - skillsCount);
            for (int i = 0; i < CONSOLIDATED_SKILLS_EXCEL_COLUMNS.length; i++) {
                skillsSheet.autoSizeColumn(i);
            }

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate consolidated Excel export.", e);
        }
    }

    private static final float[] CONSOLIDATED_SUMMARY_WIDTHS = {145f, 55f, 60f, 60f, 55f, 60f, 80f};
    private static final String[] CONSOLIDATED_SUMMARY_HEADERS =
            {"Group", "Students", "Mean %", "Maintain", "Review", "Reteach", "Priority"};
    private static final float[] CONSOLIDATED_SKILLS_WIDTHS = {140f, 185f, 80f, 110f};
    private static final String[] CONSOLIDATED_SKILLS_HEADERS =
            {"Group", "Competency / Skill", "Mastery %", "Status"};

    public byte[] exportConsolidatedPdf(V3ConsolidatedReportResponse report) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfCursor cursor = newPage(document);
            cursor.y = drawBrandedTitleBand(cursor.content,
                    "Consolidated Report - Grouped by " + humanize(report.groupedBy()),
                    "School-wide performance summary",
                    "Generated " + DISPLAY_DATE.format(report.generatedAt()));
            cursor.y = drawTableHeader(cursor.content, cursor.y, CONSOLIDATED_SUMMARY_HEADERS, CONSOLIDATED_SUMMARY_WIDTHS);

            int summaryRowCount = 0;
            for (GroupRow group : report.groups()) {
                if (cursor.y < MARGIN + ROW_HEIGHT + FOOTER_RESERVE) {
                    closePage(cursor);
                    cursor = newPage(document);
                    cursor.y = drawTableHeader(cursor.content, cursor.y, CONSOLIDATED_SUMMARY_HEADERS, CONSOLIDATED_SUMMARY_WIDTHS);
                }
                String[] values = new String[3 + PERFORMANCE_BAND_CODES.length];
                values[0] = nullToEmpty(group.groupLabel());
                values[1] = String.valueOf(group.studentCount());
                values[2] = percentOrBlank(group.meanPercentage());
                for (int i = 0; i < PERFORMANCE_BAND_CODES.length; i++) {
                    values[3 + i] = String.valueOf(countForStatus(group.masteryStatusCounts(), PERFORMANCE_BAND_CODES[i]));
                }
                cursor.y = drawTableRow(cursor.content, cursor.y, values, CONSOLIDATED_SUMMARY_WIDTHS, summaryRowCount++ % 2 == 1);
            }
            closePage(cursor);

            cursor = newPage(document);
            drawText(cursor.content, FONT_BOLD, 13, MARGIN, cursor.y, "Least Mastered Skills");
            cursor.y -= 22f;
            cursor.y = drawTableHeader(cursor.content, cursor.y, CONSOLIDATED_SKILLS_HEADERS, CONSOLIDATED_SKILLS_WIDTHS);
            int skillsRowCount = 0;
            for (GroupRow group : report.groups()) {
                for (LeastMasteredSkill skill : group.leastMasteredSkills()) {
                    if (cursor.y < MARGIN + ROW_HEIGHT + FOOTER_RESERVE) {
                        closePage(cursor);
                        cursor = newPage(document);
                        cursor.y = drawTableHeader(cursor.content, cursor.y, CONSOLIDATED_SKILLS_HEADERS, CONSOLIDATED_SKILLS_WIDTHS);
                    }
                    String[] values = {
                            nullToEmpty(group.groupLabel()),
                            nullToEmpty(skill.skillName()),
                            skill.masteryPercentage() == null ? "" : skill.masteryPercentage() + "%",
                            humanize(skill.masteryStatusCode())
                    };
                    cursor.y = drawTableRow(cursor.content, cursor.y, values, CONSOLIDATED_SKILLS_WIDTHS, skillsRowCount++ % 2 == 1);
                }
            }
            closePage(cursor);
            finalizeFooters(document);
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate consolidated PDF export.", e);
        }
    }

    // ---------- Teacher Sync Activity ----------

    private static final float[] SYNC_ASSESSMENT_WIDTHS = {24f, 160f, 110f, 105f, 116f};
    /** Principal view with several teachers: the same table plus a Teacher column. */
    private static final float[] SYNC_ASSESSMENT_WITH_TEACHER_WIDTHS = {22f, 120f, 85f, 100f, 98f, 90f};
    private static final float[] SYNC_TEACHER_WIDTHS = {24f, 200f, 120f, 80f, 91f};
    private static final String[] SYNC_TEACHER_HEADERS = {"No.", "Teacher", "Last Synced", "Assessments", "Not Uploaded"};
    private static final float[] SYNC_FAILED_WIDTHS = {22f, 120f, 85f, 60f, 228f};
    private static final String[] SYNC_FAILED_HEADERS = {"No.", "Assessment", "Class", "Not Uploaded", "Error Details"};
    private static final String SYNC_SUGGESTED_ACTION = "Suggested action: open the SMART mobile app and retry the upload."
            + " If a result keeps failing, check the error above, then rescan or re-verify that answer sheet.";

    private String uploadStatus(int resultsNotUploaded) {
        return resultsNotUploaded == 0 ? "All uploaded" : resultsNotUploaded + " not uploaded";
    }

    private static java.awt.Color uploadColor(int resultsNotUploaded) {
        return resultsNotUploaded == 0 ? ReportPdfDocument.GOOD : ReportPdfDocument.BAD;
    }

    private String syncDate(Instant value) {
        return value == null ? "Not yet synced" : REPORT_DATE_TIME.format(value);
    }

    /** The reporting window in plain dates; the stored end is exclusive, so show the day before. */
    private String syncPeriod(V3SyncActivityReportResponse report) {
        if (report.windowStart() == null && report.windowEnd() == null) {
            return "All recorded activity";
        }
        String start = report.windowStart() == null ? "Beginning" : REPORT_DATE.format(report.windowStart());
        String end = report.windowEnd() == null ? "Today" : REPORT_DATE.format(report.windowEnd().minusSeconds(1));
        return start + " - " + end;
    }

    private boolean singleTeacher(V3SyncActivityReportResponse report) {
        return report.teachers().size() == 1;
    }

    private Instant lastSynced(V3SyncActivityReportResponse report) {
        return report.teachers().stream().map(V3SyncActivityReportResponse.TeacherSummary::lastSyncedAt)
                .filter(java.util.Objects::nonNull).max(java.util.Comparator.naturalOrder()).orElse(null);
    }

    private List<String[]> syncDetailsLeft(V3SyncActivityReportResponse report) {
        String teacher = singleTeacher(report)
                ? orNotAvailable(personName(report.teachers().get(0).teacherName()))
                : "All teachers (" + report.teachers().size() + ")";
        return List.of(
                new String[]{"Teacher", teacher},
                new String[]{"Period", syncPeriod(report)},
                new String[]{"Last Synced", syncDate(lastSynced(report))});
    }

    private List<String[]> syncDetailsRight(V3SyncActivityReportResponse report) {
        // A teacher's own report doesn't repeat their name as "Prepared By".
        boolean ownReport = singleTeacher(report) && report.preparedByName() != null
                && report.preparedByName().equalsIgnoreCase(report.teachers().get(0).teacherName());
        List<String[]> right = new java.util.ArrayList<>();
        if (!ownReport) {
            right.add(new String[]{"Prepared By", orNotAvailable(personName(report.preparedByName()))});
        }
        right.add(new String[]{"Generated On", REPORT_DATE_TIME.format(report.generatedAt())});
        return right;
    }

    private List<ReportPdfDocument.Card> syncCards(V3SyncActivityReportResponse report) {
        int synced = report.teachers().stream().mapToInt(V3SyncActivityReportResponse.TeacherSummary::assessmentsSynced).sum();
        int notUploaded = report.assessments().stream().mapToInt(V3SyncActivityReportResponse.AssessmentSync::resultsNotUploaded).sum();
        long withProblems = report.assessments().stream().filter(row -> row.resultsNotUploaded() > 0).count();
        Instant last = lastSynced(report);
        return List.of(
                new ReportPdfDocument.Card("Last Synced", last == null ? "Never" : REPORT_DATE.format(last),
                        last == null ? "(no upload yet)" : "(" + DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
                                .withZone(ZoneId.of("Asia/Manila")).format(last) + ")"),
                new ReportPdfDocument.Card("Assessments Synced", Integer.toString(synced), "(successfully uploaded)"),
                new ReportPdfDocument.Card("Results Not Uploaded", Integer.toString(notUploaded),
                        "(in " + withProblems + (withProblems == 1 ? " assessment)" : " assessments)"),
                        notUploaded == 0 ? ReportPdfDocument.GOOD : ReportPdfDocument.BAD),
                new ReportPdfDocument.Card("Sync Status", notUploaded == 0 ? "Up to date" : "Needs attention",
                        notUploaded == 0 ? "(nothing pending)" : "(see section 3)",
                        notUploaded == 0 ? ReportPdfDocument.GOOD : ReportPdfDocument.BAD));
    }

    public byte[] exportSyncActivityPdf(V3SyncActivityReportResponse report) {
        boolean single = singleTeacher(report);
        try {
            ReportPdfDocument pdf = new ReportPdfDocument(report.schoolName(), "TEACHER SYNC ACTIVITY", SCHOOL_FOOTER);
            pdf.section(1, "Sync Summary");
            pdf.details(syncDetailsLeft(report), syncDetailsRight(report));
            pdf.gap(8);
            pdf.cards(syncCards(report));
            pdf.gap(12);

            if (!single && !report.teachers().isEmpty()) {
                pdf.section(null, "Teachers");
                List<ReportPdfDocument.Cell[]> teachers = new java.util.ArrayList<>();
                int number = 1;
                for (var teacher : report.teachers()) {
                    teachers.add(new ReportPdfDocument.Cell[]{
                            ReportPdfDocument.Cell.of(Integer.toString(number++)),
                            ReportPdfDocument.Cell.of(orNotAvailable(personName(teacher.teacherName()))),
                            teacher.lastSyncedAt() == null
                                    ? ReportPdfDocument.Cell.colored("Never", ReportPdfDocument.MUTED)
                                    : ReportPdfDocument.Cell.of(syncDate(teacher.lastSyncedAt())),
                            ReportPdfDocument.Cell.of(Integer.toString(teacher.assessmentsSynced())),
                            ReportPdfDocument.Cell.colored(Integer.toString(teacher.resultsNotUploaded()),
                                    uploadColor(teacher.resultsNotUploaded()))
                    });
                }
                pdf.table(List.of(
                        ReportPdfDocument.Column.center("No.", SYNC_TEACHER_WIDTHS[0]),
                        ReportPdfDocument.Column.left("Teacher", SYNC_TEACHER_WIDTHS[1]),
                        ReportPdfDocument.Column.left("Last Synced", SYNC_TEACHER_WIDTHS[2]),
                        ReportPdfDocument.Column.center("Assessments", SYNC_TEACHER_WIDTHS[3]),
                        ReportPdfDocument.Column.center("Not Uploaded", SYNC_TEACHER_WIDTHS[4])), teachers);
                pdf.gap(12);
            }

            pdf.section(2, "Synced Assessments");
            List<ReportPdfDocument.Cell[]> assessments = new java.util.ArrayList<>();
            int number = 1;
            for (var assessment : report.assessments()) {
                List<ReportPdfDocument.Cell> cells = new java.util.ArrayList<>();
                cells.add(ReportPdfDocument.Cell.of(Integer.toString(number++)));
                cells.add(ReportPdfDocument.Cell.of(orNotAvailable(assessment.assessmentName())));
                cells.add(ReportPdfDocument.Cell.of(orNotAvailable(assessment.className())));
                if (!single) {
                    cells.add(ReportPdfDocument.Cell.of(orNotAvailable(personName(assessment.teacherName()))));
                }
                cells.add(assessment.lastSyncedAt() == null
                        ? ReportPdfDocument.Cell.colored("Not yet synced", ReportPdfDocument.MUTED)
                        : ReportPdfDocument.Cell.of(syncDate(assessment.lastSyncedAt())));
                cells.add(ReportPdfDocument.Cell.colored(uploadStatus(assessment.resultsNotUploaded()),
                        uploadColor(assessment.resultsNotUploaded())));
                assessments.add(cells.toArray(ReportPdfDocument.Cell[]::new));
            }
            if (assessments.isEmpty()) {
                pdf.note("No synchronization activity was recorded in this period.");
            } else if (single) {
                pdf.table(List.of(
                        ReportPdfDocument.Column.center("No.", SYNC_ASSESSMENT_WIDTHS[0]),
                        ReportPdfDocument.Column.left("Assessment", SYNC_ASSESSMENT_WIDTHS[1]),
                        ReportPdfDocument.Column.left("Class", SYNC_ASSESSMENT_WIDTHS[2]),
                        ReportPdfDocument.Column.left("Last Synced", SYNC_ASSESSMENT_WIDTHS[3]),
                        ReportPdfDocument.Column.centerWrap("Upload Status", SYNC_ASSESSMENT_WIDTHS[4])), assessments);
            } else {
                pdf.table(List.of(
                        ReportPdfDocument.Column.center("No.", SYNC_ASSESSMENT_WITH_TEACHER_WIDTHS[0]),
                        ReportPdfDocument.Column.left("Assessment", SYNC_ASSESSMENT_WITH_TEACHER_WIDTHS[1]),
                        ReportPdfDocument.Column.left("Class", SYNC_ASSESSMENT_WITH_TEACHER_WIDTHS[2]),
                        ReportPdfDocument.Column.left("Teacher", SYNC_ASSESSMENT_WITH_TEACHER_WIDTHS[3]),
                        ReportPdfDocument.Column.left("Last Synced", SYNC_ASSESSMENT_WITH_TEACHER_WIDTHS[4]),
                        ReportPdfDocument.Column.centerWrap("Upload Status", SYNC_ASSESSMENT_WITH_TEACHER_WIDTHS[5])),
                        assessments);
            }
            pdf.gap(12);

            pdf.section(3, "Results Not Uploaded");
            List<ReportPdfDocument.Cell[]> failed = new java.util.ArrayList<>();
            number = 1;
            for (var assessment : report.assessments()) {
                if (assessment.resultsNotUploaded() == 0) continue;
                failed.add(new ReportPdfDocument.Cell[]{
                        ReportPdfDocument.Cell.of(Integer.toString(number++)),
                        ReportPdfDocument.Cell.of(orNotAvailable(assessment.assessmentName())),
                        ReportPdfDocument.Cell.of(orNotAvailable(assessment.className())),
                        ReportPdfDocument.Cell.colored(Integer.toString(assessment.resultsNotUploaded()), ReportPdfDocument.BAD),
                        ReportPdfDocument.Cell.of(orNotAvailable(assessment.uploadErrorDetails()))
                });
            }
            if (failed.isEmpty()) {
                pdf.note("All scanned results have been uploaded. Nothing needs attention.");
            } else {
                pdf.table(columns(SYNC_FAILED_HEADERS, SYNC_FAILED_WIDTHS, new boolean[]{true, false, false, true, false}),
                        failed);
                pdf.note(SYNC_SUGGESTED_ACTION);
            }
            for (var warning : report.warnings()) {
                pdf.note("Note: " + warning.message());
            }
            pdf.signature(personName(report.preparedByName()),
                    report.preparedByRole() == null ? "Teacher" : report.preparedByRole());
            return pdf.finish();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate sync-activity PDF export.", e);
        }
    }

    public byte[] exportSyncActivityExcel(V3SyncActivityReportResponse report) {
        boolean single = singleTeacher(report);
        List<String[]> details = new java.util.ArrayList<>(syncDetailsLeft(report));
        details.addAll(syncDetailsRight(report));
        String title = "TEACHER SYNC ACTIVITY";
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var styles = ReportExcelSheet.newStyleCache();

            ReportExcelSheet sync = new ReportExcelSheet(workbook, "Sync Activity",
                    new float[]{7f, 34f, 24f, 24f, 24f, 20f}, styles);
            sync.header(report.schoolName(), title);
            sync.section("1  Sync Summary");
            sync.details(details, List.of(), 1, 5, 0, 0);
            sync.cards(syncCards(report), new int[][]{{1, 1}, {2, 2}, {3, 4}, {5, 5}});
            sync.section("2  Synced Assessments");
            sync.tableHeader(new String[]{"No.", "Assessment", "Class", "Teacher", "Last Synced", "Upload Status"});
            int number = 1;
            for (var assessment : report.assessments()) {
                sync.tableRow(new Object[]{number++, orNotAvailable(assessment.assessmentName()),
                                orNotAvailable(assessment.className()), orNotAvailable(personName(assessment.teacherName())),
                                syncDate(assessment.lastSyncedAt()), uploadStatus(assessment.resultsNotUploaded())},
                        new java.awt.Color[]{null, null, null, null, null, uploadColor(assessment.resultsNotUploaded())},
                        new boolean[]{true, false, false, false, false, true}, null);
            }
            if (report.assessments().isEmpty()) {
                sync.note("No synchronization activity was recorded in this period.");
            }
            sync.signature(personName(report.preparedByName()),
                    report.preparedByRole() == null ? "Teacher" : report.preparedByRole(), 1);
            sync.footer(SCHOOL_FOOTER);

            ReportExcelSheet failed = new ReportExcelSheet(workbook, "Not Uploaded",
                    new float[]{7f, 34f, 24f, 14f, 70f}, styles);
            failed.header(report.schoolName(), title);
            failed.section("3  Results Not Uploaded");
            failed.tableHeader(SYNC_FAILED_HEADERS);
            number = 1;
            for (var assessment : report.assessments()) {
                if (assessment.resultsNotUploaded() == 0) continue;
                failed.tableRow(new Object[]{number++, orNotAvailable(assessment.assessmentName()),
                                orNotAvailable(assessment.className()), assessment.resultsNotUploaded(),
                                orNotAvailable(assessment.uploadErrorDetails())},
                        new java.awt.Color[]{null, null, null, ReportPdfDocument.BAD, null},
                        new boolean[]{true, false, false, true, false}, null);
            }
            failed.skip(1);
            failed.note(number == 1 ? "All scanned results have been uploaded. Nothing needs attention." : SYNC_SUGGESTED_ACTION);
            failed.footer(SCHOOL_FOOTER);

            if (!single && !report.teachers().isEmpty()) {
                ReportExcelSheet teachers = new ReportExcelSheet(workbook, "Teachers",
                        new float[]{7f, 34f, 26f, 16f, 16f}, styles);
                teachers.header(report.schoolName(), title);
                teachers.section("Teachers");
                teachers.tableHeader(SYNC_TEACHER_HEADERS);
                number = 1;
                for (var teacher : report.teachers()) {
                    teachers.tableRow(new Object[]{number++, orNotAvailable(personName(teacher.teacherName())),
                                    teacher.lastSyncedAt() == null ? "Never" : syncDate(teacher.lastSyncedAt()),
                                    teacher.assessmentsSynced(), teacher.resultsNotUploaded()},
                            new java.awt.Color[]{null, null, null, null, uploadColor(teacher.resultsNotUploaded())},
                            new boolean[]{true, false, false, true, true}, null);
                }
                teachers.footer(SCHOOL_FOOTER);
            }

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate sync-activity Excel export.", e);
        }
    }

    // ---------- Learning Competency ----------

    private static final float[] LEAST_MASTERED_WIDTHS = {22f, 185f, 90f, 62f, 72f, 84f};
    private static final String[] LEAST_MASTERED_HEADERS =
            {"No.", "Learning Competency", "Related Skill Area", "Mastery %", "Affected Learners", "Intervention"};
    private static final float[] AFFECTED_LEARNER_WIDTHS = {170f, 170f, 70f, 105f};
    private static final String LEAST_MASTERED_NOTE = "Least mastered first. Mastery % is the class mastery on that"
            + " competency across every finalized assessment in the term; affected learners are those below 80%.";

    private record RootSkill(V3LearningCompetencyReportResponse.RootCompetency root,
                             V3LearningCompetencyReportResponse.Skill skill) {
    }

    /** Every skill across all roots, least mastered first: one ranking, not one per root. */
    private List<RootSkill> leastMasteredFirst(V3LearningCompetencyReportResponse report) {
        return report.rootCompetencies().stream()
                .flatMap(root -> root.skills().stream().map(skill -> new RootSkill(root, skill)))
                .sorted(java.util.Comparator
                        .comparing((RootSkill entry) -> entry.skill().masteryPercentage(),
                                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        .thenComparing(entry -> nullToEmpty(entry.skill().competencyName())))
                .toList();
    }

    private static boolean belowMastery(BigDecimal value) {
        return value != null && value.compareTo(MASTERY_LINE) < 0;
    }

    private List<String[]> competencyDetailsLeft(V3LearningCompetencyReportResponse report) {
        var scope = report.scope();
        String role = report.preparedByRole() == null ? "Teacher" : report.preparedByRole();
        return List.of(
                new String[]{"Grade Level", orNotAvailable(scope.gradeLevelName())},
                new String[]{"Subject", orNotAvailable(scope.subjectName())},
                new String[]{"Teacher".equals(role) ? "Teacher" : "Prepared By",
                        orNotAvailable(personName(report.preparedByName()))});
    }

    private List<String[]> competencyDetailsRight(V3LearningCompetencyReportResponse report) {
        var scope = report.scope();
        int count = report.assessments().size();
        return List.of(
                new String[]{"Term", orNotAvailable(scope.termName())},
                new String[]{"School Year", orNotAvailable(scope.academicYearName())},
                new String[]{"Assessments", count + (count == 1 ? " finalized assessment" : " finalized assessments")},
                new String[]{"Generated On", REPORT_DATE_TIME.format(report.generatedAt())});
    }

    private String includedAssessments(V3LearningCompetencyReportResponse report) {
        return report.assessments().isEmpty() ? null : "Assessments included: " + report.assessments().stream()
                .map(V3LearningCompetencyReportResponse.IncludedAssessment::testName)
                .collect(Collectors.joining(", "));
    }

    private List<String> competencyActions(V3LearningCompetencyReportResponse report) {
        List<String> actions = new java.util.ArrayList<>();
        for (RootSkill entry : leastMasteredFirst(report)) {
            var skill = entry.skill();
            if (!belowMastery(skill.masteryPercentage())) continue;
            String action = skill.suggestion() == null || skill.suggestion().isBlank()
                    ? "Reteach " + orNotAvailable(skill.competencyName()) + "."
                    : skill.suggestion();
            actions.add(action + " (" + percent(skill.masteryPercentage()) + " class mastery; "
                    + skill.weakStudentCount() + (skill.weakStudentCount() == 1 ? " learner" : " learners")
                    + " below 80%)");
        }
        return actions;
    }

    private List<ReportPdfDocument.Card> competencyCards(V3LearningCompetencyReportResponse report) {
        List<RootSkill> all = leastMasteredFirst(report);
        long below = all.stream().filter(entry -> belowMastery(entry.skill().masteryPercentage())).count();
        java.util.Set<Long> needing = new java.util.HashSet<>();
        java.util.Set<Long> priority = new java.util.HashSet<>();
        for (RootSkill entry : all) {
            for (var student : entry.skill().weakStudents()) {
                needing.add(student.studentId());
                if ("priority_intervention".equals(student.recommendationCode())) {
                    priority.add(student.studentId());
                }
            }
        }
        return List.of(
                new ReportPdfDocument.Card("Competencies Analyzed", Integer.toString(all.size()), "(in this term)"),
                new ReportPdfDocument.Card("Least Mastered", Long.toString(below), "(below 80% class mastery)",
                        below == 0 ? ReportPdfDocument.GOOD : ReportPdfDocument.ORANGE),
                new ReportPdfDocument.Card("Learners Needing Support", Integer.toString(needing.size()),
                        "(below 80% in 1+ competency)"),
                new ReportPdfDocument.Card("Priority Intervention", Integer.toString(priority.size()),
                        "(below 40% in 1+ competency)", priority.isEmpty() ? ReportPdfDocument.GOOD : ReportPdfDocument.BAD));
    }

    public byte[] exportLearningCompetencyPdf(V3LearningCompetencyReportResponse report) {
        try {
            ReportPdfDocument pdf = new ReportPdfDocument(report.scope().schoolName(), "LEARNING COMPETENCY", SCHOOL_FOOTER);
            pdf.section(null, "Report Details");
            pdf.details(competencyDetailsLeft(report), competencyDetailsRight(report));
            String included = includedAssessments(report);
            if (included != null) {
                pdf.note(included);
            }
            pdf.gap(10);

            List<RootSkill> ranked = leastMasteredFirst(report);
            pdf.section(1, "Least Mastered Competencies");
            if (ranked.isEmpty()) {
                for (var warning : report.warnings()) {
                    pdf.note(warning.message());
                }
                if (report.warnings().isEmpty()) {
                    pdf.note("No competency was assessed in this term.");
                }
            } else {
                List<ReportPdfDocument.Cell[]> rows = new java.util.ArrayList<>();
                int number = 1;
                for (RootSkill entry : ranked) {
                    var skill = entry.skill();
                    rows.add(new ReportPdfDocument.Cell[]{
                            ReportPdfDocument.Cell.of(Integer.toString(number++)),
                            ReportPdfDocument.Cell.of(orNotAvailable(skill.competencyName())),
                            ReportPdfDocument.Cell.of(orNotAvailable(entry.root().rootTagName())),
                            ReportPdfDocument.Cell.colored(percent(skill.masteryPercentage()),
                                    statusColor(skill.masteryStatusCode())),
                            ReportPdfDocument.Cell.of(skill.weakStudentCount() + " of " + skill.studentCount()),
                            ReportPdfDocument.Cell.colored(orNotAvailable(skill.recommendationLabel()),
                                    statusColor(skill.recommendationCode()))
                    });
                }
                pdf.table(List.of(
                        ReportPdfDocument.Column.center("No.", LEAST_MASTERED_WIDTHS[0]),
                        ReportPdfDocument.Column.left("Learning Competency", LEAST_MASTERED_WIDTHS[1]),
                        ReportPdfDocument.Column.left("Related Skill Area", LEAST_MASTERED_WIDTHS[2]),
                        ReportPdfDocument.Column.center("Mastery %", LEAST_MASTERED_WIDTHS[3]),
                        ReportPdfDocument.Column.center("Affected Learners", LEAST_MASTERED_WIDTHS[4]),
                        ReportPdfDocument.Column.centerWrap("Intervention", LEAST_MASTERED_WIDTHS[5])), rows);
                pdf.note(LEAST_MASTERED_NOTE);
            }
            pdf.gap(10);

            pdf.section(2, "Affected Learners");
            List<ReportPdfDocument.Cell[]> learners = new java.util.ArrayList<>();
            for (RootSkill entry : ranked) {
                var skill = entry.skill();
                boolean first = true;
                for (var student : skill.weakStudents()) {
                    learners.add(new ReportPdfDocument.Cell[]{
                            first ? new ReportPdfDocument.Cell(orNotAvailable(skill.competencyName()), null, true)
                                    : ReportPdfDocument.Cell.of(""),
                            ReportPdfDocument.Cell.of(orNotAvailable(personName(student.fullName()))),
                            ReportPdfDocument.Cell.colored(percent(student.masteryPercentage()),
                                    statusColor(student.masteryStatusCode())),
                            ReportPdfDocument.Cell.colored(orNotAvailable(student.recommendationLabel()),
                                    statusColor(student.recommendationCode()))
                    });
                    first = false;
                }
            }
            if (learners.isEmpty()) {
                pdf.note("No learner is below 80% on any competency in this term.");
            } else {
                pdf.table(List.of(
                        ReportPdfDocument.Column.left("Learning Competency", AFFECTED_LEARNER_WIDTHS[0]),
                        ReportPdfDocument.Column.left("Learner Name", AFFECTED_LEARNER_WIDTHS[1]),
                        ReportPdfDocument.Column.center("Mastery %", AFFECTED_LEARNER_WIDTHS[2]),
                        ReportPdfDocument.Column.centerWrap("Concern Level", AFFECTED_LEARNER_WIDTHS[3])),
                        learners, true);
                pdf.note(RECOMMENDATION_LEGEND);
            }
            pdf.gap(10);

            pdf.section(3, "Recommended Teacher Actions");
            List<String> actions = competencyActions(report);
            if (actions.isEmpty()) {
                pdf.note(ranked.isEmpty()
                        ? "No recommendation yet: there are no competency results for this term."
                        : "Every competency is at the Mastered level (80% and above). Maintain current instruction.");
            } else {
                pdf.gap(4);
                pdf.numberedList(actions);
            }
            pdf.gap(10);

            pdf.section(4, "Summary");
            pdf.cards(competencyCards(report));
            if (!ranked.isEmpty()) {
                for (var warning : report.warnings()) {
                    pdf.note("Note: " + warning.message());
                }
            }
            pdf.signature(personName(report.preparedByName()),
                    report.preparedByRole() == null ? "Teacher" : report.preparedByRole());
            return pdf.finish();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate learning-competency PDF export.", e);
        }
    }

    public byte[] exportLearningCompetencyExcel(V3LearningCompetencyReportResponse report) {
        List<String[]> details = new java.util.ArrayList<>(competencyDetailsLeft(report));
        details.addAll(competencyDetailsRight(report));
        String school = report.scope().schoolName();
        String title = "LEARNING COMPETENCY";
        List<RootSkill> ranked = leastMasteredFirst(report);
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var styles = ReportExcelSheet.newStyleCache();

            ReportExcelSheet competencies = new ReportExcelSheet(workbook, "Competencies",
                    new float[]{7f, 50f, 26f, 12f, 17f, 22f}, styles);
            competencies.header(school, title);
            competencies.section("Report Details");
            competencies.details(details, List.of(), 1, 5, 0, 0);
            String included = includedAssessments(report);
            if (included != null) {
                competencies.note(included);
                competencies.skip(1);
            }
            competencies.section("1  Least Mastered Competencies");
            competencies.tableHeader(LEAST_MASTERED_HEADERS);
            int number = 1;
            for (RootSkill entry : ranked) {
                var skill = entry.skill();
                competencies.tableRow(new Object[]{number++, orNotAvailable(skill.competencyName()),
                                orNotAvailable(entry.root().rootTagName()),
                                skill.masteryPercentage() == null ? NOT_AVAILABLE : skill.masteryPercentage(),
                                skill.weakStudentCount() + " of " + skill.studentCount(),
                                orNotAvailable(skill.recommendationLabel())},
                        new java.awt.Color[]{null, null, null, statusColor(skill.masteryStatusCode()), null,
                                statusColor(skill.recommendationCode())},
                        new boolean[]{true, false, false, true, true, true}, "...%..");
            }
            competencies.skip(1);
            competencies.note(ranked.isEmpty() ? "No competency was assessed in this term." : LEAST_MASTERED_NOTE);
            competencies.skip(1);
            competencies.section("4  Summary");
            competencies.cards(competencyCards(report), new int[][]{{1, 1}, {2, 2}, {3, 4}, {5, 5}});
            competencies.signature(personName(report.preparedByName()),
                    report.preparedByRole() == null ? "Teacher" : report.preparedByRole(), 1);
            competencies.footer(SCHOOL_FOOTER);

            ReportExcelSheet learners = new ReportExcelSheet(workbook, "Affected Learners",
                    new float[]{7f, 44f, 34f, 12f, 22f}, styles);
            learners.header(school, title);
            learners.section("2  Affected Learners");
            learners.tableHeader(new String[]{"#", "Learning Competency", "Learner Name", "Mastery %", "Concern Level"});
            number = 1;
            for (RootSkill entry : ranked) {
                var skill = entry.skill();
                int firstRow = -1;
                int lastRow = -1;
                for (var student : skill.weakStudents()) {
                    int written = learners.tableRow(new Object[]{number++, orNotAvailable(skill.competencyName()),
                                    orNotAvailable(personName(student.fullName())),
                                    student.masteryPercentage() == null ? NOT_AVAILABLE : student.masteryPercentage(),
                                    orNotAvailable(student.recommendationLabel())},
                            new java.awt.Color[]{null, null, null, statusColor(student.masteryStatusCode()),
                                    statusColor(student.recommendationCode())},
                            new boolean[]{true, false, false, true, true}, "...%.");
                    if (firstRow < 0) firstRow = written;
                    lastRow = written;
                }
                if (firstRow >= 0) learners.mergeColumn(firstRow, lastRow, 1);
            }
            learners.skip(1);
            learners.note(number == 1 ? "No learner is below 80% on any competency in this term." : RECOMMENDATION_LEGEND);
            learners.footer(SCHOOL_FOOTER);

            ReportExcelSheet actions = new ReportExcelSheet(workbook, "Teacher Actions",
                    new float[]{7f, 110f}, styles);
            actions.header(school, title);
            actions.section("3  Recommended Teacher Actions");
            List<String> items = competencyActions(report);
            if (items.isEmpty()) {
                actions.note("Every competency is at the Mastered level (80% and above). Maintain current instruction.");
            } else {
                actions.tableHeader(new String[]{"#", "Recommended Action"});
                number = 1;
                for (String item : items) {
                    actions.tableRow(new Object[]{number++, item}, null, new boolean[]{true, false}, null);
                }
            }
            actions.footer(SCHOOL_FOOTER);

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate learning-competency Excel export.", e);
        }
    }

    // ---------- Assessment Results PDF ----------

    public byte[] exportAssessmentResultsPdf(V3AssessmentResultsReportResponse report) {
        try {
            ReportPdfDocument pdf = new ReportPdfDocument(report.scope().schoolName(),
                    "ASSESSMENT RESULTS / CLASS RECORD", SCHOOL_FOOTER);
            pdf.section(null, "Assessment Details");
            pdf.details(classRecordDetailsLeft(report), classRecordDetailsRight(report));
            pdf.gap(10);
            pdf.section(null, "Class Summary");
            pdf.cards(classRecordCards(report));
            pdf.gap(10);
            pdf.section(null, "Class Record");
            List<ReportPdfDocument.Cell[]> rows = new java.util.ArrayList<>();
            for (ClassRecordLine line : classRecordLines(report)) {
                rows.add(new ReportPdfDocument.Cell[]{
                        ReportPdfDocument.Cell.of(line.number()),
                        ReportPdfDocument.Cell.of(line.name()),
                        ReportPdfDocument.Cell.of(line.score()),
                        ReportPdfDocument.Cell.of(line.maximum()),
                        ReportPdfDocument.Cell.of(line.percentage()),
                        ReportPdfDocument.Cell.colored(line.performance(), line.color())
                });
            }
            pdf.table(columns(CLASS_RECORD_HEADERS, CLASS_RECORD_WIDTHS,
                    new boolean[]{true, false, true, true, true, true}), rows);
            for (var warning : report.warnings()) {
                pdf.note("Note: " + warning.message());
            }
            pdf.note(PERFORMANCE_LEGEND);
            pdf.signature(personName(report.scope().teacherName()), "Teacher");
            return pdf.finish();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate assessment-results PDF export.", e);
        }
    }

    // ---------- PDF page / drawing plumbing ----------

    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float MARGIN = 40f;
    private static final float ROW_HEIGHT = 20f;
    private static final float FOOTER_RESERVE = 24f;
    private static final PDFont FONT_REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private static final class PdfCursor {
        final PDPage page;
        PDPageContentStream content;
        float y;

        PdfCursor(PDPage page, PDPageContentStream content, float y) {
            this.page = page;
            this.content = content;
            this.y = y;
        }
    }

    private PdfCursor newPage(PDDocument document) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        PDPageContentStream content = new PDPageContentStream(document, page);
        return new PdfCursor(page, content, PAGE_HEIGHT - MARGIN);
    }

    private void closePage(PdfCursor cursor) throws IOException {
        cursor.content.close();
    }

    /**
     * Page numbers require knowing the final page count, which isn't known until every
     * page has been drawn - so the footer is stamped in one pass at the end, over the
     * already-closed pages, rather than while each page is being built.
     */
    private void finalizeFooters(PDDocument document) throws IOException {
        int total = document.getNumberOfPages();
        for (int i = 0; i < total; i++) {
            try (PDPageContentStream footer = new PDPageContentStream(
                    document, document.getPage(i), PDPageContentStream.AppendMode.APPEND, true)) {
                footer.setNonStrokingColor(0.5f, 0.5f, 0.5f);
                drawText(footer, FONT_REGULAR, 8, MARGIN, MARGIN - 18f,
                        "SMART Assessment System  -  Page " + (i + 1) + " of " + total);
                footer.setNonStrokingColor(0f, 0f, 0f);
            }
        }
    }

    private float drawBrandedTitleBand(PDPageContentStream content, String title, String subtitle, String meta) throws IOException {
        float bandHeight = 52f;
        float y = PAGE_HEIGHT - MARGIN;
        content.setNonStrokingColor(BRAND_RGB[0], BRAND_RGB[1], BRAND_RGB[2]);
        content.addRect(MARGIN, y - bandHeight + 12, PAGE_WIDTH - 2 * MARGIN, bandHeight);
        content.fill();
        content.setNonStrokingColor(1f, 1f, 1f);
        drawText(content, FONT_BOLD, 16, MARGIN + 10, y - 18, title);
        drawText(content, FONT_REGULAR, 10.5f, MARGIN + 10, y - 36, subtitle);
        content.setNonStrokingColor(0f, 0f, 0f);
        y -= bandHeight + 10;
        if (meta != null && !meta.isBlank()) {
            drawText(content, FONT_REGULAR, 9, MARGIN, y, meta);
            y -= 16f;
        }
        return y - 6f;
    }

    private float drawTableHeader(PDPageContentStream content, float y, String[] headers, float[] widths) throws IOException {
        float x = MARGIN;
        content.setNonStrokingColor(BRAND_RGB[0], BRAND_RGB[1], BRAND_RGB[2]);
        content.addRect(MARGIN, y - ROW_HEIGHT + 5, sum(widths), ROW_HEIGHT);
        content.fill();
        content.setNonStrokingColor(1f, 1f, 1f);
        for (int i = 0; i < headers.length; i++) {
            drawText(content, FONT_BOLD, 9.5f, x + 4, y - 13, headers[i]);
            x += widths[i];
        }
        content.setNonStrokingColor(0f, 0f, 0f);
        return y - ROW_HEIGHT;
    }

    private float drawTableRow(PDPageContentStream content, float y, String[] values, float[] widths, boolean striped) throws IOException {
        float totalWidth = sum(widths);
        if (striped) {
            content.setNonStrokingColor(ZEBRA_RGB[0], ZEBRA_RGB[1], ZEBRA_RGB[2]);
            content.addRect(MARGIN, y - ROW_HEIGHT + 5, totalWidth, ROW_HEIGHT);
            content.fill();
            content.setNonStrokingColor(0f, 0f, 0f);
        }
        float x = MARGIN;
        for (int i = 0; i < values.length; i++) {
            drawText(content, FONT_REGULAR, 9.5f, x + 4, y - 13, fitText(values[i], widths[i] - 8));
            x += widths[i];
        }
        content.setStrokingColor(GRID_RGB[0], GRID_RGB[1], GRID_RGB[2]);
        content.setLineWidth(0.4f);
        content.moveTo(MARGIN, y - ROW_HEIGHT + 5);
        content.lineTo(MARGIN + totalWidth, y - ROW_HEIGHT + 5);
        content.stroke();
        float vx = MARGIN;
        for (float w : widths) {
            content.moveTo(vx, y + 5);
            content.lineTo(vx, y - ROW_HEIGHT + 5);
            content.stroke();
            vx += w;
        }
        content.moveTo(vx, y + 5);
        content.lineTo(vx, y - ROW_HEIGHT + 5);
        content.stroke();
        return y - ROW_HEIGHT;
    }

    private String fitText(String text, float maxWidth) {
        return fitText(text, FONT_REGULAR, 9.5f, maxWidth);
    }

    private String fitText(String text, PDFont font, float size, float maxWidth) {
        if (text == null) {
            return "";
        }
        try {
            if (font.getStringWidth(text) / 1000f * size <= maxWidth) {
                return text;
            }
            String truncated = text;
            while (!truncated.isEmpty() && font.getStringWidth(truncated + "...") / 1000f * size > maxWidth) {
                truncated = truncated.substring(0, truncated.length() - 1);
            }
            return truncated.isEmpty() ? text : truncated + "...";
        } catch (IOException e) {
            return text;
        }
    }

    /** Word-wraps text to at most maxLines lines of maxWidth; the last line is truncated with
     *  "..." if the text still does not fit. */
    private List<String> wrapText(String text, PDFont font, float size, float maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String[] words = text.trim().split("\\s+");
        int index = 0;
        try {
            while (index < words.length && lines.size() < maxLines) {
                String candidate = current.isEmpty() ? words[index] : current + " " + words[index];
                if (current.isEmpty() || font.getStringWidth(candidate) / 1000f * size <= maxWidth) {
                    current.setLength(0);
                    current.append(candidate);
                    index++;
                } else {
                    lines.add(current.toString());
                    current.setLength(0);
                }
            }
        } catch (IOException e) {
            return List.of(fitText(text, font, size, maxWidth));
        }
        if (!current.isEmpty() && lines.size() < maxLines) {
            lines.add(current.toString());
        }
        if (index < words.length && !lines.isEmpty()) {
            // Ran out of lines: mark the cut on the last one.
            String last = lines.remove(lines.size() - 1);
            lines.add(last + " " + String.join(" ", List.of(words).subList(index, words.length)));
        }
        // Also trims a single word that is wider than the whole line on its own.
        lines.replaceAll(line -> fitText(line, font, size, maxWidth));
        return lines;
    }

    private void drawText(PDPageContentStream content, PDFont font, float size, float x, float y, String text) throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(text == null ? "" : text);
        content.endText();
    }

    private float sum(float[] values) {
        float total = 0;
        for (float v : values) {
            total += v;
        }
        return total;
    }

    // ---------- Shared helpers ----------

    private String percentOrBlank(BigDecimal value) {
        return value == null ? "" : value.toPlainString() + "%";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Turns a raw snake_case code (multiple_choice, needs_support) into a readable label
     *  (Multiple Choice, Needs Support) for a non-technical reader. */
    private String humanize(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return "";
        }
        String[] words = rawCode.replace('-', '_').split("_");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (label.length() > 0) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return label.toString();
    }

    // ---------- Excel styling / helpers ----------

    private void setCell(Row row, int column, String value) {
        row.createCell(column).setCellValue(value);
    }

    private void setCell(Row row, int column, int value) {
        row.createCell(column).setCellValue(value);
    }

    private int writeTitleBlock(Sheet sheet, String title, String subtitle, int columnCount) {
        XSSFWorkbook workbook = (XSSFWorkbook) sheet.getWorkbook();
        int rowIndex = 0;

        Row titleRow = sheet.createRow(rowIndex);
        titleRow.setHeightInPoints(26f);
        CellStyle titleStyle = createTitleStyle(workbook);
        for (int i = 0; i < columnCount; i++) {
            Cell cell = titleRow.createCell(i);
            if (i == 0) {
                cell.setCellValue(title);
            }
            cell.setCellStyle(titleStyle);
        }
        if (columnCount > 1) {
            sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, columnCount - 1));
        }
        rowIndex++;

        Row subtitleRow = sheet.createRow(rowIndex);
        CellStyle subtitleStyle = createSubtitleStyle(workbook);
        Cell subtitleCell = subtitleRow.createCell(0);
        subtitleCell.setCellValue(subtitle);
        subtitleCell.setCellStyle(subtitleStyle);
        rowIndex++;

        return rowIndex + 1;
    }

    private void writeHeaderRow(Sheet sheet, int rowIndex, XSSFWorkbook workbook, String[] columns) {
        Row header = sheet.createRow(rowIndex);
        CellStyle headerStyle = createHeaderStyle(workbook);
        for (int i = 0; i < columns.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void applyBodyStyle(XSSFWorkbook workbook, Row row, int columnCount, boolean striped) {
        CellStyle style = createBodyStyle(workbook, striped);
        for (int i = 0; i < columnCount; i++) {
            Cell cell = row.getCell(i);
            if (cell == null) {
                cell = row.createCell(i);
            }
            cell.setCellStyle(style);
        }
    }

    private CellStyle createTitleStyle(XSSFWorkbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 15);
        font.setColor(IndexedColors.WHITE.getIndex());
        XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(BRAND_RGB_BYTES, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createSubtitleStyle(XSSFWorkbook workbook) {
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 11);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        font.setColor(IndexedColors.WHITE.getIndex());
        XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(BRAND_RGB_BYTES, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle createBodyStyle(XSSFWorkbook workbook, boolean striped) {
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 11);
        XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
        style.setFont(font);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        if (striped) {
            style.setFillForegroundColor(new XSSFColor(ZEBRA_RGB_BYTES, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return style;
    }
}
