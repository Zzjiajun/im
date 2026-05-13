package com.im.server.service.notification;

import com.im.server.model.entity.Notification;

/**
 * 通知工厂 —— 工厂方法模式。
 * <p>
 * 每种通知类型的构建逻辑封装为独立方法，对外隐藏
 * 标题、内容、data JSON、sender 等组装细节。
 * <p>
 * 面试点：工厂方法模式。客户（NotificationService）
 * 只需声明"我要创建什么类型的通知"，无需关心"怎么创建"。
 * 新增通知类型只需在工厂中加一个方法，符合开闭原则。
 */
public interface NotificationFactory {

    /** 好友申请通知 */
    Notification createFriendRequest(Long toUserId, Long fromUserId, Long friendRequestId,
                                     String fromNickname, String fromAvatar);

    /** 好友请求通过通知 */
    Notification createFriendAccepted(Long toUserId, Long fromUserId,
                                      String fromNickname, String fromAvatar);

    /** 群聊邀请通知 */
    Notification createGroupInvite(Long toUserId, Long fromUserId, String groupName, Long groupId,
                                   Long inviteId, String fromNickname, String fromAvatar);

    /** @提及通知 */
    Notification createMention(Long toUserId, Long fromUserId, String content,
                               Long conversationId, Long messageId,
                               String fromNickname, String fromAvatar);

    /** 群成员变更通知 */
    Notification createGroupMemberChange(Long toUserId, String type, String groupName, Long groupId,
                                         String operatorNickname);

    /** 系统公告通知 */
    Notification createSystemAnnouncement(Long userId, String title, String content, Long adminId);
}
