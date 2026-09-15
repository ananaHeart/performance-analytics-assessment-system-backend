package com.capstone.assessment.v3.evidence.service;

import com.capstone.assessment.v3.evidence.repository.V3AnswerAttachmentLineageRepository;
import com.capstone.assessment.v3.evidence.repository.V3AnswerAttachmentLineageRepository.V3AnswerAttachmentLineage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Profile("v3")
@Service
public class V3AnswerAttachmentLineageValidator {

    private static final String NORMALIZED_PAGE = "normalized_page";
    private static final String ORIGINAL_PAGE = "original_page";
    private static final int MAX_SOURCE_DEPTH = 64;

    private final V3AnswerAttachmentLineageRepository repository;

    public V3AnswerAttachmentLineageValidator(V3AnswerAttachmentLineageRepository repository) {
        this.repository = repository;
    }

    public void validateForSave(V3AnswerAttachmentCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate is required");
        Objects.requireNonNull(candidate.attachmentType(), "attachmentType is required");

        if (!NORMALIZED_PAGE.equals(candidate.attachmentType())) {
            if (candidate.sourceAnswerAttachmentId() != null) {
                throw new IllegalArgumentException(
                        "Only normalized_page evidence may reference a source attachment."
                );
            }
            return;
        }

        if (candidate.sourceAnswerAttachmentId() == null) {
            throw new IllegalArgumentException("normalized_page evidence requires an original_page source.");
        }
        if (candidate.scanPageId() == null || candidate.scanSessionId() == null) {
            throw new IllegalArgumentException(
                    "normalized_page evidence must be scoped to a scan page and scan session."
            );
        }
        if (Objects.equals(candidate.answerAttachmentId(), candidate.sourceAnswerAttachmentId())) {
            throw new IllegalArgumentException("Evidence cannot reference itself as its source.");
        }

        V3AnswerAttachmentLineage source = repository.findById(candidate.sourceAnswerAttachmentId())
                .orElseThrow(() -> new IllegalArgumentException("Source attachment does not exist."));

        if (!ORIGINAL_PAGE.equals(source.attachmentType())) {
            throw new IllegalArgumentException("normalized_page evidence must reference original_page evidence.");
        }
        requireSameContext(candidate, source);
        rejectCycle(candidate.answerAttachmentId(), source);
    }

    private void requireSameContext(
            V3AnswerAttachmentCandidate candidate,
            V3AnswerAttachmentLineage source
    ) {
        if (!Objects.equals(candidate.scanPageId(), source.scanPageId())) {
            throw new IllegalArgumentException("Normalized and original evidence must belong to the same scan page.");
        }
        if (!Objects.equals(candidate.scanSessionId(), source.scanSessionId())
                || !Objects.equals(candidate.studentAnswerId(), source.studentAnswerId())
                || !Objects.equals(candidate.answerSheetRegionId(), source.answerSheetRegionId())) {
            throw new IllegalArgumentException(
                    "Normalized and original evidence must have the same ownership context."
            );
        }
    }

    private void rejectCycle(Long candidateId, V3AnswerAttachmentLineage firstSource) {
        Set<Long> visited = new HashSet<>();
        if (candidateId != null) {
            visited.add(candidateId);
        }

        V3AnswerAttachmentLineage current = firstSource;
        for (int depth = 0; depth < MAX_SOURCE_DEPTH; depth++) {
            if (!visited.add(current.answerAttachmentId())) {
                throw new IllegalArgumentException("Evidence source lineage contains a cycle.");
            }
            if (current.sourceAnswerAttachmentId() == null) {
                return;
            }
            current = repository.findById(current.sourceAnswerAttachmentId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Evidence source lineage references a missing attachment."
                    ));
        }
        throw new IllegalArgumentException("Evidence source lineage exceeds the allowed depth.");
    }

    public record V3AnswerAttachmentCandidate(
            Long answerAttachmentId,
            String attachmentType,
            Long sourceAnswerAttachmentId,
            Long scanSessionId,
            Long scanPageId,
            Long studentAnswerId,
            Long answerSheetRegionId
    ) {
    }
}
