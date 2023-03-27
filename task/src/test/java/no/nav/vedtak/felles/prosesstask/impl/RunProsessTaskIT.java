package no.nav.vedtak.felles.prosesstask.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLTransientException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import no.nav.vedtak.felles.jpa.savepoint.SavepointRolledbackException;
import no.nav.vedtak.felles.prosesstask.JpaExtension;
import no.nav.vedtak.felles.prosesstask.api.ProsessTask;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskDispatcher;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskHandler;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;
import no.nav.vedtak.felles.prosesstask.api.TaskType;
import no.nav.vedtak.felles.testutilities.cdi.CdiAwareExtension;

@ExtendWith(CdiAwareExtension.class)
class RunProsessTaskIT {

    @RegisterExtension
    public static final JpaExtension repoRule = new JpaExtension();

    private final ProsessTaskRepository repo = new ProsessTaskRepository(repoRule.getEntityManager(), null, null);

    private final TaskManagerRepositoryImpl taskManagerRepo = new TaskManagerRepositoryImpl(repoRule.getEntityManager());

    LocalDateTime now = LocalDateTime.now();

    @Test
    void skal_kjøre_en_task() throws Exception {
        // Arrange
        var pt1 = nyTask(new TaskType("mytask1"));
        repo.lagre(pt1);
        repo.flushAndClear();

        var allDone = new AtomicBoolean();
        ProsessTaskDispatcher dispatcher = new DummyProsessTaskDispatcher((task) -> {
            allDone.set(task != null);
        });

        // Act
        var runTask = new RunTask(taskManagerRepo, null);

        runTask.doRun(new RunTaskInfo(dispatcher, pt1));

        // Assert
        assertThat(allDone.get()).isTrue();

        var prosessTask = repo.finn(pt1.getId());
        assertThat(prosessTask).isNotNull();
        assertThat(prosessTask.getSistKjørt()).isNotNull();
        assertThat(prosessTask.getSisteFeil()).isNull();
    }

    @Test
    void skal_kjøre_en_task_og_planlegge_ny() throws Exception {
        // Arrange
        var taskType = TaskType.forProsessTask(LocalDummyProsessTask.class);
        var pt1 = ProsessTaskData.forTaskType(taskType);
        pt1.setNesteKjøringEtter(now.minusSeconds(10));
        repo.lagre(pt1);
        repo.flushAndClear();

        var allDone = new AtomicBoolean();
        ProsessTaskDispatcher dispatcher = new DummyProsessTaskDispatcher((task) -> {
            allDone.set(task != null);
        });

        // Act
        var runTask = new RunTask(taskManagerRepo, null);

        runTask.doRun(new RunTaskInfo(dispatcher, pt1));

        // Assert
        assertThat(allDone.get()).isTrue();

        var prosessTask = repo.finn(pt1.getId());
        assertThat(prosessTask).isNotNull();
        assertThat(prosessTask.getSistKjørt()).isNotNull();
        assertThat(prosessTask.getSisteFeil()).isNull();

        var prosessTaskData = repo.finnAlle(List.of(ProsessTaskStatus.KLAR))
                .stream()
                .filter(it -> it.taskType().equals(taskType))
                .collect(Collectors.toList());
        assertThat(prosessTaskData).hasSize(1);
        var first = prosessTaskData.get(0);
        assertThat(first.getNesteKjøringEtter()).isAfter(now);
    }

    @ProsessTask(value = "mytask11", cronExpression = "0 0 6 * * ?")
    static class LocalDummyProsessTask implements ProsessTaskHandler {

        @Override
        public void doTask(ProsessTaskData data) {
            //
        }
    }

    @Test
    void skal_kjøre_en_task_som_feiler_og_inkrementere_feilede_forsøk_teller() throws Exception {
        // Arrange
        var pt1 = nyTask(new TaskType("mytask1"));
        repo.lagre(pt1);
        repo.flushAndClear();

        var allDone = new AtomicBoolean();
        ProsessTaskDispatcher dispatcher = new DummyProsessTaskDispatcher((task) -> {
            allDone.set(task != null);
            throw new RuntimeException("I am a walrus!");
        });

        // Act
        var runTask = new RunTask(taskManagerRepo, null);

        runTask.doRun(new RunTaskInfo(dispatcher, pt1));

        repo.flushAndClear();

        // Assert
        assertThat(allDone.get()).isTrue();

        var prosessTask = repo.finn(pt1.getId());
        assertThat(prosessTask).isNotNull();
        assertThat(prosessTask.getSistKjørt()).isNotNull();
        assertThat(prosessTask.getSisteFeil()).contains("I am a walrus!");
        assertThat(prosessTask.getAntallFeiledeForsøk()).isEqualTo(1);
    }

    @Test
    void skal_kjøre_en_task_som_feiler_med_savepoint_og_inkrementere_feilede_forsøk_teller() throws Exception {
        // Arrange
        var pt1 = nyTask(new TaskType("mytask1"));
        repo.lagre(pt1);
        repo.flushAndClear();

        var allDone = new AtomicBoolean();
        ProsessTaskDispatcher dispatcher = new DummyProsessTaskDispatcher((task) -> {
            allDone.set(task != null);
            throw new SavepointRolledbackException("Save me!", new UnsupportedOperationException("ignored"));
        });

        // Act
        var runTask = new RunTask(taskManagerRepo, null);

        runTask.doRun(new RunTaskInfo(dispatcher, pt1));

        taskManagerRepo.getEntityManager().flush();
        taskManagerRepo.getEntityManager().clear();

        // Assert
        assertThat(allDone.get()).isTrue();

        var prosessTask = repo.finn(pt1.getId());
        assertThat(prosessTask).isNotNull();
        assertThat(prosessTask.getSistKjørt()).isNotNull();
        assertThat(prosessTask.getSisteFeil())
                .contains("Save me!")
                .contains(SavepointRolledbackException.class.getSimpleName())
                .contains(UnsupportedOperationException.class.getSimpleName());
        assertThat(prosessTask.getAntallFeiledeForsøk()).isEqualTo(1);
    }

    @Test
    void skal_kjøre_en_task_som_feiler_pga_transient_databasefeil_og_ikke_endre_noe() throws Exception {
        // Arrange
        var pt1 = nyTask(new TaskType("mytask1"));
        repo.lagre(pt1);
        repo.flushAndClear();

        var allDone = new AtomicBoolean();
        ProsessTaskDispatcher dispatcher = new DummyProsessTaskDispatcher((task) -> {
            allDone.set(task != null);
            throw new SQLTransientException("I am NOT a walrus!");
        });

        // Acts
        var runTask = new RunTask(taskManagerRepo, null);

        runTask.doRun(new RunTaskInfo(dispatcher, pt1));

        // Assert
        assertThat(allDone.get()).isTrue();

        var prosessTask = repo.finn(pt1.getId());
        assertThat(prosessTask).isNotNull();
        assertThat(prosessTask.getSistKjørt()).isNotNull();

        // ingen endring på disse for transiente db feils
        assertThat(prosessTask.getSisteFeil()).isNull();
        assertThat(prosessTask.getAntallFeiledeForsøk()).isZero();
    }

    private ProsessTaskData nyTask(TaskType taskType) {
        var task = ProsessTaskData.forTaskType(taskType);
        task.setNesteKjøringEtter(now.plusSeconds(-10));
        return task;
    }

    interface DummyConsumer {
        void dispatch(ProsessTaskData task) throws Exception;
    }

    static class DummyProsessTaskDispatcher extends BasicCdiProsessTaskDispatcher {

        private final DummyConsumer consumer;

        public DummyProsessTaskDispatcher(DummyConsumer consumer) {
            this.consumer = consumer;
        }

        @Override
        public void dispatch(ProsessTaskData task) throws Exception {
            consumer.dispatch(task);
        }
    }
}
