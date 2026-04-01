package com.im.server.controller;

import com.im.server.common.ApiResponse;
import com.im.server.common.BusinessException;
import com.im.server.common.CurrentUser;
import com.im.server.model.vo.AdminDashboardVO;
import com.im.server.model.vo.AdminUserPageVO;
import com.im.server.model.vo.MessageReportAdminVO;
import com.im.server.model.vo.MessageSearchPageVO;
import com.im.server.security.LoginUser;
import com.im.server.service.AdminManagementService;
import com.im.server.service.AdminReportService;
import com.im.server.service.MessageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminReportService adminReportService;
    private final AdminManagementService adminManagementService;
    private final MessageService messageService;

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
    public ApiResponse<List<MessageReportAdminVO>> reports(@CurrentUser LoginUser loginUser,
                                                           @RequestParam(defaultValue = "50") int limit) {
        assertAdmin(loginUser);
        return ApiResponse.success(adminReportService.listReports(limit));
    }
}
