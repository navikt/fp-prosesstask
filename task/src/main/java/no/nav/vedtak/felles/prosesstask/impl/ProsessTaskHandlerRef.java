package no.nav.vedtak.felles.prosesstask.impl;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.util.AnnotationLiteral;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Metrics;
import no.nav.vedtak.felles.prosesstask.api.ProsessTask;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskHandler;
import no.nav.vedtak.felles.prosesstask.api.TaskType;
import no.nav.vedtak.felles.prosesstask.impl.cron.CronExpression;
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

    protected ProsessTaskHandlerRef(ProsessTaskHandler bean) {
        this.bean = bean;
    }

    public CronExpression cronExpression() {
        var annotatedCronExpression = bean.getClass().getAnnotation(ProsessTask.class).cronExpression();
        return annotatedCronExpression.isBlank() ? null : new CronExpression(annotatedCronExpression);
    }

    public boolean retryTask(int numFailedRuns, Throwable t) {
        return bean.retryPolicy().retryTask(numFailedRuns, t);
    }

    public int secondsToNextRun(int numFailedRuns) {
        return bean.retryPolicy().secondsToNextRun(numFailedRuns);
    }

    public static ProsessTaskHandlerRef lookup(TaskType taskType) {
        return new ProsessTaskHandlerRef(lookupHandler(taskType));
    }

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

    }


}
