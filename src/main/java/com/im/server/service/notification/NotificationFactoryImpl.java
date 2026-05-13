package com.im.server.service.notification;

import com.im.server.model.entity.Notification;
import com.im.server.model.enums.NotificationType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link NotificationFactory} 默认实现。
 * <p>
 * 内部使用 {@link NotificationBuilder} 构建每个通知，
 * 职责单一：只负责"组装 Notification 对象"。
 */
@Component
public class NotificationFactoryImpl implements NotificationFactory {

    @Override
    public Notification createFriendRequest(Long toUserId, Long fromUserId, Long friendRequestId,
                                            String fromNickname, String fromAvatar) {
        Map<String, Object> data = new HashMap<>();
        data.put("friendRequestId", friendRequestId);

        return new NotificationBuilder()
            .toUser(toUserId)
            .type(NotificationType.FRIEND_REQUEST.name())
            .title("新的好友申请")
            .content(fromNickname + " 请求添加你为好友")
            .withData(data)
            .from(fromUserId, fromNickname, fromAvatar)
            .relatedTo(friendRequestId)
            .unread()
            .build();
    }

    @Override
    public Notification createFriendAccepted(Long toUserId, Long fromUserId,
                                             String fromNickname, String fromAvatar) {
        Map<String, Object> data = new HashMap<>();
        data.put("friendUserId", fromUserId);

        return new NotificationBuilder()
            .toUser(toUserId)
            .type(NotificationType.FRIEND_ACCEPTED.name())
            .title("好友申请已通过")
            .content(fromNickname + " 通过了你的好友申请")
            .withData(data)
            .from(fromUserId, fromNickname, fromAvatar)
            .relatedTo(fromUserId)
            .unread()
            .build();
    }

    @Override
    public Notification createGroupInvite(Long toUserId, Long fromUserId, String groupName, Long groupId,
                                          Long inviteId, String fromNickname, String fromAvatar) {
        Map<String, Object> data = new HashMap<>();
        data.put("groupId", groupId);
        data.put("inviteId", inviteId);

        return new NotificationBuilder()
            .toUser(toUserId)
            .type(NotificationType.GROUP_INVITE.name())
            .title("群聊邀请")
            .content(fromNickname + " 邀请你加入群聊 \"" + groupName + "\"")
            .withData(data)
            .from(fromUserId, fromNickname, fromAvatar)
            .relatedTo(inviteId)
            .unread()
            .build();
    }

    @Override
    public Notification createMention(Long toUserId, Long fromUserId, String content,
                                      Long conversationId, Long messageId,
                                      String fromNickname, String fromAvatar) {
        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", conversationId);
        data.put("messageId", messageId);
        data.put("messageContent", content);

        String safeContent = content == null ? "[消息]" : content;
        String preview = safeContent.length() > 50 ? safeContent.substring(0, 50) + "..." : safeContent;

        return new NotificationBuilder()
            .toUser(toUserId)
            .type(NotificationType.MENTION.name())
            .title("你在群聊中被@了")
            .content(fromNickname + " 在群聊中@了你: " + preview)
            .withData(data)
            .from(fromUserId, fromNickname, fromAvatar)
            .relatedTo(messageId)
            .unread()
            .build();
    }

    @Override
    public Notification createGroupMemberChange(Long toUserId, String type, String groupName,
                                                Long groupId, String operatorNickname) {
        Map<String, Object> data = new HashMap<>();
        data.put("groupId", groupId);

        String title;
        String content;

        switch (type) {
            case "member_added" -> {
                title = "你已被添加到群聊";
                content = operatorNickname + " 将你添加到群聊 \"" + groupName + "\"";
            }
            case "joined_via_invite" -> {
                title = "成员通过邀请链接加入";
                content = operatorNickname + " 通过邀请链接加入了群聊 \"" + groupName + "\"";
            }
            case "removed" -> {
                title = "你已被移出群聊";
                content = "你已被移出群聊 \"" + groupName + "\"";
            }
            case "group_deleted" -> {
                title = "群聊已解散";
                content = "群聊 \"" + groupName + "\" 已被解散";
            }
            case "admin_added" -> {
                title = "你被设为管理员";
                content = "你在群聊 \"" + groupName + "\" 被设为管理员";
            }
            case "admin_removed" -> {
                title = "你被取消管理员";
                content = "你在群聊 \"" + groupName + "\" 被取消管理员资格";
            }
            default -> {
                title = "群聊变化通知";
                content = "群聊 \"" + groupName + "\" 发生变化";
            }
        }

        return new NotificationBuilder()
            .toUser(toUserId)
            .type(NotificationType.GROUP_MEMBER_CHANGE.name())
            .title(title)
            .content(content)
            .withData(data)
            .relatedTo(groupId)
            .unread()
            .build();
    }

    @Override
    public Notification createSystemAnnouncement(Long userId, String title, String content, Long adminId) {
        Map<String, Object> data = new HashMap<>();
        data.put("announcementTitle", title);
        data.put("announcementContent", content);

        return new NotificationBuilder()
            .toUser(userId)
            .type(NotificationType.SYSTEM_ANNOUNCEMENT.name())
            .title(title)
            .content(content)
            .withData(data)
            .from(null, "系统管理员", null)
            .createdBy(adminId)
            .unread()
            .build();
    }
}
