package com.im.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.im.server.common.BusinessException;
import com.im.server.mapper.ChatMessageMapper;
import com.im.server.mapper.ConversationMapper;
import com.im.server.mapper.ConversationMemberMapper;
import com.im.server.mapper.FriendRelationMapper;
import com.im.server.mapper.GroupInviteMapper;
import com.im.server.model.dto.ClearConversationRequest;
import com.im.server.model.dto.CreateGroupRequest;
import com.im.server.model.dto.UpdateDraftRequest;
import com.im.server.model.dto.UpdateRemarkRequest;
import com.im.server.model.dto.UpdateConversationSettingsRequest;
import com.im.server.model.dto.CreateGroupInviteRequest;
import com.im.server.model.dto.GroupMuteAllRequest;
import com.im.server.model.dto.MuteGroupMemberRequest;
import com.im.server.model.dto.UpdateGroupProfileRequest;
import com.im.server.model.dto.UpdateSyncCursorRequest;
import com.im.server.model.entity.ChatMessage;
import com.im.server.model.entity.Conversation;
import com.im.server.model.entity.ConversationMember;
import com.im.server.model.entity.FriendRelation;
import com.im.server.model.entity.GroupInvite;
import com.im.server.model.enums.ConversationType;
import com.im.server.model.enums.GroupRole;
import com.im.server.model.enums.MessageType;
import com.im.server.model.vo.ChatMessageVO;
import com.im.server.model.vo.ConversationListVO;
import com.im.server.model.vo.ConversationUnreadVO;
import com.im.server.model.vo.GroupDetailVO;
import com.im.server.model.vo.GroupInviteCreatedVO;
import com.im.server.model.vo.GroupMemberVO;
import com.im.server.model.vo.MessageReplyVO;
import com.im.server.model.vo.UserSimpleVO;
import com.im.server.model.vo.WsEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ChatMessageMapper chatMessageMapper;
    private final ConversationMapper conversationMapper;
    private final ConversationMemberMapper conversationMemberMapper;
    private final FriendRelationMapper friendRelationMapper;
    private final UnreadCacheService unreadCacheService;
    private final OnlineStatusService onlineStatusService;
    private final UserService userService;
    private final WsPushService wsPushService;
    private final BlacklistService blacklistService;
    private final GroupInviteMapper groupInviteMapper;

    @Transactional
    public Conversation ensureSingleConversation(Long userId, Long targetUserId) {
        List<ConversationMember> myConversations = conversationMemberMapper.selectList(
            new LambdaQueryWrapper<ConversationMember>().eq(ConversationMember::getUserId, userId)
        );
        if (!myConversations.isEmpty()) {
            for (ConversationMember myConversation : myConversations) {
                Long conversationId = myConversation.getConversationId();
                long memberCount = conversationMemberMapper.selectCount(
                    new LambdaQueryWrapper<ConversationMember>().eq(ConversationMember::getConversationId, conversationId)
                );
                if (memberCount != 2) {
                    continue;
                }
                Conversation conversation = conversationMapper.selectById(conversationId);
                if (conversation != null && ConversationType.SINGLE.name().equals(conversation.getType())) {
                    long targetCount = conversationMemberMapper.selectCount(
                        new LambdaQueryWrapper<ConversationMember>()
                            .eq(ConversationMember::getConversationId, conversationId)
                            .eq(ConversationMember::getUserId, targetUserId)
                    );
                    if (targetCount > 0) {
                        return conversation;
                    }
                }
            }
        }

        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.SINGLE.name());
        conversation.setOwnerId(userId);
        conversation.setMuteAll(0);
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.insert(conversation);

        saveMember(conversation.getId(), userId, GroupRole.OWNER.name());
        saveMember(conversation.getId(), targetUserId, GroupRole.MEMBER.name());
        return conversation;
    }

    @Transactional
    public Conversation createGroup(Long creatorId, CreateGroupRequest request) {
        Set<Long> memberIds = new LinkedHashSet<>(request.getMemberIds());
        memberIds.add(creatorId);
        for (Long memberId : memberIds) {
            if (!memberId.equals(creatorId)) {
                blacklistService.assertNoBlockBetween(creatorId, memberId);
            }
        }

        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.GROUP.name());
        conversation.setName(request.getName());
        conversation.setAvatar(request.getAvatar());
        conversation.setNotice(null);
        conversation.setOwnerId(creatorId);
        conversation.setMuteAll(0);
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.insert(conversation);

        saveMember(conversation.getId(), creatorId, GroupRole.OWNER.name());
        memberIds.stream()
            .filter(memberId -> !memberId.equals(creatorId))
            .forEach(memberId -> saveMember(conversation.getId(), memberId, GroupRole.MEMBER.name()));
        createSystemMessage(creatorId, conversation.getId(), userService.getSimpleUser(creatorId).getNickname() + " 创建了群聊");
        return conversation;
    }

    public List<Conversation> listByUserId(Long userId) {
        List<ConversationMember> members = conversationMemberMapper.selectList(
            new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getUserId, userId)
                .isNull(ConversationMember::getDeletedAt)
        );
        List<Conversation> conversations = new ArrayList<>();
        for (ConversationMember member : members) {
            Conversation conversation = conversationMapper.selectById(member.getConversationId());
            if (conversation != null) {
                conversations.add(conversation);
            }
        }
        return conversations;
    }

    public List<ConversationListVO> listConversationViews(Long userId) {
        return listConversationViewsByArchived(userId, false);
    }

    /** 当前用户加入的群聊列表（不含单聊、归档筛选与主列表一致） */
    public List<ConversationListVO> listGroupConversationViews(Long userId) {
        return listConversationViews(userId).stream()
            .filter(v -> ConversationType.GROUP.name().equals(v.getType()))
            .toList();
    }

    /**
     * 构建单个会话的列表项（用于创建/恢复会话后直接返回，避免依赖主列表筛选条件导致查不到）。
     */
    public ConversationListVO getConversationListView(Long userId, Long conversationId) {
        Conversation conversation = getById(conversationId);
        assertUserInConversation(userId, conversationId);
        return buildConversationView(userId, conversation);
    }

    public List<ConversationListVO> listArchivedConversationViews(Long userId) {
        return listConversationViewsByArchived(userId, true);
    }

    /** 同一用户同一会话理论上只有一条 membership；异常数据下去重，保留 updatedAt 较新的一条 */
    private List<ConversationListVO> dedupeConversationListViews(List<ConversationListVO> input) {
        if (input == null || input.size() <= 1) {
            return input;
        }
        Map<Long, ConversationListVO> map = new LinkedHashMap<>();
        for (ConversationListVO vo : input) {
            if (vo.getConversationId() == null) {
                continue;
            }
            map.merge(vo.getConversationId(), vo, (a, b) -> {
                LocalDateTime ta = a.getUpdatedAt();
                LocalDateTime tb = b.getUpdatedAt();
                if (ta == null) {
                    return b;
                }
                if (tb == null) {
                    return a;
                }
                return tb.isAfter(ta) ? b : a;
            });
        }
        return new ArrayList<>(map.values());
    }

    private List<ConversationListVO> listConversationViewsByArchived(Long userId, boolean archivedOnly) {
        LambdaQueryWrapper<ConversationMember> mw = new LambdaQueryWrapper<ConversationMember>()
            .eq(ConversationMember::getUserId, userId)
            .isNull(ConversationMember::getDeletedAt);
        if (archivedOnly) {
            mw.eq(ConversationMember::getArchived, 1);
        } else {
            mw.and(w -> w.eq(ConversationMember::getArchived, 0).or().isNull(ConversationMember::getArchived));
        }
        List<ConversationMember> memberships = conversationMemberMapper.selectList(mw);
        List<ConversationListVO> result = new ArrayList<>();
        for (ConversationMember membership : memberships) {
            Conversation conversation = conversationMapper.selectById(membership.getConversationId());
            if (conversation != null) {
                result.add(buildConversationView(userId, conversation));
            }
        }
        result = dedupeConversationListViews(result);
        result.sort((a, b) -> {
            int pinnedCompare = Boolean.compare(Boolean.TRUE.equals(b.getPinned()), Boolean.TRUE.equals(a.getPinned()));
            if (pinnedCompare != 0) {
                return pinnedCompare;
            }
            if (a.getUpdatedAt() == null && b.getUpdatedAt() == null) {
                return 0;
            }
            if (a.getUpdatedAt() == null) {
                return 1;
            }
            if (b.getUpdatedAt() == null) {
                return -1;
            }
            return b.getUpdatedAt().compareTo(a.getUpdatedAt());
        });
        return result;
    }

    public List<ConversationListVO> listHiddenConversationViews(Long userId) {
        List<ConversationMember> members = conversationMemberMapper.selectList(
            new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getUserId, userId)
                .isNotNull(ConversationMember::getDeletedAt)
        );
        List<ConversationListVO> result = new ArrayList<>();
        for (ConversationMember membership : members) {
            Conversation conversation = conversationMapper.selectById(membership.getConversationId());
            if (conversation != null) {
                result.add(buildConversationView(userId, conversation));
            }
        }
        result.sort((a, b) -> {
            if (a.getUpdatedAt() == null || b.getUpdatedAt() == null) {
                return 0;
            }
            return b.getUpdatedAt().compareTo(a.getUpdatedAt());
        });
        return result;
    }

    public List<ConversationUnreadVO> listUnread(Long userId) {
        List<Conversation> conversations = listByUserId(userId);
        List<ConversationUnreadVO> unreadList = new ArrayList<>();
        for (Conversation conversation : conversations) {
            unreadList.add(new ConversationUnreadVO(
                conversation.getId(),
                unreadCacheService.getUnread(userId, conversation.getId())
            ));
        }
        return unreadList;
    }

    public void assertUserInConversation(Long userId, Long conversationId) {
        long count = conversationMemberMapper.selectCount(
            new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, conversationId)
                .eq(ConversationMember::getUserId, userId)
        );
        if (count == 0) {
            throw new BusinessException("你不在该会话中");
        }
    }

    public List<Long> listMemberIds(Long conversationId) {
        return conversationMemberMapper.selectList(
            new LambdaQueryWrapper<ConversationMember>().eq(ConversationMember::getConversationId, conversationId)
        ).stream().map(ConversationMember::getUserId).toList();
    }

    public List<Long> listVisibleMemberIds(Long conversationId) {
        return conversationMemberMapper.selectList(
            new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, conversationId)
                .isNull(ConversationMember::getDeletedAt)
        ).stream().map(ConversationMember::getUserId).toList();
    }

    public Conversation getById(Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException("会话不存在");
        }
        return conversation;
    }

    public void touchConversation(Long conversationId, Long lastMessageId, String preview) {
        Conversation conversation = getById(conversationId);
        conversation.setLastMessageId(lastMessageId);
        conversation.setLastMessagePreview(preview);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
    }

    @Transactional
    public void updateConversationSettings(Long userId, Long conversationId, UpdateConversationSettingsRequest request) {
        ConversationMember member = getRequiredMember(conversationId, userId);
        if (request.getPinned() != null) {
            member.setPinned(Boolean.TRUE.equals(request.getPinned()) ? 1 : 0);
        }
        if (request.getMuted() != null) {
            member.setMuted(Boolean.TRUE.equals(request.getMuted()) ? 1 : 0);
        }
        if (request.getArchived() != null) {
            member.setArchived(Boolean.TRUE.equals(request.getArchived()) ? 1 : 0);
        }
        conversationMemberMapper.updateById(member);
    }

    @Transactional
    public void updateConversationRemark(Long userId, Long conversationId, UpdateRemarkRequest request) {
        ConversationMember member = getRequiredMember(conversationId, userId);
        member.setRemarkName(request.getRemarkName());
        conversationMemberMapper.updateById(member);
    }

    @Transactional
    public void updateConversationDraft(Long userId, Long conversationId, UpdateDraftRequest request) {
        ConversationMember member = getRequiredMember(conversationId, userId);
        member.setDraftContent(request.getDraftContent());
        member.setDraftUpdatedAt(StringUtils.isBlank(request.getDraftContent()) ? null : LocalDateTime.now());
        conversationMemberMapper.updateById(member);
    }

    @Transactional
    public void clearConversation(Long userId, Long conversationId, ClearConversationRequest request) {
        ConversationMember member = getRequiredMember(conversationId, userId);
        Long before = request.getBeforeMessageId();
        if (before == null) {
            ChatMessage last = chatMessageMapper.selectOne(
                new LambdaQueryWrapper<ChatMessage>()
                    .select(ChatMessage::getId)
                    .eq(ChatMessage::getConversationId, conversationId)
                    .orderByDesc(ChatMessage::getId)
                    .last("limit 1")
            );
            before = last != null ? last.getId() : null;
        }
        member.setClearMessageId(before);
        member.setClearAt(LocalDateTime.now());
        conversationMemberMapper.updateById(member);
        unreadCacheService.clearUnread(userId, conversationId);
    }

    @Transactional
    public GroupDetailVO updateGroupProfile(Long operatorId, Long conversationId, UpdateGroupProfileRequest request) {
        Conversation conversation = getById(conversationId);
        assertGroupConversation(conversation);
        assertManager(operatorId, conversationId, conversation);

        if (StringUtils.isNotBlank(request.getName())) {
            conversation.setName(request.getName());
        }
        if (request.getAvatar() != null) {
            conversation.setAvatar(request.getAvatar());
        }
        if (request.getNotice() != null) {
            conversation.setNotice(request.getNotice());
            conversation.setNoticeUpdatedAt(LocalDateTime.now());
        }
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);

        GroupDetailVO detail = getGroupDetail(operatorId, conversationId);
        wsPushService.pushToUsers(new LinkedHashSet<>(listVisibleMemberIds(conversationId)),
            new WsEvent<>("GROUP_UPDATED", detail));
        createSystemMessage(operatorId, conversationId, buildProfileUpdateText(request));
        return detail;
    }

    public void markRead(Long userId, Long conversationId) {
        ConversationMember member = conversationMemberMapper.selectOne(
            new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, conversationId)
                .eq(ConversationMember::getUserId, userId)
        );
        if (member == null) {
            throw new BusinessException("你不在该会话中");
        }
        member.setLastReadAt(LocalDateTime.now());
        conversationMemberMapper.updateById(member);
        unreadCacheService.clearUnread(userId, conversationId);
    }

    @Transactional
    public void hideConversation(Long userId, Long conversationId) {
        ConversationMember member = getMember(conversationId, userId);
        if (member == null) {
            throw new BusinessException("你不在该会话中");
        }
        member.setDeletedAt(LocalDateTime.now());
        conversationMemberMapper.updateById(member);
        unreadCacheService.clearUnread(userId, conversationId);
    }

    @Transactional
    public void restoreConversation(Long conversationId) {
        List<ConversationMember> members = conversationMemberMapper.selectList(
            new LambdaQueryWrapper<ConversationMember>().eq(ConversationMember::getConversationId, conversationId)
        );
        for (ConversationMember member : members) {
            if (member.getDeletedAt() != null) {
                member.setDeletedAt(null);
                conversationMemberMapper.updateById(member);
            }
        }
    }

    @Transactional
    public void restoreConversationForUser(Long userId, Long conversationId) {
        ConversationMember member = getMember(conversationId, userId);
        if (member != null && member.getDeletedAt() != null) {
            member.setDeletedAt(null);
            conversationMemberMapper.updateById(member);
        }
    }

    @Transactional
    public void addGroupMembers(Long operatorId, Long conversationId, List<Long> memberIds) {
        Conversation conversation = getById(conversationId);
        assertGroupConversation(conversation);
        assertManager(operatorId, conversationId, conversation);

        Set<Long> uniqueMembers = new LinkedHashSet<>(memberIds);
        uniqueMembers.remove(operatorId);
        for (Long memberId : uniqueMembers) {
            blacklistService.assertNoBlockBetween(operatorId, memberId);
        }
        for (Long memberId : uniqueMembers) {
            ConversationMember member = getMember(conversationId, memberId);
            if (member == null) {
                saveMember(conversationId, memberId, GroupRole.MEMBER.name());
            } else if (member.getDeletedAt() != null) {
                member.setDeletedAt(null);
                conversationMemberMapper.updateById(member);
            }
        }
        if (!uniqueMembers.isEmpty()) {
            createSystemMessage(operatorId, conversationId, userService.getSimpleUser(operatorId).getNickname()
                + " 邀请了成员加入群聊");
        }
    }

    @Transactional
    public void removeGroupMembers(Long operatorId, Long conversationId, List<Long> memberIds) {
        Conversation conversation = getById(conversationId);
        assertGroupConversation(conversation);
        ConversationMember operatorMember = assertManager(operatorId, conversationId, conversation);

        for (Long memberId : new LinkedHashSet<>(memberIds)) {
            ConversationMember targetMember = getMember(conversationId, memberId);
            if (targetMember == null) {
                continue;
            }
            if (GroupRole.OWNER.name().equals(targetMember.getRole())) {
                throw new BusinessException("不能移除群主");
            }
            if (GroupRole.ADMIN.name().equals(targetMember.getRole())
                && !GroupRole.OWNER.name().equals(operatorMember.getRole())) {
                throw new BusinessException("管理员不能移除其他管理员");
            }
            conversationMemberMapper.delete(
                new LambdaQueryWrapper<ConversationMember>()
                    .eq(ConversationMember::getConversationId, conversationId)
                    .eq(ConversationMember::getUserId, memberId)
            );
        }
        if (!memberIds.isEmpty()) {
            createSystemMessage(operatorId, conversationId, userService.getSimpleUser(operatorId).getNickname()
                + " 移除了群成员");
        }
    }

    @Transactional
    public void leaveGroup(Long userId, Long conversationId) {
        Conversation conversation = getById(conversationId);
        assertGroupConversation(conversation);
        if (userId.equals(conversation.getOwnerId())) {
            throw new BusinessException("群主暂不支持直接退群，请先转让群主");
        }
        int deleted = conversationMemberMapper.delete(
            new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, conversationId)
                .eq(ConversationMember::getUserId, userId)
        );
        if (deleted == 0) {
            throw new BusinessException("你不在该群聊中");
        }
        unreadCacheService.clearUnread(userId, conversationId);
        createSystemMessage(userId, conversationId, userService.getSimpleUser(userId).getNickname() + " 退出了群聊");
    }

    public ConversationMember getMember(Long conversationId, Long userId) {
        return conversationMemberMapper.selectOne(
            new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, conversationId)
                .eq(ConversationMember::getUserId, userId)
        );
    }

    public ConversationMember getRequiredMemberView(Long conversationId, Long userId) {
        return getRequiredMember(conversationId, userId);
    }

    @Transactional
    public void addAdmins(Long operatorId, Long conversationId, List<Long> memberIds) {
        Conversation conversation = getById(conversationId);
        assertGroupConversation(conversation);
        assertOwner(operatorId, conversation);
        for (Long memberId : new LinkedHashSet<>(memberIds)) {
            ConversationMember member = getRequiredMember(conversationId, memberId);
            if (!GroupRole.OWNER.name().equals(member.getRole())) {
                member.setRole(GroupRole.ADMIN.name());
                conversationMemberMapper.updateById(member);
            }
        }
        if (!memberIds.isEmpty()) {
            createSystemMessage(operatorId, conversationId, userService.getSimpleUser(operatorId).getNickname()
                + " 设置了管理员");
        }
    }

    @Transactional
    public void removeAdmins(Long operatorId, Long conversationId, List<Long> memberIds) {
        Conversation conversation = getById(conversationId);
        assertGroupConversation(conversation);
        assertOwner(operatorId, conversation);
        for (Long memberId : new LinkedHashSet<>(memberIds)) {
            ConversationMember member = getRequiredMember(conversationId, memberId);
            if (!GroupRole.OWNER.name().equals(member.getRole())) {
                member.setRole(GroupRole.MEMBER.name());
                conversationMemberMapper.updateById(member);
            }
        }
        if (!memberIds.isEmpty()) {
            createSystemMessage(operatorId, conversationId, userService.getSimpleUser(operatorId).getNickname()
                + " 取消了管理员");
        }
    }

    @Transactional
    public void transferOwner(Long operatorId, Long conversationId, Long targetUserId) {
        Conversation conversation = getById(conversationId);
        assertGroupConversation(conversation);
        assertOwner(operatorId, conversation);

        ConversationMember ownerMember = getRequiredMember(conversationId, operatorId);
        ConversationMember targetMember = getRequiredMember(conversationId, targetUserId);

        ownerMember.setRole(GroupRole.ADMIN.name());
        targetMember.setRole(GroupRole.OWNER.name());
        conversationMemberMapper.updateById(ownerMember);
        conversationMemberMapper.updateById(targetMember);

        conversation.setOwnerId(targetUserId);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
        createSystemMessage(operatorId, conversationId, userService.getSimpleUser(operatorId).getNickname()
            + " 将群主转让给了 " + userService.getSimpleUser(targetUserId).getNickname());
    }

    public GroupDetailVO getGroupDetail(Long userId, Long conversationId) {
        assertUserInConversation(userId, conversationId);
        Conversation conversation = getById(conversationId);
        assertGroupConversation(conversation);
        List<GroupMemberVO> members = buildGroupMembers(conversationId, userId);
        return GroupDetailVO.builder()
            .conversationId(conversation.getId())
            .name(conversation.getName())
            .avatar(conversation.getAvatar())
            .remarkName(getRequiredMember(conversationId, userId).getRemarkName())
            .notice(conversation.getNotice())
            .ownerId(conversation.getOwnerId())
            .muteAll(Integer.valueOf(1).equals(conversation.getMuteAll()))
            .memberCount(members.size())
            .noticeUpdatedAt(conversation.getNoticeUpdatedAt())
            .updatedAt(conversation.getUpdatedAt())
            .members(members)
            .build();
    }

    public List<GroupMemberVO> listGroupMemberViews(Long userId, Long conversationId) {
        assertUserInConversation(userId, conversationId);
        Conversation conversation = getById(conversationId);
        assertGroupConversation(conversation);
        return buildGroupMembers(conversationId, userId);
    }

    private void saveMember(Long conversationId, Long userId, String role) {
        ConversationMember member = new ConversationMember();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setRole(role);
        member.setRemarkName(null);
        member.setPinned(0);
        member.setMuted(0);
        member.setDraftContent(null);
        member.setDraftUpdatedAt(null);
        member.setClearMessageId(null);
        member.setClearAt(null);
        member.setLastReadAt(LocalDateTime.now());
        member.setDeletedAt(null);
        member.setArchived(0);
        member.setSpeakMutedUntil(null);
        member.setSyncCursorMessageId(null);
        member.setCreatedAt(LocalDateTime.now());
        conversationMemberMapper.insert(member);
    }

    private void createSystemMessage(Long operatorId, Long conversationId, String content) {
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setSenderId(operatorId);
        message.setType(MessageType.SYSTEM.name());
        message.setContent(content);
        message.setReadCount(0);
        message.setDeliveredCount(0);
        message.setFavoriteCount(0);
        message.setEdited(0);
        message.setMentionAll(0);
        message.setRecalled(0);
        message.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(message);

        touchConversation(conversationId, message.getId(), "[系统消息]");
        ChatMessageVO messageVO = ChatMessageVO.builder()
            .id(message.getId())
            .conversationId(conversationId)
            .senderId(operatorId)
            .senderNickname(userService.getSimpleUser(operatorId).getNickname())
            .senderAvatar(userService.getSimpleUser(operatorId).getAvatar())
            .type(message.getType())
            .content(content)
            .replyMessage((MessageReplyVO) null)
            .readCount(0)
            .deliveredCount(0)
            .favoriteCount(0)
            .edited(0)
            .mentionAll(false)
            .recalled(0)
            .createdAt(message.getCreatedAt())
            .build();

        for (Long memberId : listVisibleMemberIds(conversationId)) {
            if (!memberId.equals(operatorId)) {
                unreadCacheService.incrementUnread(memberId, conversationId);
                wsPushService.pushToUser(memberId, new WsEvent<>("MESSAGE", messageVO));
            }
        }
    }

    private String buildProfileUpdateText(UpdateGroupProfileRequest request) {
        List<String> changes = new ArrayList<>();
        if (StringUtils.isNotBlank(request.getName())) {
            changes.add("群名称");
        }
        if (request.getAvatar() != null) {
            changes.add("群头像");
        }
        if (request.getNotice() != null) {
            changes.add("群公告");
        }
        if (changes.isEmpty()) {
            return "群资料已更新";
        }
        return "更新了" + String.join("、", changes);
    }

    private List<GroupMemberVO> buildGroupMembers(Long conversationId, Long viewerUserId) {
        List<ConversationMember> members = conversationMemberMapper.selectList(
            new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, conversationId)
                .isNull(ConversationMember::getDeletedAt)
        );
        List<GroupMemberVO> result = new ArrayList<>();
        for (ConversationMember member : members) {
            UserSimpleVO user = userService.getSimpleUser(member.getUserId());
            result.add(GroupMemberVO.builder()
                .userId(user.getUserId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .phone(user.getPhone())
                .email(user.getEmail())
                .role(member.getRole())
                .online(onlineStatusService.isOnline(member.getUserId()))
                .blockedByMe(blacklistService.isBlocked(viewerUserId, member.getUserId()))
                .hasBlockedMe(blacklistService.isBlocked(member.getUserId(), viewerUserId))
                .build());
        }
        return result;
    }

    private ConversationListVO buildConversationView(Long currentUserId, Conversation conversation) {
        String displayName = conversation.getName();
        String displayAvatar = conversation.getAvatar();
        Long targetUserId = null;
        ConversationMember currentMember = getRequiredMember(conversation.getId(), currentUserId);
        String remarkName = currentMember.getRemarkName();

        if (ConversationType.SINGLE.name().equals(conversation.getType())) {
            UserSimpleVO otherUser = userService.findOtherUser(currentUserId, listMemberIds(conversation.getId()));
            String friendAlias = getFriendAliasName(currentUserId, otherUser.getUserId());
            displayName = StringUtils.defaultIfBlank(remarkName,
                StringUtils.defaultIfBlank(friendAlias, otherUser.getNickname()));
            displayAvatar = otherUser.getAvatar();
            targetUserId = otherUser.getUserId();
        } else {
            List<Long> visibleMemberIds = listVisibleMemberIds(conversation.getId());
            if (StringUtils.isNotBlank(remarkName)) {
                displayName = remarkName;
            } else if (StringUtils.isBlank(displayName)) {
                displayName = "群聊(" + visibleMemberIds.size() + ")";
            }
        }

        return ConversationListVO.builder()
            .conversationId(conversation.getId())
            .type(conversation.getType())
            .displayName(displayName)
            .displayAvatar(displayAvatar)
            .remarkName(remarkName)
            .notice(conversation.getNotice())
            .ownerId(conversation.getOwnerId())
            .targetUserId(targetUserId)
            .lastMessageId(conversation.getLastMessageId())
            .lastMessagePreview(conversation.getLastMessagePreview())
            .unreadCount(unreadCacheService.getUnread(currentUserId, conversation.getId()))
            .memberCount(listVisibleMemberIds(conversation.getId()).size())
            .pinned(currentMember.getPinned() != null && currentMember.getPinned() == 1)
            .muted(currentMember.getMuted() != null && currentMember.getMuted() == 1)
            .archived(currentMember.getArchived() != null && currentMember.getArchived() == 1)
            .draftContent(currentMember.getDraftContent())
            .draftUpdatedAt(currentMember.getDraftUpdatedAt())
            .updatedAt(conversation.getUpdatedAt())
            .build();
    }

    private void assertGroupConversation(Conversation conversation) {
        if (!ConversationType.GROUP.name().equals(conversation.getType())) {
            throw new BusinessException("该会话不是群聊");
        }
    }

    private void assertOwner(Long operatorId, Conversation conversation) {
        if (!operatorId.equals(conversation.getOwnerId())) {
            throw new BusinessException("只有群主可以执行该操作");
        }
    }

    private ConversationMember assertManager(Long operatorId, Long conversationId, Conversation conversation) {
        ConversationMember operatorMember = getRequiredMember(conversationId, operatorId);
        if (!GroupRole.OWNER.name().equals(operatorMember.getRole())
            && !GroupRole.ADMIN.name().equals(operatorMember.getRole())) {
            throw new BusinessException("只有群主或管理员可以执行该操作");
        }
        return operatorMember;
    }

    private ConversationMember getRequiredMember(Long conversationId, Long userId) {
        ConversationMember member = getMember(conversationId, userId);
        if (member == null) {
            throw new BusinessException("群成员不存在");
        }
        return member;
    }

    private String getFriendAliasName(Long userId, Long friendUserId) {
        FriendRelation relation = friendRelationMapper.selectOne(
            new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getUserId, userId)
                .eq(FriendRelation::getFriendUserId, friendUserId)
        );
        return relation == null ? null : relation.getAliasName();
    }

    public void assertUserCanSpeakInGroup(Long userId, Long conversationId, MessageType type) {
        if (type == MessageType.SYSTEM) {
            return;
        }
        Conversation conv = getById(conversationId);
        if (!ConversationType.GROUP.name().equals(conv.getType())) {
            return;
        }
        ConversationMember self = getRequiredMember(conversationId, userId);
        if (!userId.equals(conv.getOwnerId())) {
            if (self.getSpeakMutedUntil() != null && self.getSpeakMutedUntil().isAfter(LocalDateTime.now())) {
                throw new BusinessException("你已被禁言");
            }
        }
        if (Integer.valueOf(1).equals(conv.getMuteAll())) {
            if (!GroupRole.OWNER.name().equals(self.getRole()) && !GroupRole.ADMIN.name().equals(self.getRole())) {
                throw new BusinessException("群主或管理员已开启全员禁言");
            }
        }
    }

    @Transactional
    public void setGroupMuteAll(Long operatorId, Long conversationId, GroupMuteAllRequest request) {
        Conversation conversation = getById(conversationId);
        assertGroupConversation(conversation);
        assertManager(operatorId, conversationId, conversation);
        conversation.setMuteAll(Boolean.TRUE.equals(request.getMuteAll()) ? 1 : 0);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
        createSystemMessage(operatorId, conversationId,
            Boolean.TRUE.equals(request.getMuteAll()) ? "群主或管理员开启了全员禁言" : "群主或管理员关闭了全员禁言");
    }

    @Transactional
    public void muteGroupMember(Long operatorId, Long conversationId, MuteGroupMemberRequest request) {
        Conversation conversation = getById(conversationId);
        assertGroupConversation(conversation);
        assertManager(operatorId, conversationId, conversation);
        if (request.getUserId().equals(conversation.getOwnerId())) {
            throw new BusinessException("不能禁言群主");
        }
        ConversationMember target = getRequiredMember(conversationId, request.getUserId());
        target.setSpeakMutedUntil(request.getMutedUntil());
        conversationMemberMapper.updateById(target);
        createSystemMessage(operatorId, conversationId,
            userService.getSimpleUser(operatorId).getNickname() + " 更新了成员禁言状态");
    }

    @Transactional
    public GroupInviteCreatedVO createGroupInvite(Long operatorId, Long conversationId, CreateGroupInviteRequest request) {
        Conversation conversation = getById(conversationId);
        assertGroupConversation(conversation);
        assertManager(operatorId, conversationId, conversation);
        String token = UUID.randomUUID().toString().replace("-", "");
        GroupInvite invite = new GroupInvite();
        invite.setConversationId(conversationId);
        invite.setToken(token);
        invite.setCreatorId(operatorId);
        invite.setExpireAt(request.getExpireHours() == null ? null
            : LocalDateTime.now().plusHours(request.getExpireHours()));
        invite.setMaxUses(request.getMaxUses());
        invite.setUsedCount(0);
        invite.setCreatedAt(LocalDateTime.now());
        groupInviteMapper.insert(invite);
        return GroupInviteCreatedVO.builder()
            .token(token)
            .expireAt(invite.getExpireAt())
            .maxUses(invite.getMaxUses())
            .build();
    }

    @Transactional
    public GroupDetailVO joinGroupByInvite(Long userId, String tokenPlain) {
        GroupInvite invite = groupInviteMapper.selectOne(
            new LambdaQueryWrapper<GroupInvite>().eq(GroupInvite::getToken, tokenPlain)
        );
        if (invite == null) {
            throw new BusinessException("邀请无效");
        }
        if (invite.getExpireAt() != null && invite.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("邀请已过期");
        }
        if (invite.getMaxUses() != null && invite.getUsedCount() != null
            && invite.getUsedCount() >= invite.getMaxUses()) {
            throw new BusinessException("邀请次数已用尽");
        }
        Conversation conversation = getById(invite.getConversationId());
        assertGroupConversation(conversation);
        ConversationMember existing = getMember(conversation.getId(), userId);
        if (existing != null && existing.getDeletedAt() == null) {
            return getGroupDetail(userId, conversation.getId());
        }
        if (existing == null) {
            saveMember(conversation.getId(), userId, GroupRole.MEMBER.name());
        } else {
            existing.setDeletedAt(null);
            conversationMemberMapper.updateById(existing);
        }
        invite.setUsedCount(invite.getUsedCount() == null ? 1 : invite.getUsedCount() + 1);
        groupInviteMapper.updateById(invite);
        createSystemMessage(userId, conversation.getId(),
            userService.getSimpleUser(userId).getNickname() + " 通过邀请链接加入了群聊");
        return getGroupDetail(userId, conversation.getId());
    }

    @Transactional
    public void updateSyncCursor(Long userId, Long conversationId, UpdateSyncCursorRequest request) {
        ConversationMember member = getRequiredMember(conversationId, userId);
        member.setSyncCursorMessageId(request.getMessageId());
        conversationMemberMapper.updateById(member);
    }
}
