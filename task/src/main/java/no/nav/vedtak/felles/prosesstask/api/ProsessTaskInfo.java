package no.nav.vedtak.felles.prosesstask.api;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Properties;

import no.nav.vedtak.felles.prosesstask.impl.TaskType;

/**
 * Representerer read-only data om en {@link ProsessTask}.
 */
public interface ProsessTaskInfo {

    String getSisteFeil();

    String getSisteFeilKode();

    int getAntallFeiledeForsøk();

    LocalDateTime getSistKjørt();

    String getTaskType();

    TaskType taskType();

    int getPriority();

    Long getId();

    String getPropertyValue(String key);

    LocalDateTime getNesteKjøringEtter();

    ProsessTaskStatus getStatus();

    String getGruppe();

    String getSekvens();

    String getAktørId();
    
    Long getBlokkertAvProsessTaskId();

    Long getFagsakId();

    String getSaksnummer();
    
    String getBehandlingId();
    
    String getPayloadAsString();

    Optional<ProsessTaskHendelse> getHendelse();

    Properties getProperties();

    LocalDateTime getOpprettetTid();

}