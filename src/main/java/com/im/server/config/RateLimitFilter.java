package com.im.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.server.common.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 在 Security 链之前执行，按客户端 IP 分桶限流（固定窗口，约 1 分钟）。
 * 同一 NAT 下多用户共享额度；若需按账号限流可后续增加 JWT 解析或后置 Filter。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!properties.isEnabled() || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = clientIp(request);
        int limit;
        String bucket;
        if (isAuthHeavy(uri)) {
            limit = Math.max(5, properties.getAuthIpPerMinute());
            bucket = "rl:auth:" + ip + ":" + minuteBucket();
        } else if (uri.contains("/messages/search")) {
            limit = Math.max(5, properties.getSearchPerMinute());
            bucket = "rl:search:" + ip + ":" + minuteBucket();
        } else {
            limit = Math.max(30, properties.getGeneralPerMinute());
            bucket = "rl:api:" + ip + ":" + minuteBucket();
        }

        try {
            Long n = stringRedisTemplate.opsForValue().increment(bucket);
            if (n != null && n == 1L) {
                stringRedisTemplate.expire(bucket, 2, TimeUnit.MINUTES);
            }
            if (n != null && n > limit) {
                response.setStatus(429);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                String body = objectMapper.writeValueAsString(
                    new ApiResponse<>(429, "请求过于频繁，请稍后再试", null));
                response.getWriter().write(body);
                return;
            }
        } catch (Exception e) {
            // Redis 不可用时放行，避免整站不可用
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isAuthHeavy(String uri) {
        return uri.startsWith("/api/auth/login")
            || uri.startsWith("/api/auth/register")
            || uri.startsWith("/api/auth/send-code")
            || uri.startsWith("/api/auth/refresh")
            || uri.startsWith("/api/auth/reset-password")
            || uri.startsWith("/api/auth/oauth/login");
    }

    private static long minuteBucket() {
        return System.currentTimeMillis() / 60_000L;
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
