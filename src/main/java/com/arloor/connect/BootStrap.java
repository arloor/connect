
package com.arloor.connect;

import com.arloor.connect.common.Config;
import com.arloor.connect.common.JsonUtil;
import com.arloor.connect.common.OsHelper;
import com.arloor.connect.common.SocketChannelUtils;
import com.arloor.connect.http.HttpServerInitializer;
import com.fasterxml.jackson.core.type.TypeReference;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.util.Objects;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;

public final class BootStrap {

    private static String[] cmdArgs;
    private static Logger logger = LoggerFactory.getLogger(BootStrap.class.getSimpleName());
    public static final Class<? extends ServerSocketChannel> clazzServerSocketChannel = OsHelper.serverSocketChannelClazz();
    public static final Class<? extends SocketChannel> clazzSocketChannel = OsHelper.socketChannelClazz();
    public static Config config;


    public static String initConfig() throws IOException {
        String content;
        if (cmdArgs.length == 2 && cmdArgs[0].equals("-c")) {
            File file = new File(cmdArgs[1]);
            logger.info("config @" + file.getAbsolutePath());
            if (!file.exists()) {
                logger.error("Error: the config file not exists");
                System.exit(-1);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Files.copy(file.toPath(), outputStream);
            content=outputStream.toString();

        } else {
            logger.info("config @classpath:client.json");
            BufferedReader in = new BufferedReader(new InputStreamReader(Objects.requireNonNull(BootStrap.class.getClassLoader().getResourceAsStream("client.json"))));
            StringBuffer buffer = new StringBuffer();
            String line = "";
            while ((line = in.readLine()) != null) {
                buffer.append(line);
            }
            content= buffer.toString();
        }
        BootStrap.config = JsonUtil.fromJson(content, new TypeReference<Config>() {
        });
        return JsonUtil.toJson(BootStrap.config);
    }

    public static void main(String[] args) throws Exception {
        BootStrap.cmdArgs = args;
        logger.info(initConfig());
        EventLoopGroup bossGroup = OsHelper.buildEventLoopGroup(1);
        EventLoopGroup workerGroup = OsHelper.buildEventLoopGroup(0);
        InetSocketAddress httpAddr= config.getHttpProxy().isOnlyLocalhost()
                ?new InetSocketAddress(InetAddress.getLoopbackAddress(), config.getHttpProxy().getPort())
                :new InetSocketAddress(config.getHttpProxy().getPort());
        InetSocketAddress configAddr= config.getControlServer().isOnlyLocalhost()
                ?new InetSocketAddress(InetAddress.getLoopbackAddress(), config.getControlServer().getPort())
                :new InetSocketAddress(config.getControlServer().getPort());
        try {

            // http proxy bootstrap
            ServerBootstrap httpBootStrap = new ServerBootstrap();
            httpBootStrap.group(bossGroup, workerGroup)
                    .channel(clazzServerSocketChannel)
                    .childOption(ChannelOption.AUTO_READ, Boolean.TRUE)
                    .childHandler(new HttpServerInitializer());

            Channel httpServerChannel = httpBootStrap.bind(httpAddr).sync().channel();


            ServerBootstrap configBootstrap = new ServerBootstrap();
            configBootstrap.group(bossGroup, workerGroup)
                    .channel(clazzServerSocketChannel)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            socketChannel.pipeline().addLast(new HttpResponseEncoder());
                            socketChannel.pipeline().addLast(new HttpRequestDecoder());
                            socketChannel.pipeline().addLast(new HttpObjectAggregator(65536));
                            socketChannel.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {

                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
                                    boolean fromLocalhost = false;
                                    SocketAddress clientAddr = ctx.channel().remoteAddress();
                                    if (clientAddr instanceof InetSocketAddress) {
                                        fromLocalhost = ((InetSocketAddress) clientAddr).getAddress().isLoopbackAddress();
                                    }
                                    // reload config
                                    if (fromLocalhost && "/reload".equals(request.uri())) {
                                        logger.info("reload");
                                        try {
                                            String config = BootStrap.initConfig();
                                            FullHttpResponse response = new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.OK,
                                                    Unpooled.wrappedBuffer(config.getBytes()));
                                            response.headers()
                                                    .set(CONTENT_TYPE, "application/json; charset=utf-8")
                                                    .setInt(CONTENT_LENGTH, response.content().readableBytes());
                                            ctx.channel().writeAndFlush(response);
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                        SocketChannelUtils.closeOnFlush(ctx.channel());
                                        return;
                                    }
                                }
                            });
                        }
                    });
            Channel configChannel = configBootstrap.bind(configAddr).sync().channel();
            logger.info("init completed!");
            httpServerChannel.closeFuture().sync();
            configChannel.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
