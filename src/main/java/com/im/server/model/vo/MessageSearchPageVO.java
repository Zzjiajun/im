package com.im.server.model.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MessageSearchPageVO {

    private List<ChatMessageVO> items;
    /** 是否还有更早的消息可翻页 */
    private boolean hasMore;
    /** 下一页请求传入的 beforeMessageId；无更多时为 null */
    private Long nextBeforeMessageId;
}
