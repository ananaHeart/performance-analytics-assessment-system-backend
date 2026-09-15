package com.capstone.assessment.v3.mobile.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3SupersedeRequest;
import com.capstone.assessment.v3.mobile.repository.V3ResultSupersedeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class V3ResultSupersedeGateTest {
    @Test void disabledSupersedeAndLinkReadDoNotTouchDraftSchema(){
        try(var factory=Validation.buildDefaultValidatorFactory()){
            var repository=mock(V3ResultSupersedeRepository.class);var manager=mock(PlatformTransactionManager.class);
            var service=new V3ResultSupersedeService(repository,null,null,null,null,null,new ObjectMapper(),factory.getValidator(),manager,false);
            String uuid="00000000-0000-4000-8000-000000000001",replacement="00000000-0000-4000-8000-000000000002";
            var teacher=new V3AuthenticatedUser(42,"S1","","teacher","active","");
            var request=new V3SupersedeRequest("3.0",uuid,uuid,4L,1L,"REVIEW",null,replacement);
            assertEquals("MOBILE_SUPERSEDE_UNAVAILABLE",assertThrows(V3AuthException.class,()->service.supersede(teacher,uuid,request)).getCode());
            assertEquals("MOBILE_SUPERSEDE_UNAVAILABLE",assertThrows(V3AuthException.class,()->service.supersession(teacher,uuid)).getCode());
            assertEquals("VALIDATION_FAILED",assertThrows(V3AuthException.class,()->service.supersede(teacher,replacement,request)).getCode());
            verifyNoInteractions(repository,manager);
        }
    }
}
