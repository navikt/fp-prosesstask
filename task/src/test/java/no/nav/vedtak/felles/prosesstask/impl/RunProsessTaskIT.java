package no.nav.vedtak.felles.prosesstask.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLTransientException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import no.nav.vedtak.felles.jpa.savepoint.SavepointRolledbackException;
import no.nav.vedtak.felles.prosesstask.JpaExtension;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskDispatcher;
import no.nav.vedtak.felles.prosesstask.spi.ProsessTaskFeilhåndteringAlgoritme;
import no.nav.vedtak.felles.testutilities.cdi.CdiAwareExtension;

@ExtendWith(CdiAwareExtension.class)
public class RunProsessTaskIT {

    @RegisterExtension
    public static final JpaExtension repoRule = new JpaExtension();
    
    private ProsessTaskRepositoryImpl repo = new ProsessTaskRepositoryImpl(repoRule.getEntityManager(), null, null);

    private TaskManagerRepositoryImpl taskManagerRepo = new TaskManagerRepositoryImpl(repoRule.getEntityManager());

    @Inject
    @Any
    Instance<ProsessTaskFeilhåndteringAlgoritme> feilhåndteringAlgoritmer;

    LocalDateTime now = LocalDateTime.now();

    @BeforeEach
    public void setupTestData() throws Exception {
        TestProsessTaskTestData testData = new TestProsessTaskTestData(repoRule.getEntityManager());
        for (int i = 1; i <= 10; i++) {
            testData.opprettTaskType("mytask" + i);
        }
        testData.opprettTaskType("mytask11", "0 0 6 * * ?");
    }

    @Test
    public void skal_kjøre_en_task() throws Exception {
        // Arrange
        ProsessTaskData pt1 = nyTask("mytask1", -10);
        repo.lagre(pt1);
        repo.flushAndClear();

        AtomicBoolean allDone = new AtomicBoolean();
        ProsessTaskDispatcher dispatcher = new DummyProsessTaskDispatcher((task) -> {
            allDone.set(task != null);
        });

        // Act
        RunTask runTask = new RunTask(taskManagerRepo, null, feilhåndteringAlgoritmer);

        runTask.doRun(new RunTaskInfo(dispatcher, pt1));

        // Assert
        assertThat(allDone.get()).isTrue();

        ProsessTaskData prosessTask = repo.finn(pt1.getId());
        assertThat(prosessTask).isNotNull();
        assertThat(prosessTask.getSistKjørt()).isNotNull();
        assertThat(prosessTask.getSisteFeil()).isNull();
    }

    @Test
    public void skal_kjøre_en_task_og_planlegge_ny() throws Exception {
        // Arrange
        String taskType = "mytask11";
        ProsessTaskData pt1 = nyTask(taskType, -10);
        repo.lagre(pt1);
        repo.flushAndClear();

        AtomicBoolean allDone = new AtomicBoolean();
        ProsessTaskDispatcher dispatcher = new DummyProsessTaskDispatcher((task) -> {
            allDone.set(task != null);
        });

        // Act
        RunTask runTask = new RunTask(taskManagerRepo, null, feilhåndteringAlgoritmer);

        runTask.doRun(new RunTaskInfo(dispatcher, pt1));

        // Assert
        assertThat(allDone.get()).isTrue();

        ProsessTaskData prosessTask = repo.finn(pt1.getId());
        assertThat(prosessTask).isNotNull();
        assertThat(prosessTask.getSistKjørt()).isNotNull();
        assertThat(prosessTask.getSisteFeil()).isNull();

        List<ProsessTaskData> prosessTaskData = repo.finnIkkeStartet()
                .stream()
                .filter(it -> it.getTaskType().equals(taskType))
                .collect(Collectors.toList());
        assertThat(prosessTaskData).hasSize(1);
        ProsessTaskData first = prosessTaskData.get(0);
        assertThat(first.getNesteKjøringEtter()).isAfter(now);
    }

    @Test
    public void skal_kjøre_en_task_som_feiler_og_inkrementere_feilede_forsøk_teller() throws Exception {
        // Arrange
        ProsessTaskData pt1 = nyTask("mytask1", -10);
        repo.lagre(pt1);
        repo.flushAndClear();

        AtomicBoolean allDone = new AtomicBoolean();
        ProsessTaskDispatcher dispatcher = new DummyProsessTaskDispatcher((task) -> {
            allDone.set(task != null);
            throw new RuntimeException("I am a walrus!");
        });

        // Act
        RunTask runTask = new RunTask(taskManagerRepo, null, feilhåndteringAlgoritmer);

        runTask.doRun(new RunTaskInfo(dispatcher, pt1));

        repo.flushAndClear();

        // Assert
        assertThat(allDone.get()).isTrue();

        ProsessTaskData prosessTask = repo.finn(pt1.getId());
        assertThat(prosessTask).isNotNull();
        assertThat(prosessTask.getSistKjørt()).isNotNull();
        assertThat(prosessTask.getSisteFeil()).contains("I am a walrus!");
        assertThat(prosessTask.getAntallFeiledeForsøk()).isEqualTo(1);
    }

    @Test
    public void skal_kjøre_en_task_som_feiler_med_savepoint_og_inkrementere_feilede_forsøk_teller() throws Exception {
        // Arrange
        ProsessTaskData pt1 = nyTask("mytask1", -10);
        repo.lagre(pt1);
        repo.flushAndClear();

        AtomicBoolean allDone = new AtomicBoolean();
        ProsessTaskDispatcher dispatcher = new DummyProsessTaskDispatcher((task) -> {
            allDone.set(task != null);
            throw new SavepointRolledbackException("Save me!", new UnsupportedOperationException("ignored"));
        });

        // Act
        RunTask runTask = new RunTask(taskManagerRepo, null, feilhåndteringAlgoritmer);

        runTask.doRun(new RunTaskInfo(dispatcher, pt1));

        taskManagerRepo.getEntityManager().flush();
        taskManagerRepo.getEntityManager().clear();

        // Assert
        assertThat(allDone.get()).isTrue();

        ProsessTaskData prosessTask = repo.finn(pt1.getId());
        assertThat(prosessTask).isNotNull();
        assertThat(prosessTask.getSistKjørt()).isNotNull();
        assertThat(prosessTask.getSisteFeil())
                .contains("Save me!")
                .contains(SavepointRolledbackException.class.getSimpleName())
                .contains(UnsupportedOperationException.class.getSimpleName());
        assertThat(prosessTask.getAntallFeiledeForsøk()).isEqualTo(1);
    }

    @Test
    public void skal_kjøre_en_task_som_feiler_pga_transient_databasefeil_og_ikke_endre_noe() throws Exception {
        // Arrange
        ProsessTaskData pt1 = nyTask("mytask1", -10);
        repo.lagre(pt1);
        repo.flushAndClear();

        AtomicBoolean allDone = new AtomicBoolean();
        ProsessTaskDispatcher dispatcher = new DummyProsessTaskDispatcher((task) -> {
            allDone.set(task != null);
            throw new SQLTransientException("I am NOT a walrus!");
        });

        // Acts
        RunTask runTask = new RunTask(taskManagerRepo, null, feilhåndteringAlgoritmer);

        runTask.doRun(new RunTaskInfo(dispatcher, pt1));

        // Assert
        assertThat(allDone.get()).isTrue();

        ProsessTaskData prosessTask = repo.finn(pt1.getId());
        assertThat(prosessTask).isNotNull();
        assertThat(prosessTask.getSistKjørt()).isNotNull();

        // ingen endring på disse for transiente db feils
        assertThat(prosessTask.getSisteFeil()).isNull();
        assertThat(prosessTask.getAntallFeiledeForsøk()).isEqualTo(0);
    }

    private ProsessTaskData nyTask(String taskNavn, int nesteKjøringRelativt) {
        ProsessTaskData task = new ProsessTaskData(taskNavn);
        task.setNesteKjøringEtter(now.plusSeconds(nesteKjøringRelativt));
        return task;
    }
    
    interface DummyConsumer {
        void dispatch(ProsessTaskData task) throws Exception; 
    }

    class DummyProsessTaskDispatcher extends BasicCdiProsessTaskDispatcher {
        
        private DummyConsumer consumer;

        public DummyProsessTaskDispatcher(DummyConsumer consumer) {
            this.consumer = consumer;
        }
        
        @Override
        public void dispatch(ProsessTaskData task) throws Exception {
            consumer.dispatch(task);
        }
    }
}
