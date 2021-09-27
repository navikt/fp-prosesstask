package no.nav.vedtak.felles.prosesstask.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import no.nav.vedtak.felles.prosesstask.JpaExtension;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskGruppe;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskRepository;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;
import no.nav.vedtak.felles.prosesstask.api.TaskType;

public class OpprettProsessTaskIT {

    @RegisterExtension
    public static final JpaExtension repoRule = new JpaExtension();
    
    private ProsessTaskRepository repo = new ProsessTaskRepositoryImpl(repoRule.getEntityManager(), null, null);

    @Test
    public void skal_lagre_ProsessTask() throws Exception {
        ProsessTaskData pt = ProsessTaskData.forTaskType(new TaskType("mytask1"));
        repo.lagre(pt);
    }

    @Test
    public void skal_lagre_SammensattProsessTask() throws Exception {
        ProsessTaskData pt1 = ProsessTaskData.forTaskType(new TaskType("mytask1"));
        ProsessTaskData pt2 = ProsessTaskData.forTaskType(new TaskType("mytask2"));
        ProsessTaskGruppe sammensatt = new ProsessTaskGruppe();
        sammensatt
            .addNesteSekvensiell(pt1)
            .addNesteSekvensiell(pt2);

        // Act
        repo.lagre(sammensatt);
        repoRule.getEntityManager().flush();

        List<ProsessTaskData> list = repo.finnAlle(ProsessTaskStatus.KLAR);
        assertThat(list).hasSize(2);
        assertThat(list).containsOnly(pt1, pt2);

    }

}
