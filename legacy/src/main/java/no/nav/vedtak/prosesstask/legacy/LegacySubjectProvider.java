package no.nav.vedtak.prosesstask.legacy;

import javax.enterprise.context.Dependent;

import no.nav.vedtak.felles.prosesstask.impl.SubjectProvider;
import no.nav.vedtak.sikkerhet.context.SubjectHandler;

/** henter subject fra SubjectHandler@fp-felles. */
@Dependent
public class LegacySubjectProvider implements SubjectProvider {

    @Override
    public String getUserIdentity() {
        return SubjectHandler.getSubjectHandler().getUid();
    }

}

