-- 消息幂等：客户端 clientMsgId（同一发送者唯一）
ALTER TABLE chat_message
    ADD COLUMN client_msg_id VARCHAR(64) DEFAULT NULL AFTER recalled_at;
ALTER TABLE chat_message
    ADD UNIQUE KEY uk_chat_sender_client_msg (sender_id, client_msg_id);
