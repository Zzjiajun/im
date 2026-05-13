package com.im.server.common;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ES 搜索断路器 —— 滑动窗口计数。
 * <p>
 * 连续失败达到阈值后进入 OPEN 状态，直接降级到 MySQL LIKE 搜索；
 * 经过冷却时间后进入 HALF_OPEN 状态，放行一个探测请求；
 * 探测成功则 CLOSED，失败则回到 OPEN。
 * <p>
 * 面试点：断路器模式 + 滑动窗口计数 + 状态机，比 try-catch 降级更健壮，
 * 避免 ES 恢复后频繁重试对集群造成压力。
 * </p>
 */
@Slf4j
@Component
public class EsCircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    @Value("${app.elasticsearch.circuit-breaker.failure-threshold:5}")
    private int failureThreshold;

    @Value("${app.elasticsearch.circuit-breaker.cooldown-millis:30000}")
    private long cooldownMillis;

    /** HALF_OPEN 探测请求是否已放行，避免并发探测 */
    private final AtomicInteger halfOpenPermit = new AtomicInteger(0);

    /** 上一次切换到 OPEN 的时间戳 */
    private volatile long lastOpenTimestamp = 0;

    @PostConstruct
    void init() {
        log.info("[EsCircuitBreaker] threshold={} cooldown={}ms", failureThreshold, cooldownMillis);
    }

    /**
     * 判断当前是否允许调用 ES。
     */
    public boolean tryAcquire() {
        State s = state.get();
        if (s == State.CLOSED) {
            return true;
        }
        if (s == State.OPEN) {
            if (System.currentTimeMillis() - lastOpenTimestamp >= cooldownMillis) {
                // 冷却到期 → HALF_OPEN，尝试放行一个探测请求
                if (halfOpenPermit.compareAndSet(0, 1)) {
                    state.compareAndSet(State.OPEN, State.HALF_OPEN);
                    log.info("[EsCircuitBreaker] OPEN→HALF_OPEN, probe request permitted");
                    return true;
                }
            }
            return false;
        }
        // HALF_OPEN：只放行一个探测
        if (halfOpenPermit.compareAndSet(0, 1)) {
            return true;
        }
        return false;
    }

    /**
     * 调用成功：重置计数器或关闭 HALF_OPEN。
     */
    public void onSuccess() {
        consecutiveFailures.set(0);
        if (state.get() == State.HALF_OPEN) {
            state.compareAndSet(State.HALF_OPEN, State.CLOSED);
            halfOpenPermit.set(0);
            log.info("[EsCircuitBreaker] HALF_OPEN→CLOSED, ES recovered");
        }
    }

    /**
     * 调用失败：累加计数器，达到阈值则 OPEN。
     */
    public void onFailure() {
        halfOpenPermit.set(0);
        int fail = consecutiveFailures.incrementAndGet();
        if (fail >= failureThreshold) {
            State prev = state.getAndSet(State.OPEN);
            lastOpenTimestamp = System.currentTimeMillis();
            halfOpenPermit.set(0);
            if (prev != State.OPEN) {
                log.warn("[EsCircuitBreaker] CLOSED→OPEN, {} consecutive failures", fail);
            }
        }
        if (state.get() == State.HALF_OPEN) {
            // 探测失败回到 OPEN
            state.set(State.OPEN);
            lastOpenTimestamp = System.currentTimeMillis();
            log.warn("[EsCircuitBreaker] HALF_OPEN→OPEN, probe failed");
        }
    }

    /** 暴露当前状态（监控 / Actuator 用） */
    public State getState() {
        return state.get();
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public void reset() {
        state.set(State.CLOSED);
        consecutiveFailures.set(0);
        halfOpenPermit.set(0);
        lastOpenTimestamp = 0;
        log.info("[EsCircuitBreaker] manually reset to CLOSED");
    }
}
