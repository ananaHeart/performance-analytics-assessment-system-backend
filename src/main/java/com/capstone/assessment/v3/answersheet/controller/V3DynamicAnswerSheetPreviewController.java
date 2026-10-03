package com.capstone.assessment.v3.answersheet.controller;

import com.capstone.assessment.v3.answersheet.dto.V3DynamicAnswerSheetPreviewRequest;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicManifest;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetModels.DynamicSheetRequest;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetPacker;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetPdfRenderer;
import com.capstone.assessment.v3.answersheet.service.dynamic.DynamicSheetRequestMapper;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Generates a dynamic mixed-question (multiple_choice / true_false / identification /
 * enumeration / essay) answer-sheet PDF directly from a request body, for physical
 * scanner acceptance testing.
 *
 * <p><b>Deliberately not part of {@code V3AnswerSheetService}.</b> That service's
 * eligibility rules only accept multiple_choice questions with exactly options A-D
 * (see {@code V3AnswerSheetService.evaluate}), and its "dynamic" template code path
 * ({@code generateDynamic}) is a different, older feature - many same-type MC questions
 * across multiple pages, not the Mobile-approved mixed-question-type layout. Reusing
 * either would mean silently reinterpreting an existing template-code contract that
 * Mobile already hard-codes (see DynamicOmrDetector.kt's manifestAssets map), which is
 * exactly the kind of undocumented reuse that produced the original scanner mismatch.
 *
 * <p>This endpoint performs no database read or write: nothing is persisted, no
 * eligibility rule is evaluated, and no answer key / rubric / part-skill-mapping
 * validation happens here. It exists solely to produce a real, printable PDF from the
 * verified {@code DynamicSheetPacker}/{@code DynamicSheetPdfRenderer} engine so the
 * physical scan-acceptance question (does the printed sheet parse on the phone) can be
 * answered before the larger work of wiring mixed question types into the real
 * assessment-creation/eligibility/persistence pipeline is scoped and built.
 */
@Profile("v3")
@RestController
@RequestMapping("/api/v3/answer-sheets")
public class V3DynamicAnswerSheetPreviewController {

    private final DynamicSheetPdfRenderer renderer = new DynamicSheetPdfRenderer();

    /**
     * Debug-only: returns the exact canonical JSON that {@code manifestHash} is computed
     * from, so a mismatch against a reference manifest can be diffed field-by-field
     * instead of guessed at from the PDF and response headers alone.
     */
    @PostMapping(value = "/dynamic-preview/manifest", produces = "application/json")
    public ResponseEntity<String> previewManifest(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @Valid @RequestBody V3DynamicAnswerSheetPreviewRequest request
    ) {
        requireActiveTeacher(user);

        DynamicSheetRequest packerRequest = DynamicSheetRequestMapper.toPackerRequest(request);
        DynamicManifest manifest;
        try {
            manifest = DynamicSheetPacker.buildManifest(packerRequest);
        } catch (IllegalArgumentException exception) {
            throw new V3AuthException(
                    "DYNAMIC_ANSWER_SHEET_PREVIEW_INVALID",
                    exception.getMessage(),
                    HttpStatus.BAD_REQUEST
            );
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setCacheControl("private, no-store, max-age=0");
        headers.add("X-Dynamic-Manifest-Hash", manifest.manifestHash());
        return ResponseEntity.ok().headers(headers).body(manifest.canonicalManifestJson());
    }

    @PostMapping(value = "/dynamic-preview", produces = "application/pdf")
    public ResponseEntity<byte[]> preview(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @Valid @RequestBody V3DynamicAnswerSheetPreviewRequest request
    ) {
        requireActiveTeacher(user);

        DynamicSheetRequest packerRequest = DynamicSheetRequestMapper.toPackerRequest(request);
        DynamicManifest manifest;
        byte[] pdfBytes;
        try {
            manifest = DynamicSheetPacker.buildManifest(packerRequest);
            pdfBytes = renderer.render(manifest, packerRequest.context());
        } catch (IllegalArgumentException exception) {
            throw new V3AuthException(
                    "DYNAMIC_ANSWER_SHEET_PREVIEW_INVALID",
                    exception.getMessage(),
                    HttpStatus.BAD_REQUEST
            );
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentLength(pdfBytes.length);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("Marka-Dynamic-Answer-Sheet-Preview-%s.pdf".formatted(manifest.paperSizeCode()))
                .build());
        headers.setCacheControl("private, no-store, max-age=0");
        headers.add("X-Dynamic-Manifest-Hash", manifest.manifestHash());
        headers.add("X-Dynamic-Total-Pages", Integer.toString(manifest.totalPages()));
        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }

    private void requireActiveTeacher(V3AuthenticatedUser user) {
        if (user == null) {
            throw new V3AuthException(
                    "AUTHENTICATION_REQUIRED", "Authentication is required.", HttpStatus.UNAUTHORIZED);
        }
        if (!"teacher".equalsIgnoreCase(user.role())) {
            throw new V3AuthException(
                    "ANSWER_SHEET_TEACHER_REQUIRED",
                    "Only authenticated teachers may generate answer-sheet previews.",
                    HttpStatus.FORBIDDEN
            );
        }
    }
}
