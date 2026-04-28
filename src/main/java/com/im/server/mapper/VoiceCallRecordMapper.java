package com.im.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.im.server.model.entity.VoiceCallRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VoiceCallRecordMapper extends BaseMapper<VoiceCallRecord> {
}