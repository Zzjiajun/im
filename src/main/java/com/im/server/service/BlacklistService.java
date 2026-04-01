package com.im.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.im.server.common.BusinessException;
import com.im.server.mapper.UserBlacklistMapper;
import com.im.server.model.entity.UserBlacklist;
import com.im.server.model.vo.UserSimpleVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BlacklistService {

    private final UserBlacklistMapper userBlacklistMapper;
    private final UserService userService;

    @Transactional
    public void add(Long userId, Long blockedUserId) {
        if (userId.equals(blockedUserId)) {
            throw new BusinessException("不能拉黑自己");
        }
        userService.getUser(blockedUserId);
        long count = userBlacklistMapper.selectCount(
            new LambdaQueryWrapper<UserBlacklist>()
                .eq(UserBlacklist::getUserId, userId)
                .eq(UserBlacklist::getBlockedUserId, blockedUserId)
        );
        if (count > 0) {
            return;
        }
        UserBlacklist blacklist = new UserBlacklist();
        blacklist.setUserId(userId);
        blacklist.setBlockedUserId(blockedUserId);
        blacklist.setCreatedAt(LocalDateTime.now());
        userBlacklistMapper.insert(blacklist);
    }

    @Transactional
    public void remove(Long userId, Long blockedUserId) {
        userBlacklistMapper.delete(
            new LambdaQueryWrapper<UserBlacklist>()
                .eq(UserBlacklist::getUserId, userId)
                .eq(UserBlacklist::getBlockedUserId, blockedUserId)
        );
    }

    public List<UserSimpleVO> list(Long userId) {
        List<UserBlacklist> blacklists = userBlacklistMapper.selectList(
            new LambdaQueryWrapper<UserBlacklist>()
                .eq(UserBlacklist::getUserId, userId)
                .orderByDesc(UserBlacklist::getCreatedAt)
        );
        List<UserSimpleVO> result = new ArrayList<>();
        for (UserBlacklist blacklist : blacklists) {
            result.add(userService.getSimpleUser(blacklist.getBlockedUserId()));
        }
        return result;
    }

    public boolean isBlocked(Long userId, Long blockedUserId) {
        long count = userBlacklistMapper.selectCount(
            new LambdaQueryWrapper<UserBlacklist>()
                .eq(UserBlacklist::getUserId, userId)
                .eq(UserBlacklist::getBlockedUserId, blockedUserId)
        );
        return count > 0;
    }

    public void assertNoBlockBetween(Long userId, Long targetUserId) {
        if (isBlocked(userId, targetUserId)) {
            throw new BusinessException("你已将对方加入黑名单");
        }
        if (isBlocked(targetUserId, userId)) {
            throw new BusinessException("你已被对方拉黑");
        }
    }

    public boolean isBlockedEitherWay(Long userId, Long targetUserId) {
        return isBlocked(userId, targetUserId) || isBlocked(targetUserId, userId);
    }
}
