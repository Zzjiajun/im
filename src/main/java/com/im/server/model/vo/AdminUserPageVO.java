package com.im.server.model.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminUserPageVO {

    private long total;
    private List<AdminUserRowVO> records;
}
