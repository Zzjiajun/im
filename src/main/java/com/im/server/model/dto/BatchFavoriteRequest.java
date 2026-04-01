package com.im.server.model.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class BatchFavoriteRequest {

    @NotEmpty(message = "消息列表不能为空")
    private List<Long> messageIds;

    private String note;
    private String categoryName;
}
