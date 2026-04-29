-- ============================================================
-- v6: 性能优化 — 新增复合索引
-- 执行时间: 大表可能耗时较长，建议低峰期执行
-- ============================================================

-- chat_message: 会话内按时间排序的消息列表查询
-- 覆盖场景: SELECT * FROM chat_message WHERE conversation_id = ? ORDER BY created_at DESC LIMIT ?
ALTER TABLE `chat_message` ADD INDEX `idx_conversation_created` (`conversation_id`, `created_at`);

-- notification: 用户通知列表按时间排序
-- 覆盖场景: SELECT * FROM notification WHERE user_id = ? ORDER BY created_at DESC LIMIT ?
ALTER TABLE `notification` ADD INDEX `idx_user_created` (`user_id`, `created_at`);
