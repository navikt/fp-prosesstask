package no.nav.vedtak.felles.prosesstask.rest.app;

import static no.nav.vedtak.felles.prosesstask.api.ProsessTaskData.AKTØR_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import no.nav.vedtak.exception.TekniskException;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskRepository;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;
import no.nav.vedtak.felles.prosesstask.impl.TaskType;
import no.nav.vedtak.felles.prosesstask.rest.dto.FeiletProsessTaskDataDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskDataDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskOpprettInputDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskRestartInputDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskRestartResultatDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskRetryAllResultatDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.SokeFilterDto;

public class ProsessTaskApplikasjonTjenesteImplTest {

    private static final String TASK_TYPE_NAME = "random-string-uten-mening";
    private static final String TASK_TYPE_NAME_EXT = "random-string-uten-meningaa";
    private static final TaskType TASK_TYPE = new TaskType(TASK_TYPE_NAME);
    private static final int DEFAULT_MAKS_ANTALL_FEIL_FORSØK = 3;

    private ProsessTaskRepository prosessTaskRepositoryMock;

    private ProsessTaskApplikasjonTjeneste prosessTaskApplikasjonTjeneste;

    @BeforeEach
    public void setUp() throws Exception {
        prosessTaskRepositoryMock = mock(ProsessTaskRepository.class);
        prosessTaskApplikasjonTjeneste = new ProsessTaskApplikasjonTjeneste(prosessTaskRepositoryMock);

    }

    @Test
    public void skal_tvinge_restart_naar_prosesstask_har_feilet_maks() throws Exception {
        when(prosessTaskRepositoryMock.finn(anyLong()))
            .thenReturn(lagMedStatusOgFeiledeForsøk(TASK_TYPE, ProsessTaskStatus.SUSPENDERT, DEFAULT_MAKS_ANTALL_FEIL_FORSØK, 10L));
        when(prosessTaskRepositoryMock.lagre(any(ProsessTaskData.class))).thenReturn("gruppe-id");

        ProsessTaskRestartResultatDto restartResultatDto = prosessTaskApplikasjonTjeneste
            .flaggProsessTaskForRestart(lagProsessTaskRestartInputDto(10L, ProsessTaskStatus.SUSPENDERT.getDbKode()));

        ArgumentCaptor<ProsessTaskData> argumentCaptor = ArgumentCaptor.forClass(ProsessTaskData.class);
        verify(prosessTaskRepositoryMock).lagre(argumentCaptor.capture());

        ProsessTaskData dataTilPersistering = argumentCaptor.getValue();

        assertThat(restartResultatDto.getProsessTaskId()).isEqualTo(10L);
        assertThat(restartResultatDto.getProsessTaskStatus()).isEqualTo(ProsessTaskStatus.KLAR.getDbKode());

        assertThat(dataTilPersistering.getStatus()).isEqualTo(ProsessTaskStatus.KLAR);
        assertThat(dataTilPersistering.getAntallFeiledeForsøk()).isEqualTo(DEFAULT_MAKS_ANTALL_FEIL_FORSØK - 1);

        // Neste kjøring setes til LocalDateTime.now(), så tester på en enkel måte.
        assertThat(dataTilPersistering.getNesteKjøringEtter()).isAfter(LocalDateTime.now().minusSeconds(5));
    }

    @Test
    public void skal_opprette_task() {
        ProsessTaskOpprettInputDto dto = new ProsessTaskOpprettInputDto();
        dto.setTaskType("asdf.asdf");

        ProsessTaskDataDto prosessTaskDataDto = prosessTaskApplikasjonTjeneste.opprettTask(dto);
        assertThat(prosessTaskDataDto.getTaskType()).isEqualTo(dto.getTaskType());
        assertThat(prosessTaskDataDto.getTaskParametre()).isEmpty();
    }

    @Test
    public void skal_flagge_prosesstask_for_restart_gitt_korrekt_input() throws Exception {
        when(prosessTaskRepositoryMock.finn(anyLong())).thenReturn(lagMedStatus(ProsessTaskStatus.SUSPENDERT));
        when(prosessTaskRepositoryMock.lagre(any(ProsessTaskData.class))).thenReturn("gruppe-id");

        ProsessTaskRestartResultatDto restartResultatDto = prosessTaskApplikasjonTjeneste
            .flaggProsessTaskForRestart(lagProsessTaskRestartInputDto(10L, ProsessTaskStatus.SUSPENDERT.getDbKode()));

        ArgumentCaptor<ProsessTaskData> argumentCaptor = ArgumentCaptor.forClass(ProsessTaskData.class);
        verify(prosessTaskRepositoryMock).lagre(argumentCaptor.capture());

        ProsessTaskData dataTilPersistering = argumentCaptor.getValue();

        assertThat(restartResultatDto.getProsessTaskId()).isEqualTo(10L);
        assertThat(restartResultatDto.getProsessTaskStatus()).isEqualTo(ProsessTaskStatus.KLAR.getDbKode());

        assertThat(dataTilPersistering.getStatus()).isEqualTo(ProsessTaskStatus.KLAR);
        assertThat(dataTilPersistering.getAntallFeiledeForsøk()).isEqualTo(0);

        // Neste kjøring setes til LocalDateTime.now(), så tester på en enkel måte.
        assertThat(dataTilPersistering.getNesteKjøringEtter()).isAfter(LocalDateTime.now().minusSeconds(5));
    }

    @Test
    public void feil_hvis_angitt_status_ikke_matcher_naavaerende_status_naar_stauts_er_ulik_klar() throws Exception {
        var message = Assertions.assertThrows(TekniskException.class, () -> {
            when(prosessTaskRepositoryMock.finn(anyLong())).thenReturn(lagMedStatus(ProsessTaskStatus.SUSPENDERT));

            prosessTaskApplikasjonTjeneste.flaggProsessTaskForRestart(lagProsessTaskRestartInputDto(10L, "VENTER_SVAR"));
            verify(prosessTaskRepositoryMock, never()).lagre(any(ProsessTaskData.class));
        });
        assertThat(message).hasMessageContaining(ProsessTaskApplikasjonTjeneste.MAA_ANGI_NAVARENDE_STATUS_FEIL_ID);
    }

    @Test
    public void feil_hvis_navaarende_status_er_ulik_klar_og_angis_null_i_input() throws Exception {
        var message = Assertions.assertThrows(TekniskException.class, () -> {
            when(prosessTaskRepositoryMock.finn(anyLong())).thenReturn(lagMedStatus(ProsessTaskStatus.SUSPENDERT));

            prosessTaskApplikasjonTjeneste.flaggProsessTaskForRestart(lagProsessTaskRestartInputDto(10L, null));
            verify(prosessTaskRepositoryMock, never()).lagre(any(ProsessTaskData.class));
        });
        assertThat(message).hasMessageContaining(ProsessTaskApplikasjonTjeneste.MAA_ANGI_NAVARENDE_STATUS_FEIL_ID);
    }

    @Test
    public void skal_feile_hvis_ferdig_prosesstask_flagges_for_restart() throws Exception {
        var message = Assertions.assertThrows(TekniskException.class, () -> {
            when(prosessTaskRepositoryMock.finn(anyLong())).thenReturn(lagMedStatus(ProsessTaskStatus.FERDIG));

            prosessTaskApplikasjonTjeneste.flaggProsessTaskForRestart(lagProsessTaskRestartInputDto(10L, "FERDIG"));
        });
        assertThat(message).hasMessageContaining(ProsessTaskApplikasjonTjeneste.KAN_IKKE_RESTARTE_FERDIG_TASK_FEIL_ID);
    }

    @Test
    public void skal_feile_med_teknisk_feil_hvis_ukjent_prosesstask_id() throws Exception {
        var message = Assertions.assertThrows(TekniskException.class, () -> {
            when(prosessTaskRepositoryMock.finn(anyLong())).thenReturn(null);

            ProsessTaskRestartInputDto inputDto = lagProsessTaskRestartInputDto(10L, "VENTER_SVAR");
            prosessTaskApplikasjonTjeneste.flaggProsessTaskForRestart(inputDto);
        });
        assertThat(message).hasMessageContaining(ProsessTaskApplikasjonTjeneste.UKJENT_TASK_FEIL_ID);
    }

    private ProsessTaskRestartInputDto lagProsessTaskRestartInputDto(Long id, String status) {
        ProsessTaskRestartInputDto inputDto = new ProsessTaskRestartInputDto();
        inputDto.setProsessTaskId(id);
        inputDto.setNaaVaaerendeStatus(status);
        return inputDto;
    }

    @Test
    public void skal_returnere_en_restartet_prosesstask() {
        ProsessTaskData mockPT = lagMedStatusOgFeiledeForsøk(TASK_TYPE, ProsessTaskStatus.FEILET, DEFAULT_MAKS_ANTALL_FEIL_FORSØK, 10L);

        when(prosessTaskRepositoryMock.finnAlle(ProsessTaskStatus.FEILET)).thenReturn(Collections.singletonList(mockPT));
        when(prosessTaskRepositoryMock.lagre(any(ProsessTaskData.class))).thenReturn("gruppe-id");

        ProsessTaskRetryAllResultatDto result = prosessTaskApplikasjonTjeneste.flaggAlleFeileteProsessTasksForRestart();

        ArgumentCaptor<ProsessTaskData> argumentCaptor = ArgumentCaptor.forClass(ProsessTaskData.class);
        verify(prosessTaskRepositoryMock).lagre(argumentCaptor.capture());

        ProsessTaskData dataTilPersistering = argumentCaptor.getValue();

        assertThat(result.getProsessTaskIds()).hasSize(1);
        assertThat(result.getProsessTaskIds().get(0)).isEqualTo(10L);
        verify(prosessTaskRepositoryMock, times(1)).finnAlle(ProsessTaskStatus.FEILET);
        assertThat(dataTilPersistering.getStatus()).isEqualTo(ProsessTaskStatus.KLAR);
        assertThat(dataTilPersistering.getAntallFeiledeForsøk()).isEqualTo(DEFAULT_MAKS_ANTALL_FEIL_FORSØK - 1);

        // Neste kjøring setes til LocalDateTime.now(), så tester på en enkel måte.
        assertThat(dataTilPersistering.getNesteKjøringEtter()).isAfter(LocalDateTime.now().minusSeconds(5));
    }

    @Test
    public void skal_returnere_tre_restartet_prosesstask() {

        when(prosessTaskRepositoryMock.finnAlle(ProsessTaskStatus.FEILET)).thenReturn(Arrays.asList(
            lagMedStatusOgFeiledeForsøk(TASK_TYPE, ProsessTaskStatus.FEILET, DEFAULT_MAKS_ANTALL_FEIL_FORSØK, 10L),
            lagMedStatusOgFeiledeForsøk(TASK_TYPE, ProsessTaskStatus.FEILET, DEFAULT_MAKS_ANTALL_FEIL_FORSØK, 11L),
            lagMedStatusOgFeiledeForsøk(new TaskType(TASK_TYPE_NAME_EXT), ProsessTaskStatus.FEILET, DEFAULT_MAKS_ANTALL_FEIL_FORSØK, 12L)));
        when(prosessTaskRepositoryMock.lagre(any(ProsessTaskData.class))).thenReturn("gruppe-id");

        ProsessTaskRetryAllResultatDto result = prosessTaskApplikasjonTjeneste.flaggAlleFeileteProsessTasksForRestart();

        assertThat(result.getProsessTaskIds()).hasSize(3);
        verify(prosessTaskRepositoryMock, times(1)).finnAlle(ProsessTaskStatus.FEILET);
        verify(prosessTaskRepositoryMock, times(3)).lagre(any(ProsessTaskData.class));
    }

    @Test
    public void skal_filtrere_ut_prosesstask_med_status_feilet() {
        final Long taskId = 10L;
        var uuid = UUID.randomUUID();
        Map<String, String> parameters = Map.of(AKTØR_ID, "1111111111111",
                "behandlingUUId", uuid.toString(),
                "localProp", String.valueOf(123L));
        when(prosessTaskRepositoryMock.finn(anyLong())).thenReturn(lagMedStatusPlussParameters(ProsessTaskStatus.FEILET, parameters));
        FeiletProsessTaskDataDto result = prosessTaskApplikasjonTjeneste.finnFeiletProsessTask(taskId).get();

        verify(prosessTaskRepositoryMock, times(1)).finn(taskId);
        assertThat(result.getProsessTaskDataDto().getStatus()).isEqualTo(ProsessTaskStatus.FEILET.getDbKode());
        assertThat(result.getProsessTaskDataDto().getTaskParametre().stringPropertyNames()).hasSize(1);
        assertThat(result.getProsessTaskDataDto().getTaskParametre().getProperty("behandlingUUId")).isEqualTo(uuid.toString());
    }

    @Test
    public void skal_returnere_null_hvis_prosesstask_har_status_ulik_feilet() {
        final Long taskId = 10L;
        when(prosessTaskRepositoryMock.finn(anyLong())).thenReturn(lagMedStatus(ProsessTaskStatus.KLAR));
        FeiletProsessTaskDataDto result = prosessTaskApplikasjonTjeneste.finnFeiletProsessTask(taskId).orElse(null);

        verify(prosessTaskRepositoryMock, times(1)).finn(taskId);
        assertThat(result).isNull();
    }

    @Test
    public void skal_returnere_empty_hvis_ugyldig_kjoretidsintervall() throws Exception {
        SokeFilterDto sokeFilterDto = new SokeFilterDto();
        sokeFilterDto.setSisteKjoeretidspunktFraOgMed(LocalDateTime.now());
        sokeFilterDto.setSisteKjoeretidspunktTilOgMed(LocalDateTime.now().minusHours(2));

        List<ProsessTaskDataDto> result = prosessTaskApplikasjonTjeneste.finnAlle(sokeFilterDto);
        verify(prosessTaskRepositoryMock, never()).finnAlle(anyList(), any(), any());
        assertThat(result).isEmpty();
    }

    private ProsessTaskData lagMedStatus(ProsessTaskStatus status) {
        ProsessTaskData prosessTaskData = new ProsessTaskData(TASK_TYPE);
        prosessTaskData.setStatus(status);
        prosessTaskData.setAntallFeiledeForsøk(0);
        return prosessTaskData;
    }

    private ProsessTaskData lagMedStatusPlussParameters(ProsessTaskStatus status, Map<String, String> parameters) {
        ProsessTaskData prosessTaskData = new ProsessTaskData(TASK_TYPE);
        prosessTaskData.setStatus(status);
        prosessTaskData.setAntallFeiledeForsøk(0);
        parameters.forEach(prosessTaskData::setProperty);
        return prosessTaskData;
    }

    private ProsessTaskData lagMedStatusOgFeiledeForsøk(TaskType taskType, ProsessTaskStatus status, int antallFeiledeForsøk, Long id) {
        ProsessTaskData prosessTaskData = new ProsessTaskData(taskType);
        prosessTaskData.setId(id);
        prosessTaskData.setStatus(status);
        prosessTaskData.setAntallFeiledeForsøk(antallFeiledeForsøk);
        return prosessTaskData;
    }
}
