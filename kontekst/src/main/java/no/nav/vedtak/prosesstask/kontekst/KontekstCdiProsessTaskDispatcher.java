package no.nav.vedtak.prosesstask.kontekst;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import no.nav.vedtak.exception.IntegrasjonException;
import no.nav.vedtak.exception.TekniskException;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskHandler;
import no.nav.vedtak.felles.prosesstask.api.TaskType;
import no.nav.vedtak.felles.prosesstask.impl.BasicCdiProsessTaskDispatcher;
import no.nav.vedtak.felles.prosesstask.impl.ProsessTaskHandlerRef;
import no.nav.vedtak.log.mdc.MDCOperations;
import no.nav.vedtak.log.mdc.MdcExtendedLogContext;
import no.nav.vedtak.sikkerhet.kontekst.BasisKontekst;
import no.nav.vedtak.sikkerhet.kontekst.KontekstHolder;

/**
 * Implementerer dispatch vha. CDI scoped beans.
 */
@ApplicationScoped
public class KontekstCdiProsessTaskDispatcher extends BasicCdiProsessTaskDispatcher {
    private static final MdcExtendedLogContext LOG_CONTEXT = MdcExtendedLogContext.getContext("prosess");

    public KontekstCdiProsessTaskDispatcher() {
        super(Set.of(TekniskException.class, IntegrasjonException.class));
    }

    @Override
    public void dispatch(ProsessTaskData task) {
        try (ProsessTaskHandlerRef taskHandler = taskHandler(task.taskType())) {
            if (task.getSaksnummer() != null) {
                LOG_CONTEXT.add("fagsak", task.getSaksnummer());
            } else if (task.getFagsakId() != null) { // NOSONAR
                LOG_CONTEXT.add("fagsak", task.getFagsakId());  // NOSONAR
            }
            if (task.getBehandlingUuid() != null) {
                LOG_CONTEXT.add("behandling", task.getBehandlingUuid());
            } else if (task.getBehandlingIdAsLong() != null) {
                LOG_CONTEXT.add("behandling", task.getBehandlingIdAsLong());
            }

            taskHandler.doTask(task);

            // renser ikke LOG_CONTEXT her. tar alt i RunTask slik at vi kan logge exceptions også
        }
    }

    @SuppressWarnings("resource")
    @Override
    public ProsessTaskHandlerRef taskHandler(TaskType taskType) {
        return KontekstProsessTaskHandlerRef.lookup(taskType);
    }

    private static class KontekstProsessTaskHandlerRef extends ProsessTaskHandlerRef {

        private KontekstProsessTaskHandlerRef(ProsessTaskHandler bean) {
            super(bean);
        }

        @Override
        public void close() {
            super.close();
            if (KontekstHolder.harKontekst()) {
                KontekstHolder.fjernKontekst();
            }
            // TODO vurder å flytte MDC til KontekstHolder
            MDCOperations.removeUserId();
            MDCOperations.removeConsumerId();
        }

        @Override
        public void doTask(ProsessTaskData prosessTaskData) {
            KontekstHolder.setKontekst(BasisKontekst.forProsesstaskUtenSystembruker());
            // TODO vurder å flytte MDC til KontekstHolder
            MDCOperations.putConsumerId(KontekstHolder.getKontekst().getKonsumentId());
            MDCOperations.putUserId(KontekstHolder.getKontekst().getUid());
            super.doTask(prosessTaskData);
        }

        public static KontekstProsessTaskHandlerRef lookup(TaskType taskType) {
            var bean = ProsessTaskHandlerRef.lookupHandler(taskType);
            return new KontekstProsessTaskHandlerRef(bean);
        }

    }

}
