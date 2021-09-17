package no.nav.vedtak.felles.prosesstask.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.spi.CDI;
import javax.inject.Inject;
import javax.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import no.nav.vedtak.felles.prosesstask.JpaExtension;
import no.nav.vedtak.felles.prosesstask.api.ProsessTask;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskDispatcher;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskHandler;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskRepository;
import no.nav.vedtak.felles.testutilities.cdi.CdiAwareExtension;
import no.nav.vedtak.felles.testutilities.db.NonTransactional;

/** håndterer tx eksplisitt på egen hånd vha JpaExtensin. */
@NonTransactional
@ExtendWith(CdiAwareExtension.class)
public class RunProsessTaskTestIT {

    private static final LocalDateTime NÅ = LocalDateTime.now();
    private static final String TASK1_NAME = "mytask1";
    private static final TaskType TASK1 = new TaskType(TASK1_NAME);
    private static final TaskType TASK2 = new TaskType("mytask2");
    private static final TaskType TASK3 = new TaskType("mytask3");

    @RegisterExtension
    public static final JpaExtension repoRule = new JpaExtension();

    @Inject
    private TaskManager taskManager;

    @BeforeEach
    public void setupTestData() throws Exception {
        repoRule.doInTransaction(this::testData);
    }

    @AfterEach
    public void tearDown() throws Exception {
        taskManager.stop();
        repoRule.doInTransaction(this::slettTestData);
    }

    @Test
    public void skal_starte_TaskManager_polle_og_finne_tasks() throws Exception {
        taskManager.configureTaskThreads(1, 1);
        taskManager.startTaskThreads();

        List<IdentRunnable> tasksPolled = repoRule.doInTransaction((em) -> taskManager.pollForAvailableTasks());
        assertThat(tasksPolled).hasSize(1);
    }

    @Test
    public void skal_starte_TaskManager_polle_og_kjøre_tasks_i_egne_tråder() throws Exception {
        AtomicBoolean kjørt = new AtomicBoolean();
        ProsessTaskDispatcher taskDispatcher = new BasicCdiProsessTaskDispatcher() {
            @Override
            public void dispatch(ProsessTaskHandlerRef taskHandler, ProsessTaskData task) throws Exception {
                kjørt.set(true);
            }
        };

        testEnTask(taskDispatcher);

        assertThat(kjørt.get()).isTrue();
    }

    @Test
    public void skal_starte_TaskManager_polle_og_kjøre_en_task_som_får_egen_transaksjon() throws Exception {
        ProsessTaskDispatcher taskDispatcher = new BasicCdiProsessTaskDispatcher();

        // Act
        testEnTask(taskDispatcher);

        // Assert
        assertThat(getBean().getLastHandler()).isInstanceOf(LocalDummyProsessTask.class);
        ProsessTaskData last = getBean().getLastData();
        assertThat(last).isNotNull();
        assertThat(last.getId()).isNotNull();
        assertThat(last.taskType()).isEqualTo(TASK3);

        ProsessTaskEntitet lagret = repoRule.doInTransaction((em) -> em.find(ProsessTaskEntitet.class, last.getId()));
        assertThat(lagret.getTaskType()).isEqualTo(TASK3);

    }

    private Object slettTestData(EntityManager em) throws SQLException {
        TestProsessTaskTestData data = new TestProsessTaskTestData(em);
        data.slettAlleProssessTask();
        return null;
    }

    private Object testData(EntityManager em) throws SQLException {
        TestProsessTaskTestData testData = new TestProsessTaskTestData(em);
        testData.slettAlleProssessTask();
        LocalDateTime kjørEtter = LocalDateTime.now().minusSeconds(50);
        testData
            .opprettTask(new ProsessTaskData(TASK1).medNesteKjøringEtter(kjørEtter).medSekvens("a"))
            .opprettTask(new ProsessTaskData(TASK2).medNesteKjøringEtter(kjørEtter).medSekvens("a"));

        return null;
    }

    private void testEnTask(ProsessTaskDispatcher taskDispatcher) throws InterruptedException {
        taskManager.setProsessTaskDispatcher(taskDispatcher);

        taskManager.configureTaskThreads(1, 1);
        taskManager.startTaskThreads();

        int tasksPolled = taskManager.doSinglePolling();
        assertThat(tasksPolled).isEqualTo(1);

        CountDownLatch latch = new CountDownLatch(1);
        taskManager.getRunTaskService().submit(new IdentRunnableTask(1L, latch::countDown, NÅ));

        assertThat(latch.await(120, TimeUnit.SECONDS)).isTrue();
    }

    @ProsessTask(TASK1_NAME)
    static class LocalDummyProsessTask implements ProsessTaskHandler {

        @Inject
        ProsessTaskRepository repo;

        @Override
        public void doTask(ProsessTaskData data) {
            ProsessTaskData nyProsessTask = new ProsessTaskData(TASK3);
            repo.lagre(nyProsessTask);

            getBean().invoked(this, nyProsessTask);
        }
    }

    @ApplicationScoped
    static class LastResult {

        private ProsessTaskData data;
        private ProsessTaskHandler handler;

        ProsessTaskData getLastData() {
            return data;
        }

        ProsessTaskHandler getLastHandler() {
            return handler;
        }

        void invoked(ProsessTaskHandler bean, ProsessTaskData data) {
            this.handler = bean;
            this.data = data;
        }

    }

    private static LastResult getBean() {
        return CDI.current().select(LastResult.class).get();
    }

}
