package no.nav.vedtak.felles.prosesstask.api;

import java.util.List;
import java.util.Properties;

public interface ProsessTaskTjeneste {

    /** Validerer at det finnes implementasjon, at properties er satt, lagrer tasksgruppe og returner gruppe id. */
    String lagreValidert(ProsessTaskGruppe tasks);
    String lagre(ProsessTaskGruppe tasks); // Skipper validering - OK hvis opprettet fra classe

    /** Validerer at det finnes implementasjon, påkrevde properties er satt, lagrer task og returner gruppe id . */
    String lagreValidert(ProsessTaskData enkeltTask);
    String lagre(ProsessTaskData enkeltTask); // Skipper validering - OK hvis opprettet fra classe

    ProsessTaskData finn(Long prosessTaskId);

    List<ProsessTaskData> finnAlle(ProsessTaskStatus... status);

    void flaggProsessTaskForRestart(Long prosessTaskId, String oppgittStatus);

    List<Long> flaggAlleFeileteProsessTasksForRestart();

    void setProsessTaskFerdig(Long prosessTaskId, ProsessTaskStatus status);

    /** Tar task av vent ved mottatt hendelse og setter properties */
    void mottaHendelse(ProsessTaskData task, String hendelse);
    void mottaHendelse(ProsessTaskData task, String hendelse, Properties properties);
}
