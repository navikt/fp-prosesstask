package no.nav.vedtak.felles.prosesstask.impl;

import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskGruppe;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskLifecycleObserver;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskVeto;

@ApplicationScoped
public class HandleProsessTaskLifecycleObserver {

    private List<ProsessTaskLifecycleObserver> lifecycleObservers = List.of();

    HandleProsessTaskLifecycleObserver() {
        // for CDI proxy
    }

    /** Observere slås opp én gang - avhengige (Dependent) instanser lever like lenge som denne. */
    @Inject
    public HandleProsessTaskLifecycleObserver(Instance<ProsessTaskLifecycleObserver> lifecycleObservers) {
        this.lifecycleObservers = lifecycleObservers.stream().toList();
    }

    public Optional<SimpleEntry<ProsessTaskLifecycleObserver, ProsessTaskVeto>> vetoRunTask(ProsessTaskData prosessTaskData) {
        if (lifecycleObservers.isEmpty()) {
            return Optional.empty();
        }
        return lifecycleObservers.stream()
            .map(pt -> new SimpleEntry<>(pt, pt.vetoKjøring(prosessTaskData)))
            .filter(e -> e.getValue().isVeto())
            .findFirst();
    }

    public void opprettetProsessTaskGruppe(ProsessTaskGruppe sammensattTask) {
        lifecycleObservers.forEach(pt -> pt.opprettetProsessTaskGruppe(sammensattTask));
    }

}
