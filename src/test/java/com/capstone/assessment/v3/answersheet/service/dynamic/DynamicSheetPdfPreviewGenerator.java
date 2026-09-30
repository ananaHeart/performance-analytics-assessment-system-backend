package com.capstone.assessment.v3.answersheet.service.dynamic;

import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicManifest;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicOptionInput;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicPartInput;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicQuestionInput;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicSheetContext;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicSheetRequest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Not a correctness test (see DynamicSheetPackerTest for that) - this writes real PDFs
 * to output/dynamic-sheet-preview/ using the exact same 12-question fixture as the
 * confirmed reference files, so they can be opened and visually diffed page-by-page
 * against v3-dynamic-mixed-{a4,us-letter,us-legal}.pdf. Backend-repo-local output only;
 * nothing outside this repo is written.
 */
class DynamicSheetPdfPreviewGenerator {

    private static final String ANSWER_SHEET_UUID = "56d628da-d7fc-4faa-b018-3f740e048bf0";
    private static final String ASSIGNMENT_UUID = "26b14ea2-4383-43f5-bb40-f60683ee46bb";

    private static DynamicQuestionInput mc(long id, String uuid, int itemNumber) {
        return new DynamicQuestionInput(id, uuid, "multiple_choice", itemNumber, itemNumber,
                1, "", null, null, false,
                List.of(new DynamicOptionInput("A", "A"), new DynamicOptionInput("B", "B"),
                        new DynamicOptionInput("C", "C"), new DynamicOptionInput("D", "D")));
    }

    private static DynamicQuestionInput tf(long id, String uuid, int itemNumber, int partItem) {
        return new DynamicQuestionInput(id, uuid, "true_false", partItem, itemNumber,
                1, "", null, null, false,
                List.of(new DynamicOptionInput("T", "A"), new DynamicOptionInput("F", "B")));
    }

    private static DynamicQuestionInput identification(long id, String uuid, int itemNumber, int partItem) {
        return new DynamicQuestionInput(id, uuid, "identification", partItem, itemNumber,
                2, "", "short", null, false, List.of());
    }

    private static DynamicQuestionInput enumeration(long id, String uuid, int itemNumber) {
        return new DynamicQuestionInput(id, uuid, "enumeration", 1, itemNumber,
                3, "", "medium", 3, false, List.of());
    }

    private static DynamicQuestionInput essay(long id, String uuid, int itemNumber, int partItem, String size) {
        return new DynamicQuestionInput(id, uuid, "essay", partItem, itemNumber,
                size.equals("long") ? 10 : 15, "", size, null, false, List.of());
    }

    private static DynamicManifest buildFixtureManifest(String paperSizeCode) {
        DynamicPartInput partOne = new DynamicPartInput(21001, 1, "Part I - Multiple Choice",
                "Shade one answer only.", List.of(
                mc(30001, "1dd4c493-013f-4251-9d87-ad9be9533298", 1),
                mc(30002, "2dd4c493-013f-4251-9d87-ad9be9533298", 2),
                mc(30003, "3dd4c493-013f-4251-9d87-ad9be9533298", 3),
                mc(30004, "4dd4c493-013f-4251-9d87-ad9be9533298", 4)));
        DynamicPartInput partTwo = new DynamicPartInput(21002, 2, "Part II - True or False",
                "Shade T when true or F when false.", List.of(
                tf(30005, "5dd4c493-013f-4251-9d87-ad9be9533298", 5, 1),
                tf(30006, "6dd4c493-013f-4251-9d87-ad9be9533298", 6, 2),
                tf(30007, "7dd4c493-013f-4251-9d87-ad9be9533298", 7, 3)));
        DynamicPartInput partThree = new DynamicPartInput(21003, 3, "Part III - Identification",
                "Write each answer on the provided line.", List.of(
                identification(30008, "8dd4c493-013f-4251-9d87-ad9be9533298", 8, 1),
                identification(30009, "9dd4c493-013f-4251-9d87-ad9be9533298", 9, 2)));
        DynamicPartInput partFour = new DynamicPartInput(21004, 4, "Part IV - Enumeration",
                "Write one response on each numbered line.", List.of(
                enumeration(30010, "add4c493-013f-4251-9d87-ad9be9533298", 10)));
        DynamicPartInput partFive = new DynamicPartInput(21005, 5, "Part V - Essay",
                "Write clearly. The teacher will evaluate the response.", List.of(
                essay(30011, "bdd4c493-013f-4251-9d87-ad9be9533298", 11, 1, "long"),
                essay(30012, "cdd4c493-013f-4251-9d87-ad9be9533298", 12, 2, "full_page")));

        DynamicSheetRequest request = new DynamicSheetRequest(
                ANSWER_SHEET_UUID, 12001, ASSIGNMENT_UUID, paperSizeCode, 1,
                "3.0.0-prototype.2", "2026-09-02T00:00:00Z",
                List.of(partOne, partTwo, partThree, partFour, partFive),
                new DynamicSheetContext("SAN ROQUE NATIONAL HIGH SCHOOL", "English Mixed Assessment",
                        "English", "Grade 7 - Rizal", 50.0)
        );
        return DynamicSheetPacker.buildManifest(request);
    }

    @Test
    void generatePreviewPdfsForAllPaperSizes() throws IOException {
        Path outDir = Path.of("output", "dynamic-sheet-preview");
        Files.createDirectories(outDir);
        DynamicSheetPdfRenderer renderer = new DynamicSheetPdfRenderer();

        DynamicSheetContext context = new DynamicSheetContext(
                "SAN ROQUE NATIONAL HIGH SCHOOL", "English Mixed Assessment", "English", "Grade 7 - Rizal", 50.0);
        for (String paperSize : List.of("A4", "US_LETTER", "US_LEGAL")) {
            DynamicManifest manifest = buildFixtureManifest(paperSize);
            byte[] pdfBytes = renderer.render(manifest, context);
            Path pdfPath = outDir.resolve("backend-dynamic-mixed-" + paperSize.toLowerCase() + ".pdf");
            Files.write(pdfPath, pdfBytes);
            System.out.println("Wrote " + pdfPath.toAbsolutePath() + " (" + pdfBytes.length + " bytes, "
                    + manifest.totalPages() + " pages)");
        }
    }
}
