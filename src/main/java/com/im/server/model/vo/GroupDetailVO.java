package com.im.server.model.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GroupDetailVO {

    private Long conversationId;
    private String name;
    private String avatar;
    private String remarkName;
    private String notice;
    private Long ownerId;
    private Boolean muteAll;
    private Integer memberCount;
    private LocalDateTime noticeUpdatedAt;
    private LocalDateTime updatedAt;
    private List<GroupMemberVO> members;
}
