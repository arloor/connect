
package com.arloor.socks5connect;

import com.arloor.socks5connect.http.HttpServerInitializer;
import com.fasterxml.jackson.core.type.TypeReference;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Objects;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;

public final class ClientBootStrap {

    private static String[] args;
    private static Logger logger = LoggerFactory.getLogger(ClientBootStrap.class.getSimpleName());

    public static final Class<? extends ServerSocketChannel> clazzServerSocketChannel = OsHelper.serverSocketChannelClazz();
    public static final Class<? extends SocketChannel> clazzSocketChannel = OsHelper.socketChannelClazz();

    public static Config config;

    public static LinkedHashSet<Socks5AddressType> blockedAddressType = new LinkedHashSet<>();


    public static String initConfig() throws IOException {
        if (args.length == 2 && args[0].equals("-c")) {
            File file = new File(args[1]);
            logger.info("config @" + file.getAbsolutePath());
            if (!file.exists()) {
                logger.error("Error: the config file not exists");
                System.exit(-1);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Files.copy(file.toPath(), outputStream);
            config = JsonUtil.fromJson(outputStream.toString(), new TypeReference<Config>() {
            });
            outputStream.close();
        } else {
            //        读取jar中resources下的sogo.json
            logger.info("config @classpath:client.json");
            BufferedReader in = new BufferedReader(new InputStreamReader(Objects.requireNonNull(ClientBootStrap.class.getClassLoader().getResourceAsStream("client.json"))));
            StringBuffer buffer = new StringBuffer();
            String line = "";
            while ((line = in.readLine()) != null) {
                buffer.append(line);
            }
            String input = buffer.toString();
            config = JsonUtil.fromJson(input, new TypeReference<Config>() {
            });
        }
        return JsonUtil.toJson(config);
    }

    public static void main(String[] args) throws Exception {
        ClientBootStrap.args = args;
        initConfig();
        EventLoopGroup bossGroup = OsHelper.buildEventLoopGroup(1);
        EventLoopGroup workerGroup = OsHelper.buildEventLoopGroup(0);
        try {

            // http proxy bootstrap
            ServerBootstrap httpBootStrap = new ServerBootstrap();
            httpBootStrap.group(bossGroup, workerGroup)
                    .channel(clazzServerSocketChannel)
                    .childOption(ChannelOption.AUTO_READ, Boolean.FALSE)
                    .childHandler(new HttpServerInitializer());

            Channel httpServerChannel = httpBootStrap.bind(config.getHttpPort()).sync().channel();

            // socks5 proxy bootstrap
            ServerBootstrap socks5BootStrap = new ServerBootstrap();
            socks5BootStrap.group(bossGroup, workerGroup)
                    .channel(clazzServerSocketChannel)
                    .childHandler(new SocksServerInitializer());
            Channel socks5ServerChannel = socks5BootStrap.bind(config.getSocks5Port()).sync().channel();

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
                                            String config = ClientBootStrap.initConfig();
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
            Channel configChannel = configBootstrap.bind(config.getConfigPort()).sync().channel();
            logger.info("init completed!");
            httpServerChannel.closeFuture().sync();
            socks5ServerChannel.closeFuture().sync();
            configChannel.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
