package no.nav.vedtak.felles.prosesstask.api;

/**
 * Implementerer en ProsessTask. Klasser som implementere bør også annoteres med {@link ProsessTask} for å angi type.
 */
public interface ProsessTaskHandler {
    void doTask(ProsessTaskData prosessTaskData);

    /**
     * Cron-expression to schedule next instance of a repeating task.
     */
    default String cronExpression() {
        return null;
    }

    /*
     * Methods and parameters for default backoff failure handling strategy
     * Tasks can implement any of these methods to change strategy or configuration
     */
    default boolean retryTask(int numFailedRuns, @SuppressWarnings("unused") Throwable t) {
        return numFailedRuns < maxFailedRuns();
    }

    default int secondsToNextRun(int numFailedRuns) {
        if (numFailedRuns >= maxFailedRuns()) {
            throw new IllegalArgumentException("Max number of runs exceeded");
        }
        if (numFailedRuns == 0) return 0;
        return numFailedRuns * baseSecondsBetweenRuns();
    }

    default int maxFailedRuns() {
        return 3;
    }

    default int baseSecondsBetweenRuns() {
        return 30;
    }

}
