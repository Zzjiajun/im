package com.im.server.controller;

import com.im.server.common.ApiResponse;
import com.im.server.common.CurrentUser;
import com.im.server.model.vo.NotificationUnreadVO;
import com.im.server.model.vo.NotificationVO;
import com.im.server.security.LoginUser;
import com.im.server.service.NotificationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 获取通知列表
     */
    @GetMapping
    public ApiResponse<List<NotificationVO>> list(@CurrentUser LoginUser loginUser,
                                                    @RequestParam(required = false) Boolean isRead,
                                                    @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer size) {
        return ApiResponse.success(notificationService.listNotifications(
            loginUser.getUserId(), isRead, page, size
        ));
    }

    /**
     * 获取未读通知数量
     */
    @GetMapping("/unread")
    public ApiResponse<NotificationUnreadVO> unread(@CurrentUser LoginUser loginUser) {
        return ApiResponse.success(notificationService.getUnreadCount(loginUser.getUserId()));
    }

    /**
     * 标记单个通知为已读
     */
    @PostMapping("/{notificationId}/read")
    public ApiResponse<Void> markAsRead(@CurrentUser LoginUser loginUser,
                                          @PathVariable Long notificationId) {
        notificationService.markAsRead(loginUser.getUserId(), notificationId);
        return ApiResponse.success("已标记为已读", null);
    }

    /**
     * 标记所有通知为已读
     */
    @PostMapping("/read-all")
    public ApiResponse<Void> markAllAsRead(@CurrentUser LoginUser loginUser) {
        notificationService.markAllAsRead(loginUser.getUserId());
        return ApiResponse.success("已标记所有通知为已读", null);
    }

    /**
     * 删除单个通知
     */
    @DeleteMapping("/{notificationId}")
    public ApiResponse<Void> delete(@CurrentUser LoginUser loginUser,
                                       @PathVariable Long notificationId) {
        notificationService.deleteNotification(loginUser.getUserId(), notificationId);
        return ApiResponse.success("通知已删除", null);
    }

    /**
     * 清空所有通知
     */
    @DeleteMapping("/clear")
    public ApiResponse<Void> clearAll(@CurrentUser LoginUser loginUser) {
        notificationService.clearAll(loginUser.getUserId());
        return ApiResponse.success("所有通知已清空", null);
    }
}