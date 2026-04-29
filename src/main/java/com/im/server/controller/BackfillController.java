package com.im.server.controller;

import com.im.server.config.ElasticsearchConfig;
import com.im.server.service.MessageBackfillService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ES 历史消息回填接口（仅管理员可用）。
 */
@RestController
@RequestMapping("/api/admin/es")
@ConditionalOnBean(ElasticsearchConfig.class)
@RequiredArgsConstructor
public class BackfillController {

    private static final Logger log = LoggerFactory.getLogger(BackfillController.class);

    private final MessageBackfillService messageBackfillService;

    /**
     * 触发历史消息回填到 ES。
     *
     * @param batchSize 每批处理数量（默认 500）
     * @param fromId    起始消息 ID（默认 0）
     * @param maxId     最大消息 ID（默认 999999999）
     */
    @PostMapping("/backfill")
    public ResponseEntity<Map<String, Object>> backfill(
            @RequestParam(defaultValue = "500") int batchSize,
            @RequestParam(defaultValue = "0") long fromId,
            @RequestParam(defaultValue = "999999999") long maxId) {

        log.info("[ES Backfill] 手动触发回填: batchSize={}, fromId={}, maxId={}", batchSize, fromId, maxId);

        long total;
        long existing;
        try {
            existing = messageBackfillService.countIndexed();
            total = messageBackfillService.backfillAll(batchSize, fromId, maxId);
        } catch (Exception e) {
            log.error("[ES Backfill] 回填失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "indexedCount", total,
            "existingBeforeBackfill", Math.max(0, existing)
        ));
    }

    /**
     * 查看 ES 索引状态。
     */
    @PostMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        long count = messageBackfillService.countIndexed();
        return ResponseEntity.ok(Map.of(
            "success", true,
            "documentCount", count
        ));
    }
}
