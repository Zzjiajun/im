package com.im.server.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MessageReactionSummaryVO {

    private String reactionType;
    private Integer count;
    private Boolean reactedByMe;
}
