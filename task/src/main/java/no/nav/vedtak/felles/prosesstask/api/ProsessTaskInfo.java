package no.nav.vedtak.felles.prosesstask.api;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/**
 * Representerer read-only data om en {@link ProsessTask}.
 */
public interface ProsessTaskInfo {

    /*
     * Generell informasjon og domenedata
     */
    TaskType taskType();

    String getTaskType();

    ProsessTaskStatus getStatus();

    String getPayloadAsString();

    Optional<String> getVentetHendelse();

    Properties getProperties();

    String getPropertyValue(String key);

    /*
     * Kjøretidsinformasjon
     */
    String getGruppe();

    String getSekvens();

    int getPriority();

    LocalDateTime getNesteKjøringEtter();

    LocalDateTime getSistKjørt();

    /*
     * Failrelatert informasjon
     */
    String getSisteFeil();

    String getSisteFeilKode();

    int getAntallFeiledeForsøk();

    /*
     * Internt
     */

    Long getId();

    Long getBlokkertAvProsessTaskId();

    LocalDateTime getOpprettetTid();

    /*
     * Disse er @Deprecated bør flyttes til applikasjon der de kan mappes til aktuelle valuetypes
     */
    String getAktørId();

    @Deprecated
    Long getFagsakId();

    String getSaksnummer();
    
    String getBehandlingId();

    UUID getBehandlingUuid();
}