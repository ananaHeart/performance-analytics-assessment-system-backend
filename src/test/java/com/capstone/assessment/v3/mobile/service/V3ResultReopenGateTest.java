package com.capstone.assessment.v3.mobile.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3ReopenRequest;
import com.capstone.assessment.v3.mobile.repository.*;
import com.capstone.assessment.v3.scoring.repository.V3ScoringRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class V3ResultReopenGateTest {
    @Test void disabledReopenDoesNotQueryDraftSchemaOrStartTransaction(){
        try(var factory=Validation.buildDefaultValidatorFactory()){
            var repo=mock(V3ResultReopenRepository.class);var owners=mock(V3ScanUploadLedgerRepository.class);var scoring=mock(V3ScoringRepository.class);
            var finals=mock(V3MobileFinalizationRepository.class);var finalizer=mock(V3MobileFinalizationService.class);var manager=mock(PlatformTransactionManager.class);
            var service=new V3ResultReopenService(repo,owners,scoring,finals,finalizer,new ObjectMapper(),factory.getValidator(),manager,false);
            String uuid="00000000-0000-4000-8000-000000000001";
            var teacher=new V3AuthenticatedUser(42,"S1","","teacher","active","");
            assertEquals("MOBILE_REOPEN_UNAVAILABLE",assertThrows(V3AuthException.class,()->service.reopen(teacher,uuid,new V3ReopenRequest("3.0",uuid,uuid,4L,1L,"REVIEW",null))).getCode());
            verifyNoInteractions(repo,owners,scoring,finals,finalizer,manager);
        }
    }
    @Test void validatesReasonVersionsAndUuidBeforeDatabaseAccess(){
        try(var factory=Validation.buildDefaultValidatorFactory()){
            var repo=mock(V3ResultReopenRepository.class);var manager=mock(PlatformTransactionManager.class);
            var service=new V3ResultReopenService(repo,mock(V3ScanUploadLedgerRepository.class),mock(V3ScoringRepository.class),mock(V3MobileFinalizationRepository.class),mock(V3MobileFinalizationService.class),new ObjectMapper(),factory.getValidator(),manager,true);
            String uuid="00000000-0000-4000-8000-000000000001";var teacher=new V3AuthenticatedUser(42,"S1","","teacher","active","");
            for(var r:List.of(new V3ReopenRequest("3.0",uuid,uuid,4L,1L," ",null),new V3ReopenRequest("3.0",uuid,uuid,0L,1L,"REVIEW",null),
                    new V3ReopenRequest("3.0",uuid,uuid,4L,1L,"X".repeat(51),null),new V3ReopenRequest("3.0",uuid,uuid,4L,1L,"REVIEW","X".repeat(4001)),
                    new V3ReopenRequest("3.1",uuid,uuid,4L,1L,"REVIEW",null),new V3ReopenRequest("3.0","invalid",uuid,4L,1L,"REVIEW",null)))
                assertEquals("VALIDATION_FAILED",assertThrows(V3AuthException.class,()->service.reopen(teacher,uuid,r)).getCode());
            verifyNoInteractions(repo,manager);
        }
    }
}
