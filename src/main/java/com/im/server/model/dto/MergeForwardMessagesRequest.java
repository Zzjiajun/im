package com.im.server.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class MergeForwardMessagesRequest {

    @NotEmpty
    private List<Long> sourceMessageIds;

    @NotEmpty
    private List<Long> targetConversationIds;

    @NotBlank
    private String title;
}
