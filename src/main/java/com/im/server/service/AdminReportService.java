package com.im.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.im.server.mapper.ChatMessageMapper;
import com.im.server.mapper.MessageReportMapper;
import com.im.server.model.entity.ChatMessage;
import com.im.server.model.entity.MessageReport;
import com.im.server.model.vo.MessageReportAdminVO;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminReportService {

    private final MessageReportMapper messageReportMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final UserService userService;

    /** 举报列表（分页） */
    public Map<String, Object> listReports(int page, int size) {
        int pageSize = Math.min(Math.max(size, 1), 100);
        int pageNo = Math.max(page, 1);
        int offset = (pageNo - 1) * pageSize;

        // 总条数
        long total = messageReportMapper.selectCount(null);

        // 当前页
        List<MessageReport> reports = messageReportMapper.selectList(
            new LambdaQueryWrapper<MessageReport>()
                .orderByDesc(MessageReport::getCreatedAt)
                .last("limit " + pageSize + " offset " + offset)
        );
        List<MessageReportAdminVO> result = new ArrayList<>();
        for (MessageReport r : reports) {
            ChatMessage msg = chatMessageMapper.selectById(r.getMessageId());
            String preview = msg == null ? null : StringUtils.abbreviate(StringUtils.defaultString(msg.getContent()), 80);
            Long convId = msg == null ? null : msg.getConversationId();
            result.add(MessageReportAdminVO.builder()
                .id(r.getId())
                .messageId(r.getMessageId())
                .reporterUserId(r.getReporterUserId())
                .reporterNickname(userService.getSimpleUser(r.getReporterUserId()).getNickname())
                .reason(r.getReason())
                .remark(r.getRemark())
                .createdAt(r.getCreatedAt())
                .messagePreview(preview)
                .conversationId(convId)
                .build());
        }
        return Map.of("records", result, "total", total);
    }
}
