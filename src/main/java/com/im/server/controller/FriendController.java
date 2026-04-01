package com.im.server.controller;

import com.im.server.common.ApiResponse;
import com.im.server.common.CurrentUser;
import com.im.server.model.dto.AssignFriendTagsRequest;
import com.im.server.model.dto.CreateFriendTagRequest;
import com.im.server.model.dto.HandleFriendRequestDTO;
import com.im.server.model.dto.SendFriendRequestDTO;
import com.im.server.model.dto.UpdateRemarkRequest;
import com.im.server.model.entity.FriendRequest;
import com.im.server.model.vo.FriendTagVO;
import com.im.server.model.vo.UserSimpleVO;
import com.im.server.security.LoginUser;
import com.im.server.service.FriendService;
import com.im.server.service.FriendTagService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;
    private final FriendTagService friendTagService;

    @PostMapping("/request")
    public ApiResponse<Void> sendRequest(@CurrentUser LoginUser loginUser,
                                         @Valid @RequestBody SendFriendRequestDTO request) {
        log.info("[friends] POST /api/friends/request fromUserId={} toUserId={} remarkPresent={}",
                loginUser.getUserId(), request.getToUserId(), request.getRemark() != null);
        friendService.sendRequest(loginUser.getUserId(), request);
        return ApiResponse.success("已发送好友申请", null);
    }

    @PostMapping("/handle")
    public ApiResponse<Void> handleRequest(@CurrentUser LoginUser loginUser,
                                           @Valid @RequestBody HandleFriendRequestDTO request) {
        log.info("[friends] POST /api/friends/handle userId={} requestId={} accept={}",
                loginUser.getUserId(), request.getRequestId(), request.getAccept());
        friendService.handleRequest(loginUser.getUserId(), request);
        return ApiResponse.success("处理成功", null);
    }

    @GetMapping("/list")
    public ApiResponse<List<UserSimpleVO>> list(@CurrentUser LoginUser loginUser,
                                                @RequestParam(required = false) Long tagId) {
        log.info("[friends] GET /api/friends/list userId={} tagId={}", loginUser.getUserId(), tagId);
        return ApiResponse.success(friendService.listFriends(loginUser.getUserId(), tagId));
    }

    @PostMapping("/tags")
    public ApiResponse<FriendTagVO> createTag(@CurrentUser LoginUser loginUser,
                                              @Valid @RequestBody CreateFriendTagRequest request) {
        return ApiResponse.success(friendTagService.createTag(loginUser.getUserId(), request));
    }

    @GetMapping("/tags")
    public ApiResponse<List<FriendTagVO>> listTags(@CurrentUser LoginUser loginUser) {
        return ApiResponse.success(friendTagService.listTags(loginUser.getUserId()));
    }

    @DeleteMapping("/tags/{tagId}")
    public ApiResponse<Void> deleteTag(@CurrentUser LoginUser loginUser,
                                         @PathVariable Long tagId) {
        friendTagService.deleteTag(loginUser.getUserId(), tagId);
        return ApiResponse.success("标签已删除", null);
    }

    @PostMapping("/tags/assign")
    public ApiResponse<Void> assignTags(@CurrentUser LoginUser loginUser,
                                        @Valid @RequestBody AssignFriendTagsRequest request) {
        friendTagService.assignFriendTags(loginUser.getUserId(), request);
        return ApiResponse.success("标签已更新", null);
    }

    @GetMapping("/requests")
    public ApiResponse<List<FriendRequest>> requests(@CurrentUser LoginUser loginUser) {
        log.info("[friends] GET /api/friends/requests userId={}", loginUser.getUserId());
        return ApiResponse.success(friendService.listRequests(loginUser.getUserId()));
    }

    @DeleteMapping("/{friendUserId}")
    public ApiResponse<Void> delete(@CurrentUser LoginUser loginUser,
                                    @PathVariable Long friendUserId) {
        friendService.deleteFriend(loginUser.getUserId(), friendUserId);
        return ApiResponse.success("删除好友成功", null);
    }

    @PostMapping("/{friendUserId}/remark")
    public ApiResponse<Void> updateRemark(@CurrentUser LoginUser loginUser,
                                          @PathVariable Long friendUserId,
                                          @RequestBody UpdateRemarkRequest request) {
        friendService.updateRemark(loginUser.getUserId(), friendUserId, request);
        return ApiResponse.success("好友备注已更新", null);
    }
}
