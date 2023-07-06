package no.nav.vedtak.felles.prosesstask.impl;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.hibernate.Session;
import org.hibernate.jdbc.Work;
import org.jboss.weld.interceptor.util.proxy.TargetInstanceProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;

/**
 * Kjører en task. Flere JVM'er kan kjøre tasks i parallell
 * <p>
 * Kun en task kjøres, i sin egen transaksjon.
 * <p>
 * Denne tasken logger kun error til databasen i separat transaksjon. Brukes når
 * {@link RunTask} totalhavarerer og transaksjon den kjørte i blir rullet
 * tilbake helt.
 */
@Dependent
@ActivateRequestContext
@Transactional
public class FatalErrorTask {
    private static final Logger LOG = LoggerFactory.getLogger(FatalErrorTask.class);
    private final TaskManagerRepositoryImpl taskManagerRepository;

    @Inject
    public FatalErrorTask(TaskManagerRepositoryImpl taskManagerRepo) {
        Objects.requireNonNull(taskManagerRepo, "taskManagerRepo");
        this.taskManagerRepository = taskManagerRepo;
    }

    public void doRun(RunTaskInfo taskInfo, Throwable t) {
        new PickAndRunErrorTask(taskInfo).runTask(t);
    }

    private EntityManager getEntityManager() {
        return taskManagerRepository.getEntityManager();
    }

    /**
     * Denne klassen enkapsulerer plukk og kjør en task, og tilhørende bokføring av
     * status og tidsstempler på kjøringen.
     */
    class PickAndRunErrorTask {

        private final RunTaskInfo taskInfo;

        PickAndRunErrorTask(RunTaskInfo taskInfo) {
            this.taskInfo = taskInfo;
        }

        RunTaskInfo getTaskInfo() {
            return taskInfo;
        }

        @SuppressWarnings("rawtypes")
        void runTask(Throwable t) {

            /* Bruker SQL+JDBC, unngår hibernate cache. */
            class PullSingleTask implements Work {
                @Override
                public void execute(Connection conn) throws SQLException {
                    Optional<ProsessTaskEntitet> opt = taskManagerRepository.finnOgLås(taskInfo);
                    if (opt.isPresent()) {
                        ProsessTaskEntitet pte = opt.get();

                        // NB: her fyrer p.t. ikke events hvis feilet. Logger derfor bare her til logg
                        // og database. Antar alle feil fanget her er fatale.
                        int feiledeForsøk = pte.getFeiledeForsøk() + 1;
                        var taskName = pte.getTaskType();
                        Long taskId = pte.getId();
                        Feil feil = TaskManagerFeil.kunneIkkeProsessereTaskPgaFatalFeilVilIkkePrøveIgjen(taskId, taskName, feiledeForsøk, t);
                        String feilMelding = RunTaskFeilOgStatusEventHåndterer.getFeiltekstOgLoggEventueltHvisEndret(pte, feil, t, true);

                        // TODO: denne bør harmoniseres med
                        // RunTaskFeilOgStatusEventHåndterer#handleTaskFeil?
                        taskManagerRepository.oppdaterStatusOgNesteKjøring(pte.getId(), ProsessTaskStatus.FEILET, null, feil.kode(), feilMelding,
                                feiledeForsøk);
                    } else {
                        LOG.warn(
                                "PT-876631 Fikk ikke lås på prosess task id [{}], type [{}]. Allerede låst eller ryddet. Kan ikke oppdatere status i databasen nå.",
                                taskInfo.getId(), taskInfo.getTaskType());
                    }
                }

            }

            PullSingleTask pullSingleTask = new PullSingleTask();
            EntityManager em = getEntityManager();
            // workaround for hibernate issue HHH-11020
            if (em instanceof TargetInstanceProxy tip) {
                em = (EntityManager) tip.weld_getTargetInstance();
            }

            @SuppressWarnings("resource") // skal ikke lukke session her
            Session session = em.unwrap(Session.class);

            session.doWork(pullSingleTask);

        }

    }

}
