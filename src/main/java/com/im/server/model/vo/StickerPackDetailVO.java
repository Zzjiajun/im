package com.im.server.model.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StickerPackDetailVO {

    private Long packId;
    private String code;
    private String name;
    private String coverUrl;
    private Integer sortOrder;
    private List<StickerItemVO> items;

    @Data
    @Builder
    public static class StickerItemVO {
        private Long itemId;
        private String code;
        private String imageUrl;
        private Integer sortOrder;
    }
}
