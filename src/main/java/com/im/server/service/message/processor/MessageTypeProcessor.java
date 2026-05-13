package com.im.server.service.message.processor;

import com.im.server.model.dto.SendMessageRequest;
import com.im.server.model.entity.ChatMessage;
import com.im.server.model.enums.MessageType;

/**
 * 消息类型处理器 —— 策略模式。
 * <p>
 * 每种消息类型（TEXT / IMAGE / VIDEO / MERGE 等）各自的校验与预览逻辑，
 * 通过 {@link MessageTypeProcessorRegistry} 按类型路由，消除 if/else 和 switch。
 * <p>
 * 面试点：策略模式。新增消息类型只需新建一个 Processor 实现类并注册，
 * 无需修改现有代码，符合开闭原则。
 */
public interface MessageTypeProcessor {

    /** 当前处理器对应的消息类型 */
    MessageType supportedType();

    /**
     * 校验发送请求的合法性。
     * @throws com.im.server.common.BusinessException 校验不通过
     */
    default void validate(SendMessageRequest request) {
        // 子类按需重写
    }

    /**
     * 生成消息预览文本（用于会话列表 lastMessagePreview）。
     */
    String buildPreview(ChatMessage message);
}
