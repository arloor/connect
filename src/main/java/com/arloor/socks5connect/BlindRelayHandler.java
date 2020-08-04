
package com.arloor.socks5connect;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BlindRelayHandler extends ChannelInboundHandlerAdapter {

    private static Logger logger = LoggerFactory.getLogger(BlindRelayHandler.class.getSimpleName());

    private final Channel relayChannel;

    public BlindRelayHandler(Channel relayChannel) {
        this.relayChannel = relayChannel;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        boolean canWrite = ctx.channel().isWritable();
        //流量控制，不允许继续读
        relayChannel.config().setAutoRead(canWrite);
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {

        if (relayChannel.isActive()) {
            HttpRequest request =null;
            if(msg instanceof HttpRequest){
                request = (HttpRequest) msg;
                if(request.getMethod().equals(HttpMethod.CONNECT)){
                    ctx.pipeline().remove(HttpRequestDecoder.class);
                }
                System.out.println(request);
            }
            HttpRequest finalRequest = request;
            relayChannel.writeAndFlush(msg).addListener(future -> {
                if (future.isSuccess()&& finalRequest !=null&& finalRequest.getMethod().equals(HttpMethod.CONNECT)){
                    relayChannel.pipeline().remove(HttpRequestEncoder.class);
                }
            });
        } else {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (relayChannel.isActive()) {
            SocketChannelUtils.closeOnFlush(relayChannel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warn(ctx.channel().remoteAddress()+" "+ExceptionUtil.getMessage(cause));
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        super.userEventTriggered(ctx, evt);
        logger.info(evt.toString());
    }
}
