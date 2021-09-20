package no.nav.vedtak.felles.prosesstask.impl;

import no.nav.vedtak.felles.prosesstask.spi.ProsessTaskRetryPolicy;

public class ProsessTaskDefaultRetryPolicy implements ProsessTaskRetryPolicy {

    private final int maxFailedRuns;
    private final int firstDelay;
    private final int thenDelay;

    /*
     * Methods and parameters for default backoff failure handling strategy
     * Tasks can implement any of these methods to change strategy or configuration
     */

    public ProsessTaskDefaultRetryPolicy(int maxFailedRuns, int firstDelay, int thenDelay) {
        this.maxFailedRuns = Math.max(maxFailedRuns, 1); // Initial run failed
        this.firstDelay = Math.max(firstDelay, 0);
        this.thenDelay = Math.max(thenDelay, 0);
    }

    @Override
    public boolean retryTask(int numFailedRuns, @SuppressWarnings("unused") Throwable t) {
        return numFailedRuns < maxFailedRuns;
    }

    @Override
    public int secondsToNextRun(int numFailedRuns) {
        if (numFailedRuns >= maxFailedRuns) {
            throw new IllegalArgumentException("Max number of runs exceeded");
        }
        if (numFailedRuns < 1) {
            return 0;
        } else if (numFailedRuns == 1) {
            return firstDelay;
        } else {
            return (numFailedRuns - 1) * thenDelay;
        }
    }

}