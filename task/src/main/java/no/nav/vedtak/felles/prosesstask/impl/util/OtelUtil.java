package no.nav.vedtak.felles.prosesstask.impl.util;

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.log.tracing.OtelSpanWrapper;

import java.util.Properties;
import java.util.function.UnaryOperator;

public class OtelUtil {

    private static final String LIB_NAME = "fpprosesstask";

    private static OtelSpanWrapper WRAPPER = OtelSpanWrapper.forBibliotek(LIB_NAME, readVersionProperty());

    public static OtelSpanWrapper wrapper() {
        return WRAPPER;
    }

    private OtelUtil() {
        // Sonar
    }

    private static String readVersionProperty() {
        String version;
        try {
            final var properties = new Properties();
            properties.load(OtelUtil.class.getClassLoader().getResourceAsStream(LIB_NAME + "-version.properties"));
            version = properties.getProperty("version");
        } catch (Exception e) {
            version = "UNKNOWN";
        }
        return version;
    }

    public static UnaryOperator<SpanBuilder> taskAttributter(ProsessTaskData data) {
        return spanBuilder -> {
            var builder = spanBuilder
                .setAttribute("prosesstaskId", data.getId())
                .setAttribute("prosesstaskType", data.getTaskType());
            if (data.getSaksnummer() != null) {
                builder = builder.setAttribute("saksnummer", data.getSaksnummer());
            }
            if (data.getBehandlingUuid() != null) {
                builder = builder.setAttribute("behandlingUuid", data.getBehandlingUuid().toString());
            } else if (data.getBehandlingId() != null) {
                builder = builder.setAttribute("behandlingId", data.getBehandlingId());
            }
            return builder.setSpanKind(SpanKind.INTERNAL);
        };
    }
}
