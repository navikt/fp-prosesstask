package no.nav.vedtak.felles.prosesstask.impl;

import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

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

    /**
     * Denne klassen enkapsulerer plukk og kjør en task, og tilhørende bokføring av
     * status og tidsstempler på kjøringen.
     */
    class PickAndRunErrorTask {

        private final RunTaskInfo taskInfo;

        PickAndRunErrorTask(RunTaskInfo taskInfo) {
            this.taskInfo = taskInfo;
        }

        void runTask(Throwable t) {
            Optional<ProsessTaskEntitet> opt = taskManagerRepository.finnOgLås(taskInfo);
            if (opt.isPresent()) {
                ProsessTaskEntitet pte = opt.get();

                // Antar alle feil fanget her er fatale.
                var feil = TaskManagerFeil.kunneIkkeProsessereTaskPgaFatalFeilVilIkkePrøveIgjen(
                        pte.getId(), pte.getTaskType(), pte.getFeiledeForsøk() + 1, t);
                new RunTaskFeilOgStatusEventHåndterer(taskInfo, taskManagerRepository).handleFatalTaskFeil(pte, feil, t);
            } else {
                LOG.warn(
                        "PT-876631 Fikk ikke lås på prosess task id [{}], type [{}]. Allerede låst eller ryddet. Kan ikke oppdatere status i databasen nå.",
                        taskInfo.getId(), taskInfo.getTaskType());
            }
        }

    }

}
