package no.nav.vedtak.felles.prosesstask.rest.app;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
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
import no.nav.vedtak.felles.prosesstask.rest.dto.StatusFilterDto;

@Dependent
public class ProsessTaskApplikasjonTjeneste {

    private ProsessTaskTjeneste prosessTaskTjeneste;

    ProsessTaskApplikasjonTjeneste() {
        // CDI
    }

    @Inject
    public ProsessTaskApplikasjonTjeneste(ProsessTaskTjeneste prosessTaskTjeneste) {
        this.prosessTaskTjeneste = prosessTaskTjeneste;
    }

    public List<ProsessTaskDataDto> finnAlle(StatusFilterDto statusFilterDto) {

        // Hvis ikke spesifisert, søkes det default bare på ProsessTaskStatus.KLAR og ProsessTaskStatus.VENTER_SVAR
        if (statusFilterDto.getProsessTaskStatuser().isEmpty()) {
            statusFilterDto.getProsessTaskStatuser().add(new ProsessTaskStatusDto(ProsessTaskStatus.KLAR.getDbKode()));
            statusFilterDto.getProsessTaskStatuser().add(new ProsessTaskStatusDto(ProsessTaskStatus.VENTER_SVAR.getDbKode()));
        }

        var statuser = statusFilterDto.getProsessTaskStatuser().stream().map(e -> ProsessTaskStatus.valueOf(e.getProsessTaskStatusName())).toList();
        var prosessTaskData = prosessTaskTjeneste.finnAlleStatuser(statuser);
        return prosessTaskData.stream().map(ProsessTaskDataKonverter::tilProsessTaskDataDto).toList();
    }

    public List<ProsessTaskDataDto> søk(SokeFilterDto sokeFilterDto) {

        var prosessTaskData = prosessTaskTjeneste.finnAlleMedParameterTekst(sokeFilterDto.getTekst(),
                sokeFilterDto.getOpprettetFraOgMed(), sokeFilterDto.getOpprettetTilOgMed());
        return prosessTaskData.stream().map(ProsessTaskDataKonverter::tilProsessTaskDataDto).toList();
    }

    public Optional<FeiletProsessTaskDataDto> finnFeiletProsessTask(Long prosessTaskId) {
        var taskData = prosessTaskTjeneste.finn(prosessTaskId);
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

        var restartResultatDto = new ProsessTaskRestartResultatDto();
        restartResultatDto.setNesteKjoeretidspunkt(LocalDateTime.now());
        restartResultatDto.setProsessTaskId(prosessTaskRestartInputDto.getProsessTaskId());
        restartResultatDto.setProsessTaskStatus(ProsessTaskStatus.KLAR.getDbKode());
        return restartResultatDto;
    }

    public ProsessTaskRetryAllResultatDto flaggAlleFeileteProsessTasksForRestart() {
        var retryAllResultatDto = new ProsessTaskRetryAllResultatDto();

        prosessTaskTjeneste.flaggAlleFeileteProsessTasksForRestart().forEach(retryAllResultatDto::addProsessTaskId);

        return retryAllResultatDto;
    }

    public ProsessTaskDataDto opprettTask(ProsessTaskOpprettInputDto inputDto) {
        var taskData = ProsessTaskData.forTaskType(new TaskType(inputDto.getTaskType()));
        taskData.setProperties(inputDto.getTaskParametre());
        prosessTaskTjeneste.lagreValidert(taskData);

        return ProsessTaskDataKonverter.tilProsessTaskDataDto(taskData);
    }

}
