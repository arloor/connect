package com.arloor.connect.reactor;

import java.nio.channels.Selector;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class EventLoop extends Thread {
    private Selector selector;
    private BlockingQueue<EventWrapper> queue = new ArrayBlockingQueue<>(1000);

    public EventLoop(Selector selector) {
        this.selector = selector;
    }

    public boolean addEvent(EventWrapper eventWrapper) {
        return queue.offer(eventWrapper);
    }

    @Override
    public void run() {
        do {
            try {
                final EventWrapper eventWrapper = queue.poll(10000, TimeUnit.SECONDS);
                if (eventWrapper != null) {
                    eventWrapper.process();
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        } while (true);
    }
}
