package no.nav.vedtak.felles.prosesstask.impl;

import java.util.Set;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.util.AnnotationLiteral;

import org.jboss.weld.proxy.WeldClientProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Metrics;
import no.nav.vedtak.felles.prosesstask.api.ProsessTask;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskHandler;
import no.nav.vedtak.felles.prosesstask.api.TaskType;
import no.nav.vedtak.felles.prosesstask.impl.cron.CronExpression;
import no.nav.vedtak.felles.prosesstask.spi.ProsessTaskRetryPolicy;
import no.nav.vedtak.log.metrics.MetricsUtil;

/**
 *  Referanse til en {@link ProsessTaskHandler}.
 */

public class ProsessTaskHandlerRef implements AutoCloseable {

    private static final String METRIC_NAME = "task";

    static {
        MetricsUtil.utvidMedHistogram(METRIC_NAME);
    }
    private static final Logger LOG = LoggerFactory.getLogger(ProsessTaskHandlerRef.class);

    private final ProsessTaskHandler bean;
    private final CronExpression cronExpression;
    private final int maxFailedRuns;
    private final int firstDelay;
    private final int thenDelay;

    protected ProsessTaskHandlerRef(ProsessTaskHandler bean) {
        ProsessTask prosessTask;
        if (bean instanceof WeldClientProxy wcp) {
            prosessTask = wcp.getMetadata().getContextualInstance().getClass().getAnnotation(ProsessTask.class);
        } else {
            prosessTask = bean.getClass().getAnnotation(ProsessTask.class);
        }
        this.bean = bean;
        this.cronExpression = prosessTask.cronExpression().isBlank() ?
                null : new CronExpression(prosessTask.cronExpression());
        this.maxFailedRuns = prosessTask.maxFailedRuns();
        this.firstDelay = prosessTask.firstDelay();
        this.thenDelay = prosessTask.thenDelay();
    }

    public CronExpression cronExpression() {
        return cronExpression;
    }

    public Set<String> requiredProperties() {
        return bean.requiredProperties();
    }

    public ProsessTaskRetryPolicy retryPolicy() {
        return bean.retryPolicy()
                .orElseGet(() -> new ProsessTaskDefaultRetryPolicy(maxFailedRuns, firstDelay, thenDelay));
    }

    public static ProsessTaskHandlerRef lookup(TaskType taskType) {
        return new ProsessTaskHandlerRef(lookupHandler(taskType));
    }

    // Brukes i subklasse -
    protected static ProsessTaskHandler lookupHandler(TaskType taskType) {
        return CDI.current().select(ProsessTaskHandler.class, new ProsessTaskLiteral(taskType.value())).get();
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
        LOG.info("Starter task {}", data.getTaskType());
        Metrics.timer(METRIC_NAME, "type", data.getTaskType()).record(() -> bean.doTask(data));
        LOG.info("Stoppet task {}", data.getTaskType());
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
