package com.im.server.config;

import com.im.server.common.BusinessException;
import com.im.server.security.JwtTokenProvider;
import com.im.server.security.LoginUser;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    /** Session 属性中存放原始 JWT 的 key */
    private static final String ATTR_TOKEN = "ws.jwt.token";

    private final JwtTokenProvider jwtTokenProvider;

    public WebSocketAuthChannelInterceptor(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            String token = extractToken(accessor);
            LoginUser loginUser = jwtTokenProvider.parseToken(token);
            Principal principal = new UsernamePasswordAuthenticationToken(
                String.valueOf(loginUser.getUserId()),
                null,
                AuthorityUtils.NO_AUTHORITIES
            );
            accessor.setUser(principal);
            // 保存原始 JWT 到 session 属性，后续 SEND 帧需重新校验过期
            Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
            if (sessionAttrs != null) {
                sessionAttrs.put(ATTR_TOKEN, token);
            }
        }
        if ((StompCommand.SEND.equals(command) || StompCommand.SUBSCRIBE.equals(command))
            && accessor.getUser() == null) {
            throw new BusinessException("WebSocket 未认证");
        }
        // SEND 帧重新校验 JWT 是否过期（长连接场景 token 可能已过期）
        if (StompCommand.SEND.equals(command)) {
            String storedToken = getStoredToken(accessor);
            if (storedToken == null) {
                throw new BusinessException("WebSocket 未认证");
            }
            try {
                jwtTokenProvider.parseToken(storedToken);
            } catch (Exception e) {
                throw new BusinessException("登录已过期，请重新连接");
            }
        }
        if (StompCommand.SUBSCRIBE.equals(command)) {
            assertAllowedSubscription(accessor.getDestination());
        }
        return message;
    }

    private static String extractToken(StompHeaderAccessor accessor) {
        List<String> authorizationHeaders = accessor.getNativeHeader("Authorization");
        if (authorizationHeaders == null || authorizationHeaders.isEmpty()) {
            throw new BusinessException("WebSocket 缺少 Authorization");
        }
        String authorization = authorizationHeaders.get(0);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BusinessException("WebSocket token 格式错误");
        }
        return authorization.substring(7);
    }

    private static String getStoredToken(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
        if (sessionAttrs == null) {
            return null;
        }
        Object token = sessionAttrs.get(ATTR_TOKEN);
        return token instanceof String s ? s : null;
    }

    private void assertAllowedSubscription(String destination) {
        if ("/user/queue/messages".equals(destination)) {
            return;
        }
        throw new BusinessException("WebSocket 订阅目标不允许");
    }
}
