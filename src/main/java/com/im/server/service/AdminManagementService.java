package com.im.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.im.server.common.BusinessException;
import com.im.server.mapper.ChatMessageMapper;
import com.im.server.mapper.MessageReportMapper;
import com.im.server.mapper.UserMapper;
import com.im.server.model.entity.MessageReport;
import com.im.server.model.entity.User;
import com.im.server.model.vo.AdminDashboardVO;
import com.im.server.model.vo.AdminUserPageVO;
import com.im.server.model.vo.AdminUserRowVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminManagementService {

    private final UserMapper userMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final MessageReportMapper messageReportMapper;
    private final UserSessionService userSessionService;
    private final UserAccountStatusService userAccountStatusService;

    public AdminDashboardVO dashboard() {
        long users = userMapper.selectCount(null);
        LocalDateTime since24h = LocalDateTime.now().minusHours(24);
        Long msg24 = chatMessageMapper.countCreatedSince(since24h);
        LocalDateTime since7d = LocalDateTime.now().minusDays(7);
        Long rep7 = messageReportMapper.selectCount(
            new LambdaQueryWrapper<MessageReport>().ge(MessageReport::getCreatedAt, since7d));
        return AdminDashboardVO.builder()
            .totalUsers(users)
            .messagesLast24h(msg24 == null ? 0L : msg24)
            .reportsLast7d(rep7 == null ? 0L : rep7)
            .build();
    }

    public AdminUserPageVO listUsers(int page, int size, String keyword) {
        int p = Math.max(1, page);
        int s = Math.min(100, Math.max(1, size));
        LambdaQueryWrapper<User> w = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(keyword)) {
            String k = keyword.trim();
            try {
                Long id = Long.parseLong(k);
                w.and(x -> x.eq(User::getId, id).or().like(User::getNickname, k));
            } catch (NumberFormatException e) {
                w.like(User::getNickname, k);
            }
        }
        long total = userMapper.selectCount(w);
        w.orderByDesc(User::getId);
        int offset = (p - 1) * s;
        w.last("limit " + s + " offset " + offset);
        List<User> records = userMapper.selectList(w);
        List<AdminUserRowVO> rows = records.stream().map(this::toRow).collect(Collectors.toList());
        return AdminUserPageVO.builder()
            .total(total)
            .records(rows)
            .build();
    }

    private AdminUserRowVO toRow(User u) {
        return AdminUserRowVO.builder()
            .id(u.getId())
            .nickname(u.getNickname())
            .phoneMasked(maskPhone(u.getPhone()))
            .emailMasked(maskEmail(u.getEmail()))
            .status(u.getStatus())
            .admin(u.getAdmin())
            .createdAt(u.getCreatedAt())
            .build();
    }

    private static String maskPhone(String phone) {
        if (StringUtils.isBlank(phone)) {
            return null;
        }
        if (phone.length() <= 4) {
            return "****";
        }
        return "****" + phone.substring(phone.length() - 4);
    }

    private static String maskEmail(String email) {
        if (StringUtils.isBlank(email)) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(at);
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    @Transactional
    public void banUser(Long operatorId, Long targetUserId) {
        if (targetUserId == null) {
            throw new BusinessException("用户 id 无效");
        }
        if (targetUserId.equals(operatorId)) {
            throw new BusinessException("不能封禁当前登录账号");
        }
        User u = userMapper.selectById(targetUserId);
        if (u == null) {
            throw new BusinessException("用户不存在");
        }
        if (u.getAdmin() != null && u.getAdmin() == 1) {
            throw new BusinessException("不能封禁管理员账号");
        }
        u.setStatus(0);
        u.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(u);
        userSessionService.revokeAllSessions(targetUserId);
        userAccountStatusService.evictCache(targetUserId);
    }

    @Transactional
    public void unbanUser(Long targetUserId) {
        if (targetUserId == null) {
            throw new BusinessException("用户 id 无效");
        }
        User u = userMapper.selectById(targetUserId);
        if (u == null) {
            throw new BusinessException("用户不存在");
        }
        u.setStatus(1);
        u.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(u);
        userAccountStatusService.evictCache(targetUserId);
    }
}
