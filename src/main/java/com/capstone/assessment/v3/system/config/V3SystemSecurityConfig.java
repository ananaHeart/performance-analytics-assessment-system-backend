package com.capstone.assessment.v3.system.config;

import com.capstone.assessment.v3.auth.config.V3AuthProperties;
import com.capstone.assessment.v3.auth.config.V3MfaProperties;
import com.capstone.assessment.v3.auth.security.V3AccessDeniedHandler;
import com.capstone.assessment.v3.auth.security.V3AuthenticationEntryPoint;
import com.capstone.assessment.v3.auth.security.V3AuthenticationFilter;
import com.capstone.assessment.v3.mobile.config.V3MobileReleaseProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.scheduling.annotation.EnableScheduling;

@Profile("v3")
@Configuration
@EnableScheduling
@EnableConfigurationProperties({V3BaselineProperties.class, V3AuthProperties.class, V3MfaProperties.class,
        V3MobileReleaseProperties.class})
public class V3SystemSecurityConfig {

    @Bean
    public PasswordEncoder v3PasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain v3SystemSecurityFilterChain(
            HttpSecurity http,
            V3AuthenticationFilter authenticationFilter,
            V3AuthenticationEntryPoint authenticationEntryPoint,
            V3AccessDeniedHandler accessDeniedHandler,
            V3MobileReleaseProperties mobileRelease
    ) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v3/system/readiness").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v3/system/mobile-release-readiness").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v3/auth/teacher-registration/reference-data").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v3/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v3/auth/register-teacher").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v3/auth/verify-teacher-email").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v3/auth/resend-teacher-verification").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v3/auth/mfa/login/verify").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v3/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v3/auth/logout").authenticated()
                        .requestMatchers("/api/v3/auth/mfa/**").hasAnyRole("PRINCIPAL", "TEACHER")
                        .requestMatchers("/api/v3/notifications/**").hasAnyRole("PRINCIPAL", "TEACHER")
                        .requestMatchers("/api/v3/reports/**").hasAnyRole("PRINCIPAL", "TEACHER")
                        .requestMatchers(
                                "/api/v3/users/teachers",
                                "/api/v3/users/teachers/**"
                        ).hasRole("PRINCIPAL")
                        .requestMatchers("/api/v3/school-setup/**").hasRole("PRINCIPAL")
                        .requestMatchers("/api/v3/import/sf1/**").hasRole("PRINCIPAL")
                        .requestMatchers(HttpMethod.GET, "/api/v3/teacher/classes/*/students")
                            .hasRole("TEACHER")
                        .requestMatchers("/api/v3/teacher/class-assignments/*/schedules/**")
                            .hasRole("TEACHER")
                        .requestMatchers("/api/v3/assessments/**").hasRole("TEACHER")
                        .requestMatchers(HttpMethod.POST, "/api/v3/scoring/results/*/finalize")
                            .access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(
                                    mobileRelease.isWriteApiEnabled() && authentication.get().getAuthorities().stream()
                                            .anyMatch(authority -> "ROLE_TEACHER".equals(authority.getAuthority()))))
                        .requestMatchers(HttpMethod.GET, "/api/v3/mobile/results/*/supersession")
                            .access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(
                                    mobileRelease.isWriteApiEnabled() && authentication.get().getAuthorities().stream()
                                            .anyMatch(authority -> "ROLE_TEACHER".equals(authority.getAuthority()))))
                        .requestMatchers(HttpMethod.GET, "/api/v3/mobile/**").hasRole("TEACHER")
                        .requestMatchers(HttpMethod.POST,
                                "/api/v3/mobile/scan-pages",
                                "/api/v3/mobile/scan-pages/*/detections",
                                "/api/v3/mobile/verification-batches",
                                "/api/v3/mobile/attachments",
                                "/api/v3/mobile/results/*/reopen",
                                "/api/v3/mobile/results/*/corrections",
                                "/api/v3/mobile/results/*/supersede")
                            .access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(
                                    mobileRelease.isWriteApiEnabled() && authentication.get().getAuthorities().stream()
                                            .anyMatch(authority -> "ROLE_TEACHER".equals(authority.getAuthority()))))
                        .requestMatchers(HttpMethod.GET, "/api/v3/answer-sheets/reference-data")
                            .hasRole("TEACHER")
                        .requestMatchers(HttpMethod.GET, "/api/v3/test-assignments/*/answer-sheet-eligibility")
                            .hasRole("TEACHER")
                        .requestMatchers(HttpMethod.POST, "/api/v3/test-assignments/*/answer-sheet-versions")
                            .hasRole("TEACHER")
                        .requestMatchers(HttpMethod.GET, "/api/v3/answer-sheet-versions/**")
                            .hasRole("TEACHER")
                        .anyRequest().denyAll())
                .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
