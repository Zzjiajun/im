package com.im.server.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminDashboardVO {

    private long totalUsers;
    private long messagesLast24h;
    private long reportsLast7d;
}
