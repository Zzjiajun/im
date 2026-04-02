package com.im.server.controller;

import com.im.server.common.ApiResponse;
import com.im.server.common.CurrentUser;
import com.im.server.model.dto.ClearConversationRequest;
import com.im.server.model.dto.CreateGroupRequest;
import com.im.server.model.dto.CreateGroupInviteRequest;
import com.im.server.model.dto.GroupMemberOperateRequest;
import com.im.server.model.dto.GroupMuteAllRequest;
import com.im.server.model.dto.JoinGroupInviteRequest;
import com.im.server.model.dto.MuteGroupMemberRequest;
import com.im.server.model.dto.TransferOwnerRequest;
import com.im.server.model.dto.UpdateConversationSettingsRequest;
import com.im.server.model.dto.UpdateDraftRequest;
import com.im.server.model.dto.UpdateGroupProfileRequest;
import com.im.server.model.dto.UpdateRemarkRequest;
import com.im.server.model.dto.UpdateSyncCursorRequest;
import com.im.server.model.vo.ConversationListVO;
import com.im.server.model.vo.ConversationUnreadVO;
import com.im.server.model.vo.GroupDetailVO;
import com.im.server.model.vo.GroupInviteCreatedVO;
import com.im.server.model.vo.GroupMemberVO;
import com.im.server.security.LoginUser;
import com.im.server.service.BlacklistService;
import com.im.server.service.ConversationService;
import com.im.server.service.FriendService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final FriendService friendService;
    private final BlacklistService blacklistService;

    @PostMapping("/single/{targetUserId}")
    public ApiResponse<ConversationListVO> createSingleConversation(@CurrentUser LoginUser loginUser,
                                                                    @PathVariable Long targetUserId) {
        friendService.assertFriend(loginUser.getUserId(), targetUserId);
        blacklistService.assertNoBlockBetween(loginUser.getUserId(), targetUserId);
        var conversation = conversationService.ensureSingleConversation(loginUser.getUserId(), targetUserId);
        conversationService.restoreConversationForUser(loginUser.getUserId(), conversation.getId());
        return ApiResponse.success(
            conversationService.getConversationListView(loginUser.getUserId(), conversation.getId()));
    }

    @PostMapping("/group")
    public ApiResponse<GroupDetailVO> createGroup(@CurrentUser LoginUser loginUser,
                                                  @Valid @RequestBody CreateGroupRequest request) {
        Long self = loginUser.getUserId();
        for (Long mid : request.getMemberIds()) {
            if (mid != null && !mid.equals(self)) {
                friendService.assertFriend(self, mid);
            }
        }
        var conversation = conversationService.createGroup(self, request);
        return ApiResponse.success(conversationService.getGroupDetail(self, conversation.getId()));
    }

    @GetMapping("/list")
    public ApiResponse<List<ConversationListVO>> list(@CurrentUser LoginUser loginUser) {
        return ApiResponse.success(conversationService.listConversationViews(loginUser.getUserId()));
    }

    @GetMapping("/groups")
    public ApiResponse<List<ConversationListVO>> myGroups(@CurrentUser LoginUser loginUser) {
        return ApiResponse.success(conversationService.listGroupConversationViews(loginUser.getUserId()));
    }

    @GetMapping("/archived")
    public ApiResponse<List<ConversationListVO>> archived(@CurrentUser LoginUser loginUser) {
        return ApiResponse.success(conversationService.listArchivedConversationViews(loginUser.getUserId()));
    }

    @GetMapping("/hidden")
    public ApiResponse<List<ConversationListVO>> hidden(@CurrentUser LoginUser loginUser) {
        return ApiResponse.success(conversationService.listHiddenConversationViews(loginUser.getUserId()));
    }

    @PostMapping("/{conversationId}/restore")
    public ApiResponse<Void> restore(@CurrentUser LoginUser loginUser,
                                     @PathVariable Long conversationId) {
        conversationService.restoreConversationForUser(loginUser.getUserId(), conversationId);
        return ApiResponse.success("会话已恢复", null);
    }

    @PostMapping("/{conversationId}/sync-cursor")
    public ApiResponse<Void> syncCursor(@CurrentUser LoginUser loginUser,
                                        @PathVariable Long conversationId,
                                        @Valid @RequestBody UpdateSyncCursorRequest request) {
        conversationService.updateSyncCursor(loginUser.getUserId(), conversationId, request);
        return ApiResponse.success("同步游标已更新", null);
    }

    @PostMapping("/{conversationId}/mute-all")
    public ApiResponse<Void> muteAll(@CurrentUser LoginUser loginUser,
                                     @PathVariable Long conversationId,
                                     @Valid @RequestBody GroupMuteAllRequest request) {
        conversationService.setGroupMuteAll(loginUser.getUserId(), conversationId, request);
        return ApiResponse.success("全员禁言状态已更新", null);
    }

    @PostMapping("/{conversationId}/members/mute")
    public ApiResponse<Void> muteMember(@CurrentUser LoginUser loginUser,
                                        @PathVariable Long conversationId,
                                        @Valid @RequestBody MuteGroupMemberRequest request) {
        conversationService.muteGroupMember(loginUser.getUserId(), conversationId, request);
        return ApiResponse.success("成员禁言状态已更新", null);
    }

    @PostMapping("/{conversationId}/invite")
    public ApiResponse<GroupInviteCreatedVO> createInvite(@CurrentUser LoginUser loginUser,
                                                          @PathVariable Long conversationId,
                                                          @RequestBody(required = false) CreateGroupInviteRequest request) {
        CreateGroupInviteRequest body = request == null ? new CreateGroupInviteRequest() : request;
        return ApiResponse.success(conversationService.createGroupInvite(loginUser.getUserId(), conversationId, body));
    }

    @PostMapping("/join-invite")
    public ApiResponse<GroupDetailVO> joinInvite(@CurrentUser LoginUser loginUser,
                                                 @Valid @RequestBody JoinGroupInviteRequest request) {
        return ApiResponse.success(conversationService.joinGroupByInvite(loginUser.getUserId(), request.getToken()));
    }

    @GetMapping("/unread")
    public ApiResponse<List<ConversationUnreadVO>> unread(@CurrentUser LoginUser loginUser) {
        return ApiResponse.success(conversationService.listUnread(loginUser.getUserId()));
    }

    @PostMapping("/{conversationId}/read")
    public ApiResponse<Void> markRead(@CurrentUser LoginUser loginUser,
                                      @PathVariable Long conversationId) {
        conversationService.markRead(loginUser.getUserId(), conversationId);
        return ApiResponse.success("已标记为已读", null);
    }

    @PostMapping("/{conversationId}/members/add")
    public ApiResponse<Void> addMembers(@CurrentUser LoginUser loginUser,
                                        @PathVariable Long conversationId,
                                        @Valid @RequestBody GroupMemberOperateRequest request) {
        Long self = loginUser.getUserId();
        for (Long mid : request.getMemberIds()) {
            if (mid != null && !mid.equals(self)) {
                friendService.assertFriend(self, mid);
            }
        }
        conversationService.addGroupMembers(self, conversationId, request.getMemberIds());
        return ApiResponse.success("已拉人进群", null);
    }

    @PostMapping("/{conversationId}/members/remove")
    public ApiResponse<Void> removeMembers(@CurrentUser LoginUser loginUser,
                                           @PathVariable Long conversationId,
                                           @Valid @RequestBody GroupMemberOperateRequest request) {
        conversationService.removeGroupMembers(loginUser.getUserId(), conversationId, request.getMemberIds());
        return ApiResponse.success("已移除群成员", null);
    }

    @PostMapping("/{conversationId}/leave")
    public ApiResponse<Void> leave(@CurrentUser LoginUser loginUser,
                                   @PathVariable Long conversationId) {
        conversationService.leaveGroup(loginUser.getUserId(), conversationId);
        return ApiResponse.success("已退出群聊", null);
    }

    @GetMapping("/{conversationId}/group-detail")
    public ApiResponse<GroupDetailVO> groupDetail(@CurrentUser LoginUser loginUser,
                                                  @PathVariable Long conversationId) {
        return ApiResponse.success(conversationService.getGroupDetail(loginUser.getUserId(), conversationId));
    }

    @PostMapping("/{conversationId}/profile")
    public ApiResponse<GroupDetailVO> updateProfile(@CurrentUser LoginUser loginUser,
                                                    @PathVariable Long conversationId,
                                                    @RequestBody UpdateGroupProfileRequest request) {
        return ApiResponse.success(conversationService.updateGroupProfile(loginUser.getUserId(), conversationId, request));
    }

    @GetMapping("/{conversationId}/members")
    public ApiResponse<List<GroupMemberVO>> members(@CurrentUser LoginUser loginUser,
                                                    @PathVariable Long conversationId) {
        return ApiResponse.success(conversationService.listGroupMemberViews(loginUser.getUserId(), conversationId));
    }

    @DeleteMapping("/{conversationId}")
    public ApiResponse<Void> delete(@CurrentUser LoginUser loginUser,
                                    @PathVariable Long conversationId) {
        conversationService.hideConversation(loginUser.getUserId(), conversationId);
        return ApiResponse.success("已删除会话", null);
    }

    /** 与 DELETE 等价；部分代理/客户端对 DELETE 支持差时用此接口 */
    @PostMapping("/{conversationId}/hide")
    public ApiResponse<Void> hide(@CurrentUser LoginUser loginUser,
                                  @PathVariable Long conversationId) {
        conversationService.hideConversation(loginUser.getUserId(), conversationId);
        return ApiResponse.success("已删除会话", null);
    }

    @PostMapping("/{conversationId}/settings")
    public ApiResponse<Void> updateSettings(@CurrentUser LoginUser loginUser,
                                            @PathVariable Long conversationId,
                                            @RequestBody UpdateConversationSettingsRequest request) {
        conversationService.updateConversationSettings(loginUser.getUserId(), conversationId, request);
        return ApiResponse.success("会话设置已更新", null);
    }

    @PostMapping("/{conversationId}/remark")
    public ApiResponse<Void> updateRemark(@CurrentUser LoginUser loginUser,
                                          @PathVariable Long conversationId,
                                          @RequestBody UpdateRemarkRequest request) {
        conversationService.updateConversationRemark(loginUser.getUserId(), conversationId, request);
        return ApiResponse.success("会话备注已更新", null);
    }

    @PostMapping("/{conversationId}/draft")
    public ApiResponse<Void> updateDraft(@CurrentUser LoginUser loginUser,
                                         @PathVariable Long conversationId,
                                         @RequestBody UpdateDraftRequest request) {
        conversationService.updateConversationDraft(loginUser.getUserId(), conversationId, request);
        return ApiResponse.success("会话草稿已更新", null);
    }

    @PostMapping("/{conversationId}/clear")
    public ApiResponse<Void> clear(@CurrentUser LoginUser loginUser,
                                   @PathVariable Long conversationId,
                                   @RequestBody(required = false) ClearConversationRequest request) {
        ClearConversationRequest body = request == null ? new ClearConversationRequest() : request;
        conversationService.clearConversation(loginUser.getUserId(), conversationId, body);
        return ApiResponse.success("聊天记录已清空", null);
    }

    @PostMapping("/{conversationId}/admins/add")
    public ApiResponse<Void> addAdmins(@CurrentUser LoginUser loginUser,
                                       @PathVariable Long conversationId,
                                       @Valid @RequestBody GroupMemberOperateRequest request) {
        conversationService.addAdmins(loginUser.getUserId(), conversationId, request.getMemberIds());
        return ApiResponse.success("已设置管理员", null);
    }

    @PostMapping("/{conversationId}/admins/remove")
    public ApiResponse<Void> removeAdmins(@CurrentUser LoginUser loginUser,
                                          @PathVariable Long conversationId,
                                          @Valid @RequestBody GroupMemberOperateRequest request) {
        conversationService.removeAdmins(loginUser.getUserId(), conversationId, request.getMemberIds());
        return ApiResponse.success("已取消管理员", null);
    }

    @PostMapping("/{conversationId}/owner/transfer")
    public ApiResponse<Void> transferOwner(@CurrentUser LoginUser loginUser,
                                           @PathVariable Long conversationId,
                                           @Valid @RequestBody TransferOwnerRequest request) {
        conversationService.transferOwner(loginUser.getUserId(), conversationId, request.getTargetUserId());
        return ApiResponse.success("群主转让成功", null);
    }
}
