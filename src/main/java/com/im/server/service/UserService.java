package com.im.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.im.server.common.BusinessException;
import com.im.server.mapper.FriendRelationMapper;
import com.im.server.mapper.UserMapper;
import com.im.server.mapper.UserPushTokenMapper;
import com.im.server.model.dto.RegisterPushTokenRequest;
import com.im.server.model.dto.UpdateProfileRequest;
import com.im.server.model.entity.FriendRelation;
import com.im.server.model.entity.User;
import com.im.server.model.entity.UserPushToken;
import com.im.server.model.vo.UserSearchVO;
import com.im.server.model.vo.UserSimpleVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final FriendRelationMapper friendRelationMapper;
    private final UserPushTokenMapper userPushTokenMapper;

    /** 本地 Caffeine 缓存：用户基本信息，TTL 5 分钟 */
    private Cache<Long, UserSimpleVO> simpleUserCache;

    @PostConstruct
    void initCache() {
        this.simpleUserCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats()
            .build();
    }

    public List<UserSearchVO> search(Long currentUserId, String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
            .ne(User::getId, currentUserId)
            .and(q -> q.like(User::getNickname, keyword)
                .or()
                .like(User::getPhone, keyword)
                .or()
                .like(User::getEmail, keyword))
            .last("limit 20");
        List<User> users = userMapper.selectList(wrapper);

        List<UserSearchVO> result = new ArrayList<>();
        for (User user : users) {
            result.add(UserSearchVO.builder()
                .userId(user.getId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .phone(user.getPhone())
                .email(user.getEmail())
                .friend(isFriend(currentUserId, user.getId()))
                .build());
        }
        return result;
    }

    public UserSimpleVO getSimpleUser(Long userId) {
        // 1. 查缓存
        UserSimpleVO cached = simpleUserCache.getIfPresent(userId);
        if (cached != null) {
            return cached;
        }
        // 2. 查 DB
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        UserSimpleVO vo = toSimpleUser(user);
        // 3. 回填缓存（忽略 null）
        simpleUserCache.put(userId, vo);
        return vo;
    }

    /** 用户更新资料后失效缓存 */
    public void evictSimpleUserCache(Long userId) {
        simpleUserCache.invalidate(userId);
    }

    public List<UserSimpleVO> getSimpleUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<UserSimpleVO> result = new ArrayList<>();
        for (Long userId : userIds) {
            if (userId != null) {
                result.add(getSimpleUser(userId));
            }
        }
        return result;
    }

    public UserSimpleVO findOtherUser(Long currentUserId, List<Long> memberIds) {
        for (Long memberId : memberIds) {
            if (!Objects.equals(memberId, currentUserId)) {
                return getSimpleUser(memberId);
            }
        }
        throw new BusinessException("单聊对象不存在");
    }

    public User getUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setPassword(null);
        return user;
    }

    /** 获取全部用户ID（用于系统公告广播） */
    public List<Long> listAllUserIds() {
        return userMapper.selectList(
            new LambdaQueryWrapper<User>().select(User::getId)
        ).stream().map(User::getId).toList();
    }

    public User updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (StringUtils.isNotBlank(request.getNickname())) {
            String nick = StringUtils.trim(request.getNickname());
            if (nick.isEmpty()) {
                throw new BusinessException("昵称不能为空");
            }
            long dup = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                    .eq(User::getNickname, nick)
                    .ne(User::getId, userId));
            if (dup > 0) {
                throw new BusinessException("昵称已被占用");
            }
            user.setNickname(nick);
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        evictSimpleUserCache(userId);
        user.setPassword(null);
        return user;
    }

    @Transactional
    public void registerPushToken(Long userId, RegisterPushTokenRequest request) {
        UserPushToken existing = userPushTokenMapper.selectOne(
            new LambdaQueryWrapper<UserPushToken>()
                .eq(UserPushToken::getUserId, userId)
                .eq(UserPushToken::getPlatform, StringUtils.trimToEmpty(request.getPlatform()))
        );
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            UserPushToken row = new UserPushToken();
            row.setUserId(userId);
            row.setPlatform(StringUtils.trimToEmpty(request.getPlatform()));
            row.setDeviceToken(StringUtils.trimToEmpty(request.getDeviceToken()));
            row.setUpdatedAt(now);
            userPushTokenMapper.insert(row);
        } else {
            existing.setDeviceToken(StringUtils.trimToEmpty(request.getDeviceToken()));
            existing.setUpdatedAt(now);
            userPushTokenMapper.updateById(existing);
        }
    }

    private boolean isFriend(Long currentUserId, Long targetUserId) {
        long count = friendRelationMapper.selectCount(
            new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getUserId, currentUserId)
                .eq(FriendRelation::getFriendUserId, targetUserId)
        );
        return count > 0;
    }

    private UserSimpleVO toSimpleUser(User user) {
        return UserSimpleVO.builder()
            .userId(user.getId())
            .nickname(user.getNickname())
            .avatar(user.getAvatar())
            .phone(user.getPhone())
            .email(user.getEmail())
            .build();
    }
}
