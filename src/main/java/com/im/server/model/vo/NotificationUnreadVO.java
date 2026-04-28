package com.im.server.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationUnreadVO {

    private Integer totalCount;
    private Integer unreadCount;
}