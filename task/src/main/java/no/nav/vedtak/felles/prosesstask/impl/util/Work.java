package no.nav.vedtak.felles.prosesstask.impl.util;

import java.util.concurrent.Callable;

/**
 * Functional interface som representerer et stykke arbeid.
 * Kunne vært en {@link Callable} men skilt ut for å synliggjøre hvor det brukes.
 */
@FunctionalInterface
public interface Work<V> {
    V doWork(); // NOSONAR
}