package com.capstone.assessment.v3.auth.security;

import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.service.V3AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Profile("v3")
@Component
public class V3AuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v3/auth/login",
            "/api/v3/auth/teacher-registration/reference-data",
            "/api/v3/auth/register-teacher",
            "/api/v3/auth/verify-teacher-email",
            "/api/v3/auth/resend-teacher-verification",
            "/api/v3/auth/mfa/login/verify",
            "/api/v3/system/readiness",
            "/api/v3/system/mobile-release-readiness"
    );

    private final V3AuthService authService;
    private final V3AuthenticationEntryPoint authenticationEntryPoint;

    public V3AuthenticationFilter(
            V3AuthService authService,
            V3AuthenticationEntryPoint authenticationEntryPoint
    ) {
        this.authService = authService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || PUBLIC_PATHS.contains(requestUri)
                || !requestUri.startsWith("/api/v3/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null
                || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            authenticationEntryPoint.commence(request, response, null);
            return;
        }

        String rawToken = authorization.substring(BEARER_PREFIX.length()).trim();
        Optional<V3AuthenticatedUser> authenticatedUser = authService.authenticate(rawToken);
        if (authenticatedUser.isEmpty()) {
            authenticationEntryPoint.commence(request, response, null);
            return;
        }

        V3AuthenticatedUser principal = authenticatedUser.get();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().toUpperCase(Locale.ROOT)))
        );
        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
