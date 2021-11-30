package com.arloor.connect.reactor;

import com.arloor.connect.ConnectorReactor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import static com.arloor.connect.ConnectorReactor.NetException;

public class SocketWrapper {
    private final ConnectorReactor connectorReactor;
    private java.util.Queue<ByteBuffer> writeBuffers = new ArrayBlockingQueue<>(100);
    private ByteArrayOutputStream readBuffer = new ByteArrayOutputStream();
    private SocketChannel socketChannel;
    private EventLoop worker;

    public SocketWrapper(ConnectorReactor connectorReactor, SocketChannel socketChannel) {
        this.connectorReactor = connectorReactor;
        this.socketChannel = socketChannel;
        this.worker = connectorReactor.getWorker();
    }

    public void writeAll(String msg) {
        ByteBuffer src = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
        this.writeBuffers.offer(src);
        flush();
    }

    public void readAll() throws NetException {
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
                try {
                    socketChannel.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            if (!buffer.hasRemaining()) {
                writeBuffers.poll();
            } else {
                break;
            }
        }
    }

    public ConnectorReactor getConnectorReactor() {
        return connectorReactor;
    }

    public Queue<ByteBuffer> getWriteBuffers() {
        return writeBuffers;
    }

    public ByteArrayOutputStream getReadBuffer() {
        return readBuffer;
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    public EventLoop getWorker() {
        return worker;
    }
}
