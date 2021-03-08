package no.nav.vedtak.felles.prosesstask.impl;

import java.time.LocalDateTime;

import org.slf4j.event.Level;

/**
 * Feilkoder knyttet til kjøring av TaskManager.
 */
class TaskManagerFeil  {


    static Feil kunneIkkeProsessereTaskVilPrøveIgjenEnkelFeilmelding(Long taskId, String taskName, int failureAttempt, LocalDateTime localDateTime, Exception e) {
        return new Feil("PT-415564",
                String.format("Kunne ikke prosessere task, id=%s, taskName=%s. Vil automatisk prøve igjen. Forsøk=%s, Neste kjøring=%s",
                        taskId, taskName, failureAttempt, localDateTime),
                Level.INFO, e);
    }


    static Feil kunneIkkeProsessereTaskVilIkkePrøveIgjenEnkelFeilmelding(Long taskId, String taskName, Integer feiletAntall, Exception e) {
        return new Feil("PT-876625",
                String.format("Kunne ikke prosessere task, id=%s, taskName=%s. Har feilet %s ganger. Vil ikke prøve igjen",
                        taskId, taskName, feiletAntall),
                Level.WARN, e);
    }

    static Feil kunneIkkeProsessereTaskPgaFatalFeilVilIkkePrøveIgjen(Long taskId, String taskName, Integer feiletAntall, Throwable t) {
        return new Feil("PT-876627",
                String.format("Kunne ikke prosessere task pga fatal feil (forårsaker transaksjon rollback), id=%s, taskName=%s. Har feilet %s ganger. Vil ikke prøve igjen",
                        taskId, taskName, feiletAntall),
                Level.WARN, t);
    }
    
    static Feil kunneIkkeProsessereTaskFeilKonfigurasjon(Long id, String name, Exception e) {
        return new Feil("PT-853562",
                String.format("Kunne ikke prosessere task, id=%s, taskName=%s. Feil konfigurasjon",
                        id, name),
                Level.ERROR, e);
    }


    static Feil kanIkkeKjøreFikkVeto(Long taskId, String taskName, Long blokkertAvProsessTaskId, String vetoBegrunnelse) {
        return new Feil("PT-900909",
                String.format("Kan ikke kjøre task [%s, %s], fikk veto. Blokkert av task [%s]: %s",
                        taskId, taskName, blokkertAvProsessTaskId, vetoBegrunnelse),
                Level.INFO, null);
    }

    
}
