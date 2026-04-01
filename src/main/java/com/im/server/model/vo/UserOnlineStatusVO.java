package com.im.server.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserOnlineStatusVO {

    private Long userId;
    private Boolean online;
}
