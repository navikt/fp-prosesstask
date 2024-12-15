package no.nav.vedtak.felles.prosesstask.rest.app;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskRestartResultatDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskRetryAllResultatDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.SokeFilterDto;

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

    public List<ProsessTaskDataDto> finnAlle(List<ProsessTaskStatus> statuser) {
        var brukStatuser = new ArrayList<>(statuser);

        // Hvis ikke spesifisert, søkes det default bare på ProsessTaskStatus.KLAR og ProsessTaskStatus.VENTER_SVAR
        if (brukStatuser.isEmpty()) {
            brukStatuser.add(ProsessTaskStatus.KLAR);
            brukStatuser.add(ProsessTaskStatus.VENTER_SVAR);
        }
        var prosessTaskData = prosessTaskTjeneste.finnAlleStatuser(brukStatuser);
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

    public ProsessTaskRestartResultatDto flaggProsessTaskForRestart(Long prosessTaskId, ProsessTaskStatus status) {
        prosessTaskTjeneste.flaggProsessTaskForRestart(prosessTaskId, status);

        var restartResultatDto = new ProsessTaskRestartResultatDto();
        restartResultatDto.setNesteKjoeretidspunkt(LocalDateTime.now());
        restartResultatDto.setProsessTaskId(prosessTaskId);
        restartResultatDto.setProsessTaskStatus(ProsessTaskStatus.KLAR.name());
        return restartResultatDto;
    }

    public ProsessTaskRetryAllResultatDto flaggAlleFeileteProsessTasksForRestart() {
        var retryAllResultatDto = new ProsessTaskRetryAllResultatDto();

        prosessTaskTjeneste.flaggAlleFeileteProsessTasksForRestart().forEach(retryAllResultatDto::addProsessTaskId);

        return retryAllResultatDto;
    }

    public ProsessTaskDataDto opprettTask(ProsessTaskOpprettInputDto inputDto) {
        var sanitizedTaskType = inputDto.getTaskType().replace("\n", "").replace("\r", "").trim();
        var taskData = ProsessTaskData.forTaskType(new TaskType(sanitizedTaskType));
        taskData.setProperties(inputDto.getTaskParametre());
        prosessTaskTjeneste.lagreValidert(taskData);

        return ProsessTaskDataKonverter.tilProsessTaskDataDto(taskData);
    }

}
