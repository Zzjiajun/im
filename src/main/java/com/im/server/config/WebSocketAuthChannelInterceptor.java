package com.im.server.config;

import com.im.server.common.BusinessException;
import com.im.server.security.JwtTokenProvider;
import com.im.server.security.LoginUser;
import java.security.Principal;
import java.util.List;
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

    private final JwtTokenProvider jwtTokenProvider;

    public WebSocketAuthChannelInterceptor(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            List<String> authorizationHeaders = accessor.getNativeHeader("Authorization");
            if (authorizationHeaders == null || authorizationHeaders.isEmpty()) {
                throw new BusinessException("WebSocket 缺少 Authorization");
            }
            String authorization = authorizationHeaders.get(0);
            if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
                throw new BusinessException("WebSocket token 格式错误");
            }
            LoginUser loginUser = jwtTokenProvider.parseToken(authorization.substring(7));
            Principal principal = new UsernamePasswordAuthenticationToken(
                String.valueOf(loginUser.getUserId()),
                null,
                AuthorityUtils.NO_AUTHORITIES
            );
            accessor.setUser(principal);
        }
        return message;
    }
}
