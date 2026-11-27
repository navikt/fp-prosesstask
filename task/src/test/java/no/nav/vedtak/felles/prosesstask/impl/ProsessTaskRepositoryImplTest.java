package no.nav.vedtak.felles.prosesstask.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import no.nav.vedtak.felles.prosesstask.JpaPostgresTestcontainerExtension;
import no.nav.vedtak.felles.prosesstask.api.CommonTaskProperties;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;
import no.nav.vedtak.felles.prosesstask.api.TaskType;
import no.nav.vedtak.felles.testutilities.db.EntityManagerAwareTest;

@ExtendWith(JpaPostgresTestcontainerExtension.class)
class ProsessTaskRepositoryImplTest extends EntityManagerAwareTest {

    private static final TaskType TASK_TYPE = new TaskType("hello.world");
    private static final TaskType TASK_TYPE_2 = new TaskType("hello.world2");

    private static final LocalDateTime NÅ = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    private final LocalDateTime nesteKjøringEtter = NÅ.plusHours(1);

    private ProsessTaskRepository prosessTaskRepository;

    @BeforeEach
    void setUp() {
        prosessTaskRepository = new ProsessTaskRepository(getEntityManager(), null);

        lagTestData();
    }

    @Test
    void test_ingen_match_innenfor_et_kjøretidsintervall() {
        List<ProsessTaskStatus> statuser = Arrays.stream(ProsessTaskStatus.values()).filter(ProsessTaskStatus::erIkkeFerdig).toList();
        List<ProsessTaskData> prosessTaskData = prosessTaskRepository.finnAlle(statuser);

        Assertions.assertThat(prosessTaskData).hasSize(5);
    }

    @Test
    void test_skal_finne_tasks_som_matcher_angitt_søk() {

        List<ProsessTaskStatus> statuser = List.of(ProsessTaskStatus.VENTER_SVAR);
        List<ProsessTaskData> prosessTaskData = prosessTaskRepository.finnAlle(statuser);

        Assertions.assertThat(prosessTaskData).hasSize(1);
    }

    @Test
    void test_restart_alle() {
        int restartet = prosessTaskRepository.settAlleFeiledeTasksKlar();

        Assertions.assertThat(restartet).isEqualTo(1);
    }

    @Test
    void test_finn_ukjent_id_returnerer_null() {
        Assertions.assertThat(prosessTaskRepository.finn(-999L)).isNull();
    }


    @Test
    void test_søk() {
        var tasks = prosessTaskRepository.finnAlleForAngittSøk(CommonTaskProperties.BEHANDLING_ID + "=2", LocalDate.now().minusMonths(1), LocalDate.now());

        Assertions.assertThat(tasks).hasSize(6);
    }

    @Test
    void test_tøm_neste_partisjon_returnerer_antall_tømte_rader() {
        var nestePartisjon = ProsessTaskRepository.utledPartisjonsNr(LocalDate.now());
        lagFerdigTaskIPartisjon(nestePartisjon);
        lagFerdigTaskIPartisjon(nestePartisjon);

        var antall = prosessTaskRepository.tømNestePartisjon();

        Assertions.assertThat(antall).isEqualTo(2);
        Assertions.assertThat(antallRader("SELECT count(*) FROM prosess_task_partition_ferdig_" + nestePartisjon)).isZero();
        // FERDIG-tasken fra lagTestData ligger i inneværende måneds partisjon og skal ikke røres
        Assertions.assertThat(antallRader("SELECT count(*) FROM prosess_task WHERE status = 'FERDIG'")).isEqualTo(1);
    }

    private void lagFerdigTaskIPartisjon(String partisjonsNr) {
        getEntityManager().createNativeQuery(
                "INSERT INTO prosess_task (id, task_type, status, partition_key) VALUES (nextval('seq_prosess_task'), :taskType, 'FERDIG', :partisjon)")
            .setParameter("taskType", TASK_TYPE.value())
            .setParameter("partisjon", partisjonsNr)
            .executeUpdate();
    }

    private long antallRader(String sql) {
        return ((Number) getEntityManager().createNativeQuery(sql).getSingleResult()).longValue();
    }

    private void lagTestData() {
        lagre(lagTestEntitet(ProsessTaskStatus.FERDIG, NÅ.minusHours(2), TASK_TYPE));
        lagre(lagTestEntitet(ProsessTaskStatus.KJOERT, NÅ.minusMinutes(59), TASK_TYPE));
        lagre(lagTestEntitet(ProsessTaskStatus.VENTER_SVAR, NÅ.minusHours(3), TASK_TYPE));
        lagre(lagTestEntitet(ProsessTaskStatus.FEILET, NÅ.minusHours(4), TASK_TYPE));
        lagre(lagTestEntitet(ProsessTaskStatus.KLAR, NÅ.minusHours(5), TASK_TYPE));
        lagre(lagTestEntitet(ProsessTaskStatus.KLAR, NÅ.minusHours(6), TASK_TYPE_2));
        flushAndClear();
    }

    private void flushAndClear() {
        var em = getEntityManager();
        em.flush();
        em.clear();
    }

    private void lagre(Object entity) {
        var em = getEntityManager();
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
        data.setBehandling("345", 2L);
        data.setGruppe("gruppe");
        data.setNesteKjøringEtter(nesteKjøringEtter);
        data.setPrioritet(2);
        data.setSekvens("123");

        if (sistKjørt != null) {
            data.setSistKjørt(sistKjørt);
        }

        ProsessTaskEntitet pte = new ProsessTaskEntitet();
        pte.setOpprettetTid(LocalDateTime.now());
        return pte.kopierFraNy(data);
    }

}
