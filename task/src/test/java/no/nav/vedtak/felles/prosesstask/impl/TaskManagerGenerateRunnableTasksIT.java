package no.nav.vedtak.felles.prosesstask.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import javax.persistence.PersistenceException;

import org.junit.Rule;
import org.junit.Test;

import no.nav.vedtak.felles.prosesstask.UnittestRepositoryRule;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;

public class TaskManagerGenerateRunnableTasksIT {

    @Rule
    public final LogSniffer logSniffer = new LogSniffer();

    @Rule
    public final UnittestRepositoryRule repoRule = new UnittestRepositoryRule();

    @Test
    public void skal_fange_PersistenceException_og_legge_til_errorCallback() throws Exception {
        ProsessTaskData data = new ProsessTaskData("hello.world");
        data.setId(99L);
        ProsessTaskEntitet pte = new ProsessTaskEntitet();
        pte.kopierFraEksisterende(data);

        AtomicReference<Throwable> errorFuncException = new AtomicReference<>();

        TaskManagerGenerateRunnableTasks generateRunnableTasks = new TaskManagerGenerateRunnableTasks(null, null, null) {

            @Override
            TaskManagerRunnableTask createTaskManagerRunnableTask(final RunTaskInfo taskInfo, final String callId, String taskName) {
                return new TaskManagerRunnableTask(taskName, taskInfo, callId, null) {

                    @Override
                    IdentRunnable lagErrorCallback(RunTaskInfo taskInfo, String callId, Throwable t) {
                        // TEST override for å fange exception
                        errorFuncException.set(t);
                        return super.lagErrorCallback(taskInfo, callId, t);
                    }

                    @Override
                    RunTask newRunTaskInstance() {
                        // TEST override for å kaste exception
                        return new RunTask(null, null, null) {
                            @Override
                            public void doRun(RunTaskInfo taskInfo) {
                                throw new PersistenceException("howdy!");
                            }
                        };
                    }

                    @Override
                    void handleErrorCallback(IdentRunnable errorCallback) {
                    }

                };
            }
        };

        assertThat(errorFuncException.get()).isNull();

        Runnable sut = generateRunnableTasks.readTask(pte);

        // Act
        sut.run();

        logSniffer.assertHasWarnMessage("FP-876628");
        logSniffer.assertNoErrors();
        logSniffer.clearLog();

        assertThat(errorFuncException.get()).isInstanceOf(PersistenceException.class);

    }
}
