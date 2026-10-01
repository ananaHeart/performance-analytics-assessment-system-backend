package com.capstone.assessment.v3.system.config;

import com.capstone.assessment.config.CorsConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Loads configuration and ordinary sentinel beans only: no datasource, application scan or server. */
class V3CloudConfigurationTest {
    private static final String CLOUD_URL = "jdbc:mysql://gateway01.ap-southeast-1.prod.aws.tidbcloud.com:4000/"
            + "performance_assessment_v3_test?sslMode=VERIFY_IDENTITY&serverTimezone=UTC";
    private static final String FRONTEND = "https://test-dashboard.onrender.com";
    private static final String PUBLIC_API = "https://test-backend.onrender.com";
    private static final String TEST_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void actualCloudOverlayWinsOverLocalDefaultsAndRetainsFullBaseline() {
        configuredProfiles("v3,v3-mobile-release,v3-cloud")
                .withPropertyValues("V3_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3307/performance_assessment_v3_db",
                        "V3_EXPECTED_CHECK_CONSTRAINT_COUNT=27", "V3_VALIDATE_ON_STARTUP=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var environment = context.getEnvironment();
                    assertThat(environment.getActiveProfiles()).contains("v3", "v3-mobile-release", "v3-cloud");
                    assertThat(environment.getProperty("spring.datasource.url"))
                            .startsWith("jdbc:mysql://gateway01.ap-southeast-1.prod.aws.tidbcloud.com:4000/performance_assessment_v3_test?")
                            .contains("sslMode=VERIFY_IDENTITY", "serverTimezone=UTC");
                    assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("synthetic-cloud-user");
                    assertThat(environment.getProperty("app.v3.baseline.database-engine")).isEqualTo("TIDB");
                    assertThat(environment.getProperty("app.v3.baseline.expected-database")).isEqualTo("performance_assessment_v3_test");
                    assertThat(environment.getProperty("app.v3.baseline.expected-table-count")).isEqualTo("79");
                    assertThat(environment.getProperty("app.v3.baseline.expected-foreign-key-count")).isEqualTo("200");
                    assertThat(environment.getProperty("app.v3.baseline.expected-check-constraint-count")).isEqualTo("131");
                    assertThat(environment.getProperty("app.v3.baseline.expected-unique-constraint-count")).isEqualTo("118");
                    assertThat(environment.getProperty("app.v3.baseline.expected-hardening-constraint-count")).isEqualTo("15");
                    assertThat(environment.getProperty("app.v3.baseline.expected-academic-calendar-constraint-count")).isEqualTo("16");
                    assertThat(environment.getProperty("app.v3.baseline.validate-on-startup")).isEqualTo("true");
                    assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("none");
                    assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");
                    assertThat(environment.getProperty("app.v3.auth.email-delivery-mode")).isEqualTo("brevo-api");
                    assertThat(environment.getProperty("app.v3.mobile.release.mode")).isEqualTo("production");
                    assertThat(environment.getProperty("app.v3.mobile.release.public-base-url")).isEqualTo(PUBLIC_API);
                    assertThat(environment.getProperty("app.v3.answer-sheets.storage-directory")).isEqualTo("/var/data/synthetic/answer-sheets");
                    assertThat(environment.getProperty("app.v3.scan-evidence.storage-directory")).isEqualTo("/var/data/synthetic/scan-evidence");
                    assertThat(context).doesNotHaveBean(CorsConfig.class);
                });
    }

    @Test
    void localV3ProfileRetainsLocalSettingsAndDoesNotCreateCloudGuard() {
        configuredProfiles("v3").run(context -> {
            assertThat(context).hasNotFailed();
            var environment = context.getEnvironment();
            assertThat(environment.getActiveProfiles()).contains("v3", "v3-mobile-release").doesNotContain("v3-cloud");
            assertThat(environment.getProperty("spring.datasource.url"))
                    .startsWith("jdbc:mysql://127.0.0.1:3307/performance_assessment_v3_db?");
            assertThat(environment.getProperty("app.v3.baseline.expected-database")).isEqualTo("performance_assessment_v3_db");
            assertThat(environment.getProperty("app.v3.auth.email-delivery-mode")).isEqualTo("log");
            assertThat(environment.getProperty("app.v3.mobile.release.mode")).isEqualTo("development");
            assertThat(context).doesNotHaveBean(V3CloudConfiguration.class).hasSingleBean(CorsConfig.class);
            assertThat(context.containsBean("v3CloudDestinationGuard")).isFalse();
        });
    }

    @Test
    void cloudCorsAllowsOnlyTheConfiguredFrontendOrigin() {
        configuredProfiles("v3,v3-mobile-release,v3-cloud").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(CorsConfigurationSource.class);
            CorsConfiguration cors = context.getBean(CorsConfigurationSource.class)
                    .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v3/reports"));
            assertThat(cors).isNotNull();
            assertThat(cors.getAllowedOrigins()).containsExactly(FRONTEND);
            assertThat(cors.getAllowedOriginPatterns()).isNullOrEmpty();
            assertThat(cors.checkOrigin(FRONTEND)).isEqualTo(FRONTEND);
            assertThat(cors.checkOrigin(PUBLIC_API)).isNull();
            assertThat(cors.checkOrigin("http://localhost:5173")).isNull();
            assertThat(cors.checkOrigin("https://test-dashboard.onrender.com.other.example")).isNull();
            assertThat(cors.checkOrigin("http://test-dashboard.onrender.com")).isNull();
        });
    }

    @Test
    void invalidDestinationStopsBeforeAnyOrdinaryBeanCanInitialize() {
        AtomicBoolean ordinaryBeanCreated = new AtomicBoolean();
        configuredProfiles("v3,v3-mobile-release,v3-cloud")
                .withPropertyValues("spring.datasource.url=jdbc:mysql://127.0.0.1:3307/performance_assessment_v3_db?sslMode=VERIFY_IDENTITY")
                .withBean("databaseConnectionSentinel", Object.class, () -> {
                    ordinaryBeanCreated.set(true);
                    return new Object();
                })
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("approved TiDB");
                    assertThat(ordinaryBeanCreated).isFalse();
                });
    }

    @Test
    void missingCloudPasswordCannotFallBackToTheLocalPassword() {
        AtomicBoolean ordinaryBeanCreated = new AtomicBoolean();
        configuredProfiles("v3,v3-mobile-release,v3-cloud")
                .withPropertyValues("V3_CLOUD_DB_PASSWORD=", "V3_DATASOURCE_PASSWORD=synthetic-local-password")
                .withBean("databaseConnectionSentinel", Object.class, () -> {
                    ordinaryBeanCreated.set(true);
                    return new Object();
                })
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("Missing cloud setting: spring.datasource.password");
                    assertThat(ordinaryBeanCreated).isFalse();
                });
    }

    @Test
    void puttingLocalProfileAfterCloudCannotStartWithLocalDestination() {
        configuredProfiles("v3-cloud,v3,v3-mobile-release").run(context -> {
            assertThat(context).hasFailed();
        });
    }

    @Test
    void syntheticValidConfigurationPassesWithoutConnectingAnywhere() {
        assertThatCode(() -> V3CloudConfiguration.validate(validEnvironment())).doesNotThrowAnyException();
    }

    /** Render Free blocks outbound SMTP: the Brevo HTTPS API needs only its key, no SMTP settings. */
    @Test
    void brevoApiEmailNeedsItsKeyButNoSmtpSettings() {
        MockEnvironment brevo = validEnvironment()
                .withProperty("app.v3.auth.email-delivery-mode", "brevo-api")
                .withProperty("app.v3.auth.brevo-api-key", "synthetic-brevo-key")
                .withProperty("spring.mail.host", "")
                .withProperty("spring.mail.username", "")
                .withProperty("spring.mail.password", "");
        assertThatCode(() -> V3CloudConfiguration.validate(brevo)).doesNotThrowAnyException();

        assertThatThrownBy(() -> V3CloudConfiguration.validate(brevo.withProperty("app.v3.auth.brevo-api-key", " ")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing cloud setting: app.v3.auth.brevo-api-key");
        assertThatThrownBy(() -> V3CloudConfiguration.validate(validEnvironment()
                        .withProperty("app.v3.auth.email-delivery-mode", "log")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("never log-only codes");
    }

    @Test
    void frontendOriginsAcceptAListAndLocalhostForTestingOnly() {
        assertThat(V3CloudConfiguration.frontendOrigins(FRONTEND + ", http://localhost:5173"))
                .containsExactly(FRONTEND, "http://localhost:5173");
        assertThat(V3CloudConfiguration.frontendOrigins("http://127.0.0.1:5173")).containsExactly("http://127.0.0.1:5173");
        // Plain-http public hosts and local hostnames under https stay rejected.
        assertThatThrownBy(() -> V3CloudConfiguration.frontendOrigins(FRONTEND + ",http://test.example"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> V3CloudConfiguration.frontendOrigins("http://localhost"))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:mysql://localhost:4000/performance_assessment_v3_test?sslMode=VERIFY_IDENTITY",
            "jdbc:mysql://127.0.0.1:3307/performance_assessment_v3_db?sslMode=VERIFY_IDENTITY",
            "jdbc:mysql://gateway01.ap-southeast-1.prod.aws.tidbcloud.com:4000/performance_assessment_db?sslMode=VERIFY_IDENTITY",
            "jdbc:mysql://gateway01.ap-southeast-1.prod.aws.tidbcloud.com:3306/performance_assessment_v3_test?sslMode=VERIFY_IDENTITY",
            "jdbc:mysql://gateway01.ap-southeast-1.prod.aws.tidbcloud.com:4000/performance_assessment_v3_test?sslMode=DISABLED",
            "jdbc:mysql://gateway01.ap-southeast-1.prod.aws.tidbcloud.com:4000/performance_assessment_v3_test?sslMode=VERIFY_IDENTITY&sslMode=DISABLED",
            "jdbc:mysql://gateway01.ap-southeast-1.prod.aws.tidbcloud.com:4000/performance_assessment_v3_test?sslMode=VERIFY_IDENTITY&%73slMode=DISABLED"
    })
    void rejectsWrongDestinationOrDisabledTls(String url) {
        assertThatThrownBy(() -> V3CloudConfiguration.validate(validEnvironment().withProperty("spring.datasource.url", url)))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "spring.jpa.hibernate.ddl-auto,update",
            "spring.sql.init.mode,always",
            "app.v3.baseline.expected-check-constraint-count,27",
            "app.v3.baseline.validate-on-startup,false",
            "app.v3.mobile.release.mode,development",
            "app.v3.auth.email-delivery-mode,log"
    })
    void rejectsUnsafeCloudOverrides(String property, String value) {
        assertThatThrownBy(() -> V3CloudConfiguration.validate(validEnvironment().withProperty(property, value)))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"tidb", "v2", "v3-dynamic-staging"})
    void rejectsMixingLegacyOrStagingProfiles(String profile) {
        MockEnvironment environment = validEnvironment();
        environment.setActiveProfiles("v3", "v3-mobile-release", "v3-cloud", profile);
        assertThatThrownBy(() -> V3CloudConfiguration.validate(environment)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be mixed");
    }

    @Test
    void rejectsCloudWithoutRequiredV3ReleaseProfile() {
        MockEnvironment environment = validEnvironment();
        environment.setActiveProfiles("v3-cloud");
        assertThatThrownBy(() -> V3CloudConfiguration.validate(environment)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Activate v3,v3-mobile-release,v3-cloud");
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.datasource.username", "spring.datasource.password", "spring.mail.host",
            "spring.mail.username", "spring.mail.password", "app.v3.auth.email-from-address", "app.v3.auth.mfa.encryption-key",
            "V3_CLOUD_STORAGE_ROOT"})
    void rejectsMissingRequiredCloudSettings(String property) {
        assertThatThrownBy(() -> V3CloudConfiguration.validate(validEnvironment().withProperty(property, " ")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Missing cloud setting: " + property);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-base64!", "YQ=="})
    void rejectsInvalidMfaEncryptionKeys(String value) {
        assertThatThrownBy(() -> V3CloudConfiguration.validate(validEnvironment().withProperty("app.v3.auth.mfa.encryption-key", value)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MFA");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://test.example", "https://localhost", "https://127.0.0.1", "https://*.example.com",
            "https://localhost.localdomain", "https://api.localhost", "https://api.local", "https://api.localdomain",
            "https://test.example/path", "https://test.example?next=evil", "https://user@test.example"})
    void rejectsNonPublicOrNonExactFrontendOrigins(String value) {
        assertThatThrownBy(() -> V3CloudConfiguration.validate(validEnvironment().withProperty("app.v3.cloud.frontend-origin", value)))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"output/scan-evidence", "/tmp/evidence", "/var/database", "/var/data/../tmp",
            "/var/data/./evidence", "/var/data/..\\tmp"})
    void rejectsStorageOutsidePermanentDiskMount(String value) {
        assertThatThrownBy(() -> V3CloudConfiguration.validate(validEnvironment().withProperty("V3_CLOUD_STORAGE_ROOT", value)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("permanent disk mount");
    }

    @ParameterizedTest
    @CsvSource({
            "app.v3.answer-sheets.storage-directory,/tmp/answer-sheets",
            "app.v3.scan-evidence.storage-directory,output/scan-evidence",
            "app.v3.answer-sheets.storage-directory,/var/data/other-root/answer-sheets",
            "app.v3.scan-evidence.storage-directory,/var/data/synthetic/../scan-evidence"
    })
    void rejectsResolvedStorageOverridesAwayFromApprovedRoot(String property, String value) {
        assertThatThrownBy(() -> V3CloudConfiguration.validate(validEnvironment().withProperty(property, value)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("configured permanent storage directories");
    }

    @ParameterizedTest
    @CsvSource({
            "spring.datasource.hikari.jdbc-url,jdbc:mysql://127.0.0.1:3307/performance_assessment_v3_db",
            "spring.datasource.jndi-name,java:comp/env/jdbc/local",
            "spring.datasource.hikari.data-source-class-name,com.mysql.cj.jdbc.MysqlDataSource",
            "spring.datasource.hikari.catalog,performance_assessment_db",
            "spring.datasource.hikari.schema,performance_assessment_v3_db",
            "spring.datasource.hikari.connection-init-sql,USE performance_assessment_v3_db"
    })
    void rejectsAlternateDatasourceAndConnectionInitializationOverrides(String property, String value) {
        assertThatThrownBy(() -> V3CloudConfiguration.validate(validEnvironment().withProperty(property, value)))
                .isInstanceOf(IllegalStateException.class);
    }

    private ApplicationContextRunner configuredProfiles(String profiles) {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    // Keep developer machine environment and credentials out of these deterministic config tests.
                    context.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    context.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                    new ConfigDataApplicationContextInitializer().initialize(context);
                })
                .withPropertyValues("spring.config.location=classpath:/application.properties",
                        "spring.profiles.active=" + profiles,
                        "V3_CLOUD_DB_USERNAME=synthetic-cloud-user", "V3_CLOUD_DB_PASSWORD=synthetic-cloud-password",
                        "V3_CLOUD_STORAGE_ROOT=/var/data/synthetic", "V3_CLOUD_FRONTEND_ORIGIN=" + FRONTEND,
                        "V3_CLOUD_PUBLIC_BASE_URL=" + PUBLIC_API, "V3_CLOUD_EMAIL_FROM=synthetic@example.invalid",
                        "V3_CLOUD_SMTP_HOST=smtp.example.invalid", "V3_CLOUD_SMTP_USERNAME=synthetic-smtp-user",
                        "V3_CLOUD_SMTP_PASSWORD=synthetic-smtp-password", "V3_CLOUD_MFA_ENCRYPTION_KEY=" + TEST_KEY,
                        "V3_CLOUD_BREVO_API_KEY=synthetic-brevo-key")
                .withUserConfiguration(V3CloudConfiguration.class, CorsConfig.class);
    }

    private MockEnvironment validEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("v3", "v3-mobile-release", "v3-cloud");
        return environment.withProperty("spring.datasource.url", CLOUD_URL)
                .withProperty("spring.datasource.username", "synthetic-cloud-user")
                .withProperty("spring.datasource.password", "synthetic-cloud-password")
                .withProperty("spring.jpa.hibernate.ddl-auto", "none")
                .withProperty("spring.sql.init.mode", "never")
                .withProperty("app.v3.baseline.expected-database", "performance_assessment_v3_test")
                .withProperty("app.v3.baseline.database-engine", "TIDB")
                .withProperty("app.v3.baseline.expected-table-count", "79")
                .withProperty("app.v3.baseline.expected-foreign-key-count", "200")
                .withProperty("app.v3.baseline.expected-check-constraint-count", "131")
                .withProperty("app.v3.baseline.expected-unique-constraint-count", "118")
                .withProperty("app.v3.baseline.expected-hardening-constraint-count", "15")
                .withProperty("app.v3.baseline.expected-academic-calendar-constraint-count", "16")
                .withProperty("app.v3.baseline.validate-on-startup", "true")
                .withProperty("app.v3.mobile.release.mode", "production")
                .withProperty("app.v3.cloud.frontend-origin", FRONTEND)
                .withProperty("app.v3.mobile.release.public-base-url", PUBLIC_API)
                .withProperty("app.v3.auth.email-delivery-mode", "smtp")
                .withProperty("spring.mail.host", "smtp.example.invalid")
                .withProperty("spring.mail.username", "synthetic-smtp-user")
                .withProperty("spring.mail.password", "synthetic-smtp-password")
                .withProperty("app.v3.auth.email-from-address", "synthetic@example.invalid")
                .withProperty("app.v3.auth.mfa.encryption-key", TEST_KEY)
                .withProperty("V3_CLOUD_STORAGE_ROOT", "/var/data/synthetic")
                .withProperty("app.v3.answer-sheets.storage-directory", "/var/data/synthetic/answer-sheets")
                .withProperty("app.v3.scan-evidence.storage-directory", "/var/data/synthetic/scan-evidence");
    }
}
