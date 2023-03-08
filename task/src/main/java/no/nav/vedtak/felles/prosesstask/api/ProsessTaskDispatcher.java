package no.nav.vedtak.felles.prosesstask.api;

import no.nav.vedtak.felles.prosesstask.impl.ProsessTaskHandlerRef;

/**
 * Interface som må implementers for å håndtere implementasjoner av oppgaver. Dersom det er flere mulige
 * implementasjoner håndteres dette inni {@link #dispatch(ProsessTaskData)} metoden.
 * <p>
 * I tillegg må
 * <ul>
 * <li>klassen markeres med {@link ProsessTask} annotation slik at den oppdages og kan plugges inn runtime.</li>
 * </ul>
 */
public interface ProsessTaskDispatcher {

    void dispatch(ProsessTaskData task) throws Exception; 

    /** Skal benytte feilhåndtering algoritme for angitt exception. Hvis ikke håndteres den som fatal feil. */
    boolean feilhåndterException(Throwable e);

    ProsessTaskHandlerRef taskHandler(TaskType taskType);

}
