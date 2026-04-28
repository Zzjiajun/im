package com.im.server.model.enums;

/**
 * 通知类型枚举：用于通知落库与前端展示映射。
 *
 * 注意：这里的 name() 会直接写入 notification.type 字段，
 * 所以变更枚举值会影响历史数据与前端类型映射。
 */
public enum NotificationType {
    FRIEND_REQUEST,
    FRIEND_ACCEPTED,
    GROUP_INVITE,
    GROUP_MEMBER_CHANGE,
    MENTION,
    SYSTEM_ANNOUNCEMENT
}

