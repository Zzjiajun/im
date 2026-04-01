package com.im.server.model.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConversationListVO {

    private Long conversationId;
    private String type;
    private String displayName;
    private String displayAvatar;
    private String remarkName;
    private String notice;
    private Long ownerId;
    private Long targetUserId;
    private Long lastMessageId;
    private String lastMessagePreview;
    private Long unreadCount;
    private Integer memberCount;
    private Boolean pinned;
    private Boolean muted;
    private Boolean archived;
    private String draftContent;
    private LocalDateTime draftUpdatedAt;
    private LocalDateTime updatedAt;
}
