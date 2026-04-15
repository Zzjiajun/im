package com.im.server.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_message")
public class ChatMessage {

    private Long id;
    private Long conversationId;
    private Long senderId;
    private String type;
    private String content;
    private String mediaUrl;
    private String mediaCoverUrl;
    private Long replyMessageId;
    private Integer readCount;
    private Integer deliveredCount;
    private Integer favoriteCount;
    private Integer edited;
    private LocalDateTime editedAt;
    private Integer mentionAll;
    private String mentionUserIds;
    private Integer recalled;
    private Long recalledBy;
    private LocalDateTime recalledAt;
    /** 客户端幂等键，可选 */
    private String clientMsgId;
    private LocalDateTime createdAt;
}
