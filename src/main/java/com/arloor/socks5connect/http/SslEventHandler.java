package com.arloor.socks5connect.http;

import com.arloor.socks5connect.BlindRelayHandler;
import com.arloor.socks5connect.SocketChannelUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;

public class SslEventHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(SslEventHandler.class);

    private final Channel relay;

    public SslEventHandler(Channel relay) {
        this.relay = relay;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
//        log.info(evt.toString());
        if(evt instanceof SslHandshakeCompletionEvent){
            SslHandshakeCompletionEvent sslComplete = (SslHandshakeCompletionEvent) evt;
            if(sslComplete.isSuccess()){
                if(ctx.channel().isActive()){
                    ctx.pipeline().remove(this);
                    ctx.pipeline().addLast(new HttpRequestEncoder());
                    ctx.pipeline().addLast(new BlindRelayHandler(relay));


                    relay.pipeline().addLast(new HttpRequestDecoder());
                    relay.pipeline().remove(HttpConnectHandler.class);
//                    relay.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                    relay.pipeline().addLast(new BlindRelayHandler(ctx.channel()));
                    relay.config().setAutoRead(true);
                }
            }else {
                ctx.close();
//                relay.writeAndFlush(
//                        new DefaultHttpResponse(HttpVersion.HTTP_1_1, INTERNAL_SERVER_ERROR)
//                );
                SocketChannelUtils.closeOnFlush(relay);
            }
        }
        super.userEventTriggered(ctx, evt);
    }
}
