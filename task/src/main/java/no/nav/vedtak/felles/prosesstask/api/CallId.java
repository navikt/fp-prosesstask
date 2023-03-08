package no.nav.vedtak.felles.prosesstask.api;

import java.util.Random;

public class CallId {

    /** Forventer at callId ligger i MDC som følgende parameter. */
    public static final String CALL_ID = "callId";

    private static final Random RANDOM = new Random(); // NOSONAR  - denne er OK

    public static String generateCallId() {
        int randomNr = RANDOM.nextInt(Integer.MAX_VALUE);
        long systemTime = System.currentTimeMillis();

        StringBuilder callId = new StringBuilder("CallId_")
                .append(systemTime)
                .append('_')
                .append(randomNr);

        return callId.toString();
    }

}

