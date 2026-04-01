package com.im.server.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateStickerPackRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    private String coverUrl;

    private Integer sortOrder;
}
