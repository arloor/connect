
package com.arloor.socks5connect;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.arloor.socks5connect.ClientBootStrap.SpeedLimitKB;


public final class SocksServerInitializer extends ChannelInitializer<SocketChannel> {
    private static Logger logger = LoggerFactory.getLogger(RelayHandler.class.getSimpleName());

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        if(SpeedLimitKB>0){
            ch.pipeline().addLast(new ChannelTrafficShapingHandler(1024*SpeedLimitKB,0,1000));
        }
        ch.pipeline().addLast(
                new SocksPortUnificationServerHandler(),
                SocksServerHandler.INSTANCE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    }
}
