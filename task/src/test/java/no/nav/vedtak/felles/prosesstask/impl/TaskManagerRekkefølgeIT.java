package no.nav.vedtak.felles.prosesstask.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import no.nav.vedtak.felles.prosesstask.JpaExtension;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskGruppe;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;
import no.nav.vedtak.felles.prosesstask.api.TaskMonitor;
import no.nav.vedtak.felles.prosesstask.api.TaskType;

class TaskManagerRekkefølgeIT {

    @RegisterExtension
    public static final JpaExtension repoRule = new JpaExtension();

    private ProsessTaskRepository repo = new ProsessTaskRepository(repoRule.getEntityManager(), null, null);

    private TaskManagerRepositoryImpl taskManagerRepo = new TaskManagerRepositoryImpl(repoRule.getEntityManager());

    private LocalDateTime now = LocalDateTime.now();

    @Test
    void skal_finne_sql_for_polling() throws Exception {
        assertThat(taskManagerRepo.getSqlForPolling()).isNotNull();
    }

    @Test
    void skal_polle_tasker_i_enkel_sekvens() throws Exception {
        ProsessTaskData pt1 = nyTask("mytask1", -10);
        ProsessTaskData pt2 = nyTask("mytask2", -10);
        ProsessTaskData pt3 = nyTask("mytask3", -10);
        ProsessTaskGruppe sammensatt = new ProsessTaskGruppe();
        sammensatt
            .addNesteSekvensiell(pt1)
            .addNesteSekvensiell(pt2)
            .addNesteSekvensiell(pt3);

        // Act
        repo.lagre(sammensatt);
        var monitor = taskManagerRepo.countTasksForStatus(TaskMonitor.monitoredStatuses());
        assertThat(monitor).containsEntry(ProsessTaskStatus.KLAR, 3);
        assertThat(monitor.get(ProsessTaskStatus.VENTER_SVAR)).isNull();
        assertThat(monitor.get(ProsessTaskStatus.FEILET)).isNull();

        pollEnRundeVerifiserOgFerdigstill(pt1);

        pollEnRundeVerifiserOgFerdigstill(pt2);

        pollEnRundeVerifiserOgFerdigstill(pt3);

        List<ProsessTaskData> sekvensiellRunde04 = taskManagerRepo.pollNeste(now);
        assertThat(sekvensiellRunde04).isEmpty();

    }

    @Test
    void skal_polle_tasker_i_sekvens_sjekk_rekkefølge() throws Exception {
        ProsessTaskGruppe sammensatt = new ProsessTaskGruppe();
        List<ProsessTaskData> tasks = new ArrayList<>();
        for (int i = 0; i < 22; i++) {
            tasks.add(nyTask("mytask" + i, -10));
            sammensatt.addNesteSekvensiell(tasks.get(i));
        }

        // Act
        repo.lagre(sammensatt);

        for (int i = 0; i < 22; i++) {
            pollEnRundeVerifiserOgFerdigstill(tasks.get(i));
        }

        List<ProsessTaskData> sekvensiellRunde22 = taskManagerRepo.pollNeste(now);
        assertThat(sekvensiellRunde22).isEmpty();

    }

    @Test
    void skal_ikke_polle_ny_når_får_feil_på_første() throws Exception {
        ProsessTaskData pt1 = nyTask("mytask1", -10);
        ProsessTaskData pt2 = nyTask("mytask2", -10);
        ProsessTaskGruppe sammensatt = new ProsessTaskGruppe();
        sammensatt
            .addNesteSekvensiell(pt1)
            .addNesteSekvensiell(pt2);

        // Act
        repo.lagre(sammensatt);

        pollEnRundeVerifiserOgMarkerFeil(pt1);

        List<ProsessTaskData> sekvensiellRunde02 = taskManagerRepo.pollNeste(now);
        assertThat(sekvensiellRunde02).isEmpty();
    }

    @Test
    void skal_polle_tasker_i_sekvens_med_feil_i_midten() throws Exception {
        ProsessTaskData pt1 = nyTask("mytask1", -10);
        ProsessTaskData pt2 = nyTask("mytask2", -10);
        ProsessTaskData pt3 = nyTask("mytask3", -10);
        ProsessTaskGruppe sammensatt = new ProsessTaskGruppe();
        sammensatt
            .addNesteSekvensiell(pt1)
            .addNesteSekvensiell(pt2)
            .addNesteSekvensiell(pt3);

        // Act
        repo.lagre(sammensatt);

        pollEnRundeVerifiserOgFerdigstill(pt1);

        pollEnRundeVerifiserOgMarkerFeil(pt2);

        List<ProsessTaskData> sekvensiellRunde03 = taskManagerRepo.pollNeste(now);
        assertThat(sekvensiellRunde03).isEmpty();

    }

    private void pollEnRundeVerifiserOgMarkerFeil(ProsessTaskData pt) {
        List<ProsessTaskData> neste = taskManagerRepo.pollNeste(now);
        assertThat(neste).containsExactly(pt);
        pt.setStatus(ProsessTaskStatus.FEILET);
        pt.setAntallFeiledeForsøk(1);
        pt.setNesteKjøringEtter(null);
        pt.setSistKjørt(LocalDateTime.now());

        repo.lagre(pt);
        repo.flushAndClear();
    }

    private void pollEnRundeVerifiserOgFerdigstill(ProsessTaskData... tasks) {
        List<ProsessTaskData> neste = taskManagerRepo.pollNeste(now);
        assertThat(neste).hasSize(tasks.length);
        assertThat(neste.stream().map(ProsessTaskData::getTaskType).toArray()).containsExactly(Arrays.stream(tasks).map(ProsessTaskData::getTaskType).toArray());

        neste.forEach(pt -> {
            pt.setStatus(ProsessTaskStatus.KJOERT);
            repo.lagre(pt);
        });

        repo.flushAndClear();
    }

    @Test
    void skal_polle_3_tasker_i_parallell_deretter_1_sekvensielt_for_avslutning() throws Exception {

        // Arrange
        ProsessTaskData pt1 = nyTask("mytask1", -10);
        ProsessTaskData pt2 = nyTask("mytask2", -10);
        ProsessTaskData pt3 = nyTask("mytask3", -10);
        ProsessTaskData pt4 = nyTask("mytask4", -10);

        ProsessTaskGruppe sammensatt = new ProsessTaskGruppe();
        sammensatt
            .addNesteParallell(pt1, pt2, pt3)
            .addNesteSekvensiell(pt4);

        repo.lagre(sammensatt);
        repo.flushAndClear();

        // Act and Assert
        pollEnRundeVerifiserOgFerdigstill(pt1, pt2, pt3);

        pollEnRundeVerifiserOgFerdigstill(pt4);

    }

    @Test
    void skal_polle_tasker_i_sekvens_så_parallell() throws Exception {

        ProsessTaskData pt1 = nyTask("mytask1", -10);
        ProsessTaskData pt2 = nyTask("mytask2", -10);
        ProsessTaskData pt3 = nyTask("mytask3", -10);
        ProsessTaskGruppe sammensatt = new ProsessTaskGruppe();
        sammensatt
            .addNesteSekvensiell(pt1)
            .addNesteParallell(pt2, pt3);

        // Act
        repo.lagre(sammensatt);
        repo.flushAndClear();

        pollEnRundeVerifiserOgFerdigstill(pt1);

        pollEnRundeVerifiserOgFerdigstill(pt2, pt3);

    }

    private ProsessTaskData nyTask(String taskNavn, int nesteKjøringRelativt) {
        ProsessTaskData task = ProsessTaskData.forTaskType(new TaskType(taskNavn));
        task.setNesteKjøringEtter(now.plusSeconds(nesteKjøringRelativt));
        return task;
    }
}
