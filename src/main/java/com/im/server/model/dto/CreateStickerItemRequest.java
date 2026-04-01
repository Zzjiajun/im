package com.im.server.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateStickerItemRequest {

    @NotNull
    private Long packId;

    @NotBlank
    private String code;

    @NotBlank
    private String imageUrl;

    private Integer sortOrder;
}
