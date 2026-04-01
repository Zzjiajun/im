package com.im.server.model.dto;

import lombok.Data;

@Data
public class UpdateGroupProfileRequest {

    private String name;
    private String avatar;
    private String notice;
}
