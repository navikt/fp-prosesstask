package no.nav.vedtak.felles.prosesstask.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import no.nav.vedtak.felles.prosesstask.JpaExtension;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskGruppe;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;
import no.nav.vedtak.felles.prosesstask.api.TaskType;

class OpprettProsessTaskIT {

    @RegisterExtension
    public static final JpaExtension repoRule = new JpaExtension();
    private final ProsessTaskRepository repo = new ProsessTaskRepository(repoRule.getEntityManager(), null, null);

    @Test
    void skal_lagre_ProsessTask() {
        var pt = ProsessTaskData.forTaskType(new TaskType("mytask1"));
        repo.lagre(pt);
        var list = repo.finnAlle(List.of(ProsessTaskStatus.KLAR));
        assertThat(list).hasSize(1);
    }

    @Test
    void skal_lagre_SammensattProsessTask() {
        var pt1 = ProsessTaskData.forTaskType(new TaskType("mytask1"));
        var pt2 = ProsessTaskData.forTaskType(new TaskType("mytask2"));
        var sammensatt = new ProsessTaskGruppe();
        sammensatt
            .addNesteSekvensiell(pt1)
            .addNesteSekvensiell(pt2);

        // Act
        repo.lagre(sammensatt);
        repoRule.getEntityManager().flush();

        var list = repo.finnAlle(List.of(ProsessTaskStatus.KLAR));
        assertThat(list).hasSize(2).containsOnly(pt1, pt2);
    }

}
