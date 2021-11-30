package com.arloor.connect.reactor;

import java.nio.channels.SelectionKey;

public class EventWrapper {
    private Event event;
    private SelectionKey key;
    private SocketWrapper socketWrapper;

    public EventWrapper(Event event, SelectionKey key, SocketWrapper socketWrapper) {
        this.event = event;
        this.key = key;
        this.socketWrapper = socketWrapper;
    }

    public void process() {
        event.process(key, socketWrapper);
    }
}
