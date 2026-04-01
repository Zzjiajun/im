package com.im.server.model.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {

    private String nickname;
    private String avatar;
}
