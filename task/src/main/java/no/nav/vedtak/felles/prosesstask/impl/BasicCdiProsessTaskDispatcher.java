package no.nav.vedtak.felles.prosesstask.impl;

import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.persistence.QueryTimeoutException;

import org.hibernate.exception.JDBCConnectionException;

import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskDispatcher;
import no.nav.vedtak.felles.prosesstask.api.TaskType;

/**
 * Implementerer dispatch vha. CDI scoped beans.
 */
public class BasicCdiProsessTaskDispatcher implements ProsessTaskDispatcher {

        /**
     * Disse delegres til Feilhåndteringsalgoritme for håndtering. Andre vil alltid
     * gi FEILET status.
     */
    private static final Set<Class<?>> DEFAULT_FEILHÅNDTERING_EXCEPTIONS = Set.of(
            JDBCConnectionException.class,
            QueryTimeoutException.class,
            SQLTransientException.class,
            SQLNonTransientConnectionException.class,
            SQLRecoverableException.class);

    private Set<Class<?>> feilhåndteringExceptions = new LinkedHashSet<>();

    protected BasicCdiProsessTaskDispatcher() {
        this(Set.of());
    }

    protected BasicCdiProsessTaskDispatcher(Set<Class<?>> feilhåndteringExceptions) {
        this.feilhåndteringExceptions.addAll(feilhåndteringExceptions);
        this.feilhåndteringExceptions.addAll(DEFAULT_FEILHÅNDTERING_EXCEPTIONS);

    }

    @Override
    public void dispatch(ProsessTaskData task) throws Exception {
        try (var prosessTaskHandler = taskHandler(task.taskType())) {
            prosessTaskHandler.doTask(task);
        }
    }

    @Override
    public boolean feilhåndterException(Throwable e) {
        return (feilhåndteringExceptions.stream()
                .anyMatch(
                        fatal -> fatal.isAssignableFrom(e.getClass()) || (e.getCause() != null && fatal.isAssignableFrom(e.getCause().getClass()))));
    }

    @Override
    public ProsessTaskHandlerRef taskHandler(TaskType taskType) {
        return ProsessTaskHandlerRef.lookup(taskType);
    }
}
