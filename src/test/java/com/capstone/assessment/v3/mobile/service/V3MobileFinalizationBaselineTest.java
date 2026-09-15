package com.capstone.assessment.v3.mobile.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import com.capstone.assessment.v3.mobile.repository.V3MobileFinalizationRepository;
import com.capstone.assessment.v3.mobile.repository.V3ScanUploadLedgerRepository;
import com.capstone.assessment.v3.mobile.storage.V3OriginalScanImageStorage;
import com.capstone.assessment.v3.scoring.model.V3ScoringModels.ResultContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class V3MobileFinalizationBaselineTest {
    @Test void disabledBridgeWorksWithoutAnyDraftTablesOrRevisionColumn() {
        var source=new DriverManagerDataSource("jdbc:h2:mem:finalization_baseline;DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE test_result_scans(test_result_id BIGINT)");
        jdbc.execute("CREATE TABLE student_answers(test_result_id BIGINT,capture_source VARCHAR(20))");
        var ledger=mock(V3ScanUploadLedgerRepository.class);var storage=mock(V3OriginalScanImageStorage.class);
        var written=mock(V3WrittenFinalizationService.class);
        var service=new V3MobileFinalizationService(new V3MobileFinalizationRepository(jdbc),ledger,storage,new ObjectMapper(),false,written);
        var context=mock(ResultContext.class);when(context.testResultId()).thenReturn(1L);
        assertTrue(service.prepare(context).isEmpty());
        jdbc.update("INSERT INTO test_result_scans VALUES(1)");
        assertEquals("MOBILE_FINALIZATION_UNAVAILABLE",assertThrows(V3AuthException.class,()->service.prepare(context)).getCode());
        jdbc.update("DELETE FROM test_result_scans");
        jdbc.update("INSERT INTO student_answers VALUES(1,'omr')");
        assertEquals("MOBILE_FINALIZATION_UNAVAILABLE",assertThrows(V3AuthException.class,()->service.prepare(context)).getCode());
        verifyNoInteractions(ledger,storage,written);
    }
}
