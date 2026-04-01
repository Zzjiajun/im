package com.im.server.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            try {
                LoginUser loginUser = jwtTokenProvider.parseToken(token);
                String[] roles = loginUser.isAdmin() ? new String[] {"ROLE_USER", "ROLE_ADMIN"} : new String[] {"ROLE_USER"};
                UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(loginUser, token, AuthorityUtils.createAuthorityList(roles));
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            } catch (JwtException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        List<String> whiteList = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/send-code",
            "/api/auth/reset-password",
            "/api/auth/refresh",
            "/api/auth/oauth/login",
            "/swagger-ui",
            "/swagger-ui.html",
            "/v3/api-docs",
            "/error",
            "/ws-chat"
        );
        return whiteList.stream().anyMatch(path::startsWith);
    }
}
