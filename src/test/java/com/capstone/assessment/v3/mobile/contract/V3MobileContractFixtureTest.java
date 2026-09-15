package com.capstone.assessment.v3.mobile.contract;

import com.capstone.assessment.v3.mobile.dto.V3ScanPageUploadMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V3MobileContractFixtureTest {

    private static final String FIXTURE_ROOT = "contracts/v3/mobile/";
    private static final Set<String> SENSITIVE_ANSWER_FIELDS = Set.of(
            "answerkey",
            "answerkeys",
            "correctoption",
            "correctoptionkey",
            "acceptedanswer",
            "acceptedanswers",
            "rubricsolution",
            "rubricsolutions"
    );

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void everyPublishedFixtureIsValidJson() throws Exception {
        for (String fixture : Set.of(
                "reference-data-response.json",
                "download-response.json",
                "answer-sheet-manifest-response.json",
                "scan-page-upload-metadata.json",
                "scan-page-upload-created-response.json",
                "scan-page-upload-replayed-response.json",
                "scan-page-upload-conflict-response.json"
        )) {
            assertNotNull(read(fixture));
        }
    }

    @Test
    void referenceFixtureDeclaresReadOnlyRuntimeBoundary() throws Exception {
        JsonNode data = read("reference-data-response.json").path("data");

        assertEquals("3.0", data.path("contractVersion").asText());
        assertEquals("full_snapshot", data.path("downloadMode").asText());
        assertFalse(data.path("syncPolicy").path("scanPageUploadAvailable").asBoolean());
        assertTrue(data.path("syncPolicy").path("oneAssignmentPerSync").asBoolean());
        assertEquals("upsert", data.path("syncPolicy").path("syncAction").asText());
        assertEquals(5, data.path("questionTypes").size());
    }

    @Test
    void downloadAndManifestFixturesNeverExposeAnswerMaterial() throws Exception {
        JsonNode download = read("download-response.json");
        JsonNode manifest = read("answer-sheet-manifest-response.json");

        assertNoFieldNamed(download, SENSITIVE_ANSWER_FIELDS);
        assertNoFieldNamed(manifest, SENSITIVE_ANSWER_FIELDS);
        assertNoFieldNamed(manifest, Set.of("studentid", "studentlrn", "classlistid", "score"));
    }

    @Test
    void uploadMetadataFixtureMatchesTheTypedContract() throws Exception {
        V3ScanPageUploadMetadata metadata = objectMapper.treeToValue(
                read("scan-page-upload-metadata.json"),
                V3ScanPageUploadMetadata.class
        );

        assertEquals("3.0", metadata.contractVersion());
        assertEquals(41001L, metadata.classListId());
        assertEquals(64, metadata.imageHash().length());
    }

    @Test
    void retryFixturesPreserveBackendIdentity() throws Exception {
        JsonNode created = read("scan-page-upload-created-response.json").path("data");
        JsonNode replayed = read("scan-page-upload-replayed-response.json").path("data");
        JsonNode conflict = read("scan-page-upload-conflict-response.json");

        assertEquals(created.path("backendScanPageId"), replayed.path("backendScanPageId"));
        assertEquals(created.path("scanPageUuid"), replayed.path("scanPageUuid"));
        assertEquals("created", created.path("uploadStatus").asText());
        assertEquals("replayed", replayed.path("uploadStatus").asText());
        assertEquals("IDEMPOTENCY_KEY_REUSE", conflict.path("errors").path("code").asText());
    }

    private JsonNode read(String fixtureName) throws IOException {
        String path = FIXTURE_ROOT + fixtureName;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, "Missing contract fixture: " + path);
            return objectMapper.readTree(input);
        }
    }

    private void assertNoFieldNamed(JsonNode node, Set<String> forbiddenNames) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                String normalizedName = field.getKey().replace("_", "").toLowerCase(Locale.ROOT);
                assertFalse(
                        forbiddenNames.contains(normalizedName),
                        "Forbidden field found in Mobile contract: " + field.getKey()
                );
                assertNoFieldNamed(field.getValue(), forbiddenNames);
            }
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> assertNoFieldNamed(child, forbiddenNames));
        }
    }
}
