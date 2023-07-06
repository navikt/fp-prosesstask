package no.nav.vedtak.prosesstask.kontekst;

import java.util.Optional;

import jakarta.enterprise.context.Dependent;

import no.nav.foreldrepenger.konfig.Environment;
import no.nav.vedtak.felles.prosesstask.impl.SubjectProvider;
import no.nav.vedtak.sikkerhet.kontekst.Kontekst;
import no.nav.vedtak.sikkerhet.kontekst.KontekstHolder;

/** henter brukernavn fra kontekst i felles. */
@Dependent
public class KontekstSubjectProvider implements SubjectProvider {

    private static final String ENV_SYSTEM_USER = "srv" + Optional.ofNullable(Environment.current().application()).orElse("local");

    @Override
    public String getUserIdentity() {
        return Optional.ofNullable(KontekstHolder.getKontekst()).map(Kontekst::getKompaktUid)
            .orElse(ENV_SYSTEM_USER);
    }

}

