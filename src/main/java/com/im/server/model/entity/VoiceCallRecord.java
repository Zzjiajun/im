package com.im.server.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("voice_call_record")
public class VoiceCallRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String callId;

    private Long callerUserId;

    private Long calleeUserId;

    private Long conversationId;

    private String status;

    private LocalDateTime startAt;

    private LocalDateTime answerAt;

    private LocalDateTime endAt;

    private Integer durationSeconds;

    private String reason;

    private Integer ringTimeoutSeconds;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}