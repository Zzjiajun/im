package com.im.server.model.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VoiceCallVO {

    private String callId;
    private Long conversationId;
    private String channelName;
    private Long callerUserId;
    private String callerNickname;
    private String callerAvatar;
    private Long calleeUserId;
    private String calleeNickname;
    private String calleeAvatar;
    private String status;
    private String reason;
    private LocalDateTime createdAt;
    private LocalDateTime answeredAt;
    private LocalDateTime endedAt;
}
