package com.capstone.assessment.v3.mobile.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.repository.V3EvaluationReferenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class V3EvaluationReferenceGateTest {
    final V3EvaluationReferenceRepository repository=mock(V3EvaluationReferenceRepository.class);
    final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    final V3EvaluationReferenceService service=new V3EvaluationReferenceService(repository,manager,false);
    final V3AuthenticatedUser teacher=new V3AuthenticatedUser(42,"S1","","teacher","active","");
    final String uuid="00000000-0000-4000-8000-000000000001";
    @Test void releaseGateMakesNoDatabaseCalls() {
        assertEquals("EVALUATION_REFERENCE_UNAVAILABLE",assertThrows(V3AuthException.class,()->service.get(teacher,uuid)).getCode());verifyNoInteractions(repository,manager);
    }
    @Test void roleAndUuidValidationPrecedePersistence() {
        assertEquals("AUTHENTICATION_REQUIRED",assertThrows(V3AuthException.class,()->service.get(null,uuid)).getCode());
        var principal=new V3AuthenticatedUser(1,"S1","","principal","active","");
        assertEquals("EVALUATION_REFERENCE_ACCESS_DENIED",assertThrows(V3AuthException.class,()->service.get(principal,uuid)).getCode());
        assertEquals("VALIDATION_FAILED",assertThrows(V3AuthException.class,()->service.get(teacher,"../wrong")).getCode());verifyNoInteractions(repository,manager);
    }
}
