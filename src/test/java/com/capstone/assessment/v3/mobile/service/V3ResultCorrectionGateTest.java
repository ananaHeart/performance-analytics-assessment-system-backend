package com.capstone.assessment.v3.mobile.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3CorrectionRequest;
import com.capstone.assessment.v3.mobile.repository.V3ResultCorrectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import java.nio.file.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class V3ResultCorrectionGateTest {
    @Test void disabledCorrectionDoesNotQueryDraftTablesOrOpenTransaction() throws Exception {
        try(var factory=Validation.buildDefaultValidatorFactory()){
            var repository=mock(V3ResultCorrectionRepository.class);var manager=mock(PlatformTransactionManager.class);
            var mapper=new ObjectMapper().findAndRegisterModules();
            var service=new V3ResultCorrectionService(repository,null,null,null,null,null,null,null,null,null,null,factory.getValidator(),mapper,manager,false);
            var request=mapper.readValue(Files.readString(Path.of("docs/contracts/mobile-v3/1.10.0/fixtures/request.json")),V3CorrectionRequest.class);
            var teacher=new V3AuthenticatedUser(42,"S1","","teacher","active","");
            assertEquals("MOBILE_CORRECTION_UNAVAILABLE",assertThrows(V3AuthException.class,()->service.correct(teacher,"00000000-0000-4000-8000-000000000001",request)).getCode());
            verifyNoInteractions(repository,manager);
        }
    }
    @Test void malformedCompleteReviewIsRejectedBeforeDatabaseAccess()throws Exception{
        try(var factory=Validation.buildDefaultValidatorFactory()){
            var repository=mock(V3ResultCorrectionRepository.class);var manager=mock(PlatformTransactionManager.class);
            var mapper=new ObjectMapper().findAndRegisterModules();
            var service=new V3ResultCorrectionService(repository,null,null,null,null,null,null,null,null,null,null,factory.getValidator(),mapper,manager,true);
            var request=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(Files.readString(Path.of("docs/contracts/mobile-v3/1.10.0/fixtures/request.json")));
            var teacher=new V3AuthenticatedUser(42,"S1","","teacher","active","");
            for(String field:new String[]{"contractVersion","reasonCode","syncUuid"}){
                var invalid=request.deepCopy();invalid.put(field," ");var dto=mapper.treeToValue(invalid,V3CorrectionRequest.class);
                assertEquals("VALIDATION_FAILED",assertThrows(V3AuthException.class,()->service.correct(teacher,"00000000-0000-4000-8000-000000000001",dto)).getCode());
            }
            var empty=request.deepCopy();empty.putArray("answers");var dto=mapper.treeToValue(empty,V3CorrectionRequest.class);
            assertEquals("VALIDATION_FAILED",assertThrows(V3AuthException.class,()->service.correct(teacher,"00000000-0000-4000-8000-000000000001",dto)).getCode());
            verifyNoInteractions(repository,manager);
        }
    }
}
