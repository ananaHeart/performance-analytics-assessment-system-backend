package com.capstone.assessment.v3.mobile.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.repository.V3MobileReadbackRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class V3MobileReadbackGateTest {
    final V3MobileReadbackRepository repository=mock(V3MobileReadbackRepository.class);
    final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    final V3MobileReadbackService service=new V3MobileReadbackService(repository,new ObjectMapper(),manager,false);
    final V3AuthenticatedUser teacher=new V3AuthenticatedUser(42,"S1","","teacher","active","");
    final String uuid="00000000-0000-4000-8000-000000000001";
    @Test void disabledReadsNeverQueryTheBaselineOrDraftSchema() {
        assertEquals("MOBILE_READBACK_UNAVAILABLE",assertThrows(V3AuthException.class,()->service.result(teacher,uuid)).getCode());
        assertEquals("MOBILE_READBACK_UNAVAILABLE",assertThrows(V3AuthException.class,()->service.analytics(teacher,uuid)).getCode());
        assertEquals("MOBILE_READBACK_UNAVAILABLE",assertThrows(V3AuthException.class,()->service.sync(teacher,uuid)).getCode());
        verifyNoInteractions(repository,manager);
    }
    @Test void anonymousWrongRoleAndMalformedUuidsNeverReachPersistence() {
        assertEquals("AUTHENTICATION_REQUIRED",assertThrows(V3AuthException.class,()->service.result(null,uuid)).getCode());
        var principal=new V3AuthenticatedUser(43,"S1","","principal","active","");
        assertEquals("RESULT_ACCESS_DENIED",assertThrows(V3AuthException.class,()->service.analytics(principal,uuid)).getCode());
        for(String invalid:new String[]{"123","../secret","ABCDEFAB-0000-4000-8000-000000000001","00000000-0000-0000-0000-000000000000"}) {
            assertEquals("VALIDATION_FAILED",assertThrows(V3AuthException.class,()->service.sync(teacher,invalid)).getCode());
        }
        verifyNoInteractions(repository,manager);
    }
}
