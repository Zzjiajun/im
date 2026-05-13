package com.im.server.service.mapper;

import com.im.server.model.entity.User;
import com.im.server.model.vo.UserSimpleVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * User → UserSimpleVO 映射。
 * <p>
 * 提供两个版本：
 * <ul>
 *   <li>{@link #toSimpleUser(User)} — 手机号/邮箱脱敏版本（默认）</li>
 *   <li>{@link #toSimpleUserFull(User)} — 完整信息版本</li>
 * </ul>
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    /** User.id → UserSimpleVO.userId */
    @Mapping(target = "userId", source = "id")
    @Mapping(target = "phone", qualifiedByName = "maskPhone")
    @Mapping(target = "email", qualifiedByName = "maskEmail")
    @Mapping(target = "aliasName", ignore = true)
    @Mapping(target = "tagIds", ignore = true)
    UserSimpleVO toSimpleUser(User user);

    /** 不加脱敏的完整信息版本 */
    @Mapping(target = "userId", source = "id")
    @Mapping(target = "aliasName", ignore = true)
    @Mapping(target = "tagIds", ignore = true)
    UserSimpleVO toSimpleUserFull(User user);

    @Named("maskPhone")
    default String maskPhone(String phone) {
        return UserSimpleVO.maskPhone(phone);
    }

    @Named("maskEmail")
    default String maskEmail(String email) {
        return UserSimpleVO.maskEmail(email);
    }
}
