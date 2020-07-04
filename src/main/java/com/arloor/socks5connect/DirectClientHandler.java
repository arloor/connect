
package com.arloor.socks5connect;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.util.concurrent.Promise;

import javax.net.ssl.SSLException;
import java.util.Arrays;
import java.util.List;

public final class DirectClientHandler extends ChannelInboundHandlerAdapter {

    private final Promise<Channel> promise;
    private final String dstAddr;
    private final int dstPort;
    private final String basicAuth;

    private  static SslContext sslContext;
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

    public DirectClientHandler(Promise<Channel> promise, String targetAddr, int targetPort, String basicAuth) {
        this.promise = promise;
        this.dstAddr=targetAddr;
        this.dstPort=targetPort;
        this.basicAuth=basicAuth;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.pipeline().addLast(sslContext.newHandler(ctx.alloc()));
        ctx.pipeline().addLast("check",new CheckConnectedHandler());
        String connect= String.format("CONNECT %s:%d HTTP/1.1\r\nHost: %s:%s\r\nProxy-Authorization: Basic %s\r\n\r\n",dstAddr,dstPort,dstAddr,dstPort,basicAuth);
        ctx.channel().writeAndFlush(Unpooled.wrappedBuffer(connect.getBytes()));
    }

    private class CheckConnectedHandler extends SimpleChannelInboundHandler<ByteBuf> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            if(msg.readableBytes()==39
                    &&msg.readByte()=='H'
                    &&msg.readByte()=='T'
                    &&msg.readByte()=='T'
                    &&msg.readByte()=='P'
                    &&msg.readByte()=='/'
                    &&msg.readByte()=='1'
                    &&msg.readByte()=='.'
                    &&msg.readByte()=='1'
                    &&msg.readByte()==' '
                    &&msg.readByte()=='2'
                    &&msg.readByte()=='0'
                    &&msg.readByte()=='0'
            ){
                ctx.pipeline().remove("check");
                //宣告成功
                ctx.pipeline().remove(DirectClientHandler.this);
                promise.setSuccess(ctx.channel());
            }else {
                ctx.close();
                promise.setFailure(new Throwable("Socks5 Connect Request Consum FAILED"));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable throwable) {
            ctx.close();
            promise.setFailure(throwable);
        }


    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable throwable) {
        promise.setFailure(throwable);
        ctx.close();
    }
}
