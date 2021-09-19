package no.nav.vedtak.felles.prosesstask.rest.app;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;

import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskRepository;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskTjeneste;
import no.nav.vedtak.felles.prosesstask.api.TaskType;
import no.nav.vedtak.felles.prosesstask.rest.dto.FeiletProsessTaskDataDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskDataDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskDataKonverter;
import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskOpprettInputDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskRestartInputDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskRestartResultatDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskRetryAllResultatDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskStatusDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.SokeFilterDto;

@Dependent
public class ProsessTaskApplikasjonTjeneste {

    private ProsessTaskTjeneste prosessTaskTjeneste;
    private ProsessTaskRepository prosessTaskRepository;

    ProsessTaskApplikasjonTjeneste() {
    }

    @Inject
    public ProsessTaskApplikasjonTjeneste(ProsessTaskTjeneste prosessTaskTjeneste,
                                          ProsessTaskRepository prosessTaskRepository) {
        this.prosessTaskTjeneste = prosessTaskTjeneste;
        this.prosessTaskRepository = prosessTaskRepository;
    }

    public List<ProsessTaskDataDto> finnAlle(SokeFilterDto sokeFilterDto) {

        // Hvis ikke spesifisert, søkes det default bare på ProsessTaskStatus.KLAR og ProsessTaskStatus.VENTER_SVAR
        if (sokeFilterDto.getProsessTaskStatuser().isEmpty()) {
            sokeFilterDto.getProsessTaskStatuser().add(new ProsessTaskStatusDto(ProsessTaskStatus.KLAR.getDbKode()));
            sokeFilterDto.getProsessTaskStatuser().add(new ProsessTaskStatusDto(ProsessTaskStatus.VENTER_SVAR.getDbKode()));
        }

        if (sokeFilterDto.getSisteKjoeretidspunktFraOgMed().isAfter(sokeFilterDto.getSisteKjoeretidspunktTilOgMed())) {
            return Collections.emptyList();
        }

        List<ProsessTaskStatus> statuser = sokeFilterDto.getProsessTaskStatuser().stream().map(e -> ProsessTaskStatus.valueOf(e.getProsessTaskStatusName())).collect(Collectors.toList());
        List<ProsessTaskData> prosessTaskData = prosessTaskRepository.finnAlle(statuser, sokeFilterDto.getSisteKjoeretidspunktFraOgMed(), sokeFilterDto.getSisteKjoeretidspunktTilOgMed());
        return prosessTaskData.stream().map(ProsessTaskDataKonverter::tilProsessTaskDataDto).collect(Collectors.toList());
    }

    public Optional<FeiletProsessTaskDataDto> finnFeiletProsessTask(Long prosessTaskId) {
        ProsessTaskData taskData = prosessTaskTjeneste.finn(prosessTaskId);
        if (taskData != null && ProsessTaskStatus.FEILET.equals(taskData.getStatus())) {
            return Optional.of(ProsessTaskDataKonverter.tilFeiletProsessTaskDataDto(taskData));
        }
        return Optional.empty();
    }

    public void setProsessTaskFerdig(Long prosessTaskId, ProsessTaskStatus status) {
        prosessTaskTjeneste.setProsessTaskFerdig(prosessTaskId, status);
    }

    public ProsessTaskRestartResultatDto flaggProsessTaskForRestart(ProsessTaskRestartInputDto prosessTaskRestartInputDto) {
        prosessTaskTjeneste.flaggProsessTaskForRestart(prosessTaskRestartInputDto.getProsessTaskId(), prosessTaskRestartInputDto.getNaaVaaerendeStatus());

        ProsessTaskRestartResultatDto restartResultatDto = new ProsessTaskRestartResultatDto();
        restartResultatDto.setNesteKjoeretidspunkt(LocalDateTime.now());
        restartResultatDto.setProsessTaskId(prosessTaskRestartInputDto.getProsessTaskId());
        restartResultatDto.setProsessTaskStatus(ProsessTaskStatus.KLAR.getDbKode());
        return restartResultatDto;
    }

    public ProsessTaskRetryAllResultatDto flaggAlleFeileteProsessTasksForRestart() {
        ProsessTaskRetryAllResultatDto retryAllResultatDto = new ProsessTaskRetryAllResultatDto();

        prosessTaskTjeneste.flaggAlleFeileteProsessTasksForRestart().forEach(retryAllResultatDto::addProsessTaskId);

        return retryAllResultatDto;
    }

    public ProsessTaskDataDto opprettTask(ProsessTaskOpprettInputDto inputDto) {
        ProsessTaskData taskData = new ProsessTaskData(new TaskType(inputDto.getTaskType()));
        taskData.setProperties(inputDto.getTaskParametre());
        prosessTaskTjeneste.lagreValidert(taskData);

        return ProsessTaskDataKonverter.tilProsessTaskDataDto(taskData);
    }

}
