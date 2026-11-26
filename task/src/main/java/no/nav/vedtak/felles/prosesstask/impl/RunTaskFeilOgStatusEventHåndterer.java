package no.nav.vedtak.felles.prosesstask.impl;

import static no.nav.vedtak.felles.prosesstask.impl.TaskManagerFeil.kunneIkkeProsessereTaskVilIkkePrøveIgjenEnkelFeilmelding;
import static no.nav.vedtak.felles.prosesstask.impl.TaskManagerFeil.kunneIkkeProsessereTaskVilPrøveIgjenEnkelFeilmelding;

import java.time.LocalDateTime;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import no.nav.vedtak.felles.prosesstask.api.ProsessTaskFeil;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;
import no.nav.vedtak.felles.prosesstask.spi.ProsessTaskRetryPolicy;

/**
 * Samler feilhåndtering som skjer på vanlige prosess tasks.
 */
public class RunTaskFeilOgStatusEventHåndterer {

    private static final Logger LOG = LoggerFactory.getLogger(RunTaskFeilOgStatusEventHåndterer.class);

    private final TaskManagerRepositoryImpl taskManagerRepository;

    private final RunTaskInfo taskInfo;

    public RunTaskFeilOgStatusEventHåndterer(RunTaskInfo taskInfo, TaskManagerRepositoryImpl taskManagerRepository) {
        this.taskInfo = taskInfo;
        this.taskManagerRepository = taskManagerRepository;
    }

    /**
     * handle exception on task. Update failure count if less than max.
     */
    protected void handleTaskFeil(ProsessTaskRetryPolicy retryPolicy, ProsessTaskEntitet pte, Throwable t) {
        var taskType = pte.getTaskType();

        var failureAttempt = pte.getFeiledeForsøk() + 1;
        if (sjekkOmSkalKjøresPåNytt(t, retryPolicy, failureAttempt)) {
            var nyTid = getNesteKjøringForNyKjøring(retryPolicy, failureAttempt);
            var feil = kunneIkkeProsessereTaskVilPrøveIgjenEnkelFeilmelding(taskInfo.getId(), taskType, failureAttempt, nyTid, t);
            var feiltekst = getFeiltekstOgLoggEventueltHvisEndret(pte, feil, t, false);
            taskManagerRepository.oppdaterStatusOgNesteKjøring(pte.getId(), ProsessTaskStatus.KLAR, nyTid, feil.kode(), feiltekst, failureAttempt);
        } else {
            var feil = kunneIkkeProsessereTaskVilIkkePrøveIgjenEnkelFeilmelding(taskInfo.getId(), taskType, failureAttempt, t);
            handleFatalTaskFeil(pte, feil, t);
        }
    }

    protected void handleFatalTaskFeil(ProsessTaskEntitet pte, Feil feil, Throwable t) {
        var feiledeForsøk = pte.getFeiledeForsøk() + 1;
        var feiltekst = getFeiltekstOgLoggEventueltHvisEndret(pte, feil, t, true);
        taskManagerRepository.oppdaterStatusOgNesteKjøring(pte.getId(), ProsessTaskStatus.FEILET, null, feil.kode(), feiltekst, feiledeForsøk);
    }

    /**
     * handle recoverable / transient exception.
     */
    protected void handleTransientAndRecoverableException(Throwable t) {
        /*
         * assume won't help to try and write to database just now, log only instead
         */
        LOG.info("PT-530440 Kunne ikke prosessere task pga transient database feil: id={}, taskName={}. Vil automatisk prøve igjen",
                taskInfo.getId(), taskInfo.getTaskType(), t);
    }

    private LocalDateTime getNesteKjøringForNyKjøring(ProsessTaskRetryPolicy retryPolicy, int failureAttempt) {
        var secsBetweenAttempts = retryPolicy.secondsToNextRun(failureAttempt);

        return LocalDateTime.now().plusSeconds(secsBetweenAttempts);
    }

    private boolean sjekkOmSkalKjøresPåNytt(Throwable t, ProsessTaskRetryPolicy retryPolicy, int failureAttempt) {

        // Prøv på nytt hvis kjent exception og feilhåndteringsalgoritmen tilsier nytt forsøk. Ellers fail-fast
        if (feilhåndteringExceptions(t) || (t.getCause() != null && feilhåndteringExceptions(t.getCause()))) {
            return retryPolicy.retryTask(failureAttempt, t);
        }
        return false;
    }

    private boolean feilhåndteringExceptions(Throwable t) {
        return taskInfo.feilhåndterException(t);
    }

    protected static String getFeiltekstOgLoggEventueltHvisEndret(ProsessTaskEntitet pte, Feil feil, Throwable t, boolean erEndeligFeil) {

        var taskFeil = ProsessTaskFeil.lagProsessTaskFeil(pte.tilProsessTask(), feil);

        var feilkode = taskFeil.feilkode();
        String feiltekst;
        try {
            feiltekst = taskFeil.writeValueAsString();
        } catch (Exception e1) {
            // kunne ikke skrive ut json, log stack trace
            feiltekst = "Kunne ikke skrive ut json struktur for feil: " + feilkode + ", json exception: " + e1;
            LOG.warn(feiltekst, t);
        }

        if (erEndeligFeil
                || feilkode == null
                || !Objects.equals(feilkode, pte.getSisteFeilKode())
                || !Objects.equals(feiltekst, pte.getSisteFeilTekst())) {
            // logg hvis første gang feil, er feil som ikke vil rekjøres, eller feil er
            // endret
            feil.log(LOG);
        }
        return feiltekst;
    }

}
