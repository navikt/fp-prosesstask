package no.nav.vedtak.prosesstask.legacy;

import javax.enterprise.context.ApplicationScoped;

import no.nav.vedtak.felles.prosesstask.impl.SubjectProvider;
import no.nav.vedtak.sikkerhet.context.SubjectHandler;

@ApplicationScoped
public class LegacySubjectProvider implements SubjectProvider {

    @Override
    public String getUserIdentity() {
        return SubjectHandler.getSubjectHandler().getUid();
    }

}
