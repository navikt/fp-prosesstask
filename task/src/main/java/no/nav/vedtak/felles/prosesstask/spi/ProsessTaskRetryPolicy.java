package no.nav.vedtak.felles.prosesstask.spi;

public interface ProsessTaskRetryPolicy {

    boolean retryTask(int numFailedRuns, Throwable t);

    int secondsToNextRun(int numFailedRuns);

}