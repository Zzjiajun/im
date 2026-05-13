-- Optimize cursor pagination for message list queries:
-- WHERE conversation_id = ? AND id < / > ? ORDER BY id LIMIT ?
ALTER TABLE `chat_message`
    ADD INDEX `idx_chat_message_conversation_id_id` (`conversation_id`, `id`);
