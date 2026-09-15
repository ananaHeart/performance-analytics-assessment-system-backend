package com.capstone.assessment.v3.auth.service;

import com.capstone.assessment.v3.auth.repository.V3AuthRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Profile("v3")
@Service
public class V3AuditService {

    private final V3AuthRepository repository;
    private final ObjectMapper objectMapper;

    public V3AuditService(V3AuthRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void record(
            Long userId,
            String action,
            String entityType,
            String entityId,
            String outcome,
            V3RequestMetadata metadata,
            Map<String, ?> details,
            Instant now
    ) {
        V3RequestMetadata safeMetadata = metadata == null
                ? new V3RequestMetadata(null, null, null)
                : metadata;
        repository.recordAudit(
                UUID.randomUUID().toString(),
                userId,
                action,
                entityType,
                entityId,
                outcome,
                normalizeNullable(safeMetadata.ipAddress()),
                normalizeNullable(safeMetadata.deviceIdentifier()),
                normalizeNullable(safeMetadata.userAgent()),
                serialize(details),
                now
        );
    }

    private String serialize(Map<String, ?> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize V3 audit details.", exception);
        }
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
