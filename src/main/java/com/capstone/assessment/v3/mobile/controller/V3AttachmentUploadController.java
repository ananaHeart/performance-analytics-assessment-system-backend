package com.capstone.assessment.v3.mobile.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3AttachmentMetadata;
import com.capstone.assessment.v3.mobile.dto.V3AttachmentResponse;
import com.capstone.assessment.v3.mobile.service.V3AttachmentUploadService;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.util.List;

/** Registered for isolated tests; V3 security deliberately denies this write in production. */
@RestController
@Profile("v3")
@RequestMapping("/api/v3/mobile/attachments")
public class V3AttachmentUploadController {
    private static final int MAX_METADATA_BYTES=64*1024;
    private final V3AttachmentUploadService service;
    private final ObjectMapper mapper;
    public V3AttachmentUploadController(V3AttachmentUploadService service,ObjectMapper mapper) {
        this.service=service;
        ObjectMapper strictMapper=mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        strictMapper.setConfig(strictMapper.getDeserializationConfig().without(MapperFeature.ALLOW_COERCION_OF_SCALARS));
        this.mapper=strictMapper;
    }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<V3AttachmentResponse>> upload(@AuthenticationPrincipal V3AuthenticatedUser user,HttpServletRequest request) {
        if(user==null || !"teacher".equals(user.role()) || !"active".equals(user.status()))
            throw new V3AuthException("TEACHER_REQUIRED","An active assigned teacher is required.",HttpStatus.FORBIDDEN);
        try {
            var parts=List.copyOf(request.getParts());
            if(parts.size()!=2 || parts.stream().filter(p->"metadata".equals(p.getName())).count()!=1
                    || parts.stream().filter(p->"file".equals(p.getName())).count()!=1) throw invalid();
            Part metadata=parts.stream().filter(p->"metadata".equals(p.getName())).findFirst().orElseThrow();
            Part file=parts.stream().filter(p->"file".equals(p.getName())).findFirst().orElseThrow();
            if(metadata.getContentType()==null || !MediaType.APPLICATION_JSON.includes(MediaType.parseMediaType(metadata.getContentType()))) throw invalid();
            if(metadata.getSize()>MAX_METADATA_BYTES) throw oversized();
            byte[] bytes;try(var input=metadata.getInputStream()){bytes=input.readNBytes(MAX_METADATA_BYTES+1);}
            if(bytes.length>MAX_METADATA_BYTES) throw oversized();
            var tree=mapper.readTree(bytes);
            if(tree==null || !tree.path("capturedAt").isTextual() || !tree.path("capturedAt").asText().endsWith("Z")) throw invalid();
            var payload=mapper.treeToValue(tree,V3AttachmentMetadata.class);
            var response=service.upload(user,payload,new FilePart(file));
            return ResponseEntity.status("replayed".equals(response.uploadStatus())?HttpStatus.OK:HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore()).body(ApiResponse.success("Attachment acknowledged.",response));
        } catch(IOException | ServletException | IllegalArgumentException e) { throw invalid(); }
    }
    private V3AuthException invalid(){return new V3AuthException("VALIDATION_FAILED","Exactly one metadata JSON part and one file part matching the attachment contract are required.",HttpStatus.UNPROCESSABLE_ENTITY);}
    private V3AuthException oversized(){return new V3AuthException("PAYLOAD_TOO_LARGE","Attachment metadata exceeds 64 KiB.",HttpStatus.PAYLOAD_TOO_LARGE);}
    private record FilePart(Part part) implements MultipartFile {
        public String getName(){return "file";}public String getOriginalFilename(){return null;}
        public String getContentType(){return part.getContentType();}public boolean isEmpty(){return part.getSize()==0;}
        public long getSize(){return part.getSize();}public InputStream getInputStream()throws IOException{return part.getInputStream();}
        public byte[] getBytes(){throw new UnsupportedOperationException("Use bounded stream.");}
        public void transferTo(File destination){throw new UnsupportedOperationException("Use private storage.");}
    }
}
