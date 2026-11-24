package no.nav.vedtak.felles.prosesstask.api;

import java.util.regex.Pattern;

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

    public static final Pattern VALID_SAKSNUMMER = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_:-]*$");

}
