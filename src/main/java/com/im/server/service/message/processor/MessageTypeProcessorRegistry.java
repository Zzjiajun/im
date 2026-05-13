package com.im.server.service.message.processor;

import com.im.server.common.BusinessException;
import com.im.server.model.enums.MessageType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 消息类型处理器注册表 —— 策略模式的上下文。
 * <p>
 * 管理所有 {@link MessageTypeProcessor} 实现，根据 {@link MessageType} 路由到对应处理器。
 * 使用 {@link EnumMap} 保证类型安全和 O(1) 查找。
 * <p>
 * 面试点：注册表模式 + EnumMap。将所有策略的实现集中管理，
 * 避免 Service 层出现大量 if/else 或 switch。
 */
@Component
@RequiredArgsConstructor
public class MessageTypeProcessorRegistry {

    private final List<MessageTypeProcessor> processors;
    private final Map<MessageType, MessageTypeProcessor> registry = new EnumMap<>(MessageType.class);

    @PostConstruct
    void init() {
        for (MessageTypeProcessor p : processors) {
            if (registry.containsKey(p.supportedType())) {
                throw new IllegalStateException("Duplicate MessageTypeProcessor for type: " + p.supportedType());
            }
            registry.put(p.supportedType(), p);
        }
    }

    /**
     * 获取指定类型的处理器。
     * @throws BusinessException 如果没有对应的处理器
     */
    public MessageTypeProcessor getProcessor(MessageType type) {
        MessageTypeProcessor p = registry.get(type);
        if (p == null) {
            throw new BusinessException("不支持的消息类型: " + type);
        }
        return p;
    }

    /**
     * 获取指定名称的处理器。
     */
    public MessageTypeProcessor getProcessor(String typeName) {
        try {
            return getProcessor(MessageType.valueOf(typeName));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("不支持的消息类型: " + typeName);
        }
    }
}
