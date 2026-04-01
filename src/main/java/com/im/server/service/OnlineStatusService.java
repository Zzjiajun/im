package com.im.server.service;

import com.im.server.model.vo.UserOnlineStatusVO;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class OnlineStatusService {

    private final StringRedisTemplate stringRedisTemplate;

    public OnlineStatusService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public boolean userConnected(Long userId) {
        Long count = stringRedisTemplate.opsForValue().increment(key(userId));
        return count != null && count == 1;
    }

    public boolean userDisconnected(Long userId) {
        Long count = stringRedisTemplate.opsForValue().decrement(key(userId));
        if (count == null || count <= 0) {
            stringRedisTemplate.delete(key(userId));
            return true;
        }
        return false;
    }

    public boolean isOnline(Long userId) {
        String count = stringRedisTemplate.opsForValue().get(key(userId));
        return count != null && Long.parseLong(count) > 0;
    }

    public List<UserOnlineStatusVO> listStatuses(List<Long> userIds) {
        Set<Long> deduplicated = new LinkedHashSet<>(userIds);
        List<UserOnlineStatusVO> result = new ArrayList<>();
        for (Long userId : deduplicated) {
            if (userId != null) {
                result.add(new UserOnlineStatusVO(userId, isOnline(userId)));
            }
        }
        return result;
    }

    private String key(Long userId) {
        return "im:online:user:" + userId;
    }
}
