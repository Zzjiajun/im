package com.im.server.model.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatMessageVO {

    private Long id;
    private Long conversationId;
    private Long senderId;
    private String senderNickname;
    private String senderAvatar;
    private String type;
    private String content;
    private String mediaUrl;
    private String mediaCoverUrl;
    private MediaMetaVO mediaMeta;
    private Long replyMessageId;
    private MessageReplyVO replyMessage;
    private Integer readCount;
    private Integer deliveredCount;
    private Integer favoriteCount;
    private Boolean pinnedByMe;
    private Integer edited;
    private LocalDateTime editedAt;
    private Boolean mentionAll;
    private List<Long> mentionUserIds;
    private List<MessageReactionSummaryVO> reactions;
    private Integer recalled;
    private Long recalledBy;
    private LocalDateTime recalledAt;
    private LocalDateTime createdAt;
}
