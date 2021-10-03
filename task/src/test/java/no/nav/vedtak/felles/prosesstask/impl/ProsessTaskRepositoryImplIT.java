package no.nav.vedtak.felles.prosesstask.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;

import no.nav.vedtak.felles.prosesstask.JpaExtension;
import no.nav.vedtak.felles.prosesstask.api.CommonTaskProperties;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;
import no.nav.vedtak.felles.prosesstask.api.TaskType;

public class ProsessTaskRepositoryImplIT {

    private static final TaskType TASK_TYPE = new TaskType("hello.world");
    private static final TaskType TASK_TYPE_2 = new TaskType("hello.world2");

    @RegisterExtension
    public static final JpaExtension repoRule = new JpaExtension();

    private static final LocalDateTime NÅ = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    private final LocalDateTime nesteKjøringEtter = NÅ.plusHours(1);
    
    private final AtomicLong ids= new AtomicLong(1);
    
    private ProsessTaskRepository prosessTaskRepository;

    @BeforeEach
    public void setUp() {
        ProsessTaskEventPubliserer prosessTaskEventPubliserer = Mockito.mock(ProsessTaskEventPubliserer.class);
        Mockito.doNothing().when(prosessTaskEventPubliserer).fireEvent(Mockito.any(ProsessTaskData.class), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        prosessTaskRepository = new ProsessTaskRepository(repoRule.getEntityManager(), null, prosessTaskEventPubliserer);

        lagTestData();
    }

    @Test
    public void test_ingen_match_innenfor_et_kjøretidsintervall() {
        List<ProsessTaskStatus> statuser = Arrays.stream(ProsessTaskStatus.values()).filter(ProsessTaskStatus::erIkkeFerdig).toList();
        List<ProsessTaskData> prosessTaskData = prosessTaskRepository.finnAlle(statuser);

        Assertions.assertThat(prosessTaskData).hasSize(6);
    }

    @Test
    public void test_ingen_match_for_angitt_prosesstatus() {
        List<ProsessTaskStatus> statuser = List.of(ProsessTaskStatus.VETO);
        List<ProsessTaskData> prosessTaskData = prosessTaskRepository.finnAlle(statuser);

        Assertions.assertThat(prosessTaskData).isEmpty();
    }

    @Test
    public void test_skal_finne_tasks_som_matcher_angitt_søk() {

        List<ProsessTaskStatus> statuser = List.of(ProsessTaskStatus.SUSPENDERT);
        List<ProsessTaskData> prosessTaskData = prosessTaskRepository.finnAlle(statuser);

        Assertions.assertThat(prosessTaskData).hasSize(1);
    }

    @Test
    public void test_restart_alle() {
        int restartet = prosessTaskRepository.settAlleFeiledeTasksKlar();

        Assertions.assertThat(restartet).isEqualTo(1);
    }

    @Test
    public void test_hent_feilet_id() {
        var feilet = prosessTaskRepository.hentIdForAlleFeilet();

        Assertions.assertThat(feilet).hasSize(1);
    }

    @Test
    public void test_søk() {
        var tasks = prosessTaskRepository.finnAlleForAngittSøk(CommonTaskProperties.BEHANDLING_ID + "=2", LocalDate.now().minusMonths(1), LocalDate.now());

        Assertions.assertThat(tasks).hasSize(7);
    }

    private void lagTestData() {

        lagre(lagTestEntitet(ProsessTaskStatus.FERDIG, NÅ.minusHours(2), TASK_TYPE));
        lagre(lagTestEntitet(ProsessTaskStatus.KJOERT, NÅ.minusMinutes(59), TASK_TYPE));
        lagre(lagTestEntitet(ProsessTaskStatus.VENTER_SVAR, NÅ.minusHours(3), TASK_TYPE));
        lagre(lagTestEntitet(ProsessTaskStatus.FEILET, NÅ.minusHours(4), TASK_TYPE));
        lagre(lagTestEntitet(ProsessTaskStatus.KLAR, NÅ.minusHours(5), TASK_TYPE));
        lagre(lagTestEntitet(ProsessTaskStatus.SUSPENDERT, NÅ.minusHours(6), TASK_TYPE));
        lagre(lagTestEntitet(ProsessTaskStatus.KLAR, NÅ.minusHours(6), TASK_TYPE_2));
        flushAndClear();
    }
    
    private void flushAndClear() {
        var em = repoRule.getEntityManager();
        em.flush();
        em.clear();
    }

    private void lagre(Object entity) {
        var em = repoRule.getEntityManager();
        em.persist(entity);
    }

    private ProsessTaskEntitet lagTestEntitet(ProsessTaskStatus status, LocalDateTime sistKjørt, TaskType taskType) {
        ProsessTaskData data = ProsessTaskData.forTaskType(taskType);
        data.setPayload("payload");
        data.setStatus(status);
        data.setSisteKjøringServerProsess("prossess-123");
        data.setSisteFeilKode("feilkode-123");
        data.setSisteFeil("siste-feil");
        data.setAntallFeiledeForsøk(2);
        data.setBehandling(1L, 2L, "3");
        data.setGruppe("gruppe");
        data.setNesteKjøringEtter(nesteKjøringEtter);
        data.setPrioritet(2);
        data.setSekvens("123");
        data.setId(ids.incrementAndGet());

        if (sistKjørt != null) {
            data.setSistKjørt(sistKjørt);
        }

        ProsessTaskEntitet pte = new ProsessTaskEntitet();
        pte.setOpprettetTid(LocalDateTime.now());
        return pte.kopierFraEksisterende(data);
    }

}
