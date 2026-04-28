package com.im.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.server.mapper.NotificationMapper;
import com.im.server.model.entity.Notification;
import com.im.server.model.entity.User;
import com.im.server.model.enums.NotificationType;
import com.im.server.model.vo.NotificationUnreadVO;
import com.im.server.model.vo.NotificationVO;
import com.im.server.model.vo.WsEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final UserService userService;
    private final WsPushService wsPushService;
    private final ObjectMapper objectMapper;

    /**
     * 创建好友申请通知
     */
    public void notifyFriendRequest(Long toUserId, Long fromUserId, Long friendRequestId) {
        User fromUser = userService.getUser(fromUserId);
        Map<String, Object> data = new HashMap<>();
        data.put("friendRequestId", friendRequestId);

        createNotification(
            toUserId,
            NotificationType.FRIEND_REQUEST.name(),
            "新的好友申请",
            fromUser.getNickname() + " 请求添加你为好友",
            data,
            fromUserId,
            fromUser.getNickname(),
            fromUser.getAvatar(),
            friendRequestId
        );
    }

    /**
     * 创建好友被接受通知
     */
    public void notifyFriendAccepted(Long toUserId, Long fromUserId) {
        User fromUser = userService.getUser(fromUserId);
        Map<String, Object> data = new HashMap<>();
        data.put("friendUserId", fromUserId);

        createNotification(
            toUserId,
            NotificationType.FRIEND_ACCEPTED.name(),
            "好友申请已通过",
            fromUser.getNickname() + " 通过了你的好友申请",
            data,
            fromUserId,
            fromUser.getNickname(),
            fromUser.getAvatar(),
            fromUserId
        );
    }

    /**
     * 创建群邀请通知
     */
    public void notifyGroupInvite(Long toUserId, Long fromUserId, String groupName, Long groupId, Long inviteId) {
        User fromUser = userService.getUser(fromUserId);
        Map<String, Object> data = new HashMap<>();
        data.put("groupId", groupId);
        data.put("inviteId", inviteId);

        createNotification(
            toUserId,
            NotificationType.GROUP_INVITE.name(),
            "群聊邀请",
            fromUser.getNickname() + " 邀请你加入群聊 \"" + groupName + "\"",
            data,
            fromUserId,
            fromUser.getNickname(),
            fromUser.getAvatar(),
            inviteId
        );
    }

    /**
     * 创建@消息通知
     */
    public void notifyMention(Long toUserId, Long fromUserId, String content, Long conversationId, Long messageId) {
        User fromUser = userService.getUser(fromUserId);
        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", conversationId);
        data.put("messageId", messageId);
        data.put("messageContent", content);

        String preview = content.length() > 50 ? content.substring(0, 50) + "..." : content;

        createNotification(
            toUserId,
            NotificationType.MENTION.name(),
            "你在群聊中被@了",
            fromUser.getNickname() + " 在群聊中@了你: " + preview,
            data,
            fromUserId,
            fromUser.getNickname(),
            fromUser.getAvatar(),
            messageId
        );
    }

    /**
     * 创建群成员变化通知
     */
    public void notifyGroupMemberChange(Long toUserId, String type, String groupName, Long groupId, String operatorNickname) {
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

        createNotification(
            toUserId,
            NotificationType.GROUP_MEMBER_CHANGE.name(),
            title,
            content,
            data,
            null,
            null,
            null,
            groupId
        );
    }

    /**
     * 创建系统公告
     */
    public void createSystemAnnouncement(String title, String content, List<Long> targetUserIds) {
        Map<String, Object> data = new HashMap<>();
        data.put("announcementTitle", title);
        data.put("announcementContent", content);

        if (targetUserIds == null || targetUserIds.isEmpty()) {
            // 发送给所有用户（这里简化处理，实际可能需要分批）
            log.info("Broadcasting system announcement to all users: {}", title);
        } else {
            for (Long userId : targetUserIds) {
                createNotification(
                    userId,
                    NotificationType.SYSTEM_ANNOUNCEMENT.name(),
                    title,
                    content,
                    data,
                    null,
                    "系统管理员",
                    null,
                    null
                );
            }
        }
    }

    /**
     * 创建通知的核心方法
     */
    private void createNotification(Long userId, String type, String title, String content,
                                     Map<String, Object> data, Long senderId, String senderNickname,
                                     String senderAvatar, Long relatedId) {
        try {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setType(type);
            notification.setTitle(title);
            notification.setContent(content);
            notification.setData(data != null ? objectMapper.writeValueAsString(data) : null);
            notification.setSenderId(senderId);
            notification.setSenderNickname(senderNickname);
            notification.setSenderAvatar(senderAvatar);
            notification.setRelatedId(relatedId);
            notification.setIsRead(false);
            notification.setCreatedAt(LocalDateTime.now());
            notification.setUpdatedAt(LocalDateTime.now());

            notificationMapper.insert(notification);

            // 实时推送通知
            NotificationVO notificationVO = buildNotificationVO(notification);
            wsPushService.pushToUser(userId, new WsEvent<>("NOTIFICATION", notificationVO));

            log.info("Notification created: userId={}, type={}, title={}", userId, type, title);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize notification data", e);
        }
    }

    /**
     * 获取用户通知列表
     */
    public List<NotificationVO> listNotifications(Long userId, Boolean isRead, Integer page, Integer size) {
        int pageSize = size == null ? 20 : Math.min(size, 100);
        int pageNo = page == null ? 1 : Math.max(page, 1);
        int offset = (pageNo - 1) * pageSize;

        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
            .eq(Notification::getUserId, userId)
            .orderByDesc(Notification::getCreatedAt);

        if (isRead != null) {
            wrapper.eq(Notification::getIsRead, isRead);
        }

        wrapper.last("LIMIT " + pageSize + " OFFSET " + offset);

        return notificationMapper.selectList(wrapper).stream()
            .map(this::buildNotificationVO)
            .toList();
    }

    /**
     * 管理员查看所有通知
     */
    public List<NotificationVO> adminNotifications(Long userId, String type, Boolean isRead, Integer page, Integer size) {
        int pageSize = size == null ? 50 : Math.min(size, 100);
        int pageNo = page == null ? 1 : Math.max(page, 1);
        int offset = (pageNo - 1) * pageSize;

        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
            .orderByDesc(Notification::getCreatedAt);

        if (userId != null) {
            wrapper.eq(Notification::getUserId, userId);
        }
        if (type != null) {
            wrapper.eq(Notification::getType, type);
        }
        if (isRead != null) {
            wrapper.eq(Notification::getIsRead, isRead);
        }

        wrapper.last("LIMIT " + pageSize + " OFFSET " + offset);

        return notificationMapper.selectList(wrapper).stream()
            .map(this::buildNotificationVO)
            .toList();
    }

    /**
     * 获取未读通知数量
     */
    public NotificationUnreadVO getUnreadCount(Long userId) {
        long total = notificationMapper.selectCount(
            new LambdaQueryWrapper<Notification>().eq(Notification::getUserId, userId)
        );
        long unread = notificationMapper.selectCount(
            new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, false)
        );
        return NotificationUnreadVO.builder()
            .totalCount((int) total)
            .unreadCount((int) unread)
            .build();
    }

    /**
     * 标记单个通知为已读
     */
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification != null && notification.getUserId().equals(userId)) {
            notification.setIsRead(true);
            notification.setReadAt(LocalDateTime.now());
            notification.setUpdatedAt(LocalDateTime.now());
            notificationMapper.updateById(notification);
        }
    }

    /**
     * 标记所有通知为已读
     */
    public void markAllAsRead(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        int updated = notificationMapper.update(
            null,
            new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, false)
                .set(Notification::getIsRead, true)
                .set(Notification::getReadAt, now)
                .set(Notification::getUpdatedAt, now)
        );
        log.info("Marked {} notifications as read for user {}", updated, userId);
    }

    /**
     * 删除通知
     */
    public void deleteNotification(Long userId, Long notificationId) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification != null && notification.getUserId().equals(userId)) {
            notificationMapper.deleteById(notificationId);
        }
    }

    /**
     * 管理员删除通知
     */
    public void adminDeleteNotification(Long notificationId) {
        notificationMapper.deleteById(notificationId);
    }

    /**
     * 清空所有通知
     */
    public void clearAll(Long userId) {
        notificationMapper.delete(
            new LambdaQueryWrapper<Notification>().eq(Notification::getUserId, userId)
        );
        log.info("Cleared all notifications for user {}", userId);
    }

    /**
     * 管理员清空指定用户的所有通知
     */
    public void adminClearAllNotifications(Long userId) {
        notificationMapper.delete(new LambdaQueryWrapper<Notification>().eq(Notification::getUserId, userId));
    }

    /**
     * 构建通知VO
     */
    private NotificationVO buildNotificationVO(Notification notification) {
        return NotificationVO.builder()
            .id(notification.getId())
            .type(notification.getType())
            .title(notification.getTitle())
            .content(notification.getContent())
            .data(notification.getData())
            .senderId(notification.getSenderId())
            .senderNickname(notification.getSenderNickname())
            .senderAvatar(notification.getSenderAvatar())
            .relatedId(notification.getRelatedId())
            .isRead(notification.getIsRead())
            .readAt(notification.getReadAt())
            .createdAt(notification.getCreatedAt())
            .build();
    }
}