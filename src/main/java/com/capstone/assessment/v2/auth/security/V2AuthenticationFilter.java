package com.capstone.assessment.v2.auth.security;

import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import com.capstone.assessment.v2.auth.service.V2AuthService;
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

@Profile("v2")
@Component
public class V2AuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final V2AuthService authService;
    private final V2AuthenticationEntryPoint authenticationEntryPoint;

    public V2AuthenticationFilter(
            V2AuthService authService,
            V2AuthenticationEntryPoint authenticationEntryPoint
    ) {
        this.authService = authService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || "/api/v2/auth/login".equals(requestUri)
                || "/api/v2/auth/teacher-registration/reference-data".equals(requestUri)
                || "/api/v2/auth/register-teacher".equals(requestUri)
                || "/api/v2/auth/verify-teacher-email".equals(requestUri)
                || "/api/v2/auth/resend-teacher-verification".equals(requestUri)
                || !requestUri.startsWith("/api/v2/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            authenticationEntryPoint.commence(request, response, null);
            return;
        }

        String rawToken = authorization.substring(BEARER_PREFIX.length()).trim();
        Optional<V2AuthenticatedUser> authenticatedUser = authService.authenticate(rawToken);
        if (authenticatedUser.isEmpty()) {
            authenticationEntryPoint.commence(request, response, null);
            return;
        }

        V2AuthenticatedUser principal = authenticatedUser.get();
        String authority = "ROLE_" + principal.role().toUpperCase(Locale.ROOT);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(authority))
        );

        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
