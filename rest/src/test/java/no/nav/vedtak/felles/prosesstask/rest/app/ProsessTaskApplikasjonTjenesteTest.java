package no.nav.vedtak.felles.prosesstask.rest.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import no.nav.vedtak.felles.prosesstask.rest.dto.FeiletProsessTaskStatusEnum;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import no.nav.vedtak.felles.prosesstask.api.CommonTaskProperties;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskTjeneste;
import no.nav.vedtak.felles.prosesstask.api.TaskType;
import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskOpprettInputDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskRestartInputDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.SokeFilterDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.StatusFilterDto;

@ExtendWith(MockitoExtension.class)
class ProsessTaskApplikasjonTjenesteTest {

    private static final String TASK_TYPE_NAME = "random-string-uten-mening";
    private static final TaskType TASK_TYPE = new TaskType(TASK_TYPE_NAME);

    @Captor
    private ArgumentCaptor<List<ProsessTaskStatus>> statusCaptor;

    @Mock
    private ProsessTaskTjeneste tjenesteMock;

    private ProsessTaskApplikasjonTjeneste prosessTaskApplikasjonTjeneste;

    @BeforeEach
    void setUp() {
        prosessTaskApplikasjonTjeneste = new ProsessTaskApplikasjonTjeneste(tjenesteMock);
    }

    @Test
    void skal_tvinge_restart_når_prosesstask_har_feilet_maks() {
        var taskId = 10L;
        var taskStatus = FeiletProsessTaskStatusEnum.FEILET;

        var restartResultatDto = prosessTaskApplikasjonTjeneste.flaggProsessTaskForRestart(lagProsessTaskRestartInputDto(taskId, taskStatus));

        verify(tjenesteMock).flaggProsessTaskForRestart(taskId, taskStatus.name());

        assertThat(restartResultatDto.getProsessTaskId()).isEqualTo(taskId);
        assertThat(restartResultatDto.getProsessTaskStatus()).isEqualTo(ProsessTaskStatus.KLAR.getDbKode());
    }

    @Test
    void skal_returnere_tre_restartet_prosesstask() {
        when(tjenesteMock.flaggAlleFeileteProsessTasksForRestart()).thenReturn(List.of(10L, 11L, 12L));

        var result = prosessTaskApplikasjonTjeneste.flaggAlleFeileteProsessTasksForRestart();

        assertThat(result.getProsessTaskIds()).hasSize(3);
        verify(tjenesteMock).flaggAlleFeileteProsessTasksForRestart();
    }

    @Test
    void skal_filtrere_ut_prosesstask_med_status_feilet() {
        final Long taskId = 10L;
        var uuid = UUID.randomUUID();
        var parameters = Map.of(CommonTaskProperties.AKTØR_ID, "1111111111111", "behandlingUUId", uuid.toString(), "localProp",
            String.valueOf(123L));
        when(tjenesteMock.finn(anyLong())).thenReturn(lagMedStatusPlussParameters(parameters));

        var result = prosessTaskApplikasjonTjeneste.finnFeiletProsessTask(taskId);

        verify(tjenesteMock).finn(taskId);
        assertThat(result).isPresent();

        var prosessTaskDataDto = result.get().getProsessTaskDataDto();
        assertThat(prosessTaskDataDto.getStatus()).isEqualTo(ProsessTaskStatus.FEILET.getDbKode());
        assertThat(prosessTaskDataDto.getTaskParametre().stringPropertyNames()).hasSize(1);
        assertThat(prosessTaskDataDto.getTaskParametre().getProperty("behandlingUUId")).isEqualTo(uuid.toString());
    }

    @Test
    void skal_returnere_null_hvis_prosesstask_har_status_ulik_feilet() {
        final Long taskId = 10L;
        when(tjenesteMock.finn(anyLong())).thenReturn(lagMedStatus());
        var result = prosessTaskApplikasjonTjeneste.finnFeiletProsessTask(taskId).orElse(null);

        verify(tjenesteMock, times(1)).finn(taskId);
        assertThat(result).isNull();
    }

    @Test
    void finn_alle_uten_status_oppgitt() {
        when(tjenesteMock.finnAlleStatuser(anyList())).thenReturn(List.of(ProsessTaskData.forTaskType(TASK_TYPE)));

        var resultat = prosessTaskApplikasjonTjeneste.finnAlle(new StatusFilterDto());

        verify(tjenesteMock).finnAlleStatuser(statusCaptor.capture());
        assertThat(statusCaptor.getValue()).hasSize(2);
        assertThat(resultat).isNotEmpty().hasSize(1);
        assertThat(resultat.get(0).getTaskType()).isEqualTo(TASK_TYPE_NAME);
    }

    @Test
    void finn_alle_med_parameter_tekst() {
        var søkeTekst = "haha";
        when(tjenesteMock.finnAlleMedParameterTekst(eq(søkeTekst), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of(ProsessTaskData.forTaskType(TASK_TYPE)));

        var søkeFilterDto = new SokeFilterDto();
        søkeFilterDto.setTekst(søkeTekst);
        var resultat = prosessTaskApplikasjonTjeneste.søk(søkeFilterDto);

        verify(tjenesteMock).finnAlleMedParameterTekst(eq(søkeTekst), any(LocalDate.class), any(LocalDate.class));
        assertThat(resultat).isNotEmpty().hasSize(1);
        assertThat(resultat.get(0).getTaskType()).isEqualTo(TASK_TYPE_NAME);
    }

    @Test
    void set_prosess_task_ferdig() {
        var prosessTaskId = 10L;
        var status = ProsessTaskStatus.FERDIG;
        prosessTaskApplikasjonTjeneste.setProsessTaskFerdig(prosessTaskId, status);
        verify(tjenesteMock).setProsessTaskFerdig(prosessTaskId, status);
    }

    @Test
    void opprett_prosess_task() {
        var input = new ProsessTaskOpprettInputDto();
        input.setTaskType(TASK_TYPE_NAME);
        input.setTaskParametre(new Properties());

        var prosessTaskDataDto = prosessTaskApplikasjonTjeneste.opprettTask(input);

        verify(tjenesteMock).lagreValidert(any(ProsessTaskData.class));
        assertThat(prosessTaskDataDto.getTaskType()).isEqualTo(TASK_TYPE_NAME);
    }

    private ProsessTaskData lagMedStatus() {
        var prosessTaskData = ProsessTaskData.forTaskType(TASK_TYPE);
        prosessTaskData.setStatus(ProsessTaskStatus.KLAR);
        prosessTaskData.setAntallFeiledeForsøk(0);
        return prosessTaskData;
    }

    private ProsessTaskData lagMedStatusPlussParameters(Map<String, String> parameters) {
        var prosessTaskData = ProsessTaskData.forTaskType(TASK_TYPE);
        prosessTaskData.setStatus(ProsessTaskStatus.FEILET);
        prosessTaskData.setAntallFeiledeForsøk(0);
        parameters.forEach(prosessTaskData::setProperty);
        return prosessTaskData;
    }

    private ProsessTaskRestartInputDto lagProsessTaskRestartInputDto(Long id, FeiletProsessTaskStatusEnum status) {
        var inputDto = new ProsessTaskRestartInputDto();
        inputDto.setProsessTaskId(id);
        inputDto.setNaaVaaerendeStatus(status);
        return inputDto;
    }
}
