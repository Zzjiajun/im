package com.im.server.model.dto;

import com.im.server.model.enums.MessageType;
import java.util.List;
import lombok.Data;

@Data
public class WsChatPayload {

    private Long conversationId;
    private MessageType type;
    /** 与 HTTP 发消息一致，可选幂等键 */
    private String clientMsgId;
    private String content;
    private String mediaUrl;
    private String mediaCoverUrl;
    private Long replyMessageId;
    private Boolean mentionAll;
    private List<Long> mentionUserIds;
}
