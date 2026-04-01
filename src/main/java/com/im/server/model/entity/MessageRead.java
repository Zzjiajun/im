package com.im.server.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("message_read")
public class MessageRead {

    private Long id;
    private Long messageId;
    private Long userId;
    private LocalDateTime readAt;
}
