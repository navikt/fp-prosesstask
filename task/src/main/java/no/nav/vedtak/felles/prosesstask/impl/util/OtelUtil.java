package no.nav.vedtak.felles.prosesstask.impl.util;

import java.util.function.UnaryOperator;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.log.tracing.OtelSpanWrapper;

public class OtelUtil {

    private static OtelSpanWrapper WRAPPER = new OtelSpanWrapper(GlobalOpenTelemetry.getTracer("fp-prosesstask"));

    public static OtelSpanWrapper wrapper() {
        return WRAPPER;
    }

    private OtelUtil() {
        // Sonar
    }

    public static UnaryOperator<SpanBuilder> taskAttributter(ProsessTaskData data) {
        return spanBuilder -> {
            var builder = spanBuilder
                .setAttribute("prosesstaskId", data.getId())
                .setAttribute("prosesstaskType", data.taskType().value());
            if (data.getSaksnummer() != null) {
                builder = builder.setAttribute("saksnummer", data.getSaksnummer());
            }
            if (data.getBehandlingUuid() != null) {
                builder = builder.setAttribute("behandlingUuid", data.getBehandlingUuid().toString());
            } else if (data.getBehandlingIdAsLong() != null) {
                builder = builder.setAttribute("behandlingId", data.getBehandlingIdAsLong());
            }
            return builder.setSpanKind(SpanKind.INTERNAL);
        };
    }
}
