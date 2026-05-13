package com.im.server.config;

import com.im.server.common.EsCircuitBreaker;
import com.im.server.service.WebSocketSessionRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

/**
 * 注册自定义 Micrometer 指标：断路器状态、WS 连接数等。
 * <p>
 * 指标自动暴露到 /actuator/prometheus。
 */
@Configuration
@RequiredArgsConstructor
public class MetricsConfiguration {

    private final EsCircuitBreaker esCircuitBreaker;
    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    void registerMetrics() {
        // ES 断路器状态（0=CLOSED, 1=OPEN, 2=HALF_OPEN）
        Gauge.builder("im.es.circuitbreaker.state", () -> {
            EsCircuitBreaker.State s = esCircuitBreaker.getState();
            if (s == EsCircuitBreaker.State.CLOSED) return 0;
            if (s == EsCircuitBreaker.State.OPEN) return 1;
            return 2;
        }).description("ES circuit breaker state: 0=CLOSED, 1=OPEN, 2=HALF_OPEN")
            .register(meterRegistry);

        // ES 断路器连续失败次数
        Gauge.builder("im.es.circuitbreaker.failures", esCircuitBreaker,
                EsCircuitBreaker::getConsecutiveFailures)
            .description("ES circuit breaker consecutive failures")
            .register(meterRegistry);

        // WebSocket 活跃连接数（总会话数）
        Gauge.builder("im.ws.connections", webSocketSessionRegistry,
                WebSocketSessionRegistry::getActiveConnectionCount)
            .description("Active WebSocket connections")
            .register(meterRegistry);

        // WebSocket 在线用户数
        Gauge.builder("im.ws.online_users", webSocketSessionRegistry,
                WebSocketSessionRegistry::getOnlineUserCount)
            .description("Unique users with active WebSocket sessions")
            .register(meterRegistry);
    }
}
