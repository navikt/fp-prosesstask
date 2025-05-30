package no.nav.vedtak.felles.prosesstask.impl;

import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.OptimisticLockException;

import org.hibernate.Session;
import org.hibernate.exception.JDBCConnectionException;
import org.hibernate.jdbc.Work;
import org.jboss.weld.interceptor.util.proxy.TargetInstanceProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.InjectionException;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import no.nav.vedtak.felles.jpa.savepoint.SavepointRolledbackException;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskDataBuilder;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskMidlertidigException;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;
import no.nav.vedtak.felles.prosesstask.impl.cron.CronExpression;
import no.nav.vedtak.felles.prosesstask.impl.util.OtelUtil;
import no.nav.vedtak.felles.prosesstask.spi.ProsessTaskRetryPolicy;

/**
 * Kjører en task. Flere JVM'er kan kjøre tasks i parallell
 * <p>
 * Kun en task kjøres, i sin egen transaksjon.
 * <p>
 * Dersom en task kjøring feiler, benyttes spesifisert feilhåndteringsalgoritme
 * til å avgjøre hvordan det skal håndteres, og evt. prøves på nytt. (default
 * algoritme prøver 3 ganger med mindre det er kritisk feil).
 */
@Dependent
@ActivateRequestContext
@Transactional
public class RunTask {
    private static final Logger LOG = LoggerFactory.getLogger(RunTask.class);

    private ProsessTaskEventPubliserer eventPubliserer;
    private TaskManagerRepositoryImpl taskManagerRepository;
    private RunTaskVetoHåndterer vetoHåndterer;

    @Inject
    public RunTask(TaskManagerRepositoryImpl taskManagerRepo,
            ProsessTaskEventPubliserer eventPubliserer) {
        Objects.requireNonNull(taskManagerRepo, "taskManagerRepo");

        this.eventPubliserer = eventPubliserer;
        this.taskManagerRepository = taskManagerRepo;
        this.vetoHåndterer = new RunTaskVetoHåndterer(eventPubliserer, taskManagerRepo.getEntityManager());
    }

    public void doRun(RunTaskInfo taskInfo) {
        new PickAndRunTask(taskInfo).runTask();
    }

    /**
     * Markerer task for kjøring, tar savepoint og forsøker å kjøre task, inklusiv
     * håndtering av forventede feil. Uventede feil som forårsaker rollback av hele
     * transaksjonen (eks {@link EntityNotFoundException} delegeres oppover).
     * Gjelder også totalt transiente feil (eks. JDBCConnectionException)
     *
     * @throws SQLException         - dersom ikke kan ta savepoint
     * @throws PersistenceException dersom transaksjoner er markert for total
     *                              rollback (dvs. savepoint vil ikke virke)
     */
    protected void runTaskAndUpdateStatus(Connection conn, ProsessTaskEntitet pte, PickAndRunTask pickAndRun)
            throws SQLException {
        var taskType = pte.getTaskType();
        pickAndRun.markerTaskUnderArbeid(pte);

        // set up a savepoint to rollback to in case of failure
        var savepoint = conn.setSavepoint();

        try {
            if (vetoHåndterer.vetoRunTask(pte)) {
                return;
            }

            OtelUtil.wrapper().span("TASK " + taskType.value(), OtelUtil.taskAttributter(pte.tilProsessTask()), () -> {
                pickAndRun.dispatchWork(pte);
                // flush for å fange andre constraint feil etc før vi markerer ferdig
                getEntityManager().flush();
            });


            if (ProsessTaskStatus.KLAR == pte.getStatus()) {
                var sluttStatus = pickAndRun.markerTaskFerdig(pte);

                if (LOG.isInfoEnabled()) {
                    LOG.info("Prosesstask [{}], id={}, status={}", pte.getTaskType().value(), pte.getId(), sluttStatus);
                }
                pickAndRun.planleggNesteKjøring(pte);
            }

        } catch (JDBCConnectionException
                 | SQLTransientException
                 | SQLNonTransientConnectionException
                 | LockTimeoutException
                 | ProsessTaskMidlertidigException
                 | OptimisticLockException
                 | SQLRecoverableException e) {

            // vil kun logges
            pickAndRun.handleTransientAndRecoverableException(e);

        } catch (SavepointRolledbackException e) {

            if (erTransaksjonRollbackEllerInaktiv()) {
                // håndter likt som vanlige exceptions (under) med mindre transaksjonen er
                // markert for rollback
                throw new PersistenceException(e);
            } else {
                // implementasjon av task har kastet en feil samtidig som det har rullet tilbake
                // et egen-definert savepoint.
                // fanger opp dette og lagrer som feil på prosess task'en.
                // Transaksjonen vil fortsatt committes (men da uten den tilstand som er rullet
                // tilbake til et savepoint).

                // NB: Task forventes å være robust nok til å kunne gjentas dersom
                // feilhåndteringsalgortime er konfigurert til det.

                // allerede rullet tilbake, skal ikke rulle mer her
                // anta feil kan skrives tilbake til databasen
                pickAndRun.handleTaskFeil(pte, e);
                // NB: pt. har denne samme feilhåndtering som andre exceptions (se under)
                // bortsett fra at savepoint her rulles ikke tilbake.
            }

        } catch (InjectionException e) {
            // Fatal feil, kan ikke kjøre denne på nytt uansett

            if (erTransaksjonRollbackEllerInaktiv()) {
                // håndter likt som vanlige exceptions (under) med mindre transaksjonen er
                // markert for rollback
                throw new PersistenceException(e);
            } else {
                getEntityManager().clear(); // fjern mulig korrupt tilstand
                conn.rollback(savepoint); // rull tilbake til savepoint

                Feil feil = TaskManagerFeil.kunneIkkeProsessereTaskFeilKonfigurasjon(pickAndRun.getTaskInfo().getId(), taskType, e);
                pickAndRun.handleFatalTaskFeil(pte, feil, e);
            }
        } catch (Exception e) {

            if (erTransaksjonRollbackEllerInaktiv()) {
                // håndter likt som vanlige exceptions (under) med mindre transaksjonen er
                // markert for rollback
                throw (e instanceof PersistenceException pe) ? pe : new PersistenceException(e);
            } else {
                getEntityManager().clear(); // fjern mulig korrupt tilstand
                conn.rollback(savepoint); // rull tilbake til savepoint

                // anta feil kan skrives tilbake til databasen
                pickAndRun.handleTaskFeil(pte, e);
            }
        }
    }

    private boolean erTransaksjonRollbackEllerInaktiv() {
        return !getEntityManager().getTransaction().isActive() || getEntityManager().getTransaction().getRollbackOnly();
    }

    private EntityManager getEntityManager() {
        return taskManagerRepository.getEntityManager();
    }

    /**
     * Denne klassen enkapsulerer plukk og kjør en task, og tilhørende bokføring av
     * status og tidsstempler på kjøringen.
     */
    class PickAndRunTask {
        private final RunTaskInfo taskInfo;
        private final RunTaskFeilOgStatusEventHåndterer feilOgStatushåndterer;
        private final ProsessTaskRetryPolicy retryPolicy;
        private final CronExpression cronExpression;

        PickAndRunTask(RunTaskInfo taskInfo) {
            this.taskInfo = taskInfo;
            this.feilOgStatushåndterer = new RunTaskFeilOgStatusEventHåndterer(taskInfo, eventPubliserer, taskManagerRepository);
            try (var handler = getTaskInfo().getTaskDispatcher().taskHandler(taskInfo.getTaskType())) {
                this.retryPolicy = handler.retryPolicy();
                this.cronExpression = handler.cronExpression();
            }
        }

        RunTaskInfo getTaskInfo() {
            return taskInfo;
        }

        void handleTaskFeil(ProsessTaskEntitet pte, Exception e) {
            feilOgStatushåndterer.handleTaskFeil(retryPolicy, pte, e);
        }

        void handleFatalTaskFeil(ProsessTaskEntitet pte, Feil feil, Exception e) {
            feilOgStatushåndterer.handleFatalTaskFeil(pte, feil, e);
        }

        void handleTransientAndRecoverableException(Exception e) {
            feilOgStatushåndterer.handleTransientAndRecoverableException(e);
        }

        private ProsessTaskEntitet refreshProsessTask(Long id) {
            return getEntityManager().find(ProsessTaskEntitet.class, id);
        }

        ProsessTaskStatus markerTaskFerdig(ProsessTaskEntitet pte) {
            // frigir veto etter at event handlere er fyrt
            vetoHåndterer.frigiVeto(pte);

            var nyStatus = ProsessTaskStatus.KJOERT;
            taskManagerRepository.oppdaterStatus(pte.getId(), nyStatus);

            pte = refreshProsessTask(pte.getId());
            feilOgStatushåndterer.publiserNyStatusEvent(pte.tilProsessTask(), ProsessTaskStatus.KLAR, nyStatus);
            return nyStatus;
        }

        // markerer task som påbegynt (merk committer ikke før til slutt).
        void markerTaskUnderArbeid(ProsessTaskEntitet pte) {
            // mark row being processed with timestamp and server process id
            var now = LocalDateTime.now();
            pte.setSisteKjøring(now);
            pte.setSisteKjøringServer(getJvmUniqueProcessName());
            getEntityManager().persist(pte);
            getEntityManager().flush();
        }

        // regner ut neste kjøretid for tasks som kan repeteres (har CronExpression)
        void planleggNesteKjøring(ProsessTaskEntitet pte) throws SQLException {
            if (cronExpression != null) {
                var gruppe = ProsessTaskRepository.getUniktProsessTaskGruppeNavn(taskManagerRepository.getEntityManager());
                var now = LocalDateTime.now();
                var nesteKjøring = cronExpression.nextLocalDateTimeAfter(now);
                var data = ProsessTaskDataBuilder.forTaskType(pte.getTaskType())
                        .medNesteKjøringEtter(nesteKjøring)
                        .medProperties(pte.getProperties())
                        .medGruppe(gruppe)
                        .medSekvens(pte.getSekvens());
                var nyPte = new ProsessTaskEntitet().kopierFraNy(data.build());

                getEntityManager().persist(nyPte);
                getEntityManager().flush();

                if (LOG.isInfoEnabled()) {
                    LOG.info("Oppretter ny prosesstask [{}], id={}, status={}, cron={}, nå={}, nytt kjøretidspunkt etter={}",
                            nyPte.getTaskType().value(),
                            nyPte.getId(),
                            nyPte.getStatus(),
                            now,
                            cronExpression,
                            nyPte.getNesteKjøringEtter());
                }
            }
        }

        void dispatchWork(ProsessTaskEntitet pte) throws Exception {
            var taskData = pte.tilProsessTask();
            taskInfo.getTaskDispatcher().dispatch(taskData);
        }

        private EntityManager getEntityManager() {
            return taskManagerRepository.getEntityManager();
        }

        @SuppressWarnings("rawtypes")
        void runTask() {

            final var pickAndRun = this;
            /*
             * Bruker SQL+JDBC for å kunne benytte savepoints og inkrementell oppdatering i
             * transaksjonen.
             */
            class PullSingleTask implements Work {
                @Override
                public void execute(Connection conn) throws SQLException {
                    try {
                        var pte = taskManagerRepository.finnOgLås(taskInfo);
                        if (pte.isPresent()) {
                            runTaskAndUpdateStatus(conn, pte.get(), pickAndRun);
                        }
                    } catch (JDBCConnectionException
                            | SQLTransientException
                            | SQLNonTransientConnectionException
                            | LockTimeoutException
                            | OptimisticLockException
                            | ProsessTaskMidlertidigException
                            | SQLRecoverableException e) {

                        // vil kun logges
                        pickAndRun.handleTransientAndRecoverableException(e);
                    }
                }

            }

            var pullSingleTask = new PullSingleTask();
            var em = getEntityManager();
            // workaround for hibernate issue HHH-11020
            if (em instanceof TargetInstanceProxy tip) {
                em = (EntityManager) tip.weld_getTargetInstance();
            }

            @SuppressWarnings("resource") // skal ikke lukke session her
            var session = em.unwrap(Session.class);

            session.doWork(pullSingleTask);

        }

    }

    static synchronized String getJvmUniqueProcessName() {
        return ManagementFactory.getRuntimeMXBean().getName();
    }
}
