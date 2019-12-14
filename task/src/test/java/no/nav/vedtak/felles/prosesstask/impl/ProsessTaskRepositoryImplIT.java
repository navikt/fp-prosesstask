package no.nav.vedtak.felles.prosesstask.impl;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import no.nav.vedtak.felles.prosesstask.UnittestRepositoryRule;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskRepository;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;

public class ProsessTaskRepositoryImplIT {

    private static final LocalDateTime NÅ = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    private final LocalDateTime nesteKjøringEtter = NÅ.plusHours(1);
    
    private final AtomicLong ids= new AtomicLong(1);
    @Rule
    public UnittestRepositoryRule repoRule = new UnittestRepositoryRule();
    private ProsessTaskRepository prosessTaskRepository;

    @Before
    public void setUp() throws Exception {
        ProsessTaskEventPubliserer prosessTaskEventPubliserer = Mockito.mock(ProsessTaskEventPubliserer.class);
        Mockito.doNothing().when(prosessTaskEventPubliserer).fireEvent(Mockito.any(ProsessTaskData.class), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        prosessTaskRepository = new ProsessTaskRepositoryImpl(repoRule.getEntityManager(), null, prosessTaskEventPubliserer);

        lagTestData();
    }

    @Test
    public void test_ingen_match_innenfor_et_kjøretidsintervall() throws Exception {
        List<ProsessTaskStatus> statuser = Arrays.asList(ProsessTaskStatus.values());
        List<ProsessTaskData> prosessTaskData = prosessTaskRepository.finnAlle(statuser, NÅ.minusHours(1), NÅ);

        Assertions.assertThat(prosessTaskData).isEmpty();
    }

    @Test
    public void test_har_match_innenfor_et_kjøretidsntervall() throws Exception {
        List<ProsessTaskStatus> statuser = Arrays.asList(ProsessTaskStatus.values());
        List<ProsessTaskData> prosessTaskData = prosessTaskRepository.finnAlle(statuser, NÅ.minusHours(2), NÅ);

        Assertions.assertThat(prosessTaskData).hasSize(1);
        Assertions.assertThat(prosessTaskData.get(0).getStatus()).isEqualTo(ProsessTaskStatus.FERDIG);
    }

    @Test
    public void test_ingen_match_for_angitt_prosesstatus() throws Exception {
        List<ProsessTaskStatus> statuser = Arrays.asList(ProsessTaskStatus.SUSPENDERT);
        List<ProsessTaskData> prosessTaskData = prosessTaskRepository.finnAlle(statuser, NÅ.minusHours(2), NÅ);

        Assertions.assertThat(prosessTaskData).isEmpty();
    }

    @Test
    public void test_skal_finne_tasks_som_matcher_angitt_søk() throws Exception {

        List<ProsessTaskStatus> statuser = Arrays.asList(ProsessTaskStatus.SUSPENDERT);
        List<ProsessTaskData> prosessTaskData = prosessTaskRepository.finnAlleForAngittSøk(statuser, null, nesteKjøringEtter, nesteKjøringEtter, "fagsakId=1%behandlingId=2%");

        Assertions.assertThat(prosessTaskData).hasSize(1);
    }

    @Test
    public void skal_finne_tasks_med_cron_expressions() {
        Map<ProsessTaskType, ProsessTaskEntitet> map = prosessTaskRepository.finnStatusForBatchTasks();

        Assertions.assertThat(map).hasSize(2);
    }

    private void lagTestData() {
        ProsessTaskType taskType = new ProsessTaskType("hello.world");
        ProsessTaskType taskType2 = new ProsessTaskType("hello.world2", "0 0 8 * * ?");
        ProsessTaskType taskType3 = new ProsessTaskType("hello.world3", "0 0 8 * * ?");
        
        lagre(taskType);
        lagre(taskType2);
        lagre(taskType3);
        flushAndClear();

        lagre(lagTestEntitet(ProsessTaskStatus.FERDIG, NÅ.minusHours(2), "hello.world"));
        lagre(lagTestEntitet(ProsessTaskStatus.VENTER_SVAR, NÅ.minusHours(3), "hello.world"));
        lagre(lagTestEntitet(ProsessTaskStatus.FEILET, NÅ.minusHours(4), "hello.world"));
        lagre(lagTestEntitet(ProsessTaskStatus.KLAR, NÅ.minusHours(5), "hello.world"));
        lagre(lagTestEntitet(ProsessTaskStatus.SUSPENDERT, NÅ.minusHours(6), "hello.world"));
        lagre(lagTestEntitet(ProsessTaskStatus.KLAR, NÅ.minusHours(6), "hello.world2"));
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

    private ProsessTaskEntitet lagTestEntitet(ProsessTaskStatus status, LocalDateTime sistKjørt, String taskType) {
        ProsessTaskData data = new ProsessTaskData(taskType);
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
        return pte.kopierFraEksisterende(data);
    }

}
