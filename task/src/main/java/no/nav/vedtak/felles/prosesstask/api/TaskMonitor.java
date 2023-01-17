package no.nav.vedtak.felles.prosesstask.api;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;

public class TaskMonitor {

    public static final String TASK = "prosesstask.";

    public static final Timer TASK_TIMER = Timer.builder(TASK + "timer")
        .publishPercentileHistogram()
        .register(Metrics.globalRegistry);

    private static final Map<ProsessTaskStatus, AtomicInteger> TASK_GAUGES = Map.of(
        ProsessTaskStatus.KLAR, statusGauge(ProsessTaskStatus.KLAR),
        ProsessTaskStatus.VENTER_SVAR, statusGauge("venter"),
        ProsessTaskStatus.VETO, statusGauge(ProsessTaskStatus.VETO),
        ProsessTaskStatus.FEILET, statusGauge(ProsessTaskStatus.FEILET)
    );

    private TaskMonitor() {
        // NOSONAR
    }

    public static void setStatusCount(ProsessTaskStatus status, Integer antall) {
        Optional.ofNullable(TASK_GAUGES.get(status)).ifPresent(a -> a.set(antall));
    }

    public static Set<ProsessTaskStatus> monitoredStatuses() {
        return TASK_GAUGES.keySet();
    }

    private static AtomicInteger statusGauge(ProsessTaskStatus status) {
        return statusGauge(status.name().toLowerCase());
    }

    private static AtomicInteger statusGauge(String name) {
        return Metrics.gauge(TASK + name, new AtomicInteger(0));
    }

}
