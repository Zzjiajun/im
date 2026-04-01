package com.im.server.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("conversation_member")
public class ConversationMember {

    private Long id;
    private Long conversationId;
    private Long userId;
    private String role;
    private String remarkName;
    private Integer pinned;
    private Integer muted;
    private String draftContent;
    private LocalDateTime draftUpdatedAt;
    private Long clearMessageId;
    private LocalDateTime clearAt;
    private LocalDateTime lastReadAt;
    private LocalDateTime deletedAt;
    private Integer archived;
    private LocalDateTime speakMutedUntil;
    private Long syncCursorMessageId;
    private LocalDateTime createdAt;
}
