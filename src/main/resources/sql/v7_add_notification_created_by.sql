-- notification 表增加 created_by 字段
-- 记录系统公告的创建管理员 ID
ALTER TABLE `notification`
    ADD COLUMN `created_by` bigint DEFAULT NULL COMMENT '创建人(管理员)ID' AFTER `related_id`;
