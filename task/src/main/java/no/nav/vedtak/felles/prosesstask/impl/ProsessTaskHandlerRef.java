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

    public String cronExpression() {
        return bean.cronExpression();
    }

    public boolean retryTask(int numFailedRuns, Throwable t) {
        return bean.retryTask(numFailedRuns, t);
    }

    public int secondsToNextRun(int numFailedRuns) {
        return bean.secondsToNextRun(numFailedRuns);
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

    }


}
