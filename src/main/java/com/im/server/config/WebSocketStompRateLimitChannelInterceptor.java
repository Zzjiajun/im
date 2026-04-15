package com.im.server.config;

import com.im.server.common.BusinessException;
import java.security.Principal;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * 对 STOMP 业务入站（/app/chat.*）按用户限频，避免绕过 HTTP /api 限流刷消息。
 */
@Component
@RequiredArgsConstructor
public class WebSocketStompRateLimitChannelInterceptor implements ChannelInterceptor {

    private final WebSocketProperties webSocketProperties;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.MESSAGE.equals(accessor.getCommand())) {
            return message;
        }
        WebSocketProperties.RateLimit rl = webSocketProperties.getRateLimit();
        if (!rl.isEnabled()) {
            return message;
        }
        Principal principal = accessor.getUser();
        if (principal == null) {
            return message;
        }
        String dest = accessor.getDestination();
        if (dest == null) {
            return message;
        }
        int limit;
        String op;
        if (dest.endsWith("/chat.send")) {
            limit = Math.max(1, rl.getSendPerMinute());
            op = "send";
        } else if (dest.endsWith("/chat.typing")) {
            limit = Math.max(1, rl.getTypingPerMinute());
            op = "typing";
        } else if (dest.endsWith("/chat.deliver")) {
            limit = Math.max(1, rl.getDeliverPerMinute());
            op = "deliver";
        } else {
            return message;
        }
        String userId = principal.getName();
        String bucket = "im:ws:rl:" + op + ":" + userId + ":" + (System.currentTimeMillis() / 60_000L);
        try {
            Long n = stringRedisTemplate.opsForValue().increment(bucket);
            if (n != null && n == 1L) {
                stringRedisTemplate.expire(bucket, 2, TimeUnit.MINUTES);
            }
            if (n != null && n > limit) {
                throw new BusinessException("操作过于频繁，请稍后再试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception ignored) {
            // Redis 不可用时放行，与 HTTP RateLimitFilter 一致
        }
        return message;
    }
}
