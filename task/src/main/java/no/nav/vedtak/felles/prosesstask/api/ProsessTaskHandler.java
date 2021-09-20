package no.nav.vedtak.felles.prosesstask.api;

import java.util.Optional;
import java.util.Set;

import no.nav.vedtak.felles.prosesstask.spi.ProsessTaskRetryPolicy;

/**
 * Implementerer en ProsessTask. Klasser som implementere bør også annoteres med {@link ProsessTask} for å angi type.
 */
public interface ProsessTaskHandler {
    /*
     *
     */
    void doTask(ProsessTaskData prosessTaskData);

    /*
     * The default errer handling policy is to retry 2 times with an increasing delay, first after 30s.
     * This policy can be configured in the ProsessTask annotation
     *
     * Other policies can be provided by implementing the method retryPolicy()
     */
    default Optional<ProsessTaskRetryPolicy> retryPolicy() {
        return Optional.empty();
    }

    /*
     * Property names required by doTask. For validation when creating new tasks
     */
    default Set<String> requiredProperties() {
        return Set.of();
    }
}
