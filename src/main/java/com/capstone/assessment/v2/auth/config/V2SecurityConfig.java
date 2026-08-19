package com.capstone.assessment.v2.auth.config;

import com.capstone.assessment.v2.auth.security.V2AccessDeniedHandler;
import com.capstone.assessment.v2.auth.security.V2AuthenticationEntryPoint;
import com.capstone.assessment.v2.auth.security.V2AuthenticationFilter;
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

@Profile("v2")
@Configuration
@EnableConfigurationProperties(V2AuthProperties.class)
public class V2SecurityConfig {

    @Bean
    public PasswordEncoder v2PasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain v2SecurityFilterChain(
            HttpSecurity http,
            V2AuthenticationFilter authenticationFilter,
            V2AuthenticationEntryPoint authenticationEntryPoint,
            V2AccessDeniedHandler accessDeniedHandler
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
                        .requestMatchers(HttpMethod.GET, "/api/v2/auth/teacher-registration/reference-data").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v2/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v2/auth/register-teacher").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/import/sf1/preview").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/import/sf1/confirm").permitAll()
                        .requestMatchers("/api/v2/users/**").hasRole("PRINCIPAL")
                        .requestMatchers("/api/v2/school-setup/**").hasRole("PRINCIPAL")
                        .requestMatchers("/api/v2/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
