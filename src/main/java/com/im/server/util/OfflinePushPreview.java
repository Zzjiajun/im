package com.im.server.util;

import com.im.server.model.vo.ChatMessageVO;
import org.apache.commons.lang3.StringUtils;

/**
 * 离线推送展示文案：媒体类不依赖 content 字段。
 */
public final class OfflinePushPreview {

    private OfflinePushPreview() {
    }

    public static String fromMessage(ChatMessageVO message) {
        if (message == null) {
            return "";
        }
        String t = message.getType();
        if (t != null) {
            switch (t) {
                case "IMAGE":
                    return "[图片]";
                case "VIDEO":
                    return "[视频]";
                case "VOICE":
                    return "[语音]";
                case "FILE":
                    return "[文件]";
                case "EMOJI":
                    return "[表情]";
                case "MERGE":
                    return "[聊天记录]";
                case "LOCATION":
                    return "[位置]";
                case "CONTACT":
                    return "[名片]";
                case "FAVORITE_CARD":
                    return "[收藏]";
                case "SYSTEM":
                    return StringUtils.abbreviate(StringUtils.defaultString(message.getContent()), 80);
                case "TEXT":
                default:
                    break;
            }
        }
        return StringUtils.abbreviate(StringUtils.defaultString(message.getContent()), 80);
    }
}
