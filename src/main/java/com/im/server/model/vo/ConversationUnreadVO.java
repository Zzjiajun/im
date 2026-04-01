package com.im.server.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ConversationUnreadVO {

    private Long conversationId;
    private Long unreadCount;
}
