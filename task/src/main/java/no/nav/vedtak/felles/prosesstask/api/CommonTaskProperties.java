package no.nav.vedtak.felles.prosesstask.api;

public class CommonTaskProperties {

    private CommonTaskProperties() {
    }

    /**
     * Standard properties
     */
    public static final String AKTØR_ID = "aktoerId";
    public static final String BEHANDLING_ID ="behandlingId";
    public static final String BEHANDLING_UUID = "behandlingUuid";
    public static final String SAKSNUMMER = "saksnummer";
    /**
     * Erstatt med saksnummer
     * @deprecated use saksnummer
     */
    @Deprecated(forRemoval = true)
    public static final String FAGSAK_ID = "fagsakId";

    /*
     * Reservert for tasks med status VENTER_SVAR
     */
    static final String HENDELSE_PROPERTY = "hendelse";

}
