package com.capstone.assessment.v3.answersheet.service.dynamic;

import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicManifest;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicOptionInput;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicPage;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicPartInput;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicQuestionInput;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicRegion;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicSheetContext;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicSheetRequest;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.RegistrationMarker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reconstructs the exact "English Mixed Assessment" 12-question fixture that produced
 * the confirmed reference files (OMRPrototype output, byte-identical to the manifests
 * bundled in the Mobile app under android/app/src/main/assets/omr/dynamic/). Every
 * expected value below was read directly from
 * v3-dynamic-mixed-a4.manifest.json (answerSheetUuid 56d628da-d7fc-4faa-b018-3f740e048bf0),
 * not invented, so a failure here means the Java port has drifted from the approved
 * contract, not that the reference values are wrong.
 *
 * <p>Region and page UUIDs are derived deterministically (UUIDv5) from the same inputs
 * the Python generator uses, so this test can assert them for an EXACT match - not just
 * "looks similar" - which is the strongest signal available that the port is faithful.
 */
class DynamicSheetPackerTest {

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
    void a4FixtureMatchesConfirmedReferenceManifestExactly() {
        DynamicManifest manifest = buildFixtureManifest("A4");

        assertEquals(2, manifest.totalPages(), "reference manifest has exactly 2 pages");
        assertEquals(12, manifest.totalQuestions());
        assertEquals("3.0.0-prototype.2", manifest.requiredScannerVersion());
        assertEquals("3.0", manifest.contractVersion());
        assertEquals(2, manifest.manifestVersion());

        DynamicPage page1 = manifest.pages().get(0);
        DynamicPage page2 = manifest.pages().get(1);

        // Deterministic UUIDv5 must match the reference file's actual page UUIDs exactly.
        assertEquals("4d1659d1-ef8b-5d08-8515-708c5658d7b9", page1.pageUuid());
        assertEquals("f8f2bbe4-5c9f-5e14-99ab-22787489ab17", page2.pageUuid());
        assertEquals("OMR-A4-DYNAMIC-CTX-V3", page1.templateCode());
        assertEquals("3", page1.templateVersion());

        // Registration markers: exact order and hollow/solid pattern the Mobile detector requires.
        List<RegistrationMarker> markers = page1.markers();
        assertEquals(List.of("top_left", "top_right", "bottom_right", "bottom_left"),
                markers.stream().map(RegistrationMarker::corner).toList());
        assertEquals(List.of("hollow", "solid", "solid", "solid"),
                markers.stream().map(RegistrationMarker::style).toList());
        assertEquals(20.0, markers.get(0).rectangle().x());
        assertEquals(806.89, markers.get(0).rectangle().y(), 0.001);
        assertEquals(15.0, markers.get(0).rectangle().width());

        // QR rectangle: bottom-right, 72pt - not upper-right, 96pt like the previous backend attempt.
        assertEquals(481.276, page1.qrRectangle().x(), 0.001);
        assertEquals(20.0, page1.qrRectangle().y());
        assertEquals(72.0, page1.qrRectangle().width());
        assertEquals(72.0, page1.qrRectangle().height());

        // QR payload must carry tv as a JSON number, not a quoted string.
        assertTrue(page1.qrPayload().contains("\"tv\":3"), "tv must be numeric: " + page1.qrPayload());
        assertTrue(page1.qrPayload().contains("\"v\":3"));
        assertEquals(64, page1.pageGeometryHash().length());

        // Region 1 (item 1, MC) - exact rectangle and option centers from the reference file.
        DynamicRegion item1 = page1.regions().get(0);
        assertEquals("439c6dc4-30fc-5169-bdc4-3b017d79dae2", item1.regionUuid());
        assertEquals(42.0, item1.rectangle().x());
        assertEquals(618.89, item1.rectangle().y(), 0.001);
        assertEquals(246.638, item1.rectangle().width(), 0.001);
        assertEquals(34.0, item1.rectangle().height());
        assertEquals(124.0, item1.options().get(0).centerX());
        assertEquals(635.89, item1.options().get(0).centerY(), 0.001);
        assertEquals(232.0, item1.options().get(3).centerX());

        // Item 5 (first True/False) sits in the right-hand lane at the same row as item 1.
        DynamicRegion item5 = page1.regions().get(4);
        assertEquals(306.638, item5.rectangle().x(), 0.001);
        assertEquals(618.89, item5.rectangle().y(), 0.001);

        // Item 10 (enumeration, 3 lines): height must be 104pt per max(72, 38+22*3).
        DynamicRegion item10 = page1.regions().stream()
                .filter(r -> r.globalItemNumber() == 10).findFirst().orElseThrow();
        assertEquals(104.0, item10.rectangle().height(), 0.001);

        // Item 11 (essay, "long"): fixed 220pt.
        DynamicRegion item11 = page1.regions().get(page1.regions().size() - 1);
        assertEquals(11, item11.globalItemNumber());
        assertEquals(220.0, item11.rectangle().height(), 0.001);

        // Item 12 (essay, full_page) must be alone on page 2 and match its known UUID exactly.
        assertEquals(1, page2.regions().size());
        DynamicRegion item12 = page2.regions().get(0);
        assertEquals(12, item12.globalItemNumber());
        assertEquals("19444873-bc5e-5887-9568-ee15659cbf35", item12.regionUuid());
        assertEquals(102.0, item12.rectangle().y(), 0.001);
    }

    @Test
    void usLetterAndUsLegalProduceDistinctTemplateCodesAndGeometry() {
        DynamicManifest letter = buildFixtureManifest("US_LETTER");
        DynamicManifest legal = buildFixtureManifest("US_LEGAL");

        assertEquals("OMR-US-LETTER-DYNAMIC-CTX-V3", letter.pages().get(0).templateCode());
        assertEquals("OMR-US-LEGAL-DYNAMIC-CTX-V3", legal.pages().get(0).templateCode());
        assertEquals(612.0, letter.widthPt());
        assertEquals(792.0, letter.heightPt());
        assertEquals(612.0, legal.widthPt());
        assertEquals(1008.0, legal.heightPt());

        // QR rectangle formula (width - 42 - 72) must hold for every paper size.
        assertEquals(498.0, letter.pages().get(0).qrRectangle().x(), 0.001);
        assertEquals(498.0, legal.pages().get(0).qrRectangle().x(), 0.001);
    }
}
