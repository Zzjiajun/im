package com.im.server.service.mapper;

import com.im.server.model.entity.FriendTag;
import com.im.server.model.vo.FriendTagVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * FriendTag + memberCount → FriendTagVO 映射。
 * <ul>
 *   <li>FriendTag.id → FriendTagVO.tagId</li>
 *   <li>memberCount 作为额外参数传入</li>
 * </ul>
 */
@Mapper(componentModel = "spring")
public interface FriendTagMapper {

    @Mapping(target = "tagId", source = "tag.id")
    FriendTagVO toFriendTagVO(FriendTag tag, int memberCount);
}
