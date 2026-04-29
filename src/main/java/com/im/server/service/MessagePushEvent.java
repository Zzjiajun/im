package com.im.server.service;

import com.im.server.model.vo.ChatMessageVO;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 消息推送事件：在 sendMessage 事务提交后触发。
 * 将 WS 推送、离线推送、@提及推送移出事务，避免大事务锁定。
 */
@Getter
@RequiredArgsConstructor
public class MessagePushEvent {

    private final Long senderId;
    private final ChatMessageVO messageVO;
    private final List<Long> memberIds;
    private final boolean isGroup;
    private final List<Long> mentionTargetIds;
    private final Long conversationId;

    public boolean isMentionAll() {
        return mentionTargetIds != null && mentionTargetIds.contains(-1L);
    }
}
