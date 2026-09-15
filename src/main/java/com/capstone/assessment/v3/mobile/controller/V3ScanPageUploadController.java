package com.capstone.assessment.v3.mobile.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3ScanPageUploadMetadata;
import com.capstone.assessment.v3.mobile.dto.V3ScanPageUploadResponse;
import com.capstone.assessment.v3.mobile.service.V3ScanPageIngestionService;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Registered handler, deliberately denied by V3 security until receipt/recovery release checks pass. */
@RestController
@Profile("v3")
@RequestMapping("/api/v3/mobile/scan-pages")
public class V3ScanPageUploadController {
    public static final int MAX_METADATA_BYTES = 64 * 1024;
    private final V3ScanPageIngestionService service;
    private final ObjectMapper mapper;

    public V3ScanPageUploadController(V3ScanPageIngestionService service, ObjectMapper mapper) {
        this.service = service;
        ObjectMapper strictMapper = mapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        strictMapper.setConfig(strictMapper.getDeserializationConfig()
                .without(MapperFeature.ALLOW_COERCION_OF_SCALARS));
        this.mapper = strictMapper;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<V3ScanPageUploadResponse>> upload(
            @AuthenticationPrincipal V3AuthenticatedUser user, HttpServletRequest request) {
        if (user == null || !"teacher".equals(user.role()) || !"active".equals(user.status())) {
            throw new V3AuthException("TEACHER_REQUIRED", "An active assigned teacher is required.", HttpStatus.FORBIDDEN);
        }
        try {
            List<Part> parts = List.copyOf(request.getParts());
            if (parts.size() != 2 || parts.stream().filter(p -> "metadata".equals(p.getName())).count() != 1
                    || parts.stream().filter(p -> "image".equals(p.getName())).count() != 1) {
                throw invalid("Exactly one metadata part and one original image part are required.");
            }
            Part metadata = parts.stream().filter(p -> "metadata".equals(p.getName())).findFirst().orElseThrow();
            Part image = parts.stream().filter(p -> "image".equals(p.getName())).findFirst().orElseThrow();
            if (metadata.getContentType() == null
                    || !MediaType.APPLICATION_JSON.includes(MediaType.parseMediaType(metadata.getContentType()))) {
                throw invalid("The metadata part must have Content-Type application/json.");
            }
            if (metadata.getSize() > MAX_METADATA_BYTES) throw oversizedMetadata();
            byte[] bytes;
            try (InputStream input = metadata.getInputStream()) { bytes = input.readNBytes(MAX_METADATA_BYTES + 1); }
            if (bytes.length > MAX_METADATA_BYTES) throw oversizedMetadata();
            V3ScanPageUploadMetadata payload = mapper.readValue(bytes, V3ScanPageUploadMetadata.class);
            var response = service.ingest(user, payload, new ImagePart(image));
            return ResponseEntity.status("replayed".equals(response.uploadStatus()) ? HttpStatus.OK : HttpStatus.CREATED)
                    .body(ApiResponse.success("Scan page acknowledged.", response));
        } catch (IOException | ServletException | IllegalArgumentException e) {
            throw invalid("The multipart metadata could not be read as the supported scan contract.");
        }
    }

    private static V3AuthException invalid(String message) {
        return new V3AuthException("VALIDATION_FAILED", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private static V3AuthException oversizedMetadata() {
        return new V3AuthException("PAYLOAD_TOO_LARGE", "Scan metadata exceeds 64 KiB.", HttpStatus.PAYLOAD_TOO_LARGE);
    }

    private record ImagePart(Part part) implements MultipartFile {
        @Override public String getName() { return "image"; }
        @Override public String getOriginalFilename() { return null; }
        @Override public String getContentType() { return part.getContentType(); }
        @Override public boolean isEmpty() { return part.getSize() == 0; }
        @Override public long getSize() { return part.getSize(); }
        @Override public InputStream getInputStream() throws IOException { return part.getInputStream(); }
        @Override public byte[] getBytes() { throw new UnsupportedOperationException("Use the bounded storage stream."); }
        @Override public void transferTo(File destination) { throw new UnsupportedOperationException("Use private evidence storage."); }
    }
}
