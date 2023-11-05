package com.arloor.connect.http;

import com.arloor.connect.BootStrap;
import com.arloor.connect.common.Config;
import com.arloor.connect.common.ExceptionUtil;
import com.arloor.connect.common.SocketChannelUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.arloor.connect.BootStrap.clazzSocketChannel;

public class HttpRequestHandler extends SimpleChannelInboundHandler<HttpObject> {
    private static Logger logger = LoggerFactory.getLogger(HttpRequestHandler.class);
    private HttpRequest request;
    private boolean requestConsumed;
    private ArrayList<HttpContent> contents = new ArrayList<>();
    private final Bootstrap b = new Bootstrap();
    private Channel outbound;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        switch (msg) {
            case HttpRequest request:
                this.request = request;
                boolean isTunnel = HttpMethod.CONNECT.equals(request.method());
                String hostAndPortStr = isTunnel ? request.uri() : request.headers().get("Host");
                String host = hostAndPortStr.split(":")[0];
                Promise<Channel> promise = buildPromise(ctx, isTunnel);
                buildOutboundChannel(host, promise, ctx.channel().eventLoop(), request);
                break;
//            case LastHttpContent lastHttpContent:
//                break;
            case HttpContent httpContent:
                if (outbound != null && outbound.isActive()) {
                    if (!requestConsumed) {
                        outbound.write(request);
                        requestConsumed = true;
                    }
                    outbound.write(httpContent);
                } else {
                    this.contents.add(httpContent);
                }
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + msg);
        }
    }

    private Promise<Channel> buildPromise(ChannelHandlerContext ctx, boolean isTunnel) {
        Promise<Channel> promise = ctx.executor().newPromise();
        String name = Thread.currentThread().getName();
        promise.addListener(new FutureListener<Channel>() {
            @Override
            public void operationComplete(Future<Channel> future) throws Exception {
                if (!Thread.currentThread().getName().equals(name)) {
                    // 需要保证inbound和outbound channel在一个eventloop中处理，也就是实际串行
                    logger.error("{} != {}", Thread.currentThread().getName(), name);
                }
                if (future.isSuccess()) {
                    outbound = future.get(); //此处outbound已经ssl握手好
                    if (ctx.channel().isActive()){
                        if (isTunnel) {
                            ctx.pipeline().remove(HttpRequestDecoder.class);
                        }
                        ctx.pipeline().remove(HttpRequestHandler.class);
                        ctx.pipeline().addLast(new BlindRelayHandler(outbound));
                    }else {
                        logger.warn("client is abort before ssl handshake done with outbound, {}",ctx.channel().remoteAddress());
                    }

                    outbound.pipeline().addLast(new BlindRelayHandler(ctx.channel()));
                    outbound.pipeline().addLast(new HttpRequestEncoder());
                    if (!requestConsumed) {
                        outbound.write(request);
                        requestConsumed = true;
                    }
                    for (HttpContent content : contents) {
                        outbound.write(content);
                    }
                    if (isTunnel) {
                        outbound.pipeline().remove(HttpRequestEncoder.class);
                    }
                } else {
                    logger.error("", future.cause());
                    SocketChannelUtils.closeOnFlush(ctx.channel());
                }
            }
        });
        return promise;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        if (outbound != null && outbound.isActive()) {
            SocketChannelUtils.closeOnFlush(outbound);
        }
    }

    private void buildOutboundChannel(String host, Promise<Channel> promise, EventLoop eventExecutors, HttpRequest request) {
        b.group(eventExecutors)
                .channel(clazzSocketChannel)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel outboud) throws Exception {
                        outboud.pipeline().addLast(sslContext.newHandler(PooledByteBufAllocator.DEFAULT));
                        outboud.pipeline().addLast(new SslEventHandler(promise));

                    }
                });
        Config.Server server = BootStrap.config.getHttpProxy().route(host);
        logger.info("{} by {}", host, server.getHost());
        request.headers().add("Proxy-Authorization", "Basic " + server.base64Auth());
        b.connect(server.getHost(), server.getPort()).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                promise.setFailure(new Throwable("connect to: " + server.getHost() + ":" + server.getPort() + " failed! == " + ExceptionUtil.getMessage(future.cause())));
            } else {
                // 写0字符，促使ssl握手
                future.channel().writeAndFlush(Unpooled.wrappedBuffer("".getBytes()))
                        .addListener((future1 -> {
                            if (!future1.isSuccess()) {
                                Throwable cause = future1.cause();
                                logger.error("", cause);
                                promise.setFailure(new Throwable("trigger ssl handshake fail"));
                            }
                        }));
            }
        });
    }


    private static SslContext sslContext;

    static {
        // 解决algid parse error, not a sequence
        // https://blog.csdn.net/ls0111/article/details/77533768
        java.security.Security.addProvider(
                new org.bouncycastle.jce.provider.BouncyCastleProvider()
        );
        List<String> ciphers = Arrays.asList("ECDHE-RSA-AES128-SHA", "ECDHE-RSA-AES256-SHA", "AES128-SHA", "AES256-SHA", "DES-CBC3-SHA");
        try {
            sslContext = SslContextBuilder.forClient()
                    .protocols("TLSv1.3", "TLSv1.2")
                    .sslProvider(SslProvider.OPENSSL)
                    .clientAuth(ClientAuth.NONE)
//                    .ciphers(ciphers)
                    .build();
        } catch (SSLException e) {
            e.printStackTrace();
        }
    }
}
