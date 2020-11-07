
package com.arloor.socks5connect;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.arloor.socks5connect.http.HttpServerInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Objects;

public final class ClientBootStrap {

    private static Logger logger = LoggerFactory.getLogger(ClientBootStrap.class.getSimpleName());

    public static final Class<? extends ServerSocketChannel> clazzServerSocketChannel = OsHelper.serverSocketChannelClazz();
    public static final Class<? extends SocketChannel> clazzSocketChannel = OsHelper.socketChannelClazz();

    private static int socks5Port = 1080;
    private static int httpPort = 3128;

    public static int use = -1;
    public static int SpeedLimitKB = 0;
    public static String user;
    public static String pass;
    public static boolean auth;

    public static LinkedHashSet<Socks5AddressType> blockedAddressType = new LinkedHashSet<>();

    public static JSONArray servers;


    public static void initConfig(String[] args) throws IOException {
        JSONObject config = null;
        if (args.length == 2 && args[0].equals("-c")) {
            File file = new File(args[1]);
            logger.info("config @" + file.getAbsolutePath());
            if (!file.exists()) {
                logger.error("Error: the config file not exists");
                System.exit(-1);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Files.copy(file.toPath(), outputStream);
            config = JSON.parseObject(outputStream.toString());
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
            config = JSON.parseObject(input);
        }

        logger.info("config : " + config);
        if (config.containsKey("SupportDomain") && !config.getBoolean("SupportDomain"))
            blockedAddressType.add(Socks5AddressType.DOMAIN);
        if (config.containsKey("SupportIPv4") && !config.getBoolean("SupportIPv4"))
            blockedAddressType.add(Socks5AddressType.IPv4);
        if (config.containsKey("SupportIPv6") && !config.getBoolean("SupportIPv6"))
            blockedAddressType.add(Socks5AddressType.IPv6);
        socks5Port = config.getInteger("Socks5Port");
        httpPort = config.getInteger("HttpPort");
        user = config.getString("User");
        SpeedLimitKB = config.getInteger("SpeedLimitKB");
        pass = config.getString("Pass");
        auth = config.getBoolean("Auth");
        use = config.getInteger("Use");
        servers = config.getJSONArray("Servers");

        System.out.println();
        System.out.println();
    }

    public static void printUsage() {
        System.out.println("> Usage: java -jar xxx.jar [-c client.json]");
        System.out.println("> if \"client.json\" path is not set, it will the default client.json in classpath");
        System.out.println("> which listen on 6666;and connect to ss5-server:80");
        System.out.println();
    }

    public static void main(String[] args) throws Exception {
        initConfig(args);
        EventLoopGroup bossGroup = OsHelper.buildEventLoopGroup(1);
        EventLoopGroup workerGroup = OsHelper.buildEventLoopGroup(0);
        try {

            // http proxy bootstrap
            ServerBootstrap httpBootStrap = new ServerBootstrap();
            httpBootStrap.group(bossGroup, workerGroup)
                    .channel(clazzServerSocketChannel)
                    .childOption(ChannelOption.AUTO_READ, Boolean.FALSE)
                    .childHandler(new HttpServerInitializer());

            Channel httpServerChannel = httpBootStrap.bind(httpPort).sync().channel();

            // socks5 proxy bootstrap
            ServerBootstrap socks5BootStrap = new ServerBootstrap();
            socks5BootStrap.group(bossGroup, workerGroup)
                    .channel(clazzServerSocketChannel)
                    .childHandler(new SocksServerInitializer());
            Channel socks5ServerChannel = socks5BootStrap.bind(socks5Port).sync().channel();

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
                                protected void channelRead0(ChannelHandlerContext channelHandlerContext, FullHttpRequest fullHttpRequest) throws Exception {

                                }
                            });
                        }
                    });
            Channel configChannel = configBootstrap.bind(socks5Port).sync().channel();

            httpServerChannel.closeFuture().sync();
            socks5ServerChannel.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
