package com.arloor.connect;

import com.arloor.connect.reactor.Event;
import com.arloor.connect.reactor.EventLoop;
import com.arloor.connect.reactor.EventWrapper;
import com.arloor.connect.reactor.SocketWrapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectorReactor implements Runnable {
    private Selector selector;
    private ConcurrentLinkedQueue<SocketWrapper> waitQueue = new ConcurrentLinkedQueue<>();
    private EventLoop[] workers = new EventLoop[8];
    private AtomicInteger index = new AtomicInteger(0);

    public EventLoop getWorker() {
        return workers[index.getAndIncrement() % workers.length];
    }


    public ConnectorReactor() throws IOException {
        selector = Selector.open();
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new EventLoop(selector);
            workers[i].start();
        }
    }

    public SocketWrapper connect(String host, int port) {
        try {
            final SocketChannel socketChannel = SocketChannel.open();
            // 先阻塞connect
            socketChannel.socket().connect(new InetSocketAddress(host, port), 5000);
            // 设置为非阻塞
            socketChannel.configureBlocking(false);
            final SocketWrapper socketWrapper = new SocketWrapper(this, socketChannel);
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
                    socketWrapper.getSocketChannel().register(selector, SelectionKey.OP_READ, socketWrapper);
                }
                final int numKey = selector.select(10);
                if (numKey > 0) {
                    Set<SelectionKey> keys = selector.selectedKeys();
                    Iterator<SelectionKey> keyIterator = keys.iterator();
                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        keyIterator.remove();
                        final SocketWrapper wrapper = (SocketWrapper) key.attachment();
                        // 取消注册，防止不断的可读事件
                        key.interestOps(key.interestOps() & (~key.readyOps()));
                        wrapper.getWorker().addEvent(new EventWrapper(Event.OP_READ, key, wrapper));
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
                if (socketWrapper.getSocketChannel().isOpen()) {
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
        Thread.sleep(1000);
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
