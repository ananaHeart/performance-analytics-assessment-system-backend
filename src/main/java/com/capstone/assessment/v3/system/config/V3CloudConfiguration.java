package com.capstone.assessment.v3.system.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates the isolated cloud destination before ordinary beans (including the datasource) initialize. */
@Configuration(proxyBeanMethods = false)
@Profile("v3-cloud")
public class V3CloudConfiguration {
    private static final String CLOUD_HOST = "gateway01.ap-southeast-1.prod.aws.tidbcloud.com";
    private static final String CLOUD_DATABASE = "performance_assessment_v3_test";

    @Bean
    static BeanFactoryPostProcessor v3CloudDestinationGuard(Environment environment) {
        return beanFactory -> validate(environment);
    }

    static void validate(Environment environment) {
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        require(profiles.contains("v3") && profiles.contains("v3-mobile-release"),
                "Activate v3,v3-mobile-release,v3-cloud together, with v3-cloud last.");
        require(!profiles.contains("tidb") && !profiles.contains("v2") && !profiles.contains("v3-dynamic-staging"),
                "The isolated V3 cloud profile cannot be mixed with legacy or staging profiles.");
        URI database;
        try {
            String url = required(environment, "spring.datasource.url");
            require(url.startsWith("jdbc:mysql://"), "Cloud requires a MySQL JDBC URL.");
            database = URI.create(url.substring("jdbc:".length()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Cloud database URL is invalid.");
        }
        require(CLOUD_HOST.equalsIgnoreCase(database.getHost()) && database.getPort() == 4000
                        && ("/" + CLOUD_DATABASE).equals(database.getPath()) && database.getUserInfo() == null,
                "Cloud may connect only to the approved TiDB performance_assessment_v3_test destination.");
        String hikariUrl = environment.getProperty("spring.datasource.hikari.jdbc-url");
        require(hikariUrl == null || hikariUrl.isBlank() || hikariUrl.equals(environment.getProperty("spring.datasource.url")),
                "A Hikari JDBC URL override cannot change the approved cloud destination.");
        for (String setting : List.of("spring.datasource.jndi-name", "spring.datasource.hikari.data-source-class-name",
                "spring.datasource.hikari.connection-init-sql", "spring.datasource.hikari.catalog",
                "spring.datasource.hikari.schema")) {
            String value = environment.getProperty(setting);
            require(value == null || value.isBlank(), "Cloud forbids alternate connection or initialization setting: " + setting);
        }
        List<String> parameters = Arrays.asList(database.getRawQuery() == null ? new String[0] : database.getRawQuery().split("&"));
        Map<String, String> allowedParameters = Map.of("sslMode", "VERIFY_IDENTITY",
                "enabledTLSProtocols", "TLSv1.2,TLSv1.3", "serverTimezone", "UTC");
        Set<String> parameterNames = new HashSet<>();
        for (String parameter : parameters) {
            String[] pair = parameter.split("=", 2);
            // Reject encoded keys too: Connector/J decodes them and otherwise permits hidden overrides.
            require(pair.length == 2 && pair[1].equals(allowedParameters.get(pair[0]))
                            && parameterNames.add(pair[0]),
                    "Cloud database parameters must use the approved TLS and timezone settings without duplicates.");
        }
        require(parameterNames.contains("sslMode") && database.getFragment() == null,
                "Cloud database TLS identity verification must remain enabled.");
        required(environment, "spring.datasource.username");
        required(environment, "spring.datasource.password");
        require("none".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
                        && "never".equals(environment.getProperty("spring.sql.init.mode")),
                "Cloud schema creation and seed initialization must remain disabled.");
        require(CLOUD_DATABASE.equals(environment.getProperty("app.v3.baseline.expected-database"))
                        && "TIDB".equalsIgnoreCase(environment.getProperty("app.v3.baseline.database-engine"))
                        && "131".equals(environment.getProperty("app.v3.baseline.expected-check-constraint-count"))
                        && "15".equals(environment.getProperty("app.v3.baseline.expected-hardening-constraint-count"))
                        && "16".equals(environment.getProperty("app.v3.baseline.expected-academic-calendar-constraint-count"))
                        && "true".equalsIgnoreCase(environment.getProperty("app.v3.baseline.validate-on-startup")),
                "Cloud requires the repaired TiDB baseline with startup validation enabled.");
        require("production".equals(environment.getProperty("app.v3.mobile.release.mode")),
                "Cloud release mode must be production.");
        httpsOrigin(required(environment, "app.v3.cloud.frontend-origin"));
        httpsOrigin(required(environment, "app.v3.mobile.release.public-base-url"));
        String storageRoot = required(environment, "V3_CLOUD_STORAGE_ROOT");
        require((storageRoot.equals("/var/data") || storageRoot.startsWith("/var/data/"))
                        && !Arrays.asList(storageRoot.split("/")).contains("..")
                        && !Arrays.asList(storageRoot.split("/")).contains(".")
                        && !storageRoot.contains("\\"),
                "Cloud storage must stay inside the /var/data permanent disk mount.");
        require((storageRoot + "/answer-sheets").equals(environment.getProperty("app.v3.answer-sheets.storage-directory"))
                        && (storageRoot + "/scan-evidence").equals(environment.getProperty("app.v3.scan-evidence.storage-directory")),
                "Cloud answer sheets and scan evidence must use the configured permanent storage directories.");
        require("smtp".equals(environment.getProperty("app.v3.auth.email-delivery-mode")),
                "Cloud teacher verification requires configured email delivery, never log-only codes.");
        required(environment, "spring.mail.host");
        required(environment, "spring.mail.username");
        required(environment, "spring.mail.password");
        required(environment, "app.v3.auth.email-from-address");
        try {
            require(Base64.getDecoder().decode(required(environment, "app.v3.auth.mfa.encryption-key")).length == 32,
                    "Cloud MFA requires a stable base64-encoded 32-byte encryption key.");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Cloud MFA encryption key must be valid base64.");
        }
    }

    static String httpsOrigin(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(java.util.Locale.ROOT);
            require("https".equals(uri.getScheme()) && uri.getHost() != null
                            && uri.getHost().contains(".") && !uri.getHost().matches("[0-9.]+")
                            && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                            && (uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                            && !value.contains("*") && uri.getPort() != 0 && uri.getPort() <= 65535,
                    "Cloud URLs must be exact public HTTPS origins without paths or wildcards.");
            require(!host.equals("localhost.localdomain") && !host.endsWith(".localhost")
                            && !host.endsWith(".local") && !host.endsWith(".localdomain"),
                    "Cloud URLs cannot use local hostnames.");
            return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Cloud HTTPS origin is invalid.");
        }
    }

    private static String required(Environment environment, String name) {
        String value = environment.getProperty(name);
        require(value != null && !value.isBlank() && !value.contains("${"), "Missing cloud setting: " + name);
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(Environment environment) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(httpsOrigin(required(environment, "app.v3.cloud.frontend-origin"))));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        cors.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
