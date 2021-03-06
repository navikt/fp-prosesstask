package no.nav.vedtak.felles.prosesstask.impl;

import static io.micrometer.core.instrument.Metrics.counter;
import static no.nav.vedtak.felles.prosesstask.impl.TaskManagerFeil.kunneIkkeProsessereTaskVilIkkePrøveIgjenEnkelFeilmelding;
import static no.nav.vedtak.felles.prosesstask.impl.TaskManagerFeil.kunneIkkeProsessereTaskVilPrøveIgjenEnkelFeilmelding;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.enterprise.inject.Instance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskFeil;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;
import no.nav.vedtak.felles.prosesstask.spi.ProsessTaskFeilhåndteringAlgoritme;

/**
 * Samler feilhåndtering og status publisering som skjer på vanlige prosess
 * tasks.
 */
public class RunTaskFeilOgStatusEventHåndterer {

    private static final String TYPE = "type";

    private static final String TASK_FEIL = "task.feil";

    private static final String SEVERITY = "severity";

    private static final Logger LOG = LoggerFactory.getLogger(RunTaskFeilOgStatusEventHåndterer.class);

    private final ProsessTaskEventPubliserer eventPubliserer;
    private final TaskManagerRepositoryImpl taskManagerRepository;
    private final Instance<ProsessTaskFeilhåndteringAlgoritme> feilhåndteringsalgoritmer;

    private final RunTaskInfo taskInfo;

    public RunTaskFeilOgStatusEventHåndterer(RunTaskInfo taskInfo, ProsessTaskEventPubliserer eventPubliserer,
            TaskManagerRepositoryImpl taskManagerRepository,
            Instance<ProsessTaskFeilhåndteringAlgoritme> feilhåndteringsalgoritmer) {
        this.taskInfo = taskInfo;
        this.eventPubliserer = eventPubliserer;
        this.taskManagerRepository = taskManagerRepository;
        this.feilhåndteringsalgoritmer = feilhåndteringsalgoritmer;
    }

    protected void publiserNyStatusEvent(ProsessTaskData data, ProsessTaskStatus gammelStatus, ProsessTaskStatus nyStatus) {
        publiserNyStatusEvent(data, gammelStatus, nyStatus, null, null);
    }

    protected void publiserNyStatusEvent(ProsessTaskData data, ProsessTaskStatus gammelStatus, ProsessTaskStatus nyStatus, Feil feil, Exception e) {
        if (eventPubliserer != null) {
            eventPubliserer.fireEvent(data, gammelStatus, nyStatus, feil, e);
        }
    }

    /**
     * handle exception on task. Update failure count if less than max. NB:
     * Exception may be null if a lifecycle observer vetoed it (veto==true)
     */
    protected void handleTaskFeil(ProsessTaskEntitet pte, Exception e) {
        String taskName = pte.getTaskName();
        var taskType = taskManagerRepository.getTaskType(taskName);
        var feilhåndteringsData = taskType.getFeilhåndteringAlgoritme();
        var feilhåndteringsalgoritme = getFeilhåndteringsalgoritme(feilhåndteringsData.getKode());

        int failureAttempt = pte.getFeiledeForsøk() + 1;

        if (sjekkOmSkalKjøresPåNytt(e, taskType, feilhåndteringsalgoritme, failureAttempt)) {
            LocalDateTime nyTid = getNesteKjøringForNyKjøring(feilhåndteringsData, feilhåndteringsalgoritme, failureAttempt);
            var feil = kunneIkkeProsessereTaskVilPrøveIgjenEnkelFeilmelding(taskInfo.getId(), taskName, failureAttempt, nyTid, e);
            String feiltekst = getFeiltekstOgLoggEventueltHvisEndret(pte, feil, e, false);
            taskManagerRepository.oppdaterStatusOgNesteKjøring(pte.getId(), ProsessTaskStatus.KLAR, nyTid, feil.kode(), feiltekst, failureAttempt);
            count("retryable");
            // endrer ikke status ved nytt forsøk eller publiserer event p.t.
        } else {
            var feil = feilhåndteringsalgoritme.hendelserNårIkkeKjøresPåNytt(e, pte.tilProsessTask());
            if (feil == null) {
                feil = kunneIkkeProsessereTaskVilIkkePrøveIgjenEnkelFeilmelding(taskInfo.getId(), taskName, failureAttempt, e);
            }
            handleFatalTaskFeil(pte, feil, e);
        }
    }

    protected void handleFatalTaskFeil(ProsessTaskEntitet pte, Feil feil, Exception e) {
        var nyStatus = ProsessTaskStatus.FEILET;
        try {
            publiserNyStatusEvent(pte.tilProsessTask(), pte.getStatus(), nyStatus, feil, e);
            count("fatal");
        } finally {
            int failureAttempt = pte.getFeiledeForsøk() + 1;
            String feiltekst = getFeiltekstOgLoggEventueltHvisEndret(pte, feil, e, true);
            taskManagerRepository.oppdaterStatusOgNesteKjøring(pte.getId(), nyStatus, null, feil.kode(), feiltekst, failureAttempt);
        }
    }

    /**
     * handle recoverable / transient exception.
     */
    protected void handleTransientAndRecoverableException(Exception e) {
        /*
         * assume won't help to try and write to database just now, log only instead
         */
        count("transient");
        LOG.warn("PT-530440 Kunne ikke prosessere task pga transient database feil: id={}, taskName={}. Vil automatisk prøve igjen",
                taskInfo.getId(), taskInfo.getTaskType(), e);
    }

    private LocalDateTime getNesteKjøringForNyKjøring(ProsessTaskFeilhand feilhåndteringsData,
            ProsessTaskFeilhåndteringAlgoritme feilhåndteringsalgoritme,
            int failureAttempt) {
        int secsBetweenAttempts = feilhåndteringsalgoritme.getForsinkelseStrategi().sekunderTilNesteForsøk(failureAttempt,
                feilhåndteringsData);

        LocalDateTime nyTid = LocalDateTime.now().plusSeconds(secsBetweenAttempts);
        return nyTid;
    }

    private boolean sjekkOmSkalKjøresPåNytt(Exception e, ProsessTaskType taskType, ProsessTaskFeilhåndteringAlgoritme feilhåndteringsalgoritme,
            int failureAttempt) {

        // Prøv på nytt hvis kjent exception og feilhåndteringsalgoritmen tilsier nytt
        // forsøk. Ellers fail-fast
        if (feilhåndteringExceptions(e) || (e.getCause() != null && feilhåndteringExceptions(e.getCause()))) {
            return feilhåndteringsalgoritme.skalKjørePåNytt(taskType.tilProsessTaskTypeInfo(), failureAttempt, e);
        }
        return false;
    }

    private boolean feilhåndteringExceptions(Throwable e) {
        return taskInfo.feilhåndterException(e);
    }

    protected static String getFeiltekstOgLoggEventueltHvisEndret(ProsessTaskEntitet pte, Feil feil, Throwable t, boolean erEndeligFeil) {

        var taskFeil = new ProsessTaskFeil(pte.tilProsessTask(), feil);

        String feilkode = taskFeil.getFeilkode();
        String feiltekst = null;
        try {
            feiltekst = taskFeil.writeValueAsString();
        } catch (IOException e1) {
            // kunne ikke skrive ut json, log stack trace
            feiltekst = "Kunne ikke skrive ut json struktur for feil: " + feilkode + ", json exception: " + e1;
            LOG.warn(feiltekst, t); // NOSONAR
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

    protected ProsessTaskFeilhåndteringAlgoritme getFeilhåndteringsalgoritme(String kode) {
        List<ProsessTaskFeilhåndteringAlgoritme> kandidater = new ArrayList<>(1);
        for (var algoritme : feilhåndteringsalgoritmer) {
            if (algoritme.kode().equals(kode)) {
                kandidater.add(algoritme);
            }
        }
        if (kandidater.size() == 1) {
            return kandidater.get(0);
        }
        throw new IllegalStateException("Forventet å finne 1 feilhåndteringsalgoritme for '" + kode + "', men fant " + kandidater); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private void count(String severity) {
        counter(TASK_FEIL, SEVERITY, severity, TYPE, taskInfo.getTaskType()).increment();
    }
}
