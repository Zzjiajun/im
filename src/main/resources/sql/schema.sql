-- 已有库升级可执行（按需）：
-- 昵称全局唯一（与注册/改资料校验一致；执行前请先清理重复昵称）：
-- ALTER TABLE im_user ADD UNIQUE KEY uk_user_nickname (nickname);
-- ALTER TABLE im_user ADD COLUMN admin TINYINT NOT NULL DEFAULT 0;
-- ALTER TABLE conversation ADD COLUMN mute_all TINYINT NOT NULL DEFAULT 0;
-- ALTER TABLE conversation_member ADD COLUMN archived TINYINT NOT NULL DEFAULT 0;
-- ALTER TABLE conversation_member ADD COLUMN speak_muted_until DATETIME DEFAULT NULL;
-- ALTER TABLE conversation_member ADD COLUMN sync_cursor_message_id BIGINT DEFAULT NULL;

CREATE TABLE IF NOT EXISTS im_user (
    id BIGINT NOT NULL PRIMARY KEY,
    nickname VARCHAR(64) NOT NULL,
    avatar VARCHAR(255) DEFAULT NULL,
    phone VARCHAR(32) DEFAULT NULL UNIQUE,
    email VARCHAR(128) DEFAULT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    admin TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS friend_request (
    id BIGINT NOT NULL PRIMARY KEY,
    from_user_id BIGINT NOT NULL,
    to_user_id BIGINT NOT NULL,
    remark VARCHAR(255) DEFAULT NULL,
    status VARCHAR(32) NOT NULL,
    handled_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_to_user_id (to_user_id),
    KEY idx_from_user_id (from_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS friend_relation (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    friend_user_id BIGINT NOT NULL,
    alias_name VARCHAR(64) DEFAULT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_user_friend (user_id, friend_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_blacklist (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    blocked_user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_user_blocked (user_id, blocked_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS conversation (
    id BIGINT NOT NULL PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    name VARCHAR(128) DEFAULT NULL,
    avatar VARCHAR(255) DEFAULT NULL,
    notice VARCHAR(1000) DEFAULT NULL,
    notice_updated_at DATETIME DEFAULT NULL,
    owner_id BIGINT DEFAULT NULL,
    last_message_id BIGINT DEFAULT NULL,
    last_message_preview VARCHAR(255) DEFAULT NULL,
    mute_all TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_owner_id (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS conversation_member (
    id BIGINT NOT NULL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    remark_name VARCHAR(128) DEFAULT NULL,
    pinned TINYINT NOT NULL DEFAULT 0,
    muted TINYINT NOT NULL DEFAULT 0,
    draft_content VARCHAR(1000) DEFAULT NULL,
    draft_updated_at DATETIME DEFAULT NULL,
    clear_message_id BIGINT DEFAULT NULL,
    clear_at DATETIME DEFAULT NULL,
    last_read_at DATETIME DEFAULT NULL,
    deleted_at DATETIME DEFAULT NULL,
    archived TINYINT NOT NULL DEFAULT 0,
    speak_muted_until DATETIME DEFAULT NULL,
    sync_cursor_message_id BIGINT DEFAULT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_conversation_user (conversation_id, user_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT NOT NULL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    content TEXT,
    media_url VARCHAR(500) DEFAULT NULL,
    media_cover_url VARCHAR(500) DEFAULT NULL,
    reply_message_id BIGINT DEFAULT NULL,
    read_count INT NOT NULL DEFAULT 0,
    delivered_count INT NOT NULL DEFAULT 0,
    favorite_count INT NOT NULL DEFAULT 0,
    edited TINYINT NOT NULL DEFAULT 0,
    edited_at DATETIME DEFAULT NULL,
    mention_all TINYINT NOT NULL DEFAULT 0,
    mention_user_ids VARCHAR(500) DEFAULT NULL,
    recalled TINYINT NOT NULL DEFAULT 0,
    recalled_by BIGINT DEFAULT NULL,
    recalled_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL,
    KEY idx_conversation_id (conversation_id),
    KEY idx_sender_id (sender_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS message_read (
    id BIGINT NOT NULL PRIMARY KEY,
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    read_at DATETIME NOT NULL,
    UNIQUE KEY uk_message_user_read (message_id, user_id),
    KEY idx_read_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS message_deliver (
    id BIGINT NOT NULL PRIMARY KEY,
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    delivered_at DATETIME NOT NULL,
    UNIQUE KEY uk_message_user_deliver (message_id, user_id),
    KEY idx_deliver_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS message_favorite (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    note VARCHAR(255) DEFAULT NULL,
    category_name VARCHAR(64) DEFAULT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_user_message (user_id, message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS message_pinned_user (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_user_pinned_message (user_id, message_id),
    KEY idx_pinned_user_message (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS message_deleted_user (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    deleted_at DATETIME NOT NULL,
    UNIQUE KEY uk_user_deleted_message (user_id, message_id),
    KEY idx_deleted_message_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS message_report (
    id BIGINT NOT NULL PRIMARY KEY,
    message_id BIGINT NOT NULL,
    reporter_user_id BIGINT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    remark VARCHAR(500) DEFAULT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_reporter_message (reporter_user_id, message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS message_reaction (
    id BIGINT NOT NULL PRIMARY KEY,
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    reaction_type VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_message_user_reaction (message_id, user_id, reaction_type),
    KEY idx_reaction_message_id (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS media_file (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    media_type VARCHAR(32) DEFAULT NULL,
    bucket VARCHAR(128) NOT NULL,
    object_name VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) DEFAULT NULL,
    content_type VARCHAR(128) DEFAULT NULL,
    size BIGINT NOT NULL,
    width INT DEFAULT NULL,
    height INT DEFAULT NULL,
    duration_seconds INT DEFAULT NULL,
    cover_url VARCHAR(500) DEFAULT NULL,
    url VARCHAR(500) NOT NULL,
    created_at DATETIME NOT NULL,
    KEY idx_media_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS friend_tag (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    KEY idx_friend_tag_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS friend_tag_member (
    id BIGINT NOT NULL PRIMARY KEY,
    tag_id BIGINT NOT NULL,
    friend_user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_tag_friend (tag_id, friend_user_id),
    KEY idx_tag_member_friend (friend_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_session (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    refresh_token_hash VARCHAR(128) NOT NULL,
    device_id VARCHAR(128) DEFAULT NULL,
    device_name VARCHAR(128) DEFAULT NULL,
    revoked TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    last_active_at DATETIME NOT NULL,
    UNIQUE KEY uk_refresh_hash (refresh_token_hash),
    KEY idx_session_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_oauth_binding (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    provider VARCHAR(32) NOT NULL,
    open_id VARCHAR(128) NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_oauth_provider_open (provider, open_id),
    KEY idx_oauth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_push_token (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    platform VARCHAR(32) NOT NULL,
    device_token VARCHAR(512) NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_user_platform (user_id, platform)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS group_invite (
    id BIGINT NOT NULL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    token VARCHAR(64) NOT NULL,
    creator_id BIGINT NOT NULL,
    expire_at DATETIME DEFAULT NULL,
    max_uses INT DEFAULT NULL,
    used_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_group_invite_token (token),
    KEY idx_invite_conversation (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sticker_pack (
    id BIGINT NOT NULL PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    cover_url VARCHAR(500) DEFAULT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_sticker_pack_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sticker_item (
    id BIGINT NOT NULL PRIMARY KEY,
    pack_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    KEY idx_sticker_pack (pack_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
