package no.nav.vedtak.felles.prosesstask.impl;

import java.lang.annotation.Annotation;
import java.util.Set;

import org.jboss.weld.interceptor.util.proxy.TargetInstanceProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.Dependent;
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

    private final ProsessTaskHandler bean;

    protected ProsessTaskHandlerRef(ProsessTaskHandler bean) {
        this.bean = bean;
    }

    public CronExpression cronExpression() {
        var task = getProsessTaskAnnotation();
        return task.cronExpression().isBlank() ? null : new CronExpression(task.cronExpression());
    }

    public Set<String> requiredProperties() {
        return bean.requiredProperties();
    }

    public ProsessTaskRetryPolicy retryPolicy() {
        return bean.retryPolicy()
                .orElseGet(() -> {
                    var task = getProsessTaskAnnotation();
                    return new ProsessTaskDefaultRetryPolicy(task.maxFailedRuns(), task.firstDelay(), task.thenDelay());
                });
    }


    public static ProsessTaskHandlerRef lookup(TaskType taskType) {
        return new ProsessTaskHandlerRef(lookupHandler(taskType));
    }

    // Brukes i subklasse -
    protected static ProsessTaskHandler lookupHandler(TaskType taskType) {
        return CDI.current().select(ProsessTaskHandler.class, new ProsessTaskLiteral(taskType.value())).get();
    }

    protected ProsessTaskHandler getBean() {
        return bean;
    }

    private ProsessTask getProsessTaskAnnotation() {
        Class<?> clazz = getTargetClassExpectingAnnotation(ProsessTask.class);
        if (!clazz.isAnnotationPresent(ProsessTask.class)) {
            throw new IllegalStateException(clazz.getSimpleName() + " mangler annotering @ProsesTask");
        }
        return clazz.getAnnotation(ProsessTask.class);
    }

    protected Class<?> getTargetClassExpectingAnnotation(Class<? extends Annotation> annotationClass) {
        if (!bean.getClass().isAnnotationPresent(annotationClass) && bean instanceof TargetInstanceProxy<?> tip) {
            return tip.weld_getTargetInstance().getClass();
        } else {
            return bean.getClass();
        }
    }

    @Override
    public void close() {
        if (bean == null) {
            return;
        }

        if (bean.getClass().isAnnotationPresent(Dependent.class)) {
            // må closes hvis @Dependent scoped siden vi slår opp. ApplicationScoped alltid ok. RequestScope også ok siden vi kjører med det.
            CDI.current().destroy(bean);
        }
    }

    public void doTask(ProsessTaskData data) {
        LOG.info("Starter task {}", data.getTaskType());
        bean.doTask(data);
        LOG.info("Stoppet task {}", data.getTaskType());
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
