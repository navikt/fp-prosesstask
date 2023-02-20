package no.nav.vedtak.prosesstask.kontekst;

import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import no.nav.vedtak.exception.IntegrasjonException;
import no.nav.vedtak.exception.TekniskException;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskHandler;
import no.nav.vedtak.felles.prosesstask.api.TaskType;
import no.nav.vedtak.felles.prosesstask.impl.BasicCdiProsessTaskDispatcher;
import no.nav.vedtak.felles.prosesstask.impl.ProsessTaskHandlerRef;
import no.nav.vedtak.felles.prosesstask.log.TaskAuditlogger;
import no.nav.vedtak.log.mdc.MDCOperations;
import no.nav.vedtak.log.mdc.MdcExtendedLogContext;
import no.nav.vedtak.sikkerhet.kontekst.BasisKontekst;
import no.nav.vedtak.sikkerhet.kontekst.KontekstHolder;

/**
 * Implementerer dispatch vha. CDI scoped beans.
 */
@ApplicationScoped
public class KontekstCdiProsessTaskDispatcher extends BasicCdiProsessTaskDispatcher {
    private static final MdcExtendedLogContext LOG_CONTEXT = MdcExtendedLogContext.getContext("prosess"); //$NON-NLS-1$

    private final TaskAuditlogger taskAuditlogger;

    @Inject
    public KontekstCdiProsessTaskDispatcher(TaskAuditlogger taskAuditlogger) {
        super(Set.of(TekniskException.class, IntegrasjonException.class));
        this.taskAuditlogger = taskAuditlogger;
    }

    @Override
    public void dispatch(ProsessTaskData task) {
        try (ProsessTaskHandlerRef taskHandler = taskHandler(task.taskType())) {
            if (task.getSaksnummer() != null) {
                LOG_CONTEXT.add("fagsak", task.getSaksnummer()); // NOSONAR //$NON-NLS-1$
            } else if (task.getFagsakId() != null) {
                LOG_CONTEXT.add("fagsak", task.getFagsakId()); // NOSONAR //$NON-NLS-1$
            }
            if (task.getBehandlingId() != null) {
                LOG_CONTEXT.add("behandling", task.getBehandlingId()); // NOSONAR //$NON-NLS-1$
            } else if (task.getBehandlingUuid() != null) {
                LOG_CONTEXT.add("behandling", task.getBehandlingUuid()); // NOSONAR //$NON-NLS-1$
            }

            taskHandler.doTask(task);

            taskAuditlogger.logg(task);
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
            KontekstHolder.setKontekst(BasisKontekst.forProsesstask());
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
