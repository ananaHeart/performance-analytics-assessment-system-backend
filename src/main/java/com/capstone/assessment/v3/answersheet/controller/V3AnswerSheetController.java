package com.capstone.assessment.v3.answersheet.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.answersheet.dto.V3AnswerSheetEligibilityResponse;
import com.capstone.assessment.v3.answersheet.dto.V3AnswerSheetReferenceDataResponse;
import com.capstone.assessment.v3.answersheet.dto.V3AnswerSheetVersionResponse;
import com.capstone.assessment.v3.answersheet.dto.V3GenerateAnswerSheetRequest;
import com.capstone.assessment.v3.answersheet.service.V3AnswerSheetService;
import com.capstone.assessment.v3.answersheet.service.V3AnswerSheetService.PdfDownload;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3RequestMetadata;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("v3")
@RestController
@RequestMapping("/api/v3")
public class V3AnswerSheetController {

    private final V3AnswerSheetService answerSheetService;

    public V3AnswerSheetController(V3AnswerSheetService answerSheetService) {
        this.answerSheetService = answerSheetService;
    }

    @GetMapping("/answer-sheets/reference-data")
    public ResponseEntity<ApiResponse<V3AnswerSheetReferenceDataResponse>> referenceData(
            @AuthenticationPrincipal V3AuthenticatedUser user
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 answer-sheet reference data retrieved successfully.",
                answerSheetService.getReferenceData(user)
        ));
    }

    @GetMapping("/test-assignments/{testAssignmentId}/answer-sheet-eligibility")
    public ResponseEntity<ApiResponse<V3AnswerSheetEligibilityResponse>> eligibility(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @PathVariable long testAssignmentId,
            @RequestParam(defaultValue = "A4") String paperSizeCode
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 answer-sheet eligibility evaluated successfully.",
                answerSheetService.getEligibility(user, testAssignmentId, paperSizeCode)
        ));
    }

    @PostMapping("/test-assignments/{testAssignmentId}/answer-sheet-versions")
    public ResponseEntity<ApiResponse<V3AnswerSheetVersionResponse>> generate(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @PathVariable long testAssignmentId,
            @Valid @RequestBody V3GenerateAnswerSheetRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.status(201).body(ApiResponse.success(
                "V3 Bubble Answer Sheet generated successfully.",
                answerSheetService.generate(
                        user,
                        testAssignmentId,
                        request.paperSizeCode(),
                        requestMetadata(httpRequest)
                )
        ));
    }

    @GetMapping("/answer-sheet-versions/{answerSheetVersionId}")
    public ResponseEntity<ApiResponse<V3AnswerSheetVersionResponse>> getVersion(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @PathVariable long answerSheetVersionId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "V3 answer-sheet version retrieved successfully.",
                answerSheetService.getVersion(user, answerSheetVersionId)
        ));
    }

    @GetMapping(value = "/answer-sheet-versions/{answerSheetVersionId}/pdf", produces = "application/pdf")
    public ResponseEntity<byte[]> downloadPdf(
            @AuthenticationPrincipal V3AuthenticatedUser user,
            @PathVariable long answerSheetVersionId
    ) {
        PdfDownload download = answerSheetService.getPdf(user, answerSheetVersionId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentLength(download.bytes().length);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(download.filename())
                .build());
        headers.setCacheControl("private, no-store, max-age=0");
        return ResponseEntity.ok().headers(headers).body(download.bytes());
    }

    private V3RequestMetadata requestMetadata(HttpServletRequest request) {
        return new V3RequestMetadata(
                clientIpAddress(request),
                request.getHeader("User-Agent"),
                null
        );
    }

    private String clientIpAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return request.getRemoteAddr();
        }
        return forwardedFor.split(",", 2)[0].trim();
    }
}
