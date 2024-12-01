package no.nav.vedtak.felles.prosesstask.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.MDC;

public class ProsessTaskGruppe {

    private int sekvensNr;
    private final List<Entry> tasks = new ArrayList<>();

    private final Map<String, String> props = new HashMap<>();

    public ProsessTaskGruppe() {
        // empty gruppe
    }

    public ProsessTaskGruppe(ProsessTaskData data) {
        // behold sekvens hvis satt fra før, ellers sett til 1
        String sekvens = data.getSekvens() != null ? data.getSekvens() : Integer.toString(++sekvensNr);
        tasks.add(new Entry(sekvens, data));
    }

    public List<Entry> getTasks() {
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
        // kopier ned props til tasks siden de lagrer dette.
        props.entrySet().stream().forEach(e -> pt.setProperty(e.getKey(), e.getValue()));

        tasks.add(new Entry(Integer.toString(sekvensNr), pt));
    }

    public ProsessTaskGruppe addNesteParallell(Collection<ProsessTaskData> prosessTasks) {
        sekvensNr++;
        for (ProsessTaskData pt : prosessTasks) {
            leggTil(pt);
        }
        return this;
    }

    public static record Entry(String sekvens, ProsessTaskData task) {}

    // FagsakId med inntil videre pga FagsakProsessTask i enkelte apps
    public void setBehandling(String saksnummer, Long fagsakId, Long behandlingId) {
        this.getTasks().forEach(e -> e.task().setBehandling(saksnummer, fagsakId, behandlingId));
    }

    public void setFagsak(String saksnummer, Long fagsakId) {
        this.getTasks().forEach(e -> e.task().setFagsak(saksnummer, fagsakId));
    }

    // For bruk der FagsakProsessTask ikke er aktuell
    public void setBehandlingUuid(UUID behandlingUuid) {
        this.getTasks().forEach(e -> e.task().setBehandlingUUid(behandlingUuid));
    }

    public void setSaksnummer(String saksnummer) {
        this.getTasks().forEach(e -> e.task().setSaksnummer(saksnummer));
    }

    public void setCallId(String callId) {
        setProperty(CallId.CALL_ID, callId);
    }

    public void setProperty(String key, String value) {
        props.put(key, value);
        for (Entry pt : tasks) {
            // skriv til tidligere lagt til (overskriver evt. tidligere verdier.)
            pt.task.setProperty(key, value);
        }
    }

    public void setCallIdFraEksisterende() {
        setCallId(MDC.get(CallId.CALL_ID));
    }

}
