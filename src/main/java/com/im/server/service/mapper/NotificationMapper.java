package com.im.server.service.mapper;

import com.im.server.model.entity.Notification;
import com.im.server.model.vo.NotificationVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Notification → NotificationVO 映射。
 * 字段名完全一致，无需额外配置。
 */
@Mapper(componentModel = "spring")
public interface NotificationMapper {

    NotificationVO toNotificationVO(Notification notification);
}
