package com.im.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.im.server.mapper.NotificationMapper;
import com.im.server.model.entity.Notification;
import com.im.server.model.vo.UserSimpleVO;
import com.im.server.model.vo.NotificationUnreadVO;
import com.im.server.model.vo.NotificationVO;
import com.im.server.model.vo.WsEvent;
import com.im.server.service.notification.NotificationFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final UserService userService;
    private final WsPushService wsPushService;
    private final NotificationFactory notificationFactory;
    private final com.im.server.service.mapper.NotificationMapper notificationMapping;

    /**
     * 创建好友申请通知
     */
    public void notifyFriendRequest(Long toUserId, Long fromUserId, Long friendRequestId) {
        UserSimpleVO fromUser = userService.getSimpleUser(fromUserId);
        Notification notification = notificationFactory.createFriendRequest(
            toUserId, fromUserId, friendRequestId,
            fromUser.getNickname(), fromUser.getAvatar()
        );
        saveAndPush(notification);
    }

    /**
     * 创建好友被接受通知
     */
    public void notifyFriendAccepted(Long toUserId, Long fromUserId) {
        UserSimpleVO fromUser = userService.getSimpleUser(fromUserId);
        Notification notification = notificationFactory.createFriendAccepted(
            toUserId, fromUserId,
            fromUser.getNickname(), fromUser.getAvatar()
        );
        saveAndPush(notification);
    }

    /**
     * 创建群邀请通知
     */
    public void notifyGroupInvite(Long toUserId, Long fromUserId, String groupName, Long groupId, Long inviteId) {
        UserSimpleVO fromUser = userService.getSimpleUser(fromUserId);
        Notification notification = notificationFactory.createGroupInvite(
            toUserId, fromUserId, groupName, groupId, inviteId,
            fromUser.getNickname(), fromUser.getAvatar()
        );
        saveAndPush(notification);
    }

    /**
     * 创建@消息通知
     */
    public void notifyMention(Long toUserId, Long fromUserId, String content, Long conversationId, Long messageId) {
        UserSimpleVO fromUser = userService.getSimpleUser(fromUserId);
        Notification notification = notificationFactory.createMention(
            toUserId, fromUserId, content, conversationId, messageId,
            fromUser.getNickname(), fromUser.getAvatar()
        );
        saveAndPush(notification);
    }

    /**
     * 创建群成员变化通知
     */
    public void notifyGroupMemberChange(Long toUserId, String type, String groupName, Long groupId, String operatorNickname) {
        Notification notification = notificationFactory.createGroupMemberChange(
            toUserId, type, groupName, groupId, operatorNickname
        );
        saveAndPush(notification);
    }

    /**
     * 创建系统公告
     *
     * @param adminId 创建公告的管理员 ID，存入 created_by 字段
     */
    @Transactional
    public void createSystemAnnouncement(String title, String content, List<Long> targetUserIds, Long adminId) {
        List<Long> userIds = targetUserIds;
        if (userIds == null || userIds.isEmpty()) {
            log.info("Broadcasting system announcement to all users: {}", title);
            userIds = userService.listAllUserIds();
        }
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        for (Long userId : userIds) {
            Notification notification = notificationFactory.createSystemAnnouncement(userId, title, content, adminId);
            saveAndPush(notification);
        }
        log.info("[Notification] System announcement sent to {} users: {}", userIds.size(), title);
    }

    /**
     * 持久化通知并实时推送
     */
    private void saveAndPush(Notification notification) {
        notificationMapper.insert(notification);
        NotificationVO notificationVO = buildNotificationVO(notification);
        wsPushService.pushToUser(notification.getUserId(), new WsEvent<>("NOTIFICATION", notificationVO));
        log.info("Notification created: userId={}, type={}, title={}",
            notification.getUserId(), notification.getType(), notification.getTitle());
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
     * 管理员查看所有通知（分页，返回记录 + 总条数）
     */
    public Map<String, Object> adminNotifications(Long userId, String type, Boolean isRead, Integer page, Integer size) {
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

        // 查询总条数（不带 LIMIT/OFFSET）
        LambdaQueryWrapper<Notification> countWrapper = new LambdaQueryWrapper<Notification>()
            .orderByDesc(Notification::getCreatedAt);
        if (userId != null) {
            countWrapper.eq(Notification::getUserId, userId);
        }
        if (type != null) {
            countWrapper.eq(Notification::getType, type);
        }
        if (isRead != null) {
            countWrapper.eq(Notification::getIsRead, isRead);
        }
        long total = notificationMapper.selectCount(countWrapper);

        // 查询当前页记录
        wrapper.last("LIMIT " + pageSize + " OFFSET " + offset);
        List<NotificationVO> records = notificationMapper.selectList(wrapper).stream()
            .map(this::buildNotificationVO)
            .toList();

        return Map.of("records", records, "total", total);
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
        return notificationMapping.toNotificationVO(notification);
    }
}