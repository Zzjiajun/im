package com.im.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.im.server.model.entity.UserSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserSessionMapper extends BaseMapper<UserSession> {
}
