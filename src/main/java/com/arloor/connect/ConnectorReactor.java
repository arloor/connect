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
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ConnectorReactor implements Runnable {
    private Selector selector;
    private ConcurrentLinkedQueue<SocketWrapper> waitQueue = new ConcurrentLinkedQueue<>();
    private ThreadPoolExecutor pool = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10000));

    public ConnectorReactor() throws IOException {
        selector = Selector.open();
    }

    public SocketWrapper connect(String host, int port) {
        try {
            final SocketChannel socketChannel = SocketChannel.open();
            // 先阻塞connect
            socketChannel.socket().connect(new InetSocketAddress(host, port), 5000);
            // 设置为非阻塞
            socketChannel.configureBlocking(false);
            final SocketWrapper socketWrapper = new SocketWrapper(socketChannel);
            waitQueue.add(socketWrapper);
            selector.wakeup();
            return socketWrapper;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void run() {
        try {
            while (true) {
                SocketWrapper socketWrapper;
                while ((socketWrapper = waitQueue.poll()) != null) {
                    socketWrapper.socketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, socketWrapper);
                }
                final int numKey = selector.select(1);
                if (numKey > 0) {
                    Set<SelectionKey> keys = selector.selectedKeys();
                    Iterator<SelectionKey> keyIterator = keys.iterator();
                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        keyIterator.remove();
                        // 取消注册，防止其他线程处理该key
                        key.interestOps(key.interestOps() & (~key.readyOps()));
                        pool.execute(new Processor(key));
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

    }

    public static void main(String[] args) throws Exception {
        ConnectorReactor connector = new ConnectorReactor();
        Thread thread = new Thread(connector, "connector");
        thread.setDaemon(true);
        thread.start();
        final SocketWrapper socketWrapper = connector.connect("sg.gcall.me", 80);
        while (true) {
            try {
                if (socketWrapper.socketChannel.isOpen()) {
                    socketWrapper.writeAll("GET / HTTP/1.1\r\nHost: sg.gcall.me\r\n\r\n");
                    Thread.sleep(100);
                } else {
                    System.out.println("closed");
                    break;
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        Thread.sleep(10000);
    }


    public static class SocketWrapper {
        private Queue<ByteBuffer> writeBuffers = new ArrayBlockingQueue<>(100);
        private ByteArrayOutputStream readBuffer = new ByteArrayOutputStream();
        private SocketChannel socketChannel;

        public SocketWrapper(SocketChannel socketChannel) {
            this.socketChannel = socketChannel;
        }

        public void writeAll(String msg) {
            ByteBuffer src = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
            this.writeBuffers.offer(src);
        }

        private void readAll() throws NetException {
            final SocketChannel channel = this.socketChannel;
            int cap = 1024;
            ByteBuffer buffer = ByteBuffer.allocate(cap);
            ByteArrayOutputStream out = this.readBuffer;
            int read;
            try {
                while ((read = channel.read(buffer)) > 0) {
                    buffer.flip();
                    final byte[] dst = new byte[read];
                    buffer.get(dst);
                    out.write(dst);
                    buffer.clear();
                }
            } catch (Throwable e) {
                throw new NetException("Unknow", e);
            }
            if (read == -1) {
                throw new NetException("EOF");
            }
        }

        public void flush() {
            ByteBuffer buffer;
            while ((buffer = writeBuffers.peek()) != null) {
                try {
                    final int write = socketChannel.write(buffer);
                    System.out.println(Thread.currentThread() + " write " + write);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (!buffer.hasRemaining()) {
                    writeBuffers.poll();
                } else {
                    break;
                }
            }
        }
    }

    public static final class Processor implements Runnable {
        private SelectionKey key;

        public Processor(SelectionKey key) {
            this.key = key;
        }

        @Override
        public void run() {
            if (key.isValid()) {
                final SocketWrapper socketWrapper = (SocketWrapper) key.attachment();
                if (socketWrapper != null) {
                    try {
                        if (key.isReadable()) {
                            socketWrapper.readAll();
                            System.out.println(Thread.currentThread() + " read字节数 " + socketWrapper.readBuffer.size());
                        }
                        if (key.isWritable()) {
                            socketWrapper.flush();
                        }
                    } catch (NetException e) {
                        key.cancel();
                        try {
                            socketWrapper.socketChannel.close();
                        } catch (IOException ex) {
                            System.err.println("close 失败");
                        }
                        return;
                    }
                }
                // 处理完毕，再次注册事件
                key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            }
        }
    }

    public static final class NetException extends Exception {
        public NetException() {
        }

        public NetException(String message) {
            super(message);
        }

        public NetException(String message, Throwable cause) {
            super(message, cause);
        }

        public NetException(Throwable cause) {
            super(cause);
        }

        public NetException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }
}
