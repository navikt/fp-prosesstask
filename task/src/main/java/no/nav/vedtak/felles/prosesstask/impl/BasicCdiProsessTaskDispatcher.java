package no.nav.vedtak.felles.prosesstask.impl;

import static io.micrometer.core.instrument.Metrics.timer;

import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.util.AnnotationLiteral;
import javax.persistence.QueryTimeoutException;

import org.hibernate.exception.JDBCConnectionException;

import no.nav.vedtak.felles.prosesstask.api.ProsessTask;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskDispatcher;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskHandler;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskInfo;

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
        try (ProsessTaskHandlerRef prosessTaskHandler = findHandler(task)) {
            prosessTaskHandler.doTask(task);
        }
    }

    @Override
    public boolean feilhåndterException(String taskType, Throwable e) {
        return (feilhåndteringExceptions.stream()
                .anyMatch(
                        fatal -> fatal.isAssignableFrom(e.getClass()) || (e.getCause() != null && fatal.isAssignableFrom(e.getCause().getClass()))));
    }

    public ProsessTaskHandlerRef findHandler(ProsessTaskInfo task) {
        ProsessTaskHandler prosessTaskHandler = CDI.current()
                .select(ProsessTaskHandler.class, new ProsessTaskLiteral(task.getTaskType())).get();
        return new ProsessTaskHandlerRef(prosessTaskHandler);
    }

    /** Referanse til en {@link ProsessTaskHandler}. */
    public static class ProsessTaskHandlerRef implements AutoCloseable {

        private ProsessTaskHandler bean;

        protected ProsessTaskHandlerRef(ProsessTaskHandler bean) {
            this.bean = bean;
        }

        @Override
        public void close() {
            if (bean == null) {
                return;
            }

            if (bean.getClass().isAnnotationPresent(Dependent.class)) {
                // må closes hvis @Dependent scoped siden vi slår opp. ApplicationScoped alltid
                // ok. RequestScope også ok siden vi kjører med det.
                CDI.current().destroy(bean);
            }
        }

        public void doTask(ProsessTaskData data) {
            timer("task", "type", data.getTaskType()).record(() -> bean.doTask(data));
        }

        public ProsessTaskHandler getBean() {
            return bean;
        }

    }

    /** Lookup Literal Referanse til en {@link ProsessTaskHandler} for CDI. */
    public static class ProsessTaskLiteral extends AnnotationLiteral<ProsessTask> implements ProsessTask {

        private String taskType;

        public ProsessTaskLiteral(String taskType) {
            this.taskType = taskType;
        }

        @Override
        public String value() {
            return taskType;
        }

    }
}
