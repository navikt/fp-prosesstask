package no.nav.vedtak.felles.prosesstask.api;


import no.nav.vedtak.felles.prosesstask.impl.Feil;

/**
 * Midlertidig feil som oppstår pga. transient feil mot annet grensesnitt, database eller annet.
 */
public class ProsessTaskMidlertidigException extends ProsessTaskException {
    public ProsessTaskMidlertidigException(Feil feil) {
        super(feil);
    }
}
