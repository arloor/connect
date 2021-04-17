package com.arloor.connect.http;

import com.arloor.forwardproxy.monitor.GlobalTrafficMonitor;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;

public class HttpServerInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        ch.pipeline().addLast(GlobalTrafficMonitor.getInstance());
        ch.pipeline().addLast(
                new HttpConnectHandler());
    }
}