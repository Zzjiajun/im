package com.im.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.im.server.common.BusinessException;
import com.im.server.common.TokenHashUtil;
import com.im.server.mapper.UserMapper;
import com.im.server.mapper.UserSessionMapper;
import com.im.server.model.entity.User;
import com.im.server.model.entity.UserSession;
import com.im.server.security.JwtProperties;
import com.im.server.security.JwtTokenProvider;
import com.im.server.security.LoginUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserSessionService {

    private final UserSessionMapper userSessionMapper;
    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    @Transactional
    public SessionTokens createSession(Long userId, String deviceId, String deviceName) {
        String refreshPlain = UUID.randomUUID().toString();
        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setRefreshTokenHash(TokenHashUtil.sha256Hex(refreshPlain));
        session.setDeviceId(deviceId);
        session.setDeviceName(deviceName);
        session.setRevoked(0);
        LocalDateTime now = LocalDateTime.now();
        session.setCreatedAt(now);
        session.setLastActiveAt(now);
        userSessionMapper.insert(session);
        return new SessionTokens(refreshPlain, session.getId(), userId);
    }

    @Transactional
    public SessionTokens refreshAccess(String refreshTokenPlain) {
        if (refreshTokenPlain == null || refreshTokenPlain.isBlank()) {
            throw new BusinessException("refreshToken 无效");
        }
        String hash = TokenHashUtil.sha256Hex(refreshTokenPlain.trim());
        UserSession session = userSessionMapper.selectOne(
            new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getRefreshTokenHash, hash)
        );
        if (session == null || Integer.valueOf(1).equals(session.getRevoked())) {
            throw new BusinessException("refreshToken 无效或已失效");
        }
        User sessionUser = userMapper.selectById(session.getUserId());
        if (sessionUser == null || sessionUser.getStatus() == null || sessionUser.getStatus() != 1) {
            throw new BusinessException("账号已禁用");
        }
        long refreshSeconds = jwtProperties.getRefreshTokenExpireSeconds() == null
            ? 2592000L : jwtProperties.getRefreshTokenExpireSeconds();
        if (session.getCreatedAt().plusSeconds(refreshSeconds).isBefore(LocalDateTime.now())) {
            throw new BusinessException("refreshToken 已过期");
        }
        session.setRevoked(1);
        userSessionMapper.updateById(session);
        return createSession(session.getUserId(), session.getDeviceId(), session.getDeviceName());
    }

    public String accessTokenForUserId(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("账号已禁用");
        }
        boolean admin = user.getAdmin() != null && user.getAdmin() == 1;
        return jwtTokenProvider.createToken(new LoginUser(user.getId(), user.getNickname(), admin));
    }

    @Transactional
    public void revokeByRefreshToken(String refreshTokenPlain) {
        if (refreshTokenPlain == null || refreshTokenPlain.isBlank()) {
            return;
        }
        String hash = TokenHashUtil.sha256Hex(refreshTokenPlain.trim());
        UserSession session = userSessionMapper.selectOne(
            new LambdaQueryWrapper<UserSession>().eq(UserSession::getRefreshTokenHash, hash)
        );
        if (session != null) {
            session.setRevoked(1);
            userSessionMapper.updateById(session);
        }
    }

    @Transactional
    public void revokeSession(Long userId, Long sessionId) {
        UserSession session = userSessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException("会话不存在");
        }
        session.setRevoked(1);
        userSessionMapper.updateById(session);
    }

    @Transactional
    public void revokeAllSessions(Long userId) {
        List<UserSession> list = userSessionMapper.selectList(
            new LambdaQueryWrapper<UserSession>().eq(UserSession::getUserId, userId)
        );
        for (UserSession s : list) {
            s.setRevoked(1);
            userSessionMapper.updateById(s);
        }
    }

    public List<com.im.server.model.vo.UserSessionVO> listSessions(Long userId) {
        return userSessionMapper.selectList(
            new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getUserId, userId)
                .orderByDesc(UserSession::getLastActiveAt)
        ).stream()
            .map(s -> com.im.server.model.vo.UserSessionVO.builder()
                .sessionId(s.getId())
                .deviceId(s.getDeviceId())
                .deviceName(s.getDeviceName())
                .createdAt(s.getCreatedAt())
                .lastActiveAt(s.getLastActiveAt())
                .revoked(Integer.valueOf(1).equals(s.getRevoked()))
                .build())
            .collect(Collectors.toList());
    }

    public record SessionTokens(String refreshTokenPlain, Long sessionId, Long userId) {
    }
}
