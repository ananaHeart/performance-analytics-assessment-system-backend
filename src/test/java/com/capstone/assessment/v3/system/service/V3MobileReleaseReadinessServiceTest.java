package com.capstone.assessment.v3.system.service;

import com.capstone.assessment.v3.auth.config.V3AuthProperties;
import com.capstone.assessment.v3.auth.config.V3MfaProperties;
import com.capstone.assessment.v3.mobile.config.V3MobileReleaseProperties;
import com.capstone.assessment.v3.system.config.V3BaselineProperties;
import com.capstone.assessment.v3.system.dto.V3DatabaseReadinessResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class V3MobileReleaseReadinessServiceTest {

    @TempDir Path evidenceDirectory;

    private final V3DatabaseReadinessService database = mock(V3DatabaseReadinessService.class);
    private final V3MobileReleaseProperties mobile = new V3MobileReleaseProperties();
    private final V3BaselineProperties baseline = new V3BaselineProperties();
    private final V3AuthProperties auth = new V3AuthProperties();
    private final V3MfaProperties mfa = new V3MfaProperties();
    private V3MobileReleaseReadinessService service;

    @BeforeEach
    void setUp() {
        when(database.checkReadiness()).thenReturn(new V3DatabaseReadinessResponse(
                "v3", "performance_assessment_v3_db", true, 79, 200, 131, 118,
                5, 1, 4, List.of()));
        baseline.setExpectedTableCount(79);
        baseline.setExpectedForeignKeyCount(200);
        baseline.setExpectedCheckConstraintCount(131);
        baseline.setExpectedUniqueConstraintCount(118);
        baseline.setValidateOnStartup(true);
        mobile.getRelease().setProfileEnabled(true);
        mobile.getRelease().setHttpEnabled(true);
        mobile.getRelease().setContractPack("1.11.0");
        mobile.getRelease().setMode("development");
        mobile.getRelease().setPublicBaseUrl("http://192.168.1.50:8082");
        mobile.setScanRecoveryEnabled(true);
        mobile.setFinalizationEnabled(true);
        mobile.setReadbackEnabled(true);
        mobile.setEvaluationReferenceEnabled(true);
        mobile.setReopenEnabled(true);
        mobile.setCorrectionEnabled(true);
        mobile.setSupersedeEnabled(true);
        auth.setSessionTtl(Duration.ofHours(8));
        service = new V3MobileReleaseReadinessService(
                database, mobile, baseline, auth, mfa, evidenceDirectory.toString(), "smtp.test");
    }

    @Test
    void developmentBackendCanPassWhileEndToEndAcceptanceRemainsPending() {
        var response = service.checkReadiness();

        assertTrue(response.backendReady());
        assertTrue(response.writeApiEnabled());
        assertFalse(response.refreshSupported());
        assertFalse(response.fullyConnected());
    }

    @Test
    void masterSwitchKeepsReleaseClosedAndRequireReadyFails() {
        mobile.getRelease().setHttpEnabled(false);

        assertFalse(service.checkReadiness().backendReady());
        assertThrows(IllegalStateException.class, service::requireReady);
    }

    @Test
    void productionRequiresHttpsAndAuthSecrets() {
        mobile.getRelease().setMode("production");
        mobile.getRelease().setPublicBaseUrl("http://api.smart.test");

        assertFalse(service.checkReadiness().backendReady());

        mobile.getRelease().setPublicBaseUrl("https://api.smart.test");
        auth.setEmailDeliveryMode("smtp");
        mfa.setEncryptionKey("deployment-secret");
        mobile.getRelease().setMobileWiringVerified(true);
        mobile.getRelease().setPhysicalScannerVerified(true);

        var response = service.checkReadiness();
        assertTrue(response.backendReady());
        assertTrue(response.fullyConnected());
    }

    @Test
    void developmentLoopbackRequiresExplicitAdbReverseMode() {
        mobile.getRelease().setPublicBaseUrl("http://127.0.0.1:8082");
        assertFalse(service.checkReadiness().backendReady());

        mobile.getRelease().setAdbReverseEnabled(true);
        assertTrue(service.checkReadiness().backendReady());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"https://127.0.0.1:8082", "https://[::1]:8082", "https://192.168.1.50", "https://api.smart.test/api", "https://api.smart.test:0"})
    void productionRejectsIpLoopbackAndNonBaseUrls(String url) {
        mobile.getRelease().setMode("production");
        mobile.getRelease().setPublicBaseUrl(url);
        auth.setEmailDeliveryMode("smtp"); mfa.setEncryptionKey("deployment-secret");
        assertFalse(service.checkReadiness().backendReady());
    }

    @Test void unknownModeCannotBypassHttpsCheck() {
        mobile.getRelease().setMode("prodution");
        assertFalse(service.checkReadiness().backendReady());
    }
}
