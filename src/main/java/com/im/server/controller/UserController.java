package com.im.server.controller;

import com.im.server.common.ApiResponse;
import com.im.server.common.CurrentUser;
import com.im.server.model.dto.RegisterPushTokenRequest;
import com.im.server.model.vo.UserOnlineStatusVO;
import com.im.server.model.vo.UserSearchVO;
import com.im.server.model.vo.UserSimpleVO;
import com.im.server.security.LoginUser;
import com.im.server.service.BlacklistService;
import com.im.server.service.ConversationService;
import com.im.server.service.FriendService;
import com.im.server.service.OnlineStatusService;
import com.im.server.service.UserService;
import jakarta.validation.Valid;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final OnlineStatusService onlineStatusService;
    private final BlacklistService blacklistService;
    private final FriendService friendService;
    private final ConversationService conversationService;

    @GetMapping("/search")
    public ApiResponse<List<UserSearchVO>> search(@CurrentUser LoginUser loginUser,
                                                  @RequestParam String keyword) {
        return ApiResponse.success(userService.search(loginUser.getUserId(), keyword));
    }

    @GetMapping("/online-status")
    public ApiResponse<List<UserOnlineStatusVO>> onlineStatus(@CurrentUser LoginUser loginUser,
                                                              @RequestParam List<Long> userIds) {
        return ApiResponse.success(onlineStatusService.listAllowedStatuses(
            userIds, allowedOnlineStatusUserIds(loginUser.getUserId())
        ));
    }

    @PostMapping("/blacklist/{targetUserId}")
    public ApiResponse<Void> addBlacklist(@CurrentUser LoginUser loginUser,
                                          @PathVariable Long targetUserId) {
        blacklistService.add(loginUser.getUserId(), targetUserId);
        return ApiResponse.success("已加入黑名单", null);
    }

    @DeleteMapping("/blacklist/{targetUserId}")
    public ApiResponse<Void> removeBlacklist(@CurrentUser LoginUser loginUser,
                                             @PathVariable Long targetUserId) {
        blacklistService.remove(loginUser.getUserId(), targetUserId);
        return ApiResponse.success("已移出黑名单", null);
    }

    @GetMapping("/blacklist")
    public ApiResponse<List<UserSimpleVO>> blacklist(@CurrentUser LoginUser loginUser) {
        return ApiResponse.success(blacklistService.list(loginUser.getUserId()));
    }

    @PostMapping("/push-token")
    public ApiResponse<Void> registerPushToken(@CurrentUser LoginUser loginUser,
                                               @Valid @RequestBody RegisterPushTokenRequest request) {
        userService.registerPushToken(loginUser.getUserId(), request);
        return ApiResponse.success("推送 Token 已登记", null);
    }

    private Set<Long> allowedOnlineStatusUserIds(Long currentUserId) {
        Set<Long> allowed = new LinkedHashSet<>();
        allowed.add(currentUserId);
        allowed.addAll(friendService.listFriendIds(currentUserId));
        // 批量查询所有会话的可见成员（替代逐会话查询，避免 N+1）
        allowed.addAll(conversationService.listAllVisibleMemberIds(currentUserId));
        return allowed;
    }
}
