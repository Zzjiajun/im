package com.im.server.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 记录 /api 请求方法与路径，便于对照前端操作与后端入口（HTTP 状态多为 200，业务错误见 body.code）。
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiAccessLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        long t0 = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            String uri = request.getRequestURI();
            if (uri != null && uri.startsWith("/api/")) {
                log.info("[api] {} {} -> {} ({} ms)", request.getMethod(), uri, response.getStatus(),
                    System.currentTimeMillis() - t0);
            }
        }
    }
}
