package com.im.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.im.server.model.entity.GroupInvite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GroupInviteMapper extends BaseMapper<GroupInvite> {

    @Update("""
        UPDATE group_invite
        SET used_count = COALESCE(used_count, 0) + 1
        WHERE id = #{id}
          AND (max_uses IS NULL OR COALESCE(used_count, 0) < max_uses)
        """)
    int incrementUsedCountIfAvailable(@Param("id") Long id);
}
