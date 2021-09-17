package no.nav.vedtak.felles.prosesstask.api;

import no.nav.vedtak.felles.prosesstask.impl.ProsessTaskDefaultRetryPolicy;
import no.nav.vedtak.felles.prosesstask.spi.ProsessTaskRetryPolicy;

/**
 * Implementerer en ProsessTask. Klasser som implementere bør også annoteres med {@link ProsessTask} for å angi type.
 */
public interface ProsessTaskHandler {
    void doTask(ProsessTaskData prosessTaskData);

    /*
     * Default policy is to retry 2 times with an increasing delay, first after 30s

     * Local policies can be provided by implementing retryPolicy
     * - The default policy can be used with other settings for maxFailedRuns and delay.
     * - Other retry strategies.
     */
    default ProsessTaskRetryPolicy retryPolicy() {
        return new ProsessTaskDefaultRetryPolicy(3, 30);
    }


}
