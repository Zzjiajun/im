package com.im.server.model.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FriendTagVO {

    private Long tagId;
    private String name;
    private Integer sortOrder;
    private Integer memberCount;
    private LocalDateTime createdAt;
}
