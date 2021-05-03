package no.nav.vedtak.felles.prosesstask.impl;

import java.time.LocalDateTime;

import org.slf4j.MDC;

/** IdentRunnable som tar id og Runnable. */
record IdentRunnableTask(Long id, Runnable runnable, LocalDateTime createTime) implements IdentRunnable {

    @Override
    public void run() {
        MDC.clear();
        runnable.run();
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public LocalDateTime getCreateTime() {
        return createTime;
    }
}