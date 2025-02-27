package no.nav.vedtak.felles.prosesstask.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import ch.qos.logback.classic.Level;
import jakarta.persistence.PersistenceException;
import no.nav.vedtak.felles.prosesstask.JpaPostgresTestcontainerExtension;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.TaskType;
import no.nav.vedtak.felles.testutilities.db.EntityManagerAwareTest;

@ExtendWith(JpaPostgresTestcontainerExtension.class)
class TaskManagerGenerateRunnableTasksITTest extends EntityManagerAwareTest {

    private static final MemoryAppender LOG_SNIFFER = MemoryAppender.sniff(TaskManagerGenerateRunnableTasks.class);

    @AfterEach
    public void afterEach() {
        LOG_SNIFFER.reset();
    }

    @Test
    void skal_fange_PersistenceException_og_legge_til_errorCallback() {
        ProsessTaskData data = ProsessTaskData.forTaskType(new TaskType("hello.world"));
        data.setId(99L);
        ProsessTaskEntitet pte = new ProsessTaskEntitet();
        pte.kopierFraEksisterende(data);

        AtomicReference<Throwable> errorFuncException = new AtomicReference<>();

        TaskManagerGenerateRunnableTasks generateRunnableTasks = new TaskManagerGenerateRunnableTasks(null, null, null) {

            @Override
            TaskManagerRunnableTask createTaskManagerRunnableTask(final RunTaskInfo taskInfo, final String callId, TaskType taskType) {
                return new TaskManagerRunnableTask(taskType, taskInfo, callId, null) {

                    @Override
                    IdentRunnable lagErrorCallback(RunTaskInfo taskInfo, String callId, Throwable t) {
                        // TEST override for å fange exception
                        errorFuncException.set(t);
                        return super.lagErrorCallback(taskInfo, callId, t);
                    }

                    @Override
                    RunTask newRunTaskInstance() {
                        // TEST override for å kaste exception
                        return new RunTask(Mockito.mock(TaskManagerRepositoryImpl.class), null) {
                            @Override
                            public void doRun(RunTaskInfo taskInfo) {
                                throw new PersistenceException("howdy!");
                            }
                        };
                    }

                    @Override
                    void handleErrorCallback(IdentRunnable errorCallback) {
                        // Do nothing
                    }

                };
            }
        };

        assertThat(errorFuncException.get()).isNull();

        Runnable sut = generateRunnableTasks.readTask(pte);

        // Act
        sut.run();

        assertThat(LOG_SNIFFER.search("PT-876628", Level.WARN)).isNotEmpty();

        assertThat(errorFuncException.get()).isInstanceOf(PersistenceException.class);

    }
}
