package no.nav.vedtak.felles.prosesstask.api;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import no.nav.vedtak.exception.VLException;
import no.nav.vedtak.felles.jpa.savepoint.SavepointRolledbackException;
import no.nav.vedtak.felles.prosesstask.impl.Feil;
import no.nav.vedtak.mapper.json.DefaultJsonMapper;

/**
 * Json struktur for feil som kan oppstå. Dupliserer noen properties for enkelthets skyld til senere prosessering.
 */
public record ProsessTaskFeil(String exceptionCauseClass,
                              String exceptionCauseMessage,
                              String taskName,
                              String taskId,
                              String callId,
                              String feilkode,
                              String feilmelding,
                              String stacktrace) {

    public static ProsessTaskFeil lagProsessTaskFeil(ProsessTaskInfo taskInfo, Feil feil) {
        var feilOpt = Optional.ofNullable(feil);
        var cause = getCause(feil);

        return new ProsessTaskFeil(
            cause.map(c -> c.getClass().getName()).orElse(null),
            cause.map(Throwable::getMessage).orElse(null),
            taskInfo.taskType().value(),
            Optional.ofNullable(taskInfo.getId()).map(Object::toString).orElse(null),
            taskInfo.getPropertyValue(CallId.CALL_ID),
            cause.map(ProsessTaskFeil::finnFeilkode).or(() -> feilOpt.map(Feil::kode)).orElse(null),
            feilOpt.map(Feil::feilmelding).orElse(null),
            // her brukes original exception (ikke unwrapped) slik at vi får med hele historikken hvor eksakt dette inntraff
            feilOpt.map(f -> getStacktraceAsString(f.cause())).orElse(null));
    }

    public String writeValueAsString() {
        return DefaultJsonMapper.toPrettyJson(this);
    }

    public static ProsessTaskFeil readFrom(String str) {
        return DefaultJsonMapper.fromJson(str, ProsessTaskFeil.class);
    }

    private static String getStacktraceAsString(Throwable cause) {
        if (cause == null) {
            return null;
        }
        var sw = new StringWriter(4096);
        var pw = new PrintWriter(sw);
        cause.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private static Optional<Throwable> getCause(Feil feil) {
        if (feil == null) {
            return Optional.empty();
        }
        var cause = feil.cause();
        if (cause instanceof SavepointRolledbackException && cause.getCause() != null) {
            return Optional.of(cause.getCause());
        }
        return Optional.ofNullable(cause);
    }

    private static String finnFeilkode(Throwable e) {
        return e instanceof VLException vle ? vle.getKode() : null;
    }
}
