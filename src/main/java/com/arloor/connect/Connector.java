package com.arloor.connect;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Connector implements Runnable {
    private Selector selector;
    private ConcurrentLinkedQueue<SocketChannel> waitQueue = new ConcurrentLinkedQueue<>();

    public Connector() throws IOException {
        selector = Selector.open();
    }

    public SocketChannel connect(String host, int port) {
        try {
            final SocketChannel socketChannel = SocketChannel.open();
            // 先阻塞connect
            socketChannel.socket().connect(new InetSocketAddress(host, port), 5000);
            // 设置为非阻塞
            socketChannel.configureBlocking(false);
            waitQueue.add(socketChannel);
            selector.wakeup();
            return socketChannel;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void run() {
        try {
            while (true) {
                SocketChannel socketChannel;
                while ((socketChannel = waitQueue.poll()) != null) {
                    socketChannel.register(selector, SelectionKey.OP_READ, new Object());
                }
                final int numKey = selector.select(1);
                if (numKey > 0) {
                    Set<SelectionKey> keys = selector.selectedKeys();
                    Iterator<SelectionKey> keyIterator = keys.iterator();
                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        if (key.isReadable()) {//读来自远程服务器的响应
                            String content = readAll(key);
                            System.out.println(content);
                        }
                        keyIterator.remove();
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

    }

    private String readAll(SelectionKey key) {
        final SocketChannel channel = (SocketChannel) key.channel();
        int cap = 1024;
        ByteBuffer buffer = ByteBuffer.allocate(cap);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            int read;
            while ((read = channel.read(buffer)) > 0) {
                buffer.flip();
                final byte[] dst = new byte[read];
                buffer.get(dst);
                out.write(dst);
                buffer.clear();
            }
            if (read == -1) {
                key.cancel();
                channel.close();
            }
        } catch (Throwable e) {
            e.printStackTrace();
            key.cancel();
            try {
                channel.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return out.toString();
    }

    public static void main(String[] args) throws Exception {
        Connector connector = new Connector();
        Thread thread = new Thread(connector, "connector");
        thread.setDaemon(true);
        thread.start();
        final SocketChannel socketChannel = connector.connect("sg.gcall.me", 80);
        while (true) {
            try {
                if (socketChannel.isOpen()) {
                    writeAll(socketChannel, "GET / HTTP/1.1\r\nHost: sg.gcall.me\r\n\r\n");
                } else {
                    System.out.println("closed");
                    break;
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        Thread.sleep(100);
    }

    private static void writeAll(SocketChannel socketChannel, String msg) throws IOException {
        ByteBuffer src = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
        while (src.hasRemaining()) {
            socketChannel.write(src);
        }
    }
}

