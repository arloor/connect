
package com.arloor.connect.socks5;

import com.arloor.connect.BootStrap;
import com.arloor.connect.common.ExceptionUtil;
import com.arloor.connect.common.SocketChannelUtils;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.v5.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

@ChannelHandler.Sharable
public final class SocksServerHandler extends SimpleChannelInboundHandler<SocksMessage> {
    private static Logger logger = LoggerFactory.getLogger(SocksServerHandler.class);

    public static final SocksServerHandler INSTANCE = new SocksServerHandler();

    private SocksServerHandler() {
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, SocksMessage socksRequest) throws Exception {
        switch (socksRequest.version()) {
            case SOCKS4a:
                //不处理sock4,直接关闭channel
                logger.warn("socks4 request from" + ctx.channel().remoteAddress());
                ctx.close();
                break;
            case SOCKS5:
                SocketAddress socketAddress = ctx.channel().remoteAddress();
                boolean fromLocalhost = isLocalhost(socketAddress);
                switch (socksRequest) {
                    case Socks5InitialRequest init -> {
                        if (BootStrap.config.getSocks5Proxy().isCheckAuth() && !fromLocalhost) {//需要密码认证
                            ctx.pipeline().addFirst(new Socks5PasswordAuthRequestDecoder());
                            ctx.write(new DefaultSocks5InitialResponse(Socks5AuthMethod.PASSWORD));
                        } else {//不需要密码认证
                            ctx.pipeline().addFirst(new Socks5CommandRequestDecoder());
                            ctx.write(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH));
                        }
                    }
                    case Socks5PasswordAuthRequest authRequest -> {
                        if (authRequest.username().equals(BootStrap.config.getSocks5Proxy().getUser()) && authRequest.password().equals(BootStrap.config.getSocks5Proxy().getUser())) {
                            ctx.pipeline().addFirst(new Socks5CommandRequestDecoder());
                            ctx.write(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS));
                        } else {
                            logger.warn("Error auth from " + ctx.channel().remoteAddress() + " === " + authRequest.username() + "/" + authRequest.password());
                            ctx.write(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.FAILURE));
                            SocketChannelUtils.closeOnFlush(ctx.channel());
                        }
                    }
                    case Socks5CommandRequest commandRequest -> {
                        Socks5CommandRequest socks5CmdRequest = (Socks5CommandRequest) socksRequest;
                        if (socks5CmdRequest.type() == Socks5CommandType.CONNECT) {
                            ctx.pipeline().addLast(new SocksServerConnectHandler());
                            ctx.pipeline().remove(this);
                            ctx.fireChannelRead(socksRequest);
                        } else {
                            ctx.close();
                        }
                    }
                    default -> ctx.close();
                }
                break;
            case UNKNOWN:
                ctx.close();
                break;
        }
    }

    private boolean isLocalhost(SocketAddress socketAddress) {
        boolean fromLocalhost = false;
        if (socketAddress instanceof InetSocketAddress) {
            fromLocalhost = ((InetSocketAddress) socketAddress).getAddress().isLoopbackAddress();
        }
        return fromLocalhost;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable throwable) {
        logger.warn(ctx.channel().remoteAddress() + " " + ExceptionUtil.getMessage(throwable));
        SocketChannelUtils.closeOnFlush(ctx.channel());
    }
}
