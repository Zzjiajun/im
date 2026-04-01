package com.im.server.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("message_report")
public class MessageReport {

    private Long id;
    private Long messageId;
    private Long reporterUserId;
    private String reason;
    private String remark;
    private LocalDateTime createdAt;
}
