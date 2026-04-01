package com.im.server.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateFriendTagRequest {

    @NotBlank
    private String name;
}
