package com.im.server.model.dto;

import com.im.server.model.enums.MessageType;
import java.util.List;
import lombok.Data;

@Data
public class WsChatPayload {

    private Long conversationId;
    private MessageType type;
    private String content;
    private String mediaUrl;
    private String mediaCoverUrl;
    private Long replyMessageId;
    private Boolean mentionAll;
    private List<Long> mentionUserIds;
}
