package com.arloor.connect.http;

import com.arloor.connect.common.SocketChannelUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SslEventHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(SslEventHandler.class);

    private Promise<Channel> promise;

    public SslEventHandler(Promise<Channel> promise) {
        this.promise = promise;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof SslHandshakeCompletionEvent sslComplete) {
            if (sslComplete.isSuccess()) {
                promise.setSuccess(ctx.channel());
            } else {
                promise.setFailure(new Throwable("ssl handshake fail"));
                SocketChannelUtils.closeOnFlush(ctx.channel());
            }
        }
        super.userEventTriggered(ctx, evt);
    }
}
