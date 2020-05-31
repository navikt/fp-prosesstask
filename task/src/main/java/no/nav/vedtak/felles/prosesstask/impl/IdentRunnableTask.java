package no.nav.vedtak.felles.prosesstask.impl;

import java.time.LocalDateTime;

import org.slf4j.MDC;

/** IdentRunnable som tar id og Runnable. */
class IdentRunnableTask implements IdentRunnable {
    private final Long id;
    private final Runnable runnable;
    private final LocalDateTime createTime;

    IdentRunnableTask(Long id, Runnable run, LocalDateTime createTime) {
        this.id = id;
        this.runnable = run;
        this.createTime = createTime;
    }

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