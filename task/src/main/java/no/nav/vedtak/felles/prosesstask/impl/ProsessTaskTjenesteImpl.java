package no.nav.vedtak.felles.prosesstask.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.InjectionException;
import jakarta.inject.Inject;

import no.nav.vedtak.exception.TekniskException;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskGruppe;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskTjeneste;

/**
 * Implementasjon av repository som er tilgjengelig for å lagre og opprette nye tasks.
 */
@ApplicationScoped
public class ProsessTaskTjenesteImpl implements ProsessTaskTjeneste {

    public static final String KAN_IKKE_RESTARTE_FERDIG_TASK_FEIL_ID = "PT-711948";
    public static final String MAA_ANGI_NAVARENDE_STATUS_FEIL_ID = "PT-306456";
    public static final String STATUS_IKKE_FEILET = "PT-507456";
    public static final String UKJENT_TASK_FEIL_ID = "PT-752429";
    public static final String MANGLER_IMPL = "PT-492729";

    private ProsessTaskRepository prosessTaskRepository;

    ProsessTaskTjenesteImpl() {
        // for CDI proxying
    }

    @Inject
    public ProsessTaskTjenesteImpl(ProsessTaskRepository prosessTaskRepository) {
        this.prosessTaskRepository = prosessTaskRepository;
    }

    @Override
    public String lagre(ProsessTaskGruppe sammensattTask) {
        return prosessTaskRepository.lagre(sammensattTask);
    }

    @Override
    public String lagreValidert(ProsessTaskGruppe sammensattTask) {
        for (var entry : sammensattTask.getTasks()) {
            validerProsessTask(entry.task());
        }
        return prosessTaskRepository.lagre(sammensattTask);
    }

    @Override
    public String lagreValidert(ProsessTaskData task) {
        // prosesserer enkelt task som en gruppe av 1
        validerProsessTask(task);
        var gruppe = new ProsessTaskGruppe(task);
        return prosessTaskRepository.lagre(gruppe);
    }

    @Override
    public String lagre(ProsessTaskData task) {
        // prosesserer enkelt task som en gruppe av 1
        var gruppe = new ProsessTaskGruppe(task);
        return prosessTaskRepository.lagre(gruppe);
    }

    private void validerProsessTask(ProsessTaskData task) {
        try (var ref = ProsessTaskHandlerRef.lookup(task.taskType())) {
            task.validerProperties(ref.requiredProperties());
        } catch (InjectionException e) {
            throw new TekniskException(MANGLER_IMPL, "Mangler implementasjon av " + task.getTaskType());
        }
    }

    @Override
    public ProsessTaskData finn(Long prosessTaskId) {
        return prosessTaskRepository.finn(prosessTaskId);
    }

    @Override
    public List<ProsessTaskData> finnUferdigForGruppe(String gruppe) {
        return prosessTaskRepository.finnGruppeIkkeFerdig(gruppe);
    }

    @Override
    public List<ProsessTaskData> finnAlle(ProsessTaskStatus status) {
        return prosessTaskRepository.finnAlle(List.of(status));
    }

    @Override
    public List<ProsessTaskData> finnAlleStatuser(List<ProsessTaskStatus> statuses) {
        return prosessTaskRepository.finnAlle(statuses);
    }

    @Override
    public List<ProsessTaskData> finnAlleMedParameterTekst(String tekst, LocalDate fom, LocalDate tom) {
        return prosessTaskRepository.finnAlleForAngittSøk(tekst, fom, tom);
    }

    @Override
    public void setProsessTaskFerdig(Long prosessTaskId, ProsessTaskStatus status) {
        var taskData = prosessTaskRepository.finn(prosessTaskId);
        if (taskData == null) {
            throw new TekniskException(UKJENT_TASK_FEIL_ID,
                    String.format("Ingen prosesstask med id %s eksisterer", prosessTaskId));
        }
        if (!status.equals(taskData.getStatus())) {
            throw new TekniskException(STATUS_IKKE_FEILET,
                    String.format("Prosesstasken %s har ikke status %s og kan ikke settes FERDIG", prosessTaskId, status));
        }
        taskData.setStatus(ProsessTaskStatus.KJOERT);
        taskData.setSisteFeil(null);
        taskData.setSisteFeilKode(null);
        prosessTaskRepository.lagre(taskData);
    }

    @Override
    public void flaggProsessTaskForRestart(Long prosessTaskId, String oppgittStatus) {
        var ptd = prosessTaskRepository.finn(prosessTaskId);

        validerBetingelserForRestart(prosessTaskId, oppgittStatus, ptd);

        oppdaterProsessTaskDataMedKjoerbarStatus(ptd);
        prosessTaskRepository.lagre(ptd);
    }

    @Override
    public List<Long> flaggAlleFeileteProsessTasksForRestart() {
        var restartet = prosessTaskRepository.hentIdForAlleFeilet();
        prosessTaskRepository.settAlleFeiledeTasksKlar();
        return restartet;
    }

    @Override
    public int restartAlleFeiledeTasks() {
        return prosessTaskRepository.settAlleFeiledeTasksKlar();
    }

    @Override
    public int slettÅrsgamleFerdige() {
        return prosessTaskRepository.slettGamleFerdige();
    }

    @Override
    public int tømNestePartisjon() {
        return prosessTaskRepository.tømNestePartisjon();
    }

    @Override
    public void mottaHendelse(ProsessTaskData task, String hendelse, Properties properties) {
        Objects.requireNonNull(task, "Mangler task");
        var venterHendelse = task.getVentetHendelse();
        if (!Objects.equals(ProsessTaskStatus.VENTER_SVAR, task.getStatus()) || venterHendelse.isEmpty()) {
            throw new IllegalStateException("Uventet hendelse " + hendelse + " mottatt i tilstand " + task.getStatus());
        }
        if (!Objects.equals(venterHendelse.get(), hendelse)) {
            throw new IllegalStateException("Uventet hendelse " + hendelse + " mottatt, venter hendelse " + venterHendelse.get());
        }
        task.setStatus(ProsessTaskStatus.KLAR);
        task.setNesteKjøringEtter(LocalDateTime.now());
        if (properties != null && !properties.isEmpty()) {
            task.setProperties(properties);
        }
        prosessTaskRepository.lagre(task);
    }

    private void oppdaterProsessTaskDataMedKjoerbarStatus(ProsessTaskData task) {

        task.setStatus(ProsessTaskStatus.KLAR);
        task.setNesteKjøringEtter(LocalDateTime.now());
        task.setSisteFeilKode(null);
        task.setSisteFeil(null);

        if (task.getAntallFeiledeForsøk() > 0) {
            task.setAntallFeiledeForsøk(task.getAntallFeiledeForsøk() - 1);
        }
    }

    private void validerBetingelserForRestart(Long prosessTaskId, String nåværendeStatus, ProsessTaskData ptd) {
        if (ptd != null) {
            if (ptd.getStatus().equals(ProsessTaskStatus.FERDIG) || ptd.getStatus().equals(ProsessTaskStatus.KJOERT)) {
                throw new TekniskException(KAN_IKKE_RESTARTE_FERDIG_TASK_FEIL_ID,
                        String.format("Prosesstasken %s har allerede kjørt ferdig, og kan ikke kjøres på nytt", prosessTaskId));
            }
            if (!ProsessTaskStatus.KLAR.equals(ptd.getStatus()) && (nåværendeStatus == null || !ptd.getStatus().equals(ProsessTaskStatus.valueOf(nåværendeStatus)))) {
                throw new TekniskException(MAA_ANGI_NAVARENDE_STATUS_FEIL_ID,
                        String.format("Prosesstasken %s har ikke status KLAR. For restart må nåværende status angis.", prosessTaskId));
            }
        } else {
            throw new TekniskException(UKJENT_TASK_FEIL_ID,
                    String.format("Ingen prosesstask med id %s eksisterer", prosessTaskId));
        }
    }

}
