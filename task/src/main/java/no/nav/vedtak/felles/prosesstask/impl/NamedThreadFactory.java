package no.nav.vedtak.felles.prosesstask.impl;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread factory which lets us configure name of pooled thread, and daemon
 * flag.
 */
class NamedThreadFactory implements ThreadFactory {
    private final AtomicInteger counter = new AtomicInteger(1);
    private final String prefix;
    private final boolean daemon;

    public NamedThreadFactory(String prefix, boolean daemon) {
        this.prefix = prefix;
        this.daemon = daemon;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, prefix + "_" + String.format("%03d", counter.getAndIncrement()));
        t.setDaemon(daemon);
        return t;
    }
}