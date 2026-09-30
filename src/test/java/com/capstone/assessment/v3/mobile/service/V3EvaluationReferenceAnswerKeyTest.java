package com.capstone.assessment.v3.mobile.service;

import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3EvaluationReference.AcceptedAnswer;
import com.capstone.assessment.v3.mobile.dto.V3EvaluationReference.Criterion;
import com.capstone.assessment.v3.mobile.repository.V3EvaluationReferenceRepository;
import com.capstone.assessment.v3.mobile.repository.V3EvaluationReferenceRepository.Assignment;
import com.capstone.assessment.v3.mobile.repository.V3EvaluationReferenceRepository.QuestionRow;
import com.capstone.assessment.v3.mobile.repository.V3EvaluationReferenceRepository.RubricRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Contract 3.1: answer keys for the phone's display-only preliminary score. */
class V3EvaluationReferenceAnswerKeyTest {
    static final String UUID="00000000-0000-4000-8000-000000000001";
    static final String MC="00000000-0000-4000-8000-000000000011";
    static final String TF="00000000-0000-4000-8000-000000000012";
    static final String ENUMERATION="00000000-0000-4000-8000-000000000013";
    static final String ESSAY="00000000-0000-4000-8000-000000000014";
    final V3EvaluationReferenceRepository repository=mock(V3EvaluationReferenceRepository.class);
    final V3EvaluationReferenceService service=new V3EvaluationReferenceService(repository,mock(PlatformTransactionManager.class),true);
    final V3AuthenticatedUser teacher=new V3AuthenticatedUser(42,"S1","","teacher","active","");

    @BeforeEach void setUp() throws Exception {
        when(repository.active(teacher)).thenReturn(true);
        when(repository.assignment(teacher,UUID)).thenReturn(Optional.of(new Assignment(7,1,"active","open")));
        when(repository.rubric(eq(60L),anyString())).thenReturn(Optional.of(new RubricRow(60,"Explanation",new BigDecimal("5.00"),"active")));
        when(repository.criteria(60L)).thenReturn(List.of(
                new Criterion(601,"Content",new BigDecimal("3.00"),true,"Main idea with details.",
                        new ObjectMapper().readTree("[{\"label\":\"Complete\",\"points\":3}]")),
                new Criterion(602,"Organization",new BigDecimal("2.00"),true,"Clear order.",null)));
        givenKeys("C","Mitochondria");
    }

    private void givenKeys(String mcKey,String enumerationAnswer) {
        when(repository.questions(7)).thenReturn(List.of(
                new QuestionRow(MC,new BigDecimal("1.00"),null,null,"multiple_choice","option",null,mcKey,101),
                new QuestionRow(TF,new BigDecimal("1.00"),null,null,"true_false","option",null,"B",102),
                new QuestionRow(ENUMERATION,new BigDecimal("2.00"),null,2,"enumeration","accepted_text",null,null,103),
                new QuestionRow(ESSAY,new BigDecimal("5.00"),60L,null,"essay","rubric",60L,null,104)));
        when(repository.acceptedAnswers(anyLong(),anyBoolean())).thenReturn(Map.of(103L,List.of(
                new AcceptedAnswer(enumerationAnswer,"normalized",false,new BigDecimal("1.00")),
                new AcceptedAnswer("Nucleus","normalized",false,new BigDecimal("1.00")))));
    }

    @Test void sendsEachKeyOnlyOnTheQuestionTypeThatUsesIt() {
        var reference=service.get(teacher,UUID);

        assertEquals("3.1",reference.contractVersion());
        var byUuid=reference.questions().stream().collect(java.util.stream.Collectors.toMap(q->q.questionUuid(),q->q));
        assertEquals("C",byUuid.get(MC).correctOptionKey());
        assertEquals("B",byUuid.get(TF).correctOptionKey(),"T/F uses the stored A=True/B=False key");
        assertTrue(byUuid.get(MC).acceptedAnswers().isEmpty());
        assertNull(byUuid.get(ENUMERATION).correctOptionKey());
        assertEquals(List.of("Mitochondria","Nucleus"),
                byUuid.get(ENUMERATION).acceptedAnswers().stream().map(AcceptedAnswer::text).toList());
        assertNull(byUuid.get(ESSAY).correctOptionKey());
        assertTrue(byUuid.get(ESSAY).acceptedAnswers().isEmpty());
        var criteria=reference.rubrics().get(0).criteria();
        assertEquals("Main idea with details.",criteria.get(0).description());
        assertEquals("Complete",criteria.get(0).levelDefinition().get(0).get("label").asText());
        assertNull(criteria.get(1).levelDefinition());
    }

    /** Regression (2026-09-27): written finalization compares the reference JSON saved at
     *  verification with a fresh read using equals(). A criterion without levels was saved as
     *  "levelDefinition":null, read back as NullNode, and every such upload failed with
     *  WRITTEN_AUDIT_MISMATCH. */
    @Test void referenceSavedAtVerificationStillEqualsTheCurrentOneWhenReadBack() throws Exception {
        var mapper=new ObjectMapper().findAndRegisterModules();
        var reference=service.get(teacher,UUID);
        assertNull(reference.rubrics().get(0).criteria().get(1).levelDefinition(),"fixture has a criterion without levels");

        var readBack=mapper.readValue(mapper.writeValueAsString(reference),
                com.capstone.assessment.v3.mobile.dto.V3EvaluationReference.class);

        assertEquals(reference,readBack);
    }

    @Test void anyAnswerKeyChangeChangesTheHashEvenWithSameQuestionsAndVersion() {
        String original=service.get(teacher,UUID).evaluationReferenceHash();
        assertEquals(original,service.get(teacher,UUID).evaluationReferenceHash(),"stable across reads");

        givenKeys("D","Mitochondria");
        String mcChanged=service.get(teacher,UUID).evaluationReferenceHash();
        givenKeys("C","Chloroplast");
        String acceptedChanged=service.get(teacher,UUID).evaluationReferenceHash();

        assertNotEquals(original,mcChanged);
        assertNotEquals(original,acceptedChanged);
        assertNotEquals(mcChanged,acceptedChanged);
    }
}
