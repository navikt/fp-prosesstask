package no.nav.vedtak.felles.prosesstask.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

public class ProsessTaskGruppe {

    private int sekvensNr;
    private final String gruppenavn;
    private final TaskRekkefølge taskRekkefølge;
    private final Long offset;
    private boolean beholdeKildeSekvens = false;
    private final List<Entry> tasks = new ArrayList<>();

    private final Properties props = new Properties();

    private ProsessTaskGruppe(TaskRekkefølge taskRekkefølge, String gruppenavn, ProsessTaskData prosessTaskData) {
        var gruppeFraParam = Optional.ofNullable(gruppenavn).filter(g -> !g.isBlank());
        this.gruppenavn = switch (taskRekkefølge) {
            case GRUPPE -> gruppeFraParam.orElseGet(() -> UUID.randomUUID().toString());
            case GLOBAL, SAKSNUMMER, TASK_TYPE -> gruppeFraParam
                .orElseThrow(() -> new IllegalArgumentException("Må angi gruppenavn for global gruppe / taskType"));
        };
        this.taskRekkefølge = taskRekkefølge;
        this.offset = taskRekkefølge.global() ? Instant.now().toEpochMilli() * 1000 : 0L;
        if (TaskRekkefølge.SAKSNUMMER.equals(taskRekkefølge)) {
            setSaksnummer(gruppenavn);
        }
        if (prosessTaskData != null) {
            tasks.add(new Entry(++sekvensNr, prosessTaskData));
        }
    }

    public static ProsessTaskGruppe forUtenRekkefølge(Class<? extends ProsessTaskHandler> clazz) {
        return new ProsessTaskGruppe(TaskRekkefølge.TASK_TYPE, TaskType.forProsessTask(clazz).value(), null);
    }

    public static ProsessTaskGruppe forUtenRekkefølge(TaskType taskType) {
        return new ProsessTaskGruppe(TaskRekkefølge.TASK_TYPE, taskType.value(), null);
    }

    public static ProsessTaskGruppe forGruppeRekkefølge() {
        return new ProsessTaskGruppe(TaskRekkefølge.GRUPPE,null, null);
    }

    public static ProsessTaskGruppe forGlobalRekkefølge(String gruppenavn) {
        return new ProsessTaskGruppe(TaskRekkefølge.GLOBAL, Objects.requireNonNull(gruppenavn, "gruppenavn"), null);
    }

    public static ProsessTaskGruppe forSaksnummer(String saksnummer) {
        return new ProsessTaskGruppe(TaskRekkefølge.SAKSNUMMER, Objects.requireNonNull(saksnummer, "saksnummer"), null);
    }

    public static ProsessTaskGruppe fraProsessTask(ProsessTaskData prosessTaskData) {
        return fraProsessTask(prosessTaskData.taskRekkefølge(), prosessTaskData);
    }

    public static ProsessTaskGruppe fraProsessTask(TaskRekkefølge taskRekkefølge, ProsessTaskData prosessTaskData) {
        return switch (taskRekkefølge) {
            case GRUPPE -> new ProsessTaskGruppe(taskRekkefølge, prosessTaskData.getGruppe(), prosessTaskData);
            case GLOBAL -> new ProsessTaskGruppe(TaskRekkefølge.GLOBAL, prosessTaskData.getGruppe(), prosessTaskData);
            case SAKSNUMMER -> new ProsessTaskGruppe(TaskRekkefølge.SAKSNUMMER, prosessTaskData.getSaksnummer(), prosessTaskData);
            case TASK_TYPE -> new ProsessTaskGruppe(TaskRekkefølge.TASK_TYPE, prosessTaskData.taskType().value(), prosessTaskData);
        };
    }

    public List<Entry> getTasks() {
        return Collections.unmodifiableList(tasks);
    }

    public List<Entry> getTasksForLagring() {
        var maxSekvens = this.taskRekkefølge.global() ? Long.MAX_VALUE : (tasks.stream().mapToLong(Entry::sekvens).max().orElse(1L) + offset);
        var formatString = "%0" + Long.toString(maxSekvens).length() + "d";
        tasks.forEach(t -> {
            t.task().setProperties(props);
            t.task().setGruppe(gruppenavn);
            if (!beholdeKildeSekvens) {
                t.task().setSekvens(formatString.formatted(t.sekvens() + offset));
            } else if (t.task().getSekvens() == null) {
                throw new IllegalStateException("BeholdeKildeSekvens er satt, men task har ikke sekvens. Task: " + t.task());
            }
        });
        return Collections.unmodifiableList(tasks);
    }

    /**
     * Legger til ny {@link ProsessTaskData}. Vil kjøres når foregående er ferdig kjørt. Hvis foregående feiler, vil
     * prosessen stoppe opp til det er løst.
     */
    public ProsessTaskGruppe addNesteSekvensiell(ProsessTaskData prosessTask) {
        sekvensNr++;
        leggTil(prosessTask);
        return this;
    }

    /**
     * støtter p.t. bare enkle fork-join (ikke multiple branch / sekvensielle steg sammensatt).
     */
    public ProsessTaskGruppe addNesteParallell(ProsessTaskData... prosessTasks) {
        sekvensNr++;
        for (ProsessTaskData pt : prosessTasks) {
            // alle får samme sekvensnr
            leggTil(pt);
        }
        return this;
    }

    private void leggTil(ProsessTaskData pt) {
        tasks.add(new Entry(TaskRekkefølge.TASK_TYPE.equals(taskRekkefølge) ? 1L : sekvensNr, pt));
    }

    public ProsessTaskGruppe addNesteParallell(Collection<ProsessTaskData> prosessTasks) {
        sekvensNr++;
        for (ProsessTaskData pt : prosessTasks) {
            leggTil(pt);
        }
        return this;
    }

    public record Entry(long sekvens, ProsessTaskData task) {}

    // FagsakId med inntil videre pga FagsakProsessTask i enkelte apps
    public void setBehandling(String saksnummer, Long behandlingId) {
        setSaksnummer(saksnummer);
        setBehandlingId(behandlingId);
    }

    public void setBehandling(String saksnummer, UUID behandlingUuid, Long behandlingId) {
        setSaksnummer(saksnummer);
        setBehandlingId(behandlingId);
        setBehandlingUuid(behandlingUuid);
    }

    public void setFagsak(String saksnummer) {
        setSaksnummer(saksnummer);
    }

    public void setBehandlingUuid(UUID behandlingUuid) {
        Objects.requireNonNull(behandlingUuid, "behandlingUuid");
        setProperty(CommonTaskProperties.BEHANDLING_UUID, behandlingUuid.toString());
    }

    public void setBehandlingId(Long behandlingId) {
        Objects.requireNonNull(behandlingId, "behandlingId");
        setProperty(CommonTaskProperties.BEHANDLING_ID, Long.toString(behandlingId));
    }

    public void setSaksnummer(String saksnummer) {
        Objects.requireNonNull(saksnummer, "saksnummer");
        setProperty(CommonTaskProperties.SAKSNUMMER, saksnummer);
    }

    public void setCallId(String callId) {
        setProperty(CallId.CALL_ID, callId);
    }

    public boolean harCallId() {
        return tasks.isEmpty() || tasks.stream().map(Entry::task).allMatch(ProsessTaskData::harCallId);
    }

    public void setProperty(String key, String value) {
        ProsessTaskData.setProperty(this.props, key, value);
    }

    // Spesielt tilfelle der det opprettes en ny callback-receiver task med samme gruppe og sekvens som en eksisterende task.
    public static ProsessTaskGruppe forSammeGruppeSekvens(Class<? extends ProsessTaskHandler> clazz, ProsessTaskData opphavsTask) {
        if (!opphavsTask.taskRekkefølge().global()) {
            throw new IllegalArgumentException("Kan kun velge denne for global rekkefølge");
        }
        var prosessTaskData = ProsessTaskData.forProsessTask(clazz);
        prosessTaskData.setProperties(opphavsTask.getProperties());
        prosessTaskData.setPayload(opphavsTask.getPayloadAsString());
        prosessTaskData.setGruppe(opphavsTask.getGruppe());
        prosessTaskData.setSekvens(opphavsTask.getSekvens());
        var gruppe = ProsessTaskGruppe.fraProsessTask(prosessTaskData);
        gruppe.beholdeKildeSekvens = true;
        return gruppe;
    }

}
