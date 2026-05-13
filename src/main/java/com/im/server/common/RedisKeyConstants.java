package com.im.server.common;

/**
 * Redis Key 常量集中管理。
 * <p>
 * 所有 Redis key 的生成逻辑统一在此，避免各 Service 中硬编码字符串。
 * 变更 key 前缀时只需修改此处，无需全文搜索替换。
 * <p>
 * 面试点：常量集中管理 + 职责单一。面试时可说明"用一个常量类统一管理所有 Redis key，
 * 解决了之前 key 散落各处、改前缀风险高的问题"。
 */
public final class RedisKeyConstants {

    private static final String SEP = ":";

    // ==================== 业务模块前缀 ====================
    private static final String PREFIX_ONLINE    = "im:online";
    private static final String PREFIX_WS_RL     = "im:ws:rl";
    private static final String PREFIX_UNREAD    = "im:unread";
    private static final String PREFIX_USER_ACT  = "im:u:active";
    private static final String PREFIX_VC_RATE   = "im:send:rate";
    private static final String PREFIX_VC_CODE   = "im:verify";
    private static final String PREFIX_CALL      = "im:voice-call";
    private static final String PREFIX_LOCK      = "im:lock";

    private RedisKeyConstants() { }

    // ==================== 在线状态 ====================

    /** 用户在线状态 key：{@code im:online:user:<userId>}，value 为设备数 */
    public static String onlineUser(Long userId) {
        return PREFIX_ONLINE + SEP + "user" + SEP + userId;
    }

    // ==================== WebSocket 限流 ====================

    /** WS 限流桶 key：{@code im:ws:rl:<op>:<userId>:<minuteBucket>} */
    public static String wsRateLimitBucket(String op, Long userId, long minuteBucket) {
        return PREFIX_WS_RL + SEP + op + SEP + userId + SEP + minuteBucket;
    }

    // ==================== 未读数 ====================

    /** 未读数 key：{@code im:unread:<userId>:<conversationId>} */
    public static String unreadCount(Long userId, Long conversationId) {
        return PREFIX_UNREAD + SEP + userId + SEP + conversationId;
    }

    // ==================== 用户活跃状态 ====================

    /** 用户活跃状态 key 前缀：{@code im:u:active:}，后接 userId */
    public static String userActivePrefix() {
        return PREFIX_USER_ACT + SEP;
    }

    /** 用户活跃状态 key：{@code im:u:active:<userId>} */
    public static String userActive(Long userId) {
        return PREFIX_USER_ACT + SEP + userId;
    }

    // ==================== 验证码 ====================

    /** 验证码发送频率 key：{@code im:send:rate:<purpose>:<authType>:<account>} */
    public static String verifyCodeRateLimit(String purpose, String authType, String account) {
        return PREFIX_VC_RATE + SEP + purpose + SEP + authType + SEP + account;
    }

    /** 验证码存储 key：{@code im:verify:<purpose>:<authType>:<account>} */
    public static String verifyCode(String purpose, String authType, String account) {
        return PREFIX_VC_CODE + SEP + purpose + SEP + authType + SEP + account;
    }

    // ==================== 语音通话 ====================

    /** 通话记录 key：{@code im:voice-call:call:<callId>} */
    public static String voiceCallSession(String callId) {
        return PREFIX_CALL + SEP + "call" + SEP + callId;
    }

    /** 用户通话 key：{@code im:voice-call:user:<userId>} */
    public static String voiceCallUser(Long userId) {
        return PREFIX_CALL + SEP + "user" + SEP + userId;
    }

    // ==================== 分布式锁 ====================

    /** 分布式锁 key：{@code im:lock:<name>} */
    public static String lock(String name) {
        return PREFIX_LOCK + SEP + name;
    }
}
