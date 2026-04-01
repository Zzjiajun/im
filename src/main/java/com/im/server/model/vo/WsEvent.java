package com.im.server.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WsEvent<T> {

    private String event;
    private T data;
}
