package com.im.server.model.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MessageReportAdminVO {

    private Long id;
    private Long messageId;
    private Long reporterUserId;
    private String reporterNickname;
    private String reason;
    private String remark;
    private LocalDateTime createdAt;
    private String messagePreview;
    private Long conversationId;
}
