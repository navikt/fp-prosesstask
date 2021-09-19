package no.nav.vedtak.felles.prosesstask.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.InjectionException;
import javax.inject.Inject;

import no.nav.vedtak.exception.TekniskException;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskGruppe;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskGruppe.Entry;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskRepository;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskTjeneste;

/**
 * Implementasjon av repository som er tilgjengelig for å lagre og opprette nye tasks.
 */
@ApplicationScoped
public class ProsessTaskTjenesteImpl implements ProsessTaskTjeneste {

    public static String KAN_IKKE_RESTARTE_FERDIG_TASK_FEIL_ID = "PT-711948";
    public static String MAA_ANGI_NAVARENDE_STATUS_FEIL_ID = "PT-306456";
    public static String STATUS_IKKE_FEILET = "PT-507456";
    public static String UKJENT_TASK_FEIL_ID = "PT-752429";
    public static String MANGLER_IMPL = "PT-492729";

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
        for (Entry entry : sammensattTask.getTasks()) {
            validerProsessTask(entry.task());
        }
        return prosessTaskRepository.lagre(sammensattTask);
    }

    @Override
    public String lagreValidert(ProsessTaskGruppe sammensattTask) {
        return prosessTaskRepository.lagre(sammensattTask);
    }

    @Override
    public String lagreValidert(ProsessTaskData task) {
        // prosesserer enkelt task som en gruppe av 1
        validerProsessTask(task);
        ProsessTaskGruppe gruppe = new ProsessTaskGruppe(task);
        return prosessTaskRepository.lagre(gruppe);
    }

    @Override
    public String lagre(ProsessTaskData task) {
        // prosesserer enkelt task som en gruppe av 1
        ProsessTaskGruppe gruppe = new ProsessTaskGruppe(task);
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
    public List<ProsessTaskData> finnAlle(ProsessTaskStatus... statuses) {
        return prosessTaskRepository.finnAlle(statuses);
    }

    @Override
    public void setProsessTaskFerdig(Long prosessTaskId, ProsessTaskStatus status) {
        ProsessTaskData taskData = prosessTaskRepository.finn(prosessTaskId);
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
        ProsessTaskData ptd = prosessTaskRepository.finn(prosessTaskId);

        validerBetingelserForRestart(prosessTaskId, oppgittStatus, ptd);

        oppdaterProsessTaskDataMedKjoerbarStatus(ptd, LocalDateTime.now());
        prosessTaskRepository.lagre(ptd);
    }

    @Override
    public List<Long> flaggAlleFeileteProsessTasksForRestart() {
        List<Long> restartet = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        prosessTaskRepository.finnAlle(ProsessTaskStatus.FEILET).forEach(ptd -> {
            oppdaterProsessTaskDataMedKjoerbarStatus(ptd, now);
            prosessTaskRepository.lagre(ptd);
            restartet.add(ptd.getId());
        });
        return restartet;
    }

    @Override
    public void mottaHendelse(ProsessTaskData task, String hendelse) {
        mottaHendelse(task, hendelse, null);
    }

    @Override
    public void mottaHendelse(ProsessTaskData task, String hendelse, Properties properties) {
        Objects.requireNonNull(task, "Mangler task");
        Optional<String> venterHendelse = task.getVentetHendelse();
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

    private void oppdaterProsessTaskDataMedKjoerbarStatus(ProsessTaskData task, LocalDateTime nesteKjøring) {

        task.setStatus(ProsessTaskStatus.KLAR);
        task.setNesteKjøringEtter(nesteKjøring);
        task.setSisteFeilKode(null);
        task.setSisteFeil(null);

        /**
         * Tvungen kjøring: reduserer anall feilede kjøring med 1 slik at {@link no.nav.foreldrepenger.felles.prosesstask.impl.TaskManager}
         * kan plukke den opp og kjøre.
         */
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
