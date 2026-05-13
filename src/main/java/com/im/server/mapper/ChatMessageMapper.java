package com.im.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.im.server.model.entity.ChatMessage;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    @Select("SELECT COUNT(1) FROM chat_message WHERE created_at >= #{since}")
    Long countCreatedSince(@Param("since") LocalDateTime since);

    @Select("UPDATE chat_message SET read_count = COALESCE(read_count, 0) + 1 WHERE id = #{messageId}")
    void incrementReadCount(@Param("messageId") Long messageId);

    @Select("UPDATE chat_message SET delivered_count = COALESCE(delivered_count, 0) + 1 WHERE id = #{messageId}")
    void incrementDeliveredCount(@Param("messageId") Long messageId);
}
