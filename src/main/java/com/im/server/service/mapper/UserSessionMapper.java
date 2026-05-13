package com.im.server.service.mapper;

import com.im.server.model.entity.UserSession;
import com.im.server.model.vo.UserSessionVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * UserSession → UserSessionVO 映射。
 * <ul>
 *   <li>UserSession.id → UserSessionVO.sessionId</li>
 *   <li>UserSession.revoked (Integer) → revoked (Boolean)：1=true, 其余=false</li>
 * </ul>
 */
@Mapper(componentModel = "spring")
public interface UserSessionMapper {

    @Mapping(target = "sessionId", source = "id")
    @Mapping(target = "revoked", qualifiedByName = "intToBoolean")
    UserSessionVO toUserSessionVO(UserSession session);

    @Named("intToBoolean")
    default Boolean intToBoolean(Integer value) {
        return Integer.valueOf(1).equals(value);
    }
}
