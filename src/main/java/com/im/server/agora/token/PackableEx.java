package com.im.server.agora.token;

public interface PackableEx {

    void marshal(ByteBuf out);

    void unmarshal(ByteBuf in);
}
