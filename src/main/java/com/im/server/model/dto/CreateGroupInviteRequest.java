package com.im.server.model.dto;

import lombok.Data;

@Data
public class CreateGroupInviteRequest {

    private Integer expireHours;
    private Integer maxUses;
}
