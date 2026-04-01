package com.im.server.model.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class AssignFriendTagsRequest {

    @NotNull
    private Long friendUserId;

    private List<Long> tagIds;
}
