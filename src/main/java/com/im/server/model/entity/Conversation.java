package com.im.server.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("conversation")
public class Conversation {

    private Long id;
    private String type;
    private String name;
    private String avatar;
    private String notice;
    private LocalDateTime noticeUpdatedAt;
    private Long ownerId;
    private Long lastMessageId;
    private String lastMessagePreview;
    private Integer muteAll;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
