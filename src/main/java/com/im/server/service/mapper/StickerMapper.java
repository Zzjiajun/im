package com.im.server.service.mapper;

import com.im.server.model.entity.StickerItem;
import com.im.server.model.entity.StickerPack;
import com.im.server.model.vo.StickerPackDetailVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * StickerPack → StickerPackDetailVO + StickerItem → StickerItemVO 映射。
 */
@Mapper(componentModel = "spring")
public interface StickerMapper {

    @Mapping(target = "packId", source = "id")
    StickerPackDetailVO toDetail(StickerPack pack);

    @Mapping(target = "itemId", source = "id")
    StickerPackDetailVO.StickerItemVO toItem(StickerItem item);
}
