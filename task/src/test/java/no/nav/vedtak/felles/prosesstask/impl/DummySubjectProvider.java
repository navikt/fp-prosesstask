package no.nav.vedtak.felles.prosesstask.impl;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DummySubjectProvider implements SubjectProvider {

    @Override
    public String getUserIdentity() {
        return "dummy-user-identity";
    }

}
