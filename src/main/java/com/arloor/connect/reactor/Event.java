package com.arloor.connect.reactor;

import com.arloor.connect.ConnectorReactor;

import java.io.IOException;
import java.nio.channels.SelectionKey;

public enum Event {
    OP_READ;

    public void process(SelectionKey key, SocketWrapper socketWrapper) {
        try {
            socketWrapper.readAll();
            System.out.println(Thread.currentThread() + " read字节数 " + socketWrapper.getReadBuffer().size());
            key.interestOps(SelectionKey.OP_READ);
        } catch (ConnectorReactor.NetException e) {
            e.printStackTrace();
            key.cancel();
            try {
                socketWrapper.getSocketChannel().close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}
