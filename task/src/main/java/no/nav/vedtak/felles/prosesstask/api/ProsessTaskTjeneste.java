package no.nav.vedtak.felles.prosesstask.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

public interface ProsessTaskTjeneste {

    /** Validerer at det finnes implementasjon, at properties er satt, lagrer tasksgruppe og returner gruppe id. */
    String lagreValidert(ProsessTaskGruppe tasks);
    String lagre(ProsessTaskGruppe tasks); // Skipper validering - OK hvis opprettet fra classe

    /** Validerer at det finnes implementasjon, påkrevde properties er satt, lagrer task og returner gruppe id . */
    String lagreValidert(ProsessTaskData enkeltTask);
    String lagre(ProsessTaskData enkeltTask); // Skipper validering - OK hvis opprettet fra classe

    /*
     * Oppslag på taskId, gruppe, status, eller property-tekst
     * Søk på gruppe vil ikke inkludere tasks med status FERDIG
     * Det er ikke tillatt å kalle finnAlle med status FERDIG
     * Søk etter en tekststreng innen tidsintervall kan ta lang tid - fortrinnsvis interaktiv bruk
     */
    ProsessTaskData finn(Long prosessTaskId);

    List<ProsessTaskData> finnUferdigForGruppe(String gruppe);

    List<ProsessTaskData> finnAlleMedParameterTekst(String tekst, LocalDate fom, LocalDate tom);

    // Søk på tasks som ikke er kjørt ferdig (FERDIG/KJOERT)
    List<ProsessTaskData> finnAktive();
    List<ProsessTaskData> finnAktive(TaskType taskType);
    List<ProsessTaskData> finnAktive(String saksnummer);
    List<ProsessTaskData> finnAktive(ProsessTaskStatus... status);
    List<ProsessTaskData> finnAktive(TaskType taskType, ProsessTaskStatus... status);
    List<ProsessTaskData> finnAktive(String saksnummer, ProsessTaskStatus... status);

    /*
     * Tar task av vent ved mottatt hendelse og setter eventuelle properties
     */
    void mottaHendelse(ProsessTaskData task, String hendelse, Properties properties);

    /*
     * Setter hhv angitt og alle feilede task til klar slik at de kjøres på nytt
     */
    void flaggProsessTaskForRestart(Long prosessTaskId, ProsessTaskStatus oppgittStatus);
    int restartAlleFeiledeTasks();

    /*
     * Setter angitttask til status FERDIG så den ikke
     */
    void setProsessTaskFerdig(Long prosessTaskId, ProsessTaskStatus status);

    /*
     * Slett ett år gamle tasks for hhv Oracle (slettÅrsgamleFerdige) og Postgres (tømNestePartisjon - antatt partisjoner)
     */
    void slettÅrsgamleFerdige();
    void slettFerdigeEldreEnn(LocalDateTime opprettetFør);
    void tømNestePartisjon();

}
