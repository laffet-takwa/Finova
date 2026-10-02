package com.finova.common.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Resolves the caller from the {@code Authorization: Bearer} access token.
 * <p>
 * An invalid token is intentionally ignored here (the request continues
 * unauthenticated) and rejected by the authorisation rules, which keeps public
 * endpoints such as login and registration reachable.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            try {
                Claims claims = tokenProvider.parse(token);
                if (JwtTokenProvider.TYPE_ACCESS.equals(claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class))) {
                    AuthenticatedUser principal = new AuthenticatedUser(
                            claims.getSubject(),
                            claims.get(JwtTokenProvider.CLAIM_EMAIL, String.class),
                            claims.get(JwtTokenProvider.CLAIM_ROLE, String.class),
                            correlationId(request));
                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority(principal.authority())));
                    authentication.setDetails(request.getRemoteAddr());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(com.finova.common.web.CorrelationId.REQUEST_ATTRIBUTE);
        return value == null ? UUID.randomUUID().toString() : value.toString();
    }
}
