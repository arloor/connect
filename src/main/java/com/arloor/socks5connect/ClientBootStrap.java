
package com.arloor.socks5connect;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.arloor.socks5connect.http.HttpServerInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
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
            new Thread(()->{
                ServerBootstrap httpB = new ServerBootstrap();
                httpB.group(bossGroup, workerGroup)
                        .channel(clazzServerSocketChannel)
                        .childOption(ChannelOption.AUTO_READ,Boolean.FALSE)
//             .handler(new LoggingHandler(LogLevel.INFO))
                        .childHandler(new HttpServerInitializer());
                try {
                    httpB.bind(httpPort).sync().channel().closeFuture().sync();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();;
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(clazzServerSocketChannel)
//             .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new SocksServerInitializer());
            b.bind(socks5Port).sync().channel().closeFuture().sync();

        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
