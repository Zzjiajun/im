package com.im.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 未读数持久化定时任务。
 * 每 5 分钟将 Redis 中的未读数同步到 MySQL unread_history 表，
 * 防止 Redis 重启导致未读数丢失。
 */
@Component
public class UnreadPersistenceTask {

    private static final Logger log = LoggerFactory.getLogger(UnreadPersistenceTask.class);

    private final UnreadCacheService unreadCacheService;

    public UnreadPersistenceTask(UnreadCacheService unreadCacheService) {
        this.unreadCacheService = unreadCacheService;
    }

    /**
     * 每 5 分钟持久化一次未读数。
     */
    @Scheduled(fixedRate = 300_000)
    public void persistUnreadCounts() {
        try {
            unreadCacheService.persistAllUnread();
        } catch (Exception e) {
            log.warn("[UnreadTask] 持久化异常: {}", e.getMessage());
        }
    }
}
