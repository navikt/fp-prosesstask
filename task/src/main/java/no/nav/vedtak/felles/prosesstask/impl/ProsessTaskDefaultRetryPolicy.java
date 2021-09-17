package no.nav.vedtak.felles.prosesstask.impl;

import no.nav.vedtak.felles.prosesstask.spi.ProsessTaskRetryPolicy;

public class ProsessTaskDefaultRetryPolicy implements ProsessTaskRetryPolicy {

    private int maxFailedRuns;
    private int delayBetweenRuns;

    /*
     * Methods and parameters for default backoff failure handling strategy
     * Tasks can implement any of these methods to change strategy or configuration
     */

    public ProsessTaskDefaultRetryPolicy(int maxFailedRuns, int delayBetweenRuns) {
        this.maxFailedRuns = Math.max(maxFailedRuns, 1);
        this.delayBetweenRuns = Math.max(delayBetweenRuns, 0);
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
        if (numFailedRuns == 0) return 0;
        return numFailedRuns * delayBetweenRuns;
    }

}