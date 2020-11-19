package no.nav.vedtak.felles.prosesstask.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import no.nav.vedtak.felles.prosesstask.JpaExtension;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskGruppe;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskRepository;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;

public class OpprettProsessTaskIT {

    @RegisterExtension
    public static final JpaExtension repoRule = new JpaExtension();
    
    private ProsessTaskRepository repo = new ProsessTaskRepositoryImpl(repoRule.getEntityManager(), null, null);

    @BeforeEach
    public void setupTestData() throws Exception {
        new TestProsessTaskTestData(repoRule.getEntityManager())
            .opprettTaskType("mytask1")
            .opprettTaskType("mytask2");
    }

    @Test
    public void skal_lagre_ProsessTask() throws Exception {
        ProsessTaskData pt = new ProsessTaskData("mytask1");
        repo.lagre(pt);
    }

    @Test
    public void skal_lagre_SammensattProsessTask() throws Exception {
        ProsessTaskData pt1 = new ProsessTaskData("mytask1");
        ProsessTaskData pt2 = new ProsessTaskData("mytask2");
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
