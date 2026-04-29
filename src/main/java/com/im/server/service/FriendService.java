package com.im.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.im.server.common.BusinessException;
import com.im.server.mapper.FriendRelationMapper;
import com.im.server.mapper.FriendRequestMapper;
import com.im.server.mapper.UserMapper;
import com.im.server.model.dto.UpdateRemarkRequest;
import com.im.server.model.dto.HandleFriendRequestDTO;
import com.im.server.model.dto.SendFriendRequestDTO;
import com.im.server.model.entity.FriendRelation;
import com.im.server.model.entity.FriendRequest;
import com.im.server.model.entity.User;
import com.im.server.model.enums.FriendRequestStatus;
import com.im.server.model.vo.UserSimpleVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendRequestMapper friendRequestMapper;
    private final FriendRelationMapper friendRelationMapper;
    private final UserMapper userMapper;
    private final ConversationService conversationService;
    private final BlacklistService blacklistService;
    private final FriendTagService friendTagService;
    private final NotificationService notificationService;

    public void sendRequest(Long userId, SendFriendRequestDTO request) {
        log.info("FriendService.sendRequest begin fromUserId={} toUserId={}", userId, request.getToUserId());
        if (userId.equals(request.getToUserId())) {
            throw new BusinessException("不能添加自己为好友");
        }
        User user = userMapper.selectById(request.getToUserId());
        if (user == null) {
            log.warn("FriendService.sendRequest target user not found for toUserId={}", request.getToUserId());
            throw new BusinessException("目标用户不存在");
        }
        blacklistService.assertNoBlockBetween(userId, request.getToUserId());
        long count = friendRelationMapper.selectCount(
            new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getUserId, userId)
                .eq(FriendRelation::getFriendUserId, request.getToUserId())
        );
        if (count > 0) {
            throw new BusinessException("你们已经是好友");
        }

        FriendRequest friendRequest = new FriendRequest();
        friendRequest.setFromUserId(userId);
        friendRequest.setToUserId(request.getToUserId());
        friendRequest.setRemark(request.getRemark());
        friendRequest.setStatus(FriendRequestStatus.PENDING.name());
        friendRequest.setCreatedAt(LocalDateTime.now());
        friendRequest.setUpdatedAt(LocalDateTime.now());
        friendRequestMapper.insert(friendRequest);
        log.info("FriendService.sendRequest ok fromUserId={} toUserId={} newId={}",
                userId, request.getToUserId(), friendRequest.getId());

        // 发送好友申请通知
        notificationService.notifyFriendRequest(request.getToUserId(), userId, friendRequest.getId());
    }

    @Transactional
    public void handleRequest(Long userId, HandleFriendRequestDTO request) {
        FriendRequest friendRequest = friendRequestMapper.selectById(request.getRequestId());
        if (friendRequest == null) {
            throw new BusinessException("好友申请不存在");
        }
        if (!userId.equals(friendRequest.getToUserId())) {
            throw new BusinessException("无权处理该申请");
        }
        if (!FriendRequestStatus.PENDING.name().equals(friendRequest.getStatus())) {
            throw new BusinessException("该申请已处理");
        }
        blacklistService.assertNoBlockBetween(friendRequest.getFromUserId(), friendRequest.getToUserId());

        friendRequest.setStatus(request.getAccept() ? FriendRequestStatus.ACCEPTED.name() : FriendRequestStatus.REJECTED.name());
        friendRequest.setHandledAt(LocalDateTime.now());
        friendRequest.setUpdatedAt(LocalDateTime.now());
        friendRequestMapper.updateById(friendRequest);

        if (Boolean.TRUE.equals(request.getAccept())) {
            saveRelation(friendRequest.getFromUserId(), friendRequest.getToUserId());
            saveRelation(friendRequest.getToUserId(), friendRequest.getFromUserId());
            conversationService.ensureSingleConversation(friendRequest.getFromUserId(), friendRequest.getToUserId());

            // 发送好友申请通过通知
            notificationService.notifyFriendAccepted(friendRequest.getFromUserId(), friendRequest.getToUserId());
        }
    }

    public List<UserSimpleVO> listFriends(Long userId, Long tagId) {
        List<FriendRelation> relations = friendRelationMapper.selectList(
            new LambdaQueryWrapper<FriendRelation>().eq(FriendRelation::getUserId, userId)
        );
        List<Long> friendIds = relations.stream().map(FriendRelation::getFriendUserId).toList();
        java.util.Map<Long, List<Long>> tagMap = friendTagService.mapFriendToTagIds(userId, friendIds);
        List<UserSimpleVO> friends = new ArrayList<>();
        for (FriendRelation relation : relations) {
            if (tagId != null && !friendTagService.isFriendInTag(userId, relation.getFriendUserId(), tagId)) {
                continue;
            }
            User user = userMapper.selectById(relation.getFriendUserId());
            if (user != null) {
                friends.add(UserSimpleVO.builder()
                    .userId(user.getId())
                    .nickname(user.getNickname())
                    .aliasName(relation.getAliasName())
                    .avatar(user.getAvatar())
                    .phone(user.getPhone())
                    .email(user.getEmail())
                    .tagIds(tagMap.getOrDefault(user.getId(), List.of()))
                    .build());
            }
        }
        return friends;
    }

    public List<FriendRequest> listRequests(Long userId) {
        return friendRequestMapper.selectList(
            new LambdaQueryWrapper<FriendRequest>()
                .eq(FriendRequest::getToUserId, userId)
                .orderByDesc(FriendRequest::getCreatedAt)
        );
    }

    public void assertFriend(Long userId, Long targetUserId) {
        long count = friendRelationMapper.selectCount(
            new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getUserId, userId)
                .eq(FriendRelation::getFriendUserId, targetUserId)
        );
        if (count == 0) {
            throw new BusinessException("对方不是你的好友");
        }
    }

    public List<Long> listFriendIds(Long userId) {
        return friendRelationMapper.selectList(
            new LambdaQueryWrapper<FriendRelation>().eq(FriendRelation::getUserId, userId)
        ).stream().map(FriendRelation::getFriendUserId).toList();
    }

    @Transactional
    public void updateRemark(Long userId, Long friendUserId, UpdateRemarkRequest request) {
        FriendRelation relation = friendRelationMapper.selectOne(
            new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getUserId, userId)
                .eq(FriendRelation::getFriendUserId, friendUserId)
        );
        if (relation == null) {
            throw new BusinessException("好友不存在");
        }
        relation.setAliasName(request.getRemarkName());
        friendRelationMapper.updateById(relation);
    }

    public String getAliasName(Long userId, Long friendUserId) {
        FriendRelation relation = friendRelationMapper.selectOne(
            new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getUserId, userId)
                .eq(FriendRelation::getFriendUserId, friendUserId)
        );
        return relation == null ? null : relation.getAliasName();
    }

    @Transactional
    public void deleteFriend(Long userId, Long friendUserId) {
        assertFriend(userId, friendUserId);
        friendRelationMapper.delete(
            new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getUserId, userId)
                .eq(FriendRelation::getFriendUserId, friendUserId)
        );
        friendRelationMapper.delete(
            new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getUserId, friendUserId)
                .eq(FriendRelation::getFriendUserId, userId)
        );
    }

    private void saveRelation(Long userId, Long friendUserId) {
        // 避免重复插入好友关系
        long existing = friendRelationMapper.selectCount(
            new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getUserId, userId)
                .eq(FriendRelation::getFriendUserId, friendUserId)
        );
        if (existing > 0) {
            return;
        }
        FriendRelation relation = new FriendRelation();
        relation.setUserId(userId);
        relation.setFriendUserId(friendUserId);
        relation.setCreatedAt(LocalDateTime.now());
        friendRelationMapper.insert(relation);
    }
}
