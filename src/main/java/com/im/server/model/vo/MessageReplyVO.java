package com.im.server.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MessageReplyVO {

    private Long messageId;
    private Long senderId;
    private String senderNickname;
    private String type;
    private String content;
    private String mediaUrl;
}
