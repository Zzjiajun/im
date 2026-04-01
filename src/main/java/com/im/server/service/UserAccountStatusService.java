package com.im.server.service;

import com.im.server.mapper.UserMapper;
import com.im.server.model.entity.User;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserAccountStatusService {

    private static final String KEY_PREFIX = "im:u:active:";

    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /** status==1 为正常 */
    public boolean isActive(Long userId) {
        if (userId == null) {
            return false;
        }
        try {
            String cache = stringRedisTemplate.opsForValue().get(KEY_PREFIX + userId);
            if ("1".equals(cache)) {
                return true;
            }
            if ("0".equals(cache)) {
                return false;
            }
        } catch (Exception ignored) {
            // Redis 不可用时直接查库
        }
        User u = userMapper.selectById(userId);
        boolean ok = u != null && u.getStatus() != null && u.getStatus() == 1;
        try {
            stringRedisTemplate.opsForValue().set(KEY_PREFIX + userId, ok ? "1" : "0", 60, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            /* ignore */
        }
        return ok;
    }

    public void evictCache(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(KEY_PREFIX + userId);
        } catch (Exception ignored) {
            /* ignore */
        }
    }
}
