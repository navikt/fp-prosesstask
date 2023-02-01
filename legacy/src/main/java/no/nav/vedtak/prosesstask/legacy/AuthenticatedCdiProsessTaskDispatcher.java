package no.nav.vedtak.prosesstask.legacy;

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
import no.nav.vedtak.log.mdc.MdcExtendedLogContext;
import no.nav.vedtak.sikkerhet.loginmodule.ContainerLogin;

/**
 * Implementerer dispatch vha. CDI scoped beans.
 */
@ApplicationScoped
public class AuthenticatedCdiProsessTaskDispatcher extends BasicCdiProsessTaskDispatcher {
    private static final MdcExtendedLogContext LOG_CONTEXT = MdcExtendedLogContext.getContext("prosess"); //$NON-NLS-1$
    
    private final TaskAuditlogger taskAuditlogger;

    @Inject
    public AuthenticatedCdiProsessTaskDispatcher(TaskAuditlogger taskAuditlogger) {
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
        return AuthenticatedProsessTaskHandlerRef.lookup(taskType);
    }

    private static class AuthenticatedProsessTaskHandlerRef extends ProsessTaskHandlerRef {

        private ContainerLogin containerLogin;
        private boolean successFullLogin = false;

        private AuthenticatedProsessTaskHandlerRef(ProsessTaskHandler bean) {
            super(bean);
            containerLogin = new ContainerLogin();
        }

        @Override
        public void close() {
            super.close();
            if (containerLogin != null && successFullLogin) {
                containerLogin.logout();
                successFullLogin = false;
            }
        }

        @Override
        public void doTask(ProsessTaskData prosessTaskData) {
            containerLogin.login();
            successFullLogin = true;
            super.doTask(prosessTaskData);
        }

        public static AuthenticatedProsessTaskHandlerRef lookup(TaskType taskType) {
            var bean = ProsessTaskHandlerRef.lookupHandler(taskType);
            return new AuthenticatedProsessTaskHandlerRef(bean);
        }

    }

}
