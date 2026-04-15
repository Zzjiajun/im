package com.im.server.service;

import com.im.server.agora.token.RtcTokenBuilder2;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.server.common.BusinessException;
import com.im.server.config.AgoraProperties;
import com.im.server.model.entity.Conversation;
import com.im.server.model.enums.ConversationType;
import com.im.server.model.enums.VoiceCallStatus;
import com.im.server.model.vo.AgoraRtcTokenVO;
import com.im.server.model.vo.UserSimpleVO;
import com.im.server.model.vo.VoiceCallVO;
import com.im.server.model.vo.WsEvent;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class VoiceCallService {

    private final AgoraProperties agoraProperties;
    private final ConversationService conversationService;
    private final UserService userService;
    private final OnlineStatusService onlineStatusService;
    private final WsPushService wsPushService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public VoiceCallService(
        AgoraProperties agoraProperties,
        ConversationService conversationService,
        UserService userService,
        OnlineStatusService onlineStatusService,
        WsPushService wsPushService,
        StringRedisTemplate stringRedisTemplate,
        ObjectMapper objectMapper
    ) {
        this.agoraProperties = agoraProperties;
        this.conversationService = conversationService;
        this.userService = userService;
        this.onlineStatusService = onlineStatusService;
        this.wsPushService = wsPushService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public VoiceCallVO start(Long callerUserId, Long conversationId) {
        assertAgoraEnabled();
        conversationService.assertUserInConversation(callerUserId, conversationId);
        Conversation conversation = conversationService.getById(conversationId);
        if (!ConversationType.SINGLE.name().equals(conversation.getType())) {
            throw new BusinessException("当前仅支持单聊语音通话");
        }
        Long calleeUserId = userService.findOtherUser(callerUserId, conversationService.listMemberIds(conversationId)).getUserId();
        if (calleeUserId == null) {
            throw new BusinessException("通话对象不存在");
        }
        if (!onlineStatusService.isOnline(calleeUserId)) {
            throw new BusinessException("对方当前不在线");
        }
        assertUserNotInActiveCall(callerUserId);
        assertUserNotInActiveCall(calleeUserId);

        String callId = UUID.randomUUID().toString().replace("-", "");
        String channelName = "call-" + callId;

        CallSession session = new CallSession();
        session.callId = callId;
        session.conversationId = conversationId;
        session.channelName = channelName;
        session.callerUserId = callerUserId;
        session.calleeUserId = calleeUserId;
        session.status = VoiceCallStatus.RINGING.name();
        session.createdAt = LocalDateTime.now();

        saveSession(session);
        bindUserToCall(callerUserId, callId);
        bindUserToCall(calleeUserId, callId);

        VoiceCallVO vo = toVO(session);
        logCallEvent("start", session, callerUserId, "发起语音通话");
        wsPushService.pushToUser(calleeUserId, new WsEvent<>("CALL_INVITE", vo));
        return vo;
    }

    public VoiceCallVO current(Long userId) {
        String callId = stringRedisTemplate.opsForValue().get(userCallKey(userId));
        if (callId == null || callId.isBlank()) {
            return null;
        }
        CallSession session = loadSession(callId);
        if (session == null) {
            stringRedisTemplate.delete(userCallKey(userId));
            return null;
        }
        try {
            session = ensureNotTimedOut(session);
        } catch (BusinessException e) {
            if ("通话已超时".equals(e.getMessage())) {
                return null;
            }
            throw e;
        }
        if (VoiceCallStatus.ENDED.name().equals(session.status) || VoiceCallStatus.REJECTED.name().equals(session.status)) {
            clearIndexes(session);
            return null;
        }
        if (!userId.equals(session.callerUserId) && !userId.equals(session.calleeUserId)) {
            stringRedisTemplate.delete(userCallKey(userId));
            return null;
        }
        return toVO(session);
    }

    public VoiceCallVO accept(Long userId, String callId) {
        CallSession session = getParticipatingCall(userId, callId);
        if (!userId.equals(session.calleeUserId)) {
            throw new BusinessException("只有被叫方可以接听");
        }
        if (!VoiceCallStatus.RINGING.name().equals(session.status)) {
            throw new BusinessException("当前通话已不是振铃状态");
        }
        session.status = VoiceCallStatus.ACCEPTED.name();
        session.answeredAt = LocalDateTime.now();
        saveSession(session);
        VoiceCallVO vo = toVO(session);
        logCallEvent("accept", session, userId, "被叫接听");
        wsPushService.pushToUsers(Set.of(session.callerUserId, session.calleeUserId), new WsEvent<>("CALL_ACCEPTED", vo));
        return vo;
    }

    public VoiceCallVO reject(Long userId, String callId) {
        CallSession session = getParticipatingCall(userId, callId);
        if (!userId.equals(session.calleeUserId)) {
            throw new BusinessException("只有被叫方可以拒接");
        }
        if (!VoiceCallStatus.RINGING.name().equals(session.status)) {
            throw new BusinessException("当前通话已不是振铃状态");
        }
        session.status = VoiceCallStatus.REJECTED.name();
        session.reason = "REJECTED";
        session.endedAt = LocalDateTime.now();
        saveSession(session);
        VoiceCallVO vo = toVO(session);
        appendCallRecord(session);
        logCallEvent("reject", session, userId, "被叫拒接");
        wsPushService.pushToUsers(Set.of(session.callerUserId, session.calleeUserId), new WsEvent<>("CALL_REJECTED", vo));
        clearIndexes(session);
        return vo;
    }

    public VoiceCallVO end(Long userId, String callId) {
        CallSession session = getParticipatingCall(userId, callId);
        if (VoiceCallStatus.ENDED.name().equals(session.status) || VoiceCallStatus.REJECTED.name().equals(session.status)) {
            return toVO(session);
        }
        boolean wasRinging = VoiceCallStatus.RINGING.name().equals(session.status);
        session.status = VoiceCallStatus.ENDED.name();
        session.reason = wasRinging ? "CANCELLED" : "HANGUP";
        session.endedAt = LocalDateTime.now();
        saveSession(session);
        VoiceCallVO vo = toVO(session);
        appendCallRecord(session);
        logCallEvent(
            "end",
            session,
            userId,
            wasRinging ? "振铃阶段取消通话" : "已接通后挂断通话"
        );
        wsPushService.pushToUsers(Set.of(session.callerUserId, session.calleeUserId), new WsEvent<>("CALL_ENDED", vo));
        clearIndexes(session);
        return vo;
    }

    public AgoraRtcTokenVO token(Long userId, String callId) {
        assertAgoraEnabled();
        CallSession session = getParticipatingCall(userId, callId);
        if (!VoiceCallStatus.ACCEPTED.name().equals(session.status)) {
            throw new BusinessException("当前通话尚未接通");
        }
        int expire = Math.max(60, agoraProperties.getRtcTokenExpireSeconds());
        String account = String.valueOf(userId);
        String token = new RtcTokenBuilder2().buildTokenWithUserAccount(
            agoraProperties.getAppId(),
            agoraProperties.getAppCertificate(),
            session.channelName,
            account,
            RtcTokenBuilder2.Role.ROLE_PUBLISHER,
            expire,
            expire
        );
        if (token == null || token.isBlank()) {
            throw new BusinessException("Agora Token 生成失败，请检查 appId/appCertificate");
        }
        AgoraRtcTokenVO result = AgoraRtcTokenVO.builder()
            .appId(agoraProperties.getAppId())
            .channelName(session.channelName)
            .uid(account)
            .token(token)
            .expiresInSeconds(expire)
            .build();
        logCallEvent("token", session, userId, "下发 Agora RTC Token");
        return result;
    }

    private void assertAgoraEnabled() {
        if (!agoraProperties.isEnabled()) {
            throw new BusinessException("语音通话未启用，请先配置 Agora");
        }
        boolean appIdMissing = agoraProperties.getAppId() == null || agoraProperties.getAppId().isBlank();
        boolean appCertificateMissing = agoraProperties.getAppCertificate() == null || agoraProperties.getAppCertificate().isBlank();
        if (appIdMissing || appCertificateMissing) {
            throw new BusinessException(
                "Agora 配置不完整："
                    + (appIdMissing ? "缺少 appId " : "")
                    + (appCertificateMissing ? "缺少 appCertificate" : "")
            );
        }
    }

    private CallSession getParticipatingCall(Long userId, String callId) {
        CallSession session = loadSession(callId);
        if (session == null) {
            throw new BusinessException("通话不存在");
        }
        session = ensureNotTimedOut(session);
        if (!userId.equals(session.callerUserId) && !userId.equals(session.calleeUserId)) {
            throw new BusinessException("无权操作该通话");
        }
        return session;
    }

    private VoiceCallVO toVO(CallSession session) {
        UserSimpleVO caller = userService.getSimpleUser(session.callerUserId);
        UserSimpleVO callee = userService.getSimpleUser(session.calleeUserId);
        return VoiceCallVO.builder()
            .callId(session.callId)
            .conversationId(session.conversationId)
            .channelName(session.channelName)
            .callerUserId(session.callerUserId)
            .callerNickname(caller.getNickname())
            .callerAvatar(caller.getAvatar())
            .calleeUserId(session.calleeUserId)
            .calleeNickname(callee.getNickname())
            .calleeAvatar(callee.getAvatar())
            .status(session.status)
            .reason(session.reason)
            .createdAt(session.createdAt)
            .answeredAt(session.answeredAt)
            .endedAt(session.endedAt)
            .build();
    }

    private void appendCallRecord(CallSession session) {
        String content = buildCallRecordText(session);
        if (content == null || content.isBlank()) {
            return;
        }
        Long operatorId = session.callerUserId != null ? session.callerUserId : session.calleeUserId;
        conversationService.appendSystemMessage(operatorId, session.conversationId, content);
    }

    private String buildCallRecordText(CallSession session) {
        if (VoiceCallStatus.REJECTED.name().equals(session.status)) {
            return "语音通话：已拒绝";
        }
        if (!VoiceCallStatus.ENDED.name().equals(session.status)) {
            return null;
        }
        if (session.answeredAt != null && session.endedAt != null) {
            long seconds = Math.max(0, java.time.Duration.between(session.answeredAt, session.endedAt).getSeconds());
            return "语音通话 " + formatDuration(seconds);
        }
        return switch (String.valueOf(session.reason)) {
            case "CANCELLED" -> "语音通话：已取消";
            case "TIMEOUT" -> "语音通话：无人接听";
            default -> "语音通话已结束";
        };
    }

    private String formatDuration(long seconds) {
        long mins = seconds / 60;
        long secs = seconds % 60;
        long hours = mins / 60;
        mins = mins % 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, mins, secs);
        }
        return String.format("%02d:%02d", mins, secs);
    }

    private void clearIndexes(CallSession session) {
        stringRedisTemplate.delete(userCallKey(session.callerUserId));
        stringRedisTemplate.delete(userCallKey(session.calleeUserId));
        stringRedisTemplate.delete(callKey(session.callId));
    }

    private void logCallEvent(String action, CallSession session, Long actorUserId, String detail) {
        log.info(
            "voice-call action={}, detail={}, callId={}, conversationId={}, actorUserId={}, callerUserId={}, calleeUserId={}, status={}, reason={}, createdAt={}, answeredAt={}, endedAt={}",
            action,
            detail,
            session.callId,
            session.conversationId,
            actorUserId,
            session.callerUserId,
            session.calleeUserId,
            session.status,
            session.reason,
            session.createdAt,
            session.answeredAt,
            session.endedAt
        );
    }

    private void assertUserNotInActiveCall(Long userId) {
        String callId = stringRedisTemplate.opsForValue().get(userCallKey(userId));
        if (callId == null || callId.isBlank()) {
            return;
        }
        CallSession session = loadSession(callId);
        if (session == null) {
            stringRedisTemplate.delete(userCallKey(userId));
            return;
        }
        try {
            session = ensureNotTimedOut(session);
        } catch (BusinessException e) {
            if ("通话已超时".equals(e.getMessage())) {
                return;
            }
            throw e;
        }
        if (!VoiceCallStatus.ENDED.name().equals(session.status) && !VoiceCallStatus.REJECTED.name().equals(session.status)) {
            throw new BusinessException("当前有一方正在通话中");
        }
    }

    private CallSession ensureNotTimedOut(CallSession session) {
        if (!VoiceCallStatus.RINGING.name().equals(session.status)) {
            return session;
        }
        long age = java.time.Duration.between(session.createdAt, LocalDateTime.now()).getSeconds();
        if (age < Math.max(5, agoraProperties.getRingTimeoutSeconds())) {
            return session;
        }
        session.status = VoiceCallStatus.ENDED.name();
        session.reason = "TIMEOUT";
        session.endedAt = LocalDateTime.now();
        saveSession(session);
        VoiceCallVO vo = toVO(session);
        appendCallRecord(session);
        logCallEvent("timeout", session, null, "振铃超时自动结束");
        wsPushService.pushToUsers(Set.of(session.callerUserId, session.calleeUserId), new WsEvent<>("CALL_ENDED", vo));
        clearIndexes(session);
        throw new BusinessException("通话已超时");
    }

    private CallSession loadSession(String callId) {
        try {
            String json = stringRedisTemplate.opsForValue().get(callKey(callId));
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, CallSession.class);
        } catch (Exception e) {
            throw new BusinessException("通话状态读取失败");
        }
    }

    private void saveSession(CallSession session) {
        try {
            stringRedisTemplate.opsForValue().set(
                callKey(session.callId),
                objectMapper.writeValueAsString(session),
                Math.max(60, agoraProperties.getSessionTtlSeconds()),
                TimeUnit.SECONDS
            );
        } catch (Exception e) {
            throw new BusinessException("通话状态保存失败");
        }
    }

    private void bindUserToCall(Long userId, String callId) {
        stringRedisTemplate.opsForValue().set(
            userCallKey(userId),
            callId,
            Math.max(60, agoraProperties.getSessionTtlSeconds()),
            TimeUnit.SECONDS
        );
    }

    private String callKey(String callId) {
        return "im:voice-call:call:" + callId;
    }

    private String userCallKey(Long userId) {
        return "im:voice-call:user:" + userId;
    }

    @Data
    private static class CallSession {
        private String callId;
        private Long conversationId;
        private String channelName;
        private Long callerUserId;
        private Long calleeUserId;
        private String status;
        private String reason;
        private LocalDateTime createdAt;
        private LocalDateTime answeredAt;
        private LocalDateTime endedAt;
    }
}
