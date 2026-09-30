package com.capstone.assessment.v3.answersheet.service.dynamic;

import com.capstone.assessment.v3.answersheet.dto.V3DynamicAnswerSheetPreviewRequest;
import com.capstone.assessment.v3.answersheet.dto.V3DynamicAnswerSheetPreviewRequest.PartInput;
import com.capstone.assessment.v3.answersheet.dto.V3DynamicAnswerSheetPreviewRequest.QuestionInput;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicManifest;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicSheetRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the HTTP-facing preview request maps into something the already-verified
 * packer accepts and produces a valid, renderable manifest - i.e. that the new
 * controller endpoint's plumbing is correct end to end, not just the engine underneath.
 */
class DynamicSheetRequestMapperTest {

    @Test
    void mapsMixedQuestionTypesWithAutoNumberingAndRendersSuccessfully() {
        QuestionInput mc = new QuestionInput(1, UUID.randomUUID().toString(), "multiple_choice",
                null, null, 1.0, "", null, null, false, null);
        QuestionInput tf = new QuestionInput(2, UUID.randomUUID().toString(), "true_false",
                null, null, 1.0, "", null, null, false, null);
        QuestionInput essay = new QuestionInput(3, UUID.randomUUID().toString(), "essay",
                null, null, 10.0, "", "long", null, false, null);

        PartInput objectivePart = new PartInput(1, 1, "Part I", "Answer all.", List.of(mc, tf));
        PartInput essayPart = new PartInput(2, 2, "Part II", "Write clearly.", List.of(essay));

        V3DynamicAnswerSheetPreviewRequest request = new V3DynamicAnswerSheetPreviewRequest(
                "A4", UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                1L, 1, "Test School", "Sample Quiz", "Math", "Grade 8",
                List.of(objectivePart, essayPart)
        );

        DynamicSheetRequest packerRequest = DynamicSheetRequestMapper.toPackerRequest(request);
        // Auto-numbering: no globalItemNumber supplied, so 1, 2, 3 in encounter order.
        assertEquals(1, packerRequest.parts().get(0).questions().get(0).globalItemNumber());
        assertEquals(2, packerRequest.parts().get(0).questions().get(1).globalItemNumber());
        assertEquals(3, packerRequest.parts().get(1).questions().get(0).globalItemNumber());
        // MC defaulted to A-D, TF defaulted to T/F, since the caller supplied no options.
        assertEquals(4, packerRequest.parts().get(0).questions().get(0).options().size());
        assertEquals(2, packerRequest.parts().get(0).questions().get(1).options().size());

        DynamicManifest manifest = DynamicSheetPacker.buildManifest(packerRequest);
        assertEquals(3, manifest.totalQuestions());
        assertTrue(manifest.totalPages() >= 1);

        DynamicSheetPdfRenderer renderer = new DynamicSheetPdfRenderer();
        byte[] pdf = renderer.render(manifest, packerRequest.context());
        assertTrue(pdf.length > 0, "renderer must produce non-empty PDF bytes");
        // %PDF header sanity check - confirms real PDF bytes, not just a byte array.
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
    }
}
