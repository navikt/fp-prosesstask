package no.nav.vedtak.felles.prosesstask.impl;

/** Initialiser tilgang til user identity slik at bruker som endrer kan spores. 
 * Definere link til container-basert login informasjon (user identity). */
public interface SubjectProvider {

    /** Implementer for å gi tilgang til pålogget bruker. (dette variere fra container til container). */ 
    public String getUserIdentity();
    
}
