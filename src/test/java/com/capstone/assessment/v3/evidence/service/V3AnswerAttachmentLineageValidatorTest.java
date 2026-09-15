package com.capstone.assessment.v3.evidence.service;

import com.capstone.assessment.v3.evidence.repository.V3AnswerAttachmentLineageRepository;
import com.capstone.assessment.v3.evidence.repository.V3AnswerAttachmentLineageRepository.V3AnswerAttachmentLineage;
import com.capstone.assessment.v3.evidence.service.V3AnswerAttachmentLineageValidator.V3AnswerAttachmentCandidate;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class V3AnswerAttachmentLineageValidatorTest {

    private final V3AnswerAttachmentLineageRepository repository =
            mock(V3AnswerAttachmentLineageRepository.class);
    private final V3AnswerAttachmentLineageValidator validator =
            new V3AnswerAttachmentLineageValidator(repository);

    @Test
    void acceptsNormalizedEvidenceFromOriginalEvidenceInTheSameContext() {
        when(repository.findById(10L)).thenReturn(Optional.of(original(10L, null, 50L, 60L, null, null)));

        assertDoesNotThrow(() -> validator.validateForSave(
                candidate(null, "normalized_page", 10L, 50L, 60L, null, null)
        ));
    }

    @Test
    void rejectsSourceOnNonNormalizedEvidence() {
        assertThrows(IllegalArgumentException.class, () -> validator.validateForSave(
                candidate(null, "answer_crop", 10L, 50L, 60L, null, 70L)
        ));
    }

    @Test
    void rejectsNormalizedEvidenceWithoutSource() {
        assertThrows(IllegalArgumentException.class, () -> validator.validateForSave(
                candidate(null, "normalized_page", null, 50L, 60L, null, null)
        ));
    }

    @Test
    void rejectsNormalizedEvidencePointingToNonOriginalEvidence() {
        when(repository.findById(10L)).thenReturn(Optional.of(
                new V3AnswerAttachmentLineage(10L, "answer_crop", null, 50L, 60L, null, 70L)
        ));

        assertThrows(IllegalArgumentException.class, () -> validator.validateForSave(
                candidate(null, "normalized_page", 10L, 50L, 60L, null, null)
        ));
    }

    @Test
    void rejectsCrossPageSource() {
        when(repository.findById(10L)).thenReturn(Optional.of(original(10L, null, 50L, 61L, null, null)));

        assertThrows(IllegalArgumentException.class, () -> validator.validateForSave(
                candidate(null, "normalized_page", 10L, 50L, 60L, null, null)
        ));
    }

    @Test
    void rejectsCrossOwnershipSource() {
        when(repository.findById(10L)).thenReturn(Optional.of(original(10L, null, 51L, 60L, null, null)));

        assertThrows(IllegalArgumentException.class, () -> validator.validateForSave(
                candidate(null, "normalized_page", 10L, 50L, 60L, null, null)
        ));
    }

    @Test
    void rejectsDirectSelfReference() {
        assertThrows(IllegalArgumentException.class, () -> validator.validateForSave(
                candidate(10L, "normalized_page", 10L, 50L, 60L, null, null)
        ));
    }

    @Test
    void rejectsCyclicSourceLineage() {
        when(repository.findById(10L)).thenReturn(Optional.of(original(10L, 11L, 50L, 60L, null, null)));
        when(repository.findById(11L)).thenReturn(Optional.of(original(11L, 10L, 50L, 60L, null, null)));

        assertThrows(IllegalArgumentException.class, () -> validator.validateForSave(
                candidate(null, "normalized_page", 10L, 50L, 60L, null, null)
        ));
    }

    private V3AnswerAttachmentCandidate candidate(
            Long id,
            String type,
            Long sourceId,
            Long scanSessionId,
            Long scanPageId,
            Long studentAnswerId,
            Long answerSheetRegionId
    ) {
        return new V3AnswerAttachmentCandidate(
                id,
                type,
                sourceId,
                scanSessionId,
                scanPageId,
                studentAnswerId,
                answerSheetRegionId
        );
    }

    private V3AnswerAttachmentLineage original(
            long id,
            Long sourceId,
            Long scanSessionId,
            Long scanPageId,
            Long studentAnswerId,
            Long answerSheetRegionId
    ) {
        return new V3AnswerAttachmentLineage(
                id,
                "original_page",
                sourceId,
                scanSessionId,
                scanPageId,
                studentAnswerId,
                answerSheetRegionId
        );
    }
}
