package no.nav.vedtak.felles.prosesstask.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import no.nav.vedtak.felles.prosesstask.JpaPostgresTestcontainerExtension;
import no.nav.vedtak.felles.testutilities.db.EntityManagerAwareTest;

@ExtendWith(JpaPostgresTestcontainerExtension.class)
class TaskManagerTest extends EntityManagerAwareTest {

    private TaskManagerRepositoryImpl taskManagerRepository;

    @BeforeEach
    void setUp() {
        taskManagerRepository = new TaskManagerRepositoryImpl(getEntityManager());
    }

    @Test
    void sjekk_startup() {
        taskManagerRepository.verifyStartup();
    }

    @Test
    void skal_avslutte_paagaende_task_og_ikke_starte_koeede_ved_stop() throws InterruptedException {
        var taskManager = new TaskManager(taskManagerRepository, null, null, null);
        taskManager.configureTaskThreads(1, 2);
        taskManager.startTaskThreads();

        var started = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var completedWithoutInterrupt = new AtomicBoolean(false);
        var queuedRan = new AtomicBoolean(false);

        taskManager.submitTask(new IdentRunnableTask(1L, () -> {
            started.countDown();
            try {
                if (finish.await(5, TimeUnit.SECONDS)) {
                    completedWithoutInterrupt.set(!Thread.currentThread().isInterrupted());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, LocalDateTime.now()));

        taskManager.submitTask(new IdentRunnableTask(2L, () -> queuedRan.set(true), LocalDateTime.now()));

        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        var stopThread = Thread.ofPlatform().start(taskManager::stop);
        // la stop() begynne å vente på pågående task
        Thread.sleep(100);
        finish.countDown();
        stopThread.join(5000);

        assertThat(completedWithoutInterrupt).isTrue();
        assertThat(queuedRan).isFalse();
        assertThat(taskManager.getRunTaskService()).isNull();
    }
}
