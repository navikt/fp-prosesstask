package no.nav.vedtak.felles.prosesstask.impl;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskEvent;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;

@Dependent
public class ProsessTaskEventPubliserer {

    private static final Logger LOG = LoggerFactory.getLogger(ProsessTaskEventPubliserer.class);

    private final Event<ProsessTaskEvent> publiserer;

    private final HandleProsessTaskLifecycleObserver handleProsessTaskLifecycleObserver;

    @Inject
    public ProsessTaskEventPubliserer(Event<ProsessTaskEvent> publiserer) {
        this.publiserer = publiserer;
        handleProsessTaskLifecycleObserver = new HandleProsessTaskLifecycleObserver();
    }

    public void fireEvent(ProsessTaskData data, ProsessTaskStatus gammelStatus, ProsessTaskStatus nyStatus) {
        fireEvent(data, gammelStatus, nyStatus, null, null);
    }

    public void fireEvent(ProsessTaskData data, ProsessTaskStatus gammelStatus, ProsessTaskStatus nyStatus, Feil feil, Exception orgException) {
        // I CDI 1.2 skjer publisering av event kun synkront så feil kan
        // avbryte inneværende transaksjon. Logger eventuelle exceptions fra event
        // observere uten å la tasken endre status.
        try {
            publiserer.fire(new ProsessTaskEvent(data, gammelStatus, nyStatus, feil, orgException));
        } catch (RuntimeException e) {
            // logger og svelger exception her. Feil oppstått i event observer
            String orgExceptionMessage = orgException == null ? null : String.valueOf(orgException);
            LOG.warn("PT-314162 Pollet task for kjøring: id={}, type={}, originalException={}", data.getId(), data.getTaskType(), orgExceptionMessage,
                    e);
        }

    }

    public HandleProsessTaskLifecycleObserver getHandleProsessTaskLifecycleObserver() {
        return handleProsessTaskLifecycleObserver;
    }
}
