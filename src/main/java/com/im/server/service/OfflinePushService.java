package com.im.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.im.server.config.PushProperties;
import com.im.server.mapper.UserPushTokenMapper;
import com.im.server.model.entity.UserPushToken;
import com.im.server.model.vo.ChatMessageVO;
import com.im.server.util.OfflinePushPreview;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class OfflinePushService {

    private final OnlineStatusService onlineStatusService;
    private final UserPushTokenMapper userPushTokenMapper;
    private final PushProperties pushProperties;
    private final RestTemplateBuilder restTemplateBuilder;

    public void notifyNewChatMessage(Long recipientUserId, ChatMessageVO message) {
        if (onlineStatusService.isOnline(recipientUserId)) {
            return;
        }
        List<UserPushToken> tokens = userPushTokenMapper.selectList(
            new LambdaQueryWrapper<UserPushToken>().eq(UserPushToken::getUserId, recipientUserId)
        );
        if (tokens.isEmpty()) {
            log.debug("offline push skipped: no device token userId={}", recipientUserId);
            return;
        }
        String preview = OfflinePushPreview.fromMessage(message);
        if (StringUtils.isNotBlank(pushProperties.getWebhookUrl())) {
            postWebhook(recipientUserId, message, tokens, preview);
        } else {
            for (UserPushToken t : tokens) {
                log.info("[OfflinePushStub] userId={} platform={} tokenPrefix={} conv={} preview={}",
                    recipientUserId,
                    t.getPlatform(),
                    StringUtils.abbreviate(t.getDeviceToken(), 12),
                    message.getConversationId(),
                    preview);
            }
        }
    }

    private void postWebhook(Long recipientUserId, ChatMessageVO message, List<UserPushToken> tokens, String preview) {
        RestTemplate rt = restTemplateBuilder.build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.isNotBlank(pushProperties.getWebhookHeaderName())
            && StringUtils.isNotBlank(pushProperties.getWebhookHeaderValue())) {
            headers.add(pushProperties.getWebhookHeaderName(), pushProperties.getWebhookHeaderValue());
        }
        List<Map<String, String>> tokenMaps = new ArrayList<>();
        for (UserPushToken t : tokens) {
            Map<String, String> m = new HashMap<>();
            m.put("platform", StringUtils.defaultString(t.getPlatform()));
            m.put("deviceToken", StringUtils.defaultString(t.getDeviceToken()));
            tokenMaps.add(m);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("recipientUserId", recipientUserId);
        body.put("conversationId", message.getConversationId());
        body.put("messageId", message.getId());
        body.put("preview", preview);
        body.put("tokens", tokenMaps);
        try {
            rt.postForEntity(
                pushProperties.getWebhookUrl(),
                new HttpEntity<>(body, headers),
                String.class
            );
            log.info("[OfflinePush] webhook ok userId={} conv={}", recipientUserId, message.getConversationId());
        } catch (Exception e) {
            log.warn("[OfflinePush] webhook failed: {}", e.getMessage());
        }
    }
}
