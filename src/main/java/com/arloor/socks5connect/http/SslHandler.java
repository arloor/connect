package com.arloor.socks5connect.http;

import com.arloor.socks5connect.RelayHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SslHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(SslHandler.class);

    private final Channel relay;

    public SslHandler(Channel relay) {
        this.relay = relay;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {

        log.info(evt.toString());
        ctx.pipeline().remove(this);
        ctx.pipeline().addLast(new RelayHandler(relay));
        relay.pipeline().remove(HttpConnectHandler.class);
//        relay.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
        relay.pipeline().addLast(new RelayHandler(ctx.channel()));
        relay.config().setAutoRead(true);
        super.userEventTriggered(ctx, evt);
    }
}
