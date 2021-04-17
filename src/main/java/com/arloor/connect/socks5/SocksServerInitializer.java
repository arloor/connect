
package com.arloor.connect.socks5;

import com.arloor.connect.ClientBootStrap;
import com.arloor.forwardproxy.monitor.GlobalTrafficMonitor;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;


public final class SocksServerInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        ch.pipeline().addLast(GlobalTrafficMonitor.getInstance());
        int speedLimitKB = ClientBootStrap.config.getSpeedLimitKB();
        if (speedLimitKB > 0) {
            ch.pipeline().addLast(new ChannelTrafficShapingHandler(1024 * speedLimitKB, 0, 1000));
        }
        ch.pipeline().addLast(
                new SocksPortUnificationServerHandler(),
                SocksServerHandler.INSTANCE);
    }
}
