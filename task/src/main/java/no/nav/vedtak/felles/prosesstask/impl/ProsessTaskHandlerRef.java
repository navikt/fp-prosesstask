package no.nav.vedtak.felles.prosesstask.impl;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.util.AnnotationLiteral;
import no.nav.vedtak.felles.prosesstask.api.ProsessTask;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskHandler;
import no.nav.vedtak.felles.prosesstask.api.TaskType;
import no.nav.vedtak.felles.prosesstask.impl.cron.CronExpression;
import no.nav.vedtak.felles.prosesstask.spi.ProsessTaskRetryPolicy;

/**
 *  Referanse til en {@link ProsessTaskHandler}.
 */

public class ProsessTaskHandlerRef implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ProsessTaskHandlerRef.class);

    private final Instance.Handle<ProsessTaskHandler> handle;

    protected ProsessTaskHandlerRef(Instance.Handle<ProsessTaskHandler> handle) {
        this.handle = Objects.requireNonNull(handle, "handle");
    }

    public static ProsessTaskHandlerRef lookup(TaskType taskType) {
        return new ProsessTaskHandlerRef(lookupHandle(taskType));
    }

    protected static Instance.Handle<ProsessTaskHandler> lookupHandle(TaskType taskType) {
        return CDI.current().select(ProsessTaskHandler.class, new ProsessTaskLiteral(taskType.value())).getHandle();
    }

    public CronExpression cronExpression() {
        var task = getProsessTaskAnnotation();
        return task.cronExpression().isBlank() ? null : new CronExpression(task.cronExpression());
    }

    public Set<String> requiredProperties() {
        return getBean().requiredProperties();
    }

    public ProsessTaskRetryPolicy retryPolicy() {
        return getBean().retryPolicy()
                .orElseGet(() -> {
                    var task = getProsessTaskAnnotation();
                    return new ProsessTaskDefaultRetryPolicy(task.maxFailedRuns(), task.firstDelay(), task.thenDelay());
                });
    }

    /** Instansieres ved første kall. Normal-scoped handlere gir klient-proxy. */
    protected ProsessTaskHandler getBean() {
        return handle.get();
    }

    /** Annotert implementasjonsklasse, hentet fra CDI-metadata uten å instansiere handleren. */
    protected Class<?> getHandlerClass() {
        return handle.getBean().getBeanClass();
    }

    private ProsessTask getProsessTaskAnnotation() {
        var clazz = getHandlerClass();
        return Optional.ofNullable(clazz.getAnnotation(ProsessTask.class))
            .orElseThrow(() -> new IllegalStateException(clazz.getSimpleName() + " mangler annotering @ProsesTask"));
    }

    /** Destruerer kun @Dependent-instanser; normal-scoped handlere eies av sin kontekst. */
    @Override
    public void close() {
        if (Dependent.class.equals(handle.getBean().getScope())) {
            handle.destroy();
        }
    }

    public void doTask(ProsessTaskData data) {
        LOG.info("Starter task {}", data.taskType());
        getBean().doTask(data);
        LOG.info("Stoppet task {}", data.taskType());
    }

    /** Lookup Literal Referanse til en {@link ProsessTaskHandler} for CDI. */
    public static class ProsessTaskLiteral extends AnnotationLiteral<ProsessTask> implements ProsessTask {

        private final String taskType;

        public ProsessTaskLiteral(String taskType) {
            this.taskType = taskType;
        }

        @Override
        public String value() {
            return taskType;
        }

        @Override
        public int prioritet() {
            return 1;
        }

        @Override
        public String cronExpression() {
            return "";
        }

        @Override
        public int maxFailedRuns() {
            return 3;
        }

        @Override
        public int firstDelay() {
            return 30;
        }

        @Override
        public int thenDelay() {
            return 60;
        }
    }


}
