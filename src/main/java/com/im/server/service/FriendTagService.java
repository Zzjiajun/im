package com.im.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.im.server.common.BusinessException;
import com.im.server.mapper.FriendRelationMapper;
import com.im.server.mapper.FriendTagMapper;
import com.im.server.mapper.FriendTagMemberMapper;
import com.im.server.model.dto.AssignFriendTagsRequest;
import com.im.server.model.dto.CreateFriendTagRequest;
import com.im.server.model.entity.FriendRelation;
import com.im.server.model.entity.FriendTag;
import com.im.server.model.entity.FriendTagMember;
import com.im.server.model.vo.FriendTagVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FriendTagService {

    private final FriendTagMapper friendTagMapper;
    private final FriendTagMemberMapper friendTagMemberMapper;
    private final FriendRelationMapper friendRelationMapper;

    @Transactional
    public FriendTagVO createTag(Long userId, CreateFriendTagRequest request) {
        FriendTag tag = new FriendTag();
        tag.setUserId(userId);
        tag.setName(StringUtils.trimToEmpty(request.getName()));
        tag.setSortOrder(0);
        tag.setCreatedAt(LocalDateTime.now());
        friendTagMapper.insert(tag);
        return toVo(tag, 0);
    }

    public List<FriendTagVO> listTags(Long userId) {
        List<FriendTag> tags = friendTagMapper.selectList(
            new LambdaQueryWrapper<FriendTag>()
                .eq(FriendTag::getUserId, userId)
                .orderByAsc(FriendTag::getSortOrder)
                .orderByAsc(FriendTag::getId)
        );
        List<FriendTagVO> result = new ArrayList<>();
        for (FriendTag tag : tags) {
            long cnt = friendTagMemberMapper.selectCount(
                new LambdaQueryWrapper<FriendTagMember>().eq(FriendTagMember::getTagId, tag.getId())
            );
            result.add(toVo(tag, (int) cnt));
        }
        return result;
    }

    @Transactional
    public void deleteTag(Long userId, Long tagId) {
        FriendTag tag = requireOwnedTag(userId, tagId);
        friendTagMemberMapper.delete(
            new LambdaQueryWrapper<FriendTagMember>().eq(FriendTagMember::getTagId, tag.getId())
        );
        friendTagMapper.deleteById(tag.getId());
    }

    @Transactional
    public void assignFriendTags(Long userId, AssignFriendTagsRequest request) {
        assertFriendRelation(userId, request.getFriendUserId());
        List<Long> tagIds = request.getTagIds() == null ? List.of() : request.getTagIds();
        Set<Long> unique = new HashSet<>(tagIds);
        for (Long tagId : unique) {
            requireOwnedTag(userId, tagId);
        }
        List<FriendTag> myTags = friendTagMapper.selectList(
            new LambdaQueryWrapper<FriendTag>().eq(FriendTag::getUserId, userId)
        );
        Set<Long> myTagIds = myTags.stream().map(FriendTag::getId).collect(Collectors.toSet());
        friendTagMemberMapper.delete(
            new LambdaQueryWrapper<FriendTagMember>()
                .eq(FriendTagMember::getFriendUserId, request.getFriendUserId())
                .in(FriendTagMember::getTagId, myTagIds)
        );
        for (Long tagId : unique) {
            FriendTagMember m = new FriendTagMember();
            m.setTagId(tagId);
            m.setFriendUserId(request.getFriendUserId());
            m.setCreatedAt(LocalDateTime.now());
            friendTagMemberMapper.insert(m);
        }
    }

    public Map<Long, List<Long>> mapFriendToTagIds(Long ownerUserId, List<Long> friendUserIds) {
        Map<Long, List<Long>> map = new HashMap<>();
        if (friendUserIds.isEmpty()) {
            return map;
        }
        List<FriendTag> tags = friendTagMapper.selectList(
            new LambdaQueryWrapper<FriendTag>().eq(FriendTag::getUserId, ownerUserId)
        );
        if (tags.isEmpty()) {
            return map;
        }
        Set<Long> tagIdSet = tags.stream().map(FriendTag::getId).collect(Collectors.toSet());
        List<FriendTagMember> members = friendTagMemberMapper.selectList(
            new LambdaQueryWrapper<FriendTagMember>().in(FriendTagMember::getTagId, tagIdSet)
        );
        for (Long fid : friendUserIds) {
            map.put(fid, new ArrayList<>());
        }
        for (FriendTagMember m : members) {
            if (map.containsKey(m.getFriendUserId())) {
                map.get(m.getFriendUserId()).add(m.getTagId());
            }
        }
        return map;
    }

    public boolean isFriendInTag(Long ownerUserId, Long friendUserId, Long tagId) {
        requireOwnedTag(ownerUserId, tagId);
        long c = friendTagMemberMapper.selectCount(
            new LambdaQueryWrapper<FriendTagMember>()
                .eq(FriendTagMember::getTagId, tagId)
                .eq(FriendTagMember::getFriendUserId, friendUserId)
        );
        return c > 0;
    }

    private void assertFriendRelation(Long userId, Long friendUserId) {
        long c = friendRelationMapper.selectCount(
            new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getUserId, userId)
                .eq(FriendRelation::getFriendUserId, friendUserId)
        );
        if (c == 0) {
            throw new BusinessException("对方不是你的好友");
        }
    }

    private FriendTag requireOwnedTag(Long userId, Long tagId) {
        FriendTag tag = friendTagMapper.selectById(tagId);
        if (tag == null || !tag.getUserId().equals(userId)) {
            throw new BusinessException("标签不存在");
        }
        return tag;
    }

    private FriendTagVO toVo(FriendTag tag, int memberCount) {
        return FriendTagVO.builder()
            .tagId(tag.getId())
            .name(tag.getName())
            .sortOrder(tag.getSortOrder())
            .memberCount(memberCount)
            .createdAt(tag.getCreatedAt())
            .build();
    }
}
