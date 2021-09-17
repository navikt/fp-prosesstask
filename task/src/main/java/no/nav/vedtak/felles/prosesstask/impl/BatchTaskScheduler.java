package no.nav.vedtak.felles.prosesstask.impl;

import java.time.LocalDateTime;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.control.ActivateRequestContext;
import javax.enterprise.inject.spi.CDI;
import javax.inject.Inject;
import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import no.nav.vedtak.apptjeneste.AppServiceHandler;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskHandler;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;

/**
 * Starter tasks med cron-expression hvis disse ikke har noen status fra før av.
 */
@ApplicationScoped
@ActivateRequestContext
@Transactional
public class BatchTaskScheduler implements AppServiceHandler {

    private static final Logger LOG = LoggerFactory.getLogger(BatchTaskScheduler.class);
    private TaskManagerRepositoryImpl taskRepository;

    BatchTaskScheduler() {
    }

    @Inject
    public BatchTaskScheduler(TaskManagerRepositoryImpl taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public void start() {
        taskRepository.finnFeiletTasks()
            .stream()
            .filter(this::erBatchTask)
            .forEach(this::restartTask);
    }

    private boolean erBatchTask(ProsessTaskEntitet pte) {
        try {
            return CDI.current().select(ProsessTaskHandler.class, new ProsessTaskHandlerRef.ProsessTaskLiteral(pte.getTaskType().value()))
                    .get().cronExpression() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private void restartTask(ProsessTaskEntitet pte) {
        taskRepository.oppdaterStatusOgNesteKjøring(pte.getId(), ProsessTaskStatus.KLAR, LocalDateTime.now(), null, null, 0);
        LOG.info("Restarter batch-task da siste='{}' står til feilet.", pte);
    }

    @Override
    public void stop() {

    }
}
