package com.im.server.service.mapper;

import com.im.server.model.entity.GroupInvite;
import com.im.server.model.vo.GroupInviteCreatedVO;
import org.mapstruct.Mapper;

/**
 * GroupInvite → GroupInviteCreatedVO 映射。
 * 字段名完全一致（token, expireAt, maxUses）。
 */
@Mapper(componentModel = "spring")
public interface GroupInviteMapper {

    GroupInviteCreatedVO toCreatedVO(GroupInvite groupInvite);
}
