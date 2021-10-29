
package com.arloor.connect.socks5;

import com.arloor.connect.BootStrap;
import com.arloor.connect.common.BlindRelayHandler;
import com.arloor.connect.common.Config;
import com.arloor.connect.common.ExceptionUtil;
import com.arloor.connect.common.SocketChannelUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.arloor.connect.BootStrap.clazzSocketChannel;
import static com.arloor.connect.BootStrap.config;

//不可共享
//@ChannelHandler.Sharable
public final class SocksServerConnectHandler extends SimpleChannelInboundHandler<SocksMessage> {

    private static Logger logger = LoggerFactory.getLogger(SocksServerConnectHandler.class.getSimpleName());

    public SocksServerConnectHandler() {
        super();
    }

    private final Bootstrap b = new Bootstrap();

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final SocksMessage message) throws Exception {

        switch (message) {
            case Socks4CommandRequest cmd -> {
                logger.warn("socks4 request from" + ctx.channel().remoteAddress());
                ctx.close();
            }
            case Socks5CommandRequest cmd -> {
                final String dstAddr = cmd.dstAddr();
                Config.Server server = config.getSocks5Proxy().route(dstAddr);

                Promise<Channel> promise = ctx.executor().newPromise();
                promise.addListener(
                        new FutureListener<Channel>() {
                            @Override
                            public void operationComplete(final Future<Channel> future) throws Exception {
                                final Channel outboundChannel = future.getNow();
                                if (future.isSuccess()) {
                                    ChannelFuture responseFuture =
                                            ctx.channel().writeAndFlush(new DefaultSocks5CommandResponse(
                                                    Socks5CommandStatus.SUCCESS,
                                                    cmd.dstAddrType(),
                                                    dstAddr,
                                                    cmd.dstPort()));

                                    responseFuture.addListener(new ChannelFutureListener() {
                                        @Override
                                        public void operationComplete(ChannelFuture channelFuture) {
                                            if (channelFuture.isSuccess()) {
                                                ctx.pipeline().remove(SocksServerConnectHandler.this);
                                                // outboundChannel先增加handler，再删除Check的Hanlder，以防有Exception没有catch
                                                outboundChannel.pipeline().addLast(new BlindRelayHandler(ctx.channel()));
                                                outboundChannel.pipeline().remove("check");
                                                logger.info(ctx.channel().remoteAddress().toString() + " " + cmd.type() + " " + dstAddr + ":" + cmd.dstPort());
                                                ctx.pipeline().addLast(new BlindRelayHandler(outboundChannel));
                                            }
                                        }
                                    });
                                } else {
                                    ctx.channel().writeAndFlush(new DefaultSocks5CommandResponse(
                                            Socks5CommandStatus.FAILURE, cmd.dstAddrType()));
                                    SocketChannelUtils.closeOnFlush(ctx.channel());
                                }
                            }
                        });

                final Channel inboundChannel = ctx.channel();
                b.group(inboundChannel.eventLoop())
                        .channel(clazzSocketChannel)
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                        .option(ChannelOption.SO_KEEPALIVE, true)
                        .handler(new DirectClientHandler(promise, dstAddr, cmd.dstPort(), server.base64Auth()));
                b.connect(server.getHost(), server.getPort()).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (!future.isSuccess()) {
                            logger.error("connect to: " + server.getHost() + ":" + server.getPort() + " failed! == " + ExceptionUtil.getMessage(future.cause()));
                            ctx.channel().writeAndFlush(
                                    new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, cmd.dstAddrType()));
                            SocketChannelUtils.closeOnFlush(ctx.channel());
                        }
                    }
                });
            }
            default -> ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        SocketChannelUtils.closeOnFlush(ctx.channel());
    }
}
