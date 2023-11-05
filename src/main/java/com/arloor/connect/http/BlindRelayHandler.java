
package com.arloor.connect.http;

import com.arloor.connect.common.ExceptionUtil;
import com.arloor.connect.common.SocketChannelUtils;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class BlindRelayHandler extends ChannelInboundHandlerAdapter {

    private static Logger logger = LoggerFactory.getLogger(BlindRelayHandler.class.getSimpleName());

    private final Channel relay;

    public BlindRelayHandler(Channel relay) {
        this.relay = relay;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        boolean canWrite = ctx.channel().isWritable();
        //流量控制，不允许继续读
        relay.config().setAutoRead(canWrite);
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {

        if (relay.isActive()) {
//            if (msg instanceof ByteBuf buf){
//                logger.info(buf.toString(StandardCharsets.UTF_8));
//            }
            relay.writeAndFlush(msg);
        } else {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (relay.isActive()) {
            SocketChannelUtils.closeOnFlush(relay);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warn(ctx.channel().remoteAddress() + " " + ExceptionUtil.getMessage(cause));
        ctx.close();
    }
}
