package no.nav.vedtak.felles.prosesstask.api;

import no.nav.vedtak.exception.TekniskException;
import no.nav.vedtak.felles.prosesstask.impl.Feil;


public class ProsessTaskKritiskException extends TekniskException {
    public ProsessTaskKritiskException(Feil feil) {
        super(feil.getKode(), feil.getFeilmelding(), feil.getCause());
    }
}
