package com.im.server.model.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FavoriteMessageVO {

    private Long favoriteId;
    private Long messageId;
    private String note;
    private String categoryName;
    private LocalDateTime favoriteAt;
    private ChatMessageVO message;
}
