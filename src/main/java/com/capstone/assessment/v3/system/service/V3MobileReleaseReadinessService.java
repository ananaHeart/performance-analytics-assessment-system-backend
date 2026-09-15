package com.capstone.assessment.v3.system.service;

import com.capstone.assessment.v3.auth.config.V3AuthProperties;
import com.capstone.assessment.v3.auth.config.V3MfaProperties;
import com.capstone.assessment.v3.mobile.config.V3MobileReleaseProperties;
import com.capstone.assessment.v3.system.config.V3BaselineProperties;
import com.capstone.assessment.v3.system.dto.V3DatabaseReadinessResponse;
import com.capstone.assessment.v3.system.dto.V3MobileReleaseCheckResponse;
import com.capstone.assessment.v3.system.dto.V3MobileReleaseReadinessResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Profile("v3")
@Service
public class V3MobileReleaseReadinessService {

    static final String RELEASE_CONTRACT_PACK = "1.11.0";
    static final int RELEASE_TABLE_COUNT = 79;
    static final int RELEASE_FOREIGN_KEY_COUNT = 200;
    static final int RELEASE_CHECK_COUNT = 131;
    static final int RELEASE_UNIQUE_COUNT = 118;

    private final V3DatabaseReadinessService databaseReadinessService;
    private final V3MobileReleaseProperties mobile;
    private final V3BaselineProperties baseline;
    private final V3AuthProperties auth;
    private final V3MfaProperties mfa;
    private final String evidenceDirectory;
    private final String smtpHost;

    public V3MobileReleaseReadinessService(
            V3DatabaseReadinessService databaseReadinessService,
            V3MobileReleaseProperties mobile,
            V3BaselineProperties baseline,
            V3AuthProperties auth,
            V3MfaProperties mfa,
            @Value("${app.v3.scan-evidence.storage-directory:}") String evidenceDirectory,
            @Value("${spring.mail.host:}") String smtpHost
    ) {
        this.databaseReadinessService = databaseReadinessService;
        this.mobile = mobile;
        this.baseline = baseline;
        this.auth = auth;
        this.mfa = mfa;
        this.evidenceDirectory = evidenceDirectory;
        this.smtpHost = smtpHost;
    }

    public V3MobileReleaseReadinessResponse checkReadiness() {
        V3DatabaseReadinessResponse database = databaseReadinessService.checkReadiness();
        List<V3MobileReleaseCheckResponse> checks = new ArrayList<>();
        add(checks, "release_profile", mobile.getRelease().isProfileEnabled(),
                "The v3-mobile-release profile must be active.");
        add(checks, "write_api_switch", mobile.getRelease().isHttpEnabled(),
                "V3_MOBILE_HTTP_ENABLED must be true after the other checks pass.");
        add(checks, "database", database.ready(),
                "The configured database must match the release baseline and reference seeds.");
        add(checks, "release_schema_counts", releaseCountsMatch(),
                "Expected 79 tables, 200 foreign keys, 131 checks and 118 unique constraints.");
        add(checks, "startup_database_validation", baseline.isValidateOnStartup(),
                "Startup database validation must remain enabled.");
        add(checks, "contract_pack", RELEASE_CONTRACT_PACK.equals(mobile.getRelease().getContractPack()),
                "Backend and Mobile must use contract pack 1.11.0.");
        add(checks, "workflow_components", workflowComponentsEnabled(),
                "Recovery, finalization, readback, evaluation reference, reopen, correction and supersession must be enabled.");
        add(checks, "public_base_url", validPublicBaseUrl(),
                "Use a phone-reachable development URL or an HTTPS production hostname.");
        add(checks, "evidence_storage", validEvidenceDirectory(),
                "Evidence storage must be an existing writable absolute directory.");
        add(checks, "session_policy", validSessionPolicy(),
                "Bearer sessions must expire between 1 minute and 24 hours; refresh is not supported.");
        add(checks, "production_auth_secrets", validProductionAuthConfiguration(),
                "Production MFA requires an encryption key and SMTP email delivery.");

        boolean backendReady = checks.stream().allMatch(V3MobileReleaseCheckResponse::passed);
        boolean fullyConnected = backendReady
                && mobile.getRelease().isMobileWiringVerified()
                && mobile.getRelease().isPhysicalScannerVerified();
        Duration sessionTtl = auth.getSessionTtl();
        return new V3MobileReleaseReadinessResponse(
                mobile.getRelease().getContractPack(),
                normalizedMode(),
                safe(mobile.getRelease().getPublicBaseUrl()),
                backendReady,
                mobile.isWriteApiEnabled(),
                database.ready(),
                sessionTtl == null ? 0 : sessionTtl.toSeconds(),
                false,
                mobile.getRelease().isMobileWiringVerified(),
                mobile.getRelease().isPhysicalScannerVerified(),
                fullyConnected,
                List.copyOf(checks)
        );
    }

    public V3MobileReleaseReadinessResponse requireReady() {
        V3MobileReleaseReadinessResponse response = checkReadiness();
        if (!response.backendReady()) {
            String failures = response.checks().stream()
                    .filter(check -> !check.passed())
                    .map(V3MobileReleaseCheckResponse::check)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("unknown");
            throw new IllegalStateException("V3 Mobile release preflight failed: " + failures);
        }
        return response;
    }

    private boolean releaseCountsMatch() {
        return baseline.getExpectedTableCount() == RELEASE_TABLE_COUNT
                && baseline.getExpectedForeignKeyCount() == RELEASE_FOREIGN_KEY_COUNT
                && baseline.getExpectedCheckConstraintCount() == RELEASE_CHECK_COUNT
                && baseline.getExpectedUniqueConstraintCount() == RELEASE_UNIQUE_COUNT;
    }

    private boolean workflowComponentsEnabled() {
        return mobile.isScanRecoveryEnabled()
                && mobile.isFinalizationEnabled()
                && mobile.isReadbackEnabled()
                && mobile.isEvaluationReferenceEnabled()
                && mobile.isReopenEnabled()
                && mobile.isCorrectionEnabled()
                && mobile.isSupersedeEnabled();
    }

    private boolean validPublicBaseUrl() {
        try {
            if (!List.of("development", "production").contains(normalizedMode())) return false;
            URI uri = URI.create(safe(mobile.getRelease().getPublicBaseUrl()));
            String host = uri.getHost();
            String scheme = uri.getScheme();
            if (host == null || scheme == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                return false;
            }
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return false;
            }
            if (!(uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath()))) return false;
            if (uri.getPort() == 0 || uri.getPort() > 65535) return false;
            boolean loopback = "localhost".equalsIgnoreCase(host) || host.startsWith("127.")
                    || "::1".equals(host) || "[::1]".equals(host);
            if (loopback) {
                return "development".equals(normalizedMode()) && mobile.getRelease().isAdbReverseEnabled();
            }
            return !"production".equals(normalizedMode()) || ("https".equalsIgnoreCase(scheme)
                    && host.contains(".") && !host.matches("[0-9.]+") && !host.contains(":"));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean validEvidenceDirectory() {
        try {
            if (evidenceDirectory == null || evidenceDirectory.isBlank()) {
                return false;
            }
            Path path = Path.of(evidenceDirectory);
            return path.isAbsolute() && Files.isDirectory(path) && Files.isWritable(path);
        } catch (InvalidPathException exception) {
            return false;
        }
    }

    private boolean validSessionPolicy() {
        Duration ttl = auth.getSessionTtl();
        return ttl != null && ttl.compareTo(Duration.ofMinutes(1)) >= 0 && ttl.compareTo(Duration.ofHours(24)) <= 0;
    }

    private boolean validProductionAuthConfiguration() {
        if (!"production".equals(normalizedMode())) {
            return true;
        }
        boolean encryptionReady = !mfa.isEnabled() || (mfa.getEncryptionKey() != null && !mfa.getEncryptionKey().isBlank());
        return encryptionReady
                && "smtp".equalsIgnoreCase(safe(auth.getEmailDeliveryMode()))
                && !safe(smtpHost).isBlank();
    }

    private String normalizedMode() {
        return safe(mobile.getRelease().getMode()).toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private void add(List<V3MobileReleaseCheckResponse> checks, String name, boolean passed, String detail) {
        checks.add(new V3MobileReleaseCheckResponse(name, passed, detail));
    }
}
