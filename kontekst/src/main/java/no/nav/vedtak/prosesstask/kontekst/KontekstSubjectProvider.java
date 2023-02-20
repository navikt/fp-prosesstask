package no.nav.vedtak.prosesstask.kontekst;

import java.util.Optional;

import javax.enterprise.context.Dependent;

import no.nav.vedtak.felles.prosesstask.impl.SubjectProvider;
import no.nav.vedtak.sikkerhet.kontekst.Kontekst;
import no.nav.vedtak.sikkerhet.kontekst.KontekstHolder;
import no.nav.vedtak.sikkerhet.kontekst.Systembruker;

/** henter brukernavn fra kontekst i felles. */
@Dependent
public class KontekstSubjectProvider implements SubjectProvider {

    @Override
    public String getUserIdentity() {
        // TODO endre når vi slutter med klassisk systembruker og går over til Azure - enten appnavn eller srv<appnavn>
        return Optional.ofNullable(KontekstHolder.getKontekst()).map(Kontekst::getKompaktUid)
            .orElseGet(Systembruker::username);
    }

}

