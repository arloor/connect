package com.arloor.socks5connect.http;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;

public class HttpServerInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        ch.pipeline().addLast(
                new HttpConnectHandler());
    }
}