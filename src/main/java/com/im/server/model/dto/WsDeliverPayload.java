package com.im.server.model.dto;

import java.util.List;
import lombok.Data;

@Data
public class WsDeliverPayload {

    private List<Long> messageIds;
}
