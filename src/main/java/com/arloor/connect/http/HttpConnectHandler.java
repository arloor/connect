package com.arloor.connect.http;

import com.arloor.connect.ClientBootStrap;
import com.arloor.connect.common.Config;
import com.arloor.connect.common.ExceptionUtil;
import com.arloor.connect.common.SocketChannelUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.util.Arrays;
import java.util.List;

import static com.arloor.connect.ClientBootStrap.clazzSocketChannel;

public class HttpConnectHandler extends ChannelInboundHandlerAdapter {

    private static Logger logger = LoggerFactory.getLogger(HttpConnectHandler.class.getSimpleName());


    private int remotePort = 80;
    private String remoteHost;
    private final Bootstrap b = new Bootstrap();
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

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        b.group(ctx.channel().eventLoop())
                .channel(clazzSocketChannel)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) throws Exception {

                    }
                });
//        logger.info("connect");
        b.connect(remoteHost, remotePort).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
//                    logger.info("connect success");
                    Channel outboud = future.channel();
                    outboud.pipeline().addLast(sslContext.newHandler(ctx.alloc()));
//                    outboud.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                    outboud.pipeline().addLast(new SslEventHandler(ctx.channel()));
                    // 写0字符，促使ssl握手
                    outboud.writeAndFlush(Unpooled.wrappedBuffer("".getBytes())).addListener((future1 -> {
                        if (future1.isSuccess()) {
//                            logger.info("write blank success");
                        } else {
//                            logger.info("write blank faild");
                        }
                    }));
                } else {
                    // Close the connection if the connection attempt has failed.
                    logger.error("connect to: " + remoteHost + ":" + remotePort + " failed! == " + ExceptionUtil.getMessage(future.cause()));
//                    ctx.channel().writeAndFlush(
//                            new DefaultHttpResponse(HttpVersion.HTTP_1_1, INTERNAL_SERVER_ERROR)
//                    );
                    SocketChannelUtils.closeOnFlush(ctx.channel());
                }
            }
        });
    }

    public HttpConnectHandler() {
        super();
        this.remotePort = ClientBootStrap.config.getServer().getPort();
        this.remoteHost = ClientBootStrap.config.getServer().getHost();
    }
}
