package com.im.server.service;

import com.im.server.model.vo.ChatMessageVO;
import com.im.server.model.vo.WsEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 消息异步推送服务。
 * 将 WebSocket 推送、离线推送从事务中分离，异步执行。
 */
@Service
@RequiredArgsConstructor
public class MessagePushService {

    private static final Logger log = LoggerFactory.getLogger(MessagePushService.class);

    private final WsPushService wsPushService;
    private final UnreadCacheService unreadCacheService;
    private final OfflinePushService offlinePushService;
    private final BlacklistService blacklistService;
    private final NotificationService notificationService;

    /**
     * 异步推送消息给会话成员。
     * 1. 未读数增加
     * 2. WebSocket 推送
     * 3. 离线推送（仅限非在线用户）
     * 4. @提及通知
     */
    @Async("messagePushExecutor")
    public void asyncPushToMembers(Long senderId, ChatMessageVO messageVO,
                                   List<Long> memberIds, boolean isGroup,
                                   List<Long> mentionTargetIds, boolean mentionAll) {
        try {
            // 1. 推送消息给所有成员（含发送者不推送）
            for (Long memberId : memberIds) {
                if (memberId.equals(senderId)) {
                    continue;
                }
                // 群聊中跳过黑名单
                if (isGroup && blacklistService.isBlockedEitherWay(senderId, memberId)) {
                    continue;
                }
                // 未读数增加
                unreadCacheService.incrementUnread(memberId, messageVO.getConversationId());
                // WS 推送
                wsPushService.pushToUser(memberId, new WsEvent<>("MESSAGE", messageVO));
                // 离线推送
                offlinePushService.notifyNewChatMessage(memberId, messageVO);
            }

            // 2. @提及通知
            if (mentionAll) {
                // 全员提及：在 memberIds 中找出发送者以外的可见成员
                for (Long memberId : memberIds) {
                    if (!memberId.equals(senderId)) {
                        if (isGroup && blacklistService.isBlockedEitherWay(senderId, memberId)) {
                            continue;
                        }
                        notificationService.notifyMention(
                            memberId, senderId, messageVO.getContent(),
                            messageVO.getConversationId(), messageVO.getId()
                        );
                    }
                }
            } else if (mentionTargetIds != null && !mentionTargetIds.isEmpty()) {
                for (Long targetId : mentionTargetIds) {
                    if (!targetId.equals(senderId)) {
                        if (isGroup && blacklistService.isBlockedEitherWay(senderId, targetId)) {
                            continue;
                        }
                        notificationService.notifyMention(
                            targetId, senderId, messageVO.getContent(),
                            messageVO.getConversationId(), messageVO.getId()
                        );
                    }
                }
            }

            log.debug("[AsyncPush] 消息推送完成 conversationId={} members={}",
                    messageVO.getConversationId(), memberIds.size());
        } catch (Exception e) {
            log.warn("[AsyncPush] 消息推送异常 conversationId={} error={}",
                    messageVO.getConversationId(), e.getMessage());
        }
    }
}
