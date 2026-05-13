package com.im.server.service.notification;

import com.im.server.model.entity.Notification;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 通知构建器 —— 建造者模式。
 * <p>
 * 替代 {@code createNotification()} 那 10+ 个参数的混乱调用，
 * 通过 fluent API 链式构建 {@link Notification}。
 * <p>
 * 面试点：建造者模式。将复杂对象的构造与表示分离，
 * 避免 telescoping constructor 反模式，同时让代码自文档化。
 *
 * <pre>{@code
 *   new NotificationBuilder()
 *       .toUser(userId)
 *       .type(NotificationType.FRIEND_REQUEST)
 *       .title("新的好友申请")
 *       .content("...")
 *       .withData("friendRequestId", friendRequestId)
 *       .from(senderId, nickname, avatar)
 *       .build();
 * }</pre>
 */
public class NotificationBuilder {

    private final Notification target = new Notification();

    /** 接收通知的用户 */
    public NotificationBuilder toUser(Long userId) {
        target.setUserId(userId);
        return this;
    }

    /** 通知类型 */
    public NotificationBuilder type(String type) {
        target.setType(type);
        return this;
    }

    /** 通知标题 */
    public NotificationBuilder title(String title) {
        target.setTitle(title);
        return this;
    }

    /** 通知内容 */
    public NotificationBuilder content(String content) {
        target.setContent(content);
        return this;
    }

    /** 附加数据（JSON 序列化前） */
    public NotificationBuilder withData(Map<String, Object> data) {
        try {
            target.setData(data != null
                ? new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data)
                : null);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize notification data", e);
        }
        return this;
    }

    /** 发起通知的用户信息 */
    public NotificationBuilder from(Long senderId, String nickname, String avatar) {
        target.setSenderId(senderId);
        target.setSenderNickname(nickname);
        target.setSenderAvatar(avatar);
        return this;
    }

    /** 关联业务 ID（friendRequestId / groupId / messageId 等） */
    public NotificationBuilder relatedTo(Long relatedId) {
        target.setRelatedId(relatedId);
        return this;
    }

    /** 创建者（管理员发布系统公告时使用） */
    public NotificationBuilder createdBy(Long adminId) {
        target.setCreatedBy(adminId);
        return this;
    }

    /** 标记为未读 */
    public NotificationBuilder unread() {
        target.setIsRead(false);
        return this;
    }

    /** 构建并返回 Notification 实体 */
    public Notification build() {
        LocalDateTime now = LocalDateTime.now();
        if (target.getIsRead() == null) {
            target.setIsRead(false);
        }
        if (target.getCreatedAt() == null) {
            target.setCreatedAt(now);
        }
        if (target.getUpdatedAt() == null) {
            target.setUpdatedAt(now);
        }
        return target;
    }
}
