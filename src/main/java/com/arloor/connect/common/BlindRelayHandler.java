
package com.arloor.connect.common;

import com.arloor.connect.ClientBootStrap;
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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Objects;


public final class BlindRelayHandler extends ChannelInboundHandlerAdapter {

    private static final String clientAuth = "Basic " + ClientBootStrap.config.getClientBasicAuth();

    private static Logger logger = LoggerFactory.getLogger(BlindRelayHandler.class.getSimpleName());

    private final Channel relayChannel;
    private String basicAuth = "";

    public BlindRelayHandler(Channel relayChannel) {
        this.relayChannel = relayChannel;
        this.basicAuth = ClientBootStrap.config.getRemoteBasicAuth();
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
            HttpRequest request = null;
            if (msg instanceof HttpRequest) {
                boolean fromLocalhost = false;
                SocketAddress clientAddr = ctx.channel().remoteAddress();
                if (clientAddr instanceof InetSocketAddress) {
                    fromLocalhost = ((InetSocketAddress) clientAddr).getAddress().isLoopbackAddress();
                }
                request = (HttpRequest) msg;
                if (ClientBootStrap.config.isAuth() && !fromLocalhost) {
                    String authorization = request.headers().get("Proxy-Authorization", "Basic " + basicAuth);
                    if (!Objects.equals(authorization, clientAuth)) {
                        logger.warn(String.format("%s %s %s !wrong_auth{%s}", clientAddr.toString(), request.method(), request.uri(), authorization));
                        SocketChannelUtils.closeOnFlush(relayChannel);
                        SocketChannelUtils.closeOnFlush(ctx.channel());
                        return;
                    }
                }
                request.headers().set("Proxy-Authorization", "Basic " + basicAuth);
                if (request.getMethod().equals(HttpMethod.CONNECT)) {
                    ctx.pipeline().remove(HttpRequestDecoder.class);
                }
                logger.warn(String.format("%s %s %s", clientAddr.toString(), request.method(), request.uri()));
            }
            HttpRequest finalRequest = request;
            relayChannel.writeAndFlush(msg).addListener(future -> {
                if (future.isSuccess() && finalRequest != null && finalRequest.getMethod().equals(HttpMethod.CONNECT)) {
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
        logger.warn(ctx.channel().remoteAddress() + " " + ExceptionUtil.getMessage(cause));
        ctx.close();
    }
}
