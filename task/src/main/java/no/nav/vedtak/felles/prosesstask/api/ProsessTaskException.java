package no.nav.vedtak.felles.prosesstask.api;

import no.nav.vedtak.exception.TekniskException;
import no.nav.vedtak.felles.prosesstask.impl.Feil;

public class ProsessTaskException extends TekniskException {
    public ProsessTaskException(Feil feil) {
        super(feil.getKode(), feil.getFeilmelding(), feil.getCause());
    }
}
