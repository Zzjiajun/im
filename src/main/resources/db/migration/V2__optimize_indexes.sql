-- V2: 性能优化索引（幂等版本）
-- 为高频查询添加复合索引，减少回表和排序开销
-- 使用存储过程避免索引已存在时迁移失败

DROP PROCEDURE IF EXISTS add_index_if_not_exists;

DELIMITER //
CREATE PROCEDURE add_index_if_not_exists(
    p_table VARCHAR(64),
    p_idx VARCHAR(64),
    p_columns VARCHAR(255)
)
BEGIN
    DECLARE cnt INT;
    SELECT COUNT(*) INTO cnt
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table
      AND INDEX_NAME = p_idx;
    IF cnt = 0 THEN
        SET @ddl = CONCAT('ALTER TABLE ', p_table, ' ADD INDEX ', p_idx, ' (', p_columns, ')');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//
DELIMITER ;

CALL add_index_if_not_exists('chat_message', 'idx_conversation_id_created', 'conversation_id, id');
CALL add_index_if_not_exists('notification', 'idx_user_created', 'user_id, created_at');

DROP PROCEDURE IF EXISTS add_index_if_not_exists;


DELETE FROM flyway_schema_history WHERE version = 2 AND success = 0;