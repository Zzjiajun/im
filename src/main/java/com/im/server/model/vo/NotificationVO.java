package com.im.server.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationVO {

    private Long id;
    private String type;
    private String title;
    private String content;
    private String data;
    private Long senderId;
    private String senderNickname;
    private String senderAvatar;
    private Long relatedId;
    private Boolean isRead;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}