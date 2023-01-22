package no.nav.vedtak.felles.prosesstask.api;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

public class TaskMonitor {

    public static final String TASK_LENGDE = "prosesstask.lengde";
    public static final String TASK_ANTALL = "prosesstask.antall";

    public static final Timer TASK_TIMER = Metrics.timer(TASK_LENGDE);

    public static final Map<ProsessTaskStatus, AtomicInteger> TASK_GAUGES = Map.of(
        ProsessTaskStatus.KLAR, statusGauge(ProsessTaskStatus.KLAR),
        ProsessTaskStatus.VENTER_SVAR, statusGauge(ProsessTaskStatus.VENTER_SVAR),
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
        return Metrics.gauge(TASK_ANTALL, Tags.of("status", statusLabel(status)), new AtomicInteger(0));
    }

    public static String statusLabel(ProsessTaskStatus status) {
        return ProsessTaskStatus.VENTER_SVAR.equals(status) ? "venter" : status.name().toLowerCase();
    }

}
