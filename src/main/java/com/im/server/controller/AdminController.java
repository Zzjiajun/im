package com.im.server.controller;

import com.im.server.common.ApiResponse;
import com.im.server.common.BusinessException;
import com.im.server.common.CurrentUser;
import com.im.server.model.vo.AdminDashboardVO;
import com.im.server.model.vo.AdminUserPageVO;
import com.im.server.model.vo.MessageReportAdminVO;
import com.im.server.model.vo.MessageSearchPageVO;
import com.im.server.model.vo.NotificationVO;
import com.im.server.security.LoginUser;
import com.im.server.service.AdminManagementService;
import com.im.server.service.AdminReportService;
import com.im.server.service.MessageService;
import com.im.server.service.NotificationService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminReportService adminReportService;
    private final AdminManagementService adminManagementService;
    private final MessageService messageService;
    private final NotificationService notificationService;

    private static void assertAdmin(LoginUser loginUser) {
        if (loginUser == null || !loginUser.isAdmin()) {
            throw new BusinessException("需要管理员权限");
        }
    }

    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardVO> dashboard(@CurrentUser LoginUser loginUser) {
        assertAdmin(loginUser);
        return ApiResponse.success(adminManagementService.dashboard());
    }

    @GetMapping("/users")
    public ApiResponse<AdminUserPageVO> users(@CurrentUser LoginUser loginUser,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(required = false) String keyword) {
        assertAdmin(loginUser);
        return ApiResponse.success(adminManagementService.listUsers(page, size, keyword));
    }

    @PostMapping("/users/{userId}/ban")
    public ApiResponse<Void> banUser(@CurrentUser LoginUser loginUser, @PathVariable Long userId) {
        assertAdmin(loginUser);
        adminManagementService.banUser(loginUser.getUserId(), userId);
        return ApiResponse.success("已封禁", null);
    }

    @PostMapping("/users/{userId}/unban")
    public ApiResponse<Void> unbanUser(@CurrentUser LoginUser loginUser, @PathVariable Long userId) {
        assertAdmin(loginUser);
        adminManagementService.unbanUser(userId);
        return ApiResponse.success("已解除封禁", null);
    }

    @GetMapping("/messages/search")
    public ApiResponse<MessageSearchPageVO> searchMessages(@CurrentUser LoginUser loginUser,
                                                           @RequestParam String keyword,
                                                           @RequestParam(required = false) Long conversationId,
                                                           @RequestParam(required = false) Long beforeMessageId,
                                                           @RequestParam(required = false) Integer size) {
        assertAdmin(loginUser);
        return ApiResponse.success(
            messageService.adminSearchMessages(keyword, conversationId, beforeMessageId, size));
    }

    @GetMapping("/reports")
    public ApiResponse<Map<String, Object>> reports(@CurrentUser LoginUser loginUser,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        assertAdmin(loginUser);
        return ApiResponse.success(adminReportService.listReports(page, size));
    }

    /**
     * 管理员发布系统公告（发送通知给指定用户或全部用户）
     */
    @PostMapping("/notifications/announcement")
    public ApiResponse<Void> createAnnouncement(@CurrentUser LoginUser loginUser,
                                                @RequestParam String title,
                                                @RequestParam String content,
                                                @RequestParam(required = false) List<Long> targetUserIds) {
        assertAdmin(loginUser);
        notificationService.createSystemAnnouncement(title, content, targetUserIds, loginUser.getUserId());
        return ApiResponse.success("公告已发送", null);
    }

    /**
     * 管理员查看所有通知（支持按用户筛选、分页）
     */
    @GetMapping("/notifications")
    public ApiResponse<Map<String, Object>> adminNotifications(@CurrentUser LoginUser loginUser,
                                                               @RequestParam(required = false) Long userId,
                                                               @RequestParam(required = false) String type,
                                                               @RequestParam(required = false) Boolean isRead,
                                                               @RequestParam(defaultValue = "1") Integer page,
                                                               @RequestParam(defaultValue = "50") Integer size) {
        assertAdmin(loginUser);
        return ApiResponse.success(notificationService.adminNotifications(userId, type, isRead, page, size));
    }

    /**
     * 管理员删除通知
     */
    @DeleteMapping("/notifications/{notificationId}")
    public ApiResponse<Void> adminDeleteNotification(@CurrentUser LoginUser loginUser,
                                                      @PathVariable Long notificationId) {
        assertAdmin(loginUser);
        notificationService.adminDeleteNotification(notificationId);
        return ApiResponse.success("通知已删除", null);
    }

    /**
     * 管理员清空指定用户的所有通知
     */
    @DeleteMapping("/notifications/user/{userId}/clear")
    public ApiResponse<Void> adminClearAllNotifications(@CurrentUser LoginUser loginUser,
                                                        @PathVariable Long userId) {
        assertAdmin(loginUser);
        notificationService.adminClearAllNotifications(userId);
        return ApiResponse.success("用户通知已清空", null);
    }
}
