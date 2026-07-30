package com.capstone.assessment.importexport.service;

import com.capstone.assessment.common.exception.BadRequestException;
import com.capstone.assessment.common.exception.ResourceNotFoundException;
import com.capstone.assessment.importexport.dto.Sf1ImportPreviewResponse;
import com.capstone.assessment.importexport.dto.Sf1ImportSummaryResponse;
import com.capstone.assessment.importexport.dto.Sf1PreviewRowDto;
import com.capstone.assessment.importexport.repository.Sf1ImportRepository;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.EmptyFileException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class Sf1ImportServiceImpl implements Sf1ImportService {

    private static final String EXPECTED_SHEET_NAME = "school_form_1_ver2014.2.1.1";
    private static final String STATUS_VALID = "VALID";
    private static final String IMPORT_SUCCESS_MESSAGE = "SF1 import completed successfully.";
    private static final Pattern EXACT_LRN_PATTERN = Pattern.compile("^\\d{12}$");
    private static final Pattern EMBEDDED_LRN_PATTERN = Pattern.compile("\\b(\\d{12})\\b");
    private static final Pattern SCHOOL_YEAR_PATTERN = Pattern.compile(
            "\\b(20\\d{2})\\s*(?:-|\\u2013|\\u2014|to)?\\s*(20\\d{2})\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SINGLE_YEAR_PATTERN = Pattern.compile("(?<!\\d)(20\\d{2})(?!\\d)");
    private static final Pattern SECTION_PATTERN = Pattern.compile(
            "\\bsection\\s*[:\\-_]?\\s*([A-Za-z0-9][A-Za-z0-9 .'-]*)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern GRADE_LEVEL_PATTERN = Pattern.compile(
            "(?<![A-Za-z0-9])grade(?:\\s*level)?\\s*[:\\-_]?\\s*(7|8|9|10|11|12)(?![A-Za-z0-9])",
            Pattern.CASE_INSENSITIVE
    );

    private static final int STUDENT_ROW_START_INDEX = 6;   // Excel row 7
    private static final int STUDENT_ROW_END_INDEX = 46;    // Excel row 47

    private static final int LRN_START_COLUMN = 0;          // A
    private static final int LRN_END_COLUMN = 1;            // B
    private static final int NAME_START_COLUMN = 2;         // C
    private static final int NAME_END_COLUMN = 5;           // F
    private static final int GENDER_COLUMN = 6;             // G

    private static final int SCHOOL_YEAR_ROW_INDEX = 3;     // Row 4
    private static final int SCHOOL_YEAR_START_COLUMN = 19; // T
    private static final int SCHOOL_YEAR_END_COLUMN = 23;   // X
    private static final int SECTION_ROW_INDEX = 3;         // Row 4
    private static final int SECTION_START_COLUMN = 38;     // AM
    private static final int SECTION_END_COLUMN = 46;       // AU
    private static final int GRADE_LEVEL_SCAN_ROW_LIMIT = 10;
    private static final int HEADER_SCAN_ROW_LIMIT = 12;
    private static final int HEADER_VALUE_LOOKAHEAD_COLUMNS = 10;

    private final Sf1ImportRepository sf1ImportRepository;

    public Sf1ImportServiceImpl(Sf1ImportRepository sf1ImportRepository) {
        this.sf1ImportRepository = sf1ImportRepository;
    }

    @Override
    public Sf1ImportPreviewResponse generatePreview(MultipartFile file) {
        ParsedSf1Data parsedSf1Data = parseSf1File(file);
        return buildPreviewResponse(parsedSf1Data);
    }

    @Override
    @Transactional
    public Sf1ImportSummaryResponse confirmImport(
            MultipartFile file,
            Long sectionId,
            Long academicYearId,
            Long gradeLevelId
    ) {
        validateFile(file);

        ParsedSf1Data parsedSf1Data = parseSf1File(file);
        ResolvedImportContext resolvedImportContext = resolveImportContext(
                parsedSf1Data,
                sectionId,
                academicYearId,
                gradeLevelId
        );

        List<Sf1PreviewRowDto> rows = parsedSf1Data.rows();
        int importedStudents = 0;
        int updatedStudents = 0;
        int enrolledStudents = 0;
        int skippedRows = 0;

        for (Sf1PreviewRowDto row : rows) {
            if (!STATUS_VALID.equals(row.status())) {
                skippedRows++;
                continue;
            }

            Long studentId;
            Optional<Long> existingStudentId = sf1ImportRepository.findStudentIdByLrn(row.studentLrn());
            if (existingStudentId.isPresent()) {
                studentId = existingStudentId.orElseThrow();
                sf1ImportRepository.updateStudent(studentId, row.firstName(), row.lastName(), row.gender());
                updatedStudents++;
            } else {
                studentId = sf1ImportRepository.createStudent(
                        row.studentLrn(),
                        row.firstName(),
                        row.lastName(),
                        row.gender()
                );
                importedStudents++;
            }

            if (!sf1ImportRepository.enrollmentExists(
                    studentId,
                    resolvedImportContext.sectionId(),
                    resolvedImportContext.academicYearId()
            )) {
                sf1ImportRepository.enrollStudent(
                        studentId,
                        resolvedImportContext.sectionId(),
                        resolvedImportContext.academicYearId()
                );
                enrolledStudents++;
            }
        }

        return new Sf1ImportSummaryResponse(
                parsedSf1Data.detectedSchoolYear(),
                parsedSf1Data.detectedSectionName(),
                importedStudents,
                updatedStudents,
                enrolledStudents,
                skippedRows,
                IMPORT_SUCCESS_MESSAGE
        );
    }

    private ParsedSf1Data parseSf1File(MultipartFile file) {
        validateFile(file);

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = resolveSheet(workbook);
            DataFormatter formatter = new DataFormatter();

            String detectedSchoolYear = firstNonBlank(
                    normalizeSchoolYear(readRangeValue(
                            sheet,
                            SCHOOL_YEAR_ROW_INDEX,
                            SCHOOL_YEAR_START_COLUMN,
                            SCHOOL_YEAR_END_COLUMN,
                            formatter
                    )),
                    detectSchoolYear(sheet, formatter),
                    detectSchoolYearFromFileName(file.getOriginalFilename())
            );
            String detectedSectionName = firstNonBlank(
                    normalizeSectionName(readRangeValue(
                            sheet,
                            SECTION_ROW_INDEX,
                            SECTION_START_COLUMN,
                            SECTION_END_COLUMN,
                            formatter
                    )),
                    detectSectionName(sheet, formatter),
                    detectSectionNameFromFileName(file.getOriginalFilename())
            );
            String detectedGradeLevelName = firstNonBlank(
                    detectGradeLevelName(sheet, formatter),
                    detectGradeLevelNameFromText(file.getOriginalFilename())
            );

            List<Sf1PreviewRowDto> rows = parseExactSf1Layout(sheet, formatter);
            List<Sf1PreviewRowDto> parsedRows = rows.isEmpty() ? parseGenericFallback(sheet, formatter) : rows;
            return new ParsedSf1Data(detectedSchoolYear, detectedSectionName, detectedGradeLevelName, parsedRows);
        } catch (BadRequestException exception) {
            throw exception;
        } catch (IOException | EncryptedDocumentException | EmptyFileException exception) {
            throw new BadRequestException("Unable to read SF1 file. Please upload a valid Excel file.");
        } catch (RuntimeException exception) {
            if (exception.getClass().getName().startsWith("org.apache.poi")) {
                throw new BadRequestException("Unable to read SF1 file. Please upload a valid Excel file.");
            }
            throw exception;
        }
    }

    private Sf1ImportPreviewResponse buildPreviewResponse(ParsedSf1Data parsedSf1Data) {
        int validRows = (int) parsedSf1Data.rows().stream()
                .filter(row -> STATUS_VALID.equals(row.status()))
                .count();

        return new Sf1ImportPreviewResponse(
                parsedSf1Data.detectedSchoolYear(),
                parsedSf1Data.detectedSectionName(),
                parsedSf1Data.rows().size(),
                validRows,
                parsedSf1Data.rows().size() - validRows,
                parsedSf1Data.rows()
        );
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("SF1 file is required.");
        }
    }

    private ResolvedImportContext resolveImportContext(
            ParsedSf1Data parsedSf1Data,
            Long sectionIdOverride,
            Long academicYearIdOverride,
            Long gradeLevelId
    ) {
        Long resolvedAcademicYearId = resolveAcademicYearId(parsedSf1Data.detectedSchoolYear(), academicYearIdOverride);
        Long resolvedGradeLevelId = sectionIdOverride == null
                ? resolveGradeLevelId(parsedSf1Data.detectedGradeLevelName(), gradeLevelId)
                : gradeLevelId;
        Long resolvedSectionId = resolveSectionId(
                parsedSf1Data.detectedSectionName(),
                sectionIdOverride,
                resolvedGradeLevelId,
                resolvedAcademicYearId
        );
        return new ResolvedImportContext(resolvedSectionId, resolvedAcademicYearId);
    }

    private Long resolveAcademicYearId(String detectedSchoolYear, Long academicYearIdOverride) {
        if (academicYearIdOverride != null) {
            if (!sf1ImportRepository.academicYearExists(academicYearIdOverride)) {
                throw new ResourceNotFoundException("Academic year not found.");
            }
            return academicYearIdOverride;
        }

        return sf1ImportRepository.findAcademicYearIdByName(detectedSchoolYear)
                .orElseThrow(() -> new BadRequestException("Academic year from SF1 was not found in the system."));
    }

    private Long resolveGradeLevelId(String detectedGradeLevelName, Long gradeLevelIdOverride) {
        if (gradeLevelIdOverride != null) {
            if (!sf1ImportRepository.gradeLevelExists(gradeLevelIdOverride)) {
                throw new ResourceNotFoundException("Grade level not found.");
            }
            return gradeLevelIdOverride;
        }

        if (isBlank(detectedGradeLevelName)) {
            throw new BadRequestException("Grade level is required before confirming import.");
        }

        return sf1ImportRepository.findGradeLevelIdByName(detectedGradeLevelName)
                .orElseThrow(() -> new BadRequestException("Detected grade level was not found in the system."));
    }

    private Long resolveSectionId(
            String detectedSectionName,
            Long sectionIdOverride,
            Long gradeLevelId,
            Long academicYearId
    ) {
        if (sectionIdOverride != null) {
            if (!sf1ImportRepository.sectionExists(sectionIdOverride)) {
                throw new ResourceNotFoundException("Section not found.");
            }
            if (!sf1ImportRepository.sectionMatchesAcademicYear(sectionIdOverride, academicYearId)) {
                throw new BadRequestException("Selected section does not match the selected academic year.");
            }
            return sectionIdOverride;
        }

        if (isBlank(detectedSectionName)) {
            throw new BadRequestException("Section name from SF1 was not found.");
        }

        return sf1ImportRepository.findSectionIdByContext(detectedSectionName, gradeLevelId, academicYearId)
                .orElseGet(() -> sf1ImportRepository.createSection(gradeLevelId, academicYearId, detectedSectionName));
    }

    private String detectSchoolYear(Sheet sheet, DataFormatter formatter) {
        for (int rowIndex = sheet.getFirstRowNum();
             rowIndex <= Math.min(sheet.getLastRowNum(), sheet.getFirstRowNum() + HEADER_SCAN_ROW_LIMIT);
             rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            short lastCellNum = row.getLastCellNum();
            if (lastCellNum < 0) {
                continue;
            }

            for (int cellIndex = 0; cellIndex < lastCellNum; cellIndex++) {
                String value = readCellValue(row, cellIndex, formatter);
                if (!containsLabel(value, "school year")) {
                    continue;
                }

                String inlineValue = normalizeSchoolYear(value);
                if (!isBlank(inlineValue)) {
                    return inlineValue;
                }

                String rightSideValue = normalizeSchoolYear(readRangeValue(
                        row,
                        cellIndex + 1,
                        Math.min(lastCellNum - 1, cellIndex + HEADER_VALUE_LOOKAHEAD_COLUMNS),
                        formatter
                ));
                if (!isBlank(rightSideValue)) {
                    return rightSideValue;
                }
            }

            String rowValue = normalizeSchoolYear(readRowText(row, formatter));
            if (!isBlank(rowValue)) {
                return rowValue;
            }
        }

        return null;
    }

    private String detectSectionName(Sheet sheet, DataFormatter formatter) {
        for (int rowIndex = sheet.getFirstRowNum();
             rowIndex <= Math.min(sheet.getLastRowNum(), sheet.getFirstRowNum() + HEADER_SCAN_ROW_LIMIT);
             rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            short lastCellNum = row.getLastCellNum();
            if (lastCellNum < 0) {
                continue;
            }

            for (int cellIndex = 0; cellIndex < lastCellNum; cellIndex++) {
                String value = readCellValue(row, cellIndex, formatter);
                if (!containsLabel(value, "section")) {
                    continue;
                }

                String inlineValue = normalizeSectionName(value);
                if (!isBlank(inlineValue)) {
                    return inlineValue;
                }

                String rightSideValue = normalizeSectionName(readRangeValue(
                        row,
                        cellIndex + 1,
                        Math.min(lastCellNum - 1, cellIndex + HEADER_VALUE_LOOKAHEAD_COLUMNS),
                        formatter
                ));
                if (!isBlank(rightSideValue)) {
                    return rightSideValue;
                }
            }

            String rowValue = extractSectionNameFromText(readRowText(row, formatter));
            if (!isBlank(rowValue)) {
                return rowValue;
            }
        }

        return null;
    }

    private String detectGradeLevelName(Sheet sheet, DataFormatter formatter) {
        int firstRow = sheet.getFirstRowNum();
        int lastRow = Math.min(sheet.getLastRowNum(), firstRow + GRADE_LEVEL_SCAN_ROW_LIMIT);

        for (int rowIndex = firstRow; rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            String rowText = readRowText(row, formatter);
            if (isBlank(rowText)) {
                continue;
            }

            Matcher matcher = GRADE_LEVEL_PATTERN.matcher(rowText);
            if (matcher.find()) {
                return "Grade " + matcher.group(1);
            }
        }

        return null;
    }

    private String detectSchoolYearFromFileName(String fileName) {
        String schoolYear = normalizeSchoolYear(fileName);
        if (!isBlank(schoolYear)) {
            return schoolYear;
        }

        String normalizedFileName = normalizeValue(fileName);
        Matcher matcher = SINGLE_YEAR_PATTERN.matcher(normalizedFileName == null ? "" : normalizedFileName);
        if (matcher.find()) {
            int startYear = Integer.parseInt(matcher.group(1));
            return startYear + "-" + (startYear + 1);
        }

        return null;
    }

    private String detectSectionNameFromFileName(String fileName) {
        return normalizeSectionName(fileName);
    }

    private String detectGradeLevelNameFromText(String value) {
        String normalized = normalizeValue(value);
        if (normalized == null) {
            return null;
        }

        Matcher matcher = GRADE_LEVEL_PATTERN.matcher(normalized);
        if (matcher.find()) {
            return "Grade " + matcher.group(1);
        }

        return null;
    }

    private Sheet resolveSheet(Workbook workbook) {
        Sheet namedSheet = workbook.getSheet(EXPECTED_SHEET_NAME);
        return namedSheet != null ? namedSheet : workbook.getSheetAt(0);
    }

    private List<Sf1PreviewRowDto> parseExactSf1Layout(Sheet sheet, DataFormatter formatter) {
        List<Sf1PreviewRowDto> rows = new ArrayList<>();

        for (int rowIndex = STUDENT_ROW_START_INDEX; rowIndex <= STUDENT_ROW_END_INDEX; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            String lrn = readRangeValue(row, LRN_START_COLUMN, LRN_END_COLUMN, formatter);
            String combinedName = readRangeValue(row, NAME_START_COLUMN, NAME_END_COLUMN, formatter);
            String genderValue = readCellValue(row, GENDER_COLUMN, formatter);

            if (isBlank(lrn) && isBlank(combinedName) && isBlank(genderValue)) {
                continue;
            }

            rows.add(buildPreviewRow(rowIndex + 1, normalizeValue(lrn), combinedName, genderValue));
        }

        return rows;
    }

    private List<Sf1PreviewRowDto> parseGenericFallback(Sheet sheet, DataFormatter formatter) {
        List<Sf1PreviewRowDto> rows = new ArrayList<>();

        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isBlankRow(row, formatter)) {
                continue;
            }

            String lrn = findEmbeddedLrn(row, formatter);
            if (lrn == null) {
                continue;
            }

            String combinedName = findFallbackName(row, formatter, lrn);
            String genderValue = findFallbackGender(row, formatter);
            rows.add(buildPreviewRow(rowIndex + 1, lrn, combinedName, genderValue));
        }

        return rows;
    }

    private Sf1PreviewRowDto buildPreviewRow(
            int rowNumber,
            String studentLrn,
            String combinedName,
            String genderValue
    ) {
        ParsedName parsedName = parseSf1Name(combinedName);
        String normalizedGender = normalizeGender(genderValue);

        List<String> validationErrors = new ArrayList<>();
        if (isBlank(studentLrn)) {
            validationErrors.add("Missing LRN.");
        } else if (!EXACT_LRN_PATTERN.matcher(studentLrn).matches()) {
            validationErrors.add("Invalid LRN format.");
        }

        if (isBlank(parsedName.lastName())) {
            validationErrors.add("Missing last name.");
        }

        if (isBlank(parsedName.firstName())) {
            validationErrors.add("Missing first name.");
        }

        if (normalizedGender == null) {
            validationErrors.add("Missing or unknown gender.");
        }

        return new Sf1PreviewRowDto(
                rowNumber,
                studentLrn,
                parsedName.firstName(),
                parsedName.lastName(),
                normalizedGender,
                validationErrors.isEmpty() ? STATUS_VALID : "INVALID",
                validationErrors.isEmpty()
                        ? "Preview row parsed successfully."
                        : String.join(" ", validationErrors)
        );
    }

    private String findEmbeddedLrn(Row row, DataFormatter formatter) {
        short lastCellNum = row.getLastCellNum();
        if (lastCellNum < 0) {
            return null;
        }

        for (int cellIndex = 0; cellIndex < lastCellNum; cellIndex++) {
            String cellValue = readCellValue(row, cellIndex, formatter);
            if (isBlank(cellValue)) {
                continue;
            }

            Matcher matcher = EMBEDDED_LRN_PATTERN.matcher(cellValue);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return null;
    }

    private String findFallbackName(Row row, DataFormatter formatter, String lrn) {
        short lastCellNum = row.getLastCellNum();
        if (lastCellNum < 0) {
            return null;
        }

        for (int cellIndex = 0; cellIndex < lastCellNum; cellIndex++) {
            String cellValue = readCellValue(row, cellIndex, formatter);
            if (isBlank(cellValue) || cellValue.contains(lrn)) {
                continue;
            }

            if (cellValue.contains(",")) {
                return cellValue;
            }
        }

        return null;
    }

    private String findFallbackGender(Row row, DataFormatter formatter) {
        short lastCellNum = row.getLastCellNum();
        if (lastCellNum < 0) {
            return null;
        }

        for (int cellIndex = 0; cellIndex < lastCellNum; cellIndex++) {
            String value = readCellValue(row, cellIndex, formatter);
            if (normalizeGender(value) != null) {
                return value;
            }
        }

        return null;
    }

    private ParsedName parseSf1Name(String combinedName) {
        if (isBlank(combinedName)) {
            return new ParsedName(null, null);
        }

        String normalized = normalizeValue(combinedName);
        String[] parts = normalized.split(",", -1);
        if (parts.length < 2) {
            return new ParsedName(null, normalizeName(parts[0]));
        }

        String lastName = normalizeName(parts[0]);
        String firstName = parts.length >= 3
                ? normalizeName(parts[1])
                : normalizeName(normalized.substring(normalized.indexOf(',') + 1));

        return new ParsedName(firstName, lastName);
    }

    private String normalizeGender(String value) {
        if (isBlank(value)) {
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("m".equals(normalized) || "male".equals(normalized)) {
            return "male";
        }
        if ("f".equals(normalized) || "female".equals(normalized)) {
            return "female";
        }
        return null;
    }

    private String readRangeValue(Sheet sheet, int rowIndex, int startColumn, int endColumn, DataFormatter formatter) {
        return readRangeValue(sheet.getRow(rowIndex), startColumn, endColumn, formatter);
    }

    private String readRangeValue(Row row, int startColumn, int endColumn, DataFormatter formatter) {
        if (row == null) {
            return null;
        }

        Set<String> values = new LinkedHashSet<>();
        for (int columnIndex = startColumn; columnIndex <= endColumn; columnIndex++) {
            String value = readCellValue(row, columnIndex, formatter);
            if (!isBlank(value)) {
                values.add(value);
            }
        }

        if (values.isEmpty()) {
            return null;
        }

        return normalizeValue(String.join(" ", values));
    }

    private String readCellValue(Row row, int columnIndex, DataFormatter formatter) {
        if (row == null) {
            return null;
        }

        Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return null;
        }

        return normalizeValue(formatter.formatCellValue(cell));
    }

    private String readRowText(Row row, DataFormatter formatter) {
        short lastCellNum = row.getLastCellNum();
        if (lastCellNum < 0) {
            return null;
        }

        List<String> values = new ArrayList<>();
        for (int cellIndex = 0; cellIndex < lastCellNum; cellIndex++) {
            String value = readCellValue(row, cellIndex, formatter);
            if (!isBlank(value)) {
                values.add(value);
            }
        }

        return normalizeValue(String.join(" ", values));
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        short lastCellNum = row.getLastCellNum();
        if (lastCellNum < 0) {
            return true;
        }

        for (int cellIndex = 0; cellIndex < lastCellNum; cellIndex++) {
            if (!isBlank(readCellValue(row, cellIndex, formatter))) {
                return false;
            }
        }

        return true;
    }

    private String normalizeName(String value) {
        String normalized = normalizeValue(value);
        if (normalized == null) {
            return null;
        }

        String cleaned = normalized
                .replaceAll("^[,;:\\-\\s]+", "")
                .replaceAll("[,;:\\-\\s]+$", "");
        return cleaned.isBlank() ? null : cleaned;
    }

    private String normalizeValue(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeSchoolYear(String value) {
        String normalized = normalizeValue(value);
        if (normalized == null) {
            return null;
        }

        Matcher matcher = SCHOOL_YEAR_PATTERN.matcher(normalized);
        if (matcher.find()) {
            return matcher.group(1) + "-" + matcher.group(2);
        }

        return null;
    }

    private String normalizeSectionName(String value) {
        String normalized = normalizeValue(value);
        if (normalized == null) {
            return null;
        }

        String extracted = extractSectionNameFromText(normalized);
        if (!isBlank(extracted)) {
            return extracted;
        }

        if ("section".equalsIgnoreCase(normalized)) {
            return null;
        }

        return cleanSectionName(normalized);
    }

    private String extractSectionNameFromText(String value) {
        String normalized = normalizeValue(value);
        if (normalized == null) {
            return null;
        }

        Matcher matcher = SECTION_PATTERN.matcher(normalized.replace('_', ' '));
        return matcher.find() ? cleanSectionName(matcher.group(1)) : null;
    }

    private String cleanSectionName(String value) {
        String normalized = normalizeValue(value);
        if (normalized == null) {
            return null;
        }

        String cleaned = normalized
                .replaceAll("(?i)^section\\s*[:\\-_]?\\s*", "")
                .replaceAll("\\.[A-Za-z0-9]+$", "")
                .replaceAll("(?i)\\b(exact|xlsx|xls)$", "")
                .replaceAll("^[,;:\\-_\\s]+", "")
                .replaceAll("[,;:\\-_\\s]+$", "")
                .trim();

        return cleaned.isBlank() || "section".equalsIgnoreCase(cleaned) ? null : cleaned;
    }

    private boolean containsLabel(String value, String label) {
        String normalized = normalizeValue(value);
        return normalized != null && normalized.toLowerCase(Locale.ROOT).contains(label);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ParsedSf1Data(
            String detectedSchoolYear,
            String detectedSectionName,
            String detectedGradeLevelName,
            List<Sf1PreviewRowDto> rows
    ) {
    }

    private record ResolvedImportContext(Long sectionId, Long academicYearId) {
    }

    private record ParsedName(String firstName, String lastName) {
    }
}
