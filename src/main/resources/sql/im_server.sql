/*
Navicat MySQL Data Transfer

Source Server         : loca
Source Server Version : 80036
Source Host           : localhost:3306
Source Database       : im_server

Target Server Type    : MYSQL
Target Server Version : 80036
File Encoding         : 65001

Date: 2026-04-28 14:20:05
*/

SET FOREIGN_KEY_CHECKS=0;

-- ----------------------------
-- Table structure for chat_message
-- ----------------------------
DROP TABLE IF EXISTS `chat_message`;
CREATE TABLE `chat_message` (
  `id` bigint NOT NULL,
  `conversation_id` bigint NOT NULL,
  `sender_id` bigint NOT NULL,
  `type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` mediumtext COLLATE utf8mb4_unicode_ci,
  `media_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `media_cover_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reply_message_id` bigint DEFAULT NULL,
  `read_count` int NOT NULL DEFAULT '0',
  `delivered_count` int NOT NULL DEFAULT '0',
  `favorite_count` int NOT NULL DEFAULT '0',
  `edited` tinyint NOT NULL DEFAULT '0',
  `edited_at` datetime DEFAULT NULL,
  `mention_all` tinyint NOT NULL DEFAULT '0',
  `mention_user_ids` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `recalled` tinyint NOT NULL DEFAULT '0',
  `recalled_by` bigint DEFAULT NULL,
  `recalled_at` datetime DEFAULT NULL,
  `client_msg_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat_sender_client_msg` (`sender_id`,`client_msg_id`),
  KEY `idx_conversation_id` (`conversation_id`),
  KEY `idx_sender_id` (`sender_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for conversation
-- ----------------------------
DROP TABLE IF EXISTS `conversation`;
CREATE TABLE `conversation` (
  `id` bigint NOT NULL,
  `type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `avatar` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `notice` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `notice_updated_at` datetime DEFAULT NULL,
  `owner_id` bigint DEFAULT NULL,
  `last_message_id` bigint DEFAULT NULL,
  `last_message_preview` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `mute_all` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_owner_id` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for conversation_member
-- ----------------------------
DROP TABLE IF EXISTS `conversation_member`;
CREATE TABLE `conversation_member` (
  `id` bigint NOT NULL,
  `conversation_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `role` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `remark_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `pinned` tinyint NOT NULL DEFAULT '0',
  `muted` tinyint NOT NULL DEFAULT '0',
  `draft_content` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `draft_updated_at` datetime DEFAULT NULL,
  `clear_message_id` bigint DEFAULT NULL,
  `clear_at` datetime DEFAULT NULL,
  `last_read_at` datetime DEFAULT NULL,
  `deleted_at` datetime DEFAULT NULL,
  `archived` tinyint NOT NULL DEFAULT '0',
  `speak_muted_until` datetime DEFAULT NULL,
  `sync_cursor_message_id` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conversation_user` (`conversation_id`,`user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for friend_relation
-- ----------------------------
DROP TABLE IF EXISTS `friend_relation`;
CREATE TABLE `friend_relation` (
  `id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `friend_user_id` bigint NOT NULL,
  `alias_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_friend` (`user_id`,`friend_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for friend_request
-- ----------------------------
DROP TABLE IF EXISTS `friend_request`;
CREATE TABLE `friend_request` (
  `id` bigint NOT NULL,
  `from_user_id` bigint NOT NULL,
  `to_user_id` bigint NOT NULL,
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `handled_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_to_user_id` (`to_user_id`),
  KEY `idx_from_user_id` (`from_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for friend_tag
-- ----------------------------
DROP TABLE IF EXISTS `friend_tag`;
CREATE TABLE `friend_tag` (
  `id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_friend_tag_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for friend_tag_member
-- ----------------------------
DROP TABLE IF EXISTS `friend_tag_member`;
CREATE TABLE `friend_tag_member` (
  `id` bigint NOT NULL,
  `tag_id` bigint NOT NULL,
  `friend_user_id` bigint NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_friend` (`tag_id`,`friend_user_id`),
  KEY `idx_tag_member_friend` (`friend_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for group_invite
-- ----------------------------
DROP TABLE IF EXISTS `group_invite`;
CREATE TABLE `group_invite` (
  `id` bigint NOT NULL,
  `conversation_id` bigint NOT NULL,
  `token` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `creator_id` bigint NOT NULL,
  `expire_at` datetime DEFAULT NULL,
  `max_uses` int DEFAULT NULL,
  `used_count` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_invite_token` (`token`),
  KEY `idx_invite_conversation` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for im_user
-- ----------------------------
DROP TABLE IF EXISTS `im_user`;
CREATE TABLE `im_user` (
  `id` bigint NOT NULL,
  `nickname` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `avatar` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phone` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `admin` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `phone` (`phone`),
  UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for media_file
-- ----------------------------
DROP TABLE IF EXISTS `media_file`;
CREATE TABLE `media_file` (
  `id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `media_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bucket` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `object_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `original_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content_type` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `size` bigint NOT NULL,
  `width` int DEFAULT NULL,
  `height` int DEFAULT NULL,
  `duration_seconds` int DEFAULT NULL,
  `cover_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_media_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for message_deleted_user
-- ----------------------------
DROP TABLE IF EXISTS `message_deleted_user`;
CREATE TABLE `message_deleted_user` (
  `id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `message_id` bigint NOT NULL,
  `deleted_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_deleted_message` (`user_id`,`message_id`),
  KEY `idx_deleted_message_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for message_deliver
-- ----------------------------
DROP TABLE IF EXISTS `message_deliver`;
CREATE TABLE `message_deliver` (
  `id` bigint NOT NULL,
  `message_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `delivered_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_user_deliver` (`message_id`,`user_id`),
  KEY `idx_deliver_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for message_delivery_status
-- ----------------------------
DROP TABLE IF EXISTS `message_delivery_status`;
CREATE TABLE `message_delivery_status` (
  `id` bigint NOT NULL,
  `message_id` bigint NOT NULL,
  `recipient_user_id` bigint NOT NULL,
  `delivery_status` enum('PENDING','DELIVERED','FAILED') NOT NULL,
  `retry_count` int NOT NULL DEFAULT '0',
  `last_retry_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_recipient` (`message_id`,`recipient_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for message_favorite
-- ----------------------------
DROP TABLE IF EXISTS `message_favorite`;
CREATE TABLE `message_favorite` (
  `id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `message_id` bigint NOT NULL,
  `note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `category_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_message` (`user_id`,`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for message_pinned_user
-- ----------------------------
DROP TABLE IF EXISTS `message_pinned_user`;
CREATE TABLE `message_pinned_user` (
  `id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `message_id` bigint NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_pinned_message` (`user_id`,`message_id`),
  KEY `idx_pinned_user_message` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for message_reaction
-- ----------------------------
DROP TABLE IF EXISTS `message_reaction`;
CREATE TABLE `message_reaction` (
  `id` bigint NOT NULL,
  `message_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `reaction_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_user_reaction` (`message_id`,`user_id`,`reaction_type`),
  KEY `idx_reaction_message_id` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for message_read
-- ----------------------------
DROP TABLE IF EXISTS `message_read`;
CREATE TABLE `message_read` (
  `id` bigint NOT NULL,
  `message_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `read_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_user_read` (`message_id`,`user_id`),
  KEY `idx_read_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for message_report
-- ----------------------------
DROP TABLE IF EXISTS `message_report`;
CREATE TABLE `message_report` (
  `id` bigint NOT NULL,
  `message_id` bigint NOT NULL,
  `reporter_user_id` bigint NOT NULL,
  `reason` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reporter_message` (`reporter_user_id`,`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for notification
-- ----------------------------
DROP TABLE IF EXISTS `notification`;
CREATE TABLE `notification` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '通知ID',
  `user_id` bigint NOT NULL COMMENT '接收用户ID',
  `type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知类型: FRIEND_REQUEST, GROUP_INVITE, MENTION, SYSTEM_ANNOUNCEMENT',
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知标题',
  `content` text COLLATE utf8mb4_unicode_ci COMMENT '通知内容',
  `data` json DEFAULT NULL COMMENT '额外数据（JSON格式）',
  `sender_id` bigint DEFAULT NULL COMMENT '发送者ID',
  `sender_nickname` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发送者昵称（冗余）',
  `sender_avatar` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发送者头像（冗余）',
  `related_id` bigint DEFAULT NULL COMMENT '关联ID（好友申请ID/群邀请ID/消息ID等）',
  `is_read` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否已读: 0-未读, 1-已读',
  `read_at` datetime DEFAULT NULL COMMENT '已读时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_type` (`type`),
  KEY `idx_is_read` (`is_read`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知表';

-- ----------------------------
-- Table structure for sticker_item
-- ----------------------------
DROP TABLE IF EXISTS `sticker_item`;
CREATE TABLE `sticker_item` (
  `id` bigint NOT NULL,
  `pack_id` bigint NOT NULL,
  `code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `image_url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sticker_pack` (`pack_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for sticker_pack
-- ----------------------------
DROP TABLE IF EXISTS `sticker_pack`;
CREATE TABLE `sticker_pack` (
  `id` bigint NOT NULL,
  `code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `cover_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sticker_pack_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for unread_history
-- ----------------------------
DROP TABLE IF EXISTS `unread_history`;
CREATE TABLE `unread_history` (
  `id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `conversation_id` bigint NOT NULL,
  `count` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_conversation` (`user_id`,`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for user_blacklist
-- ----------------------------
DROP TABLE IF EXISTS `user_blacklist`;
CREATE TABLE `user_blacklist` (
  `id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `blocked_user_id` bigint NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_blocked` (`user_id`,`blocked_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for user_oauth_binding
-- ----------------------------
DROP TABLE IF EXISTS `user_oauth_binding`;
CREATE TABLE `user_oauth_binding` (
  `id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `provider` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `open_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_oauth_provider_open` (`provider`,`open_id`),
  KEY `idx_oauth_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for user_push_token
-- ----------------------------
DROP TABLE IF EXISTS `user_push_token`;
CREATE TABLE `user_push_token` (
  `id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `platform` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `device_token` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_platform` (`user_id`,`platform`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for user_session
-- ----------------------------
DROP TABLE IF EXISTS `user_session`;
CREATE TABLE `user_session` (
  `id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `refresh_token_hash` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `device_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `device_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `revoked` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL,
  `last_active_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refresh_hash` (`refresh_token_hash`),
  KEY `idx_session_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for voice_call_record
-- ----------------------------
DROP TABLE IF EXISTS `voice_call_record`;
CREATE TABLE `voice_call_record` (
  `id` bigint NOT NULL,
  `call_id` varchar(64) NOT NULL,
  `caller_user_id` bigint NOT NULL,
  `callee_user_id` bigint NOT NULL,
  `conversation_id` bigint NOT NULL,
  `status` varchar(32) NOT NULL,
  `start_at` datetime DEFAULT NULL,
  `answer_at` datetime DEFAULT NULL,
  `end_at` datetime DEFAULT NULL,
  `duration_seconds` int DEFAULT '0',
  `reason` varchar(32) DEFAULT NULL,
  `ring_timeout_seconds` int DEFAULT '45',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_call_user` (`caller_user_id`,`callee_user_id`),
  KEY `idx_conversation` (`conversation_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
