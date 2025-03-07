package no.nav.vedtak.felles.prosesstask.impl;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;
import no.nav.vedtak.server.Controllable;

/**
 * Starter tasks med cron-expression hvis disse ikke har noen status fra før av.
 */
@ApplicationScoped
@ActivateRequestContext
@Transactional
public class BatchTaskScheduler implements Controllable {

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
        try (var ref = ProsessTaskHandlerRef.lookup(pte.getTaskType())) {
            return ref.cronExpression() != null;
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
        // Ingenting å stoppe
    }
}
