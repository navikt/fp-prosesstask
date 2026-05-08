package no.nav.vedtak.felles.prosesstask.api;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Embeddable;
import no.nav.vedtak.exception.VLException;
import no.nav.vedtak.felles.jpa.savepoint.SavepointRolledbackException;
import no.nav.vedtak.felles.prosesstask.impl.Feil;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Json struktur for feil som kan oppstå. Dupliserer noen properties for enkelthets skyld til senere prosessering.
 */
@JsonAutoDetect(getterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE, fieldVisibility = Visibility.ANY)
@JsonIgnoreProperties(ignoreUnknown = true)
@Embeddable
public class ProsessTaskFeil {

    private static final ObjectWriter OBJECT_WRITER;
    private static final ObjectReader OBJECT_READER;

    static {
        JsonMapper om = JsonMapper.builder().build();
        OBJECT_WRITER = om.writerWithDefaultPrettyPrinter();
        OBJECT_READER = om.readerFor(ProsessTaskFeil.class);
    }

    @JsonProperty("exceptionCauseClass")
    private String exceptionCauseClass;

    @JsonProperty("exceptionCauseMessage")
    private String exceptionCauseMessage;

    @JsonProperty("taskName")
    private String taskName;

    @JsonProperty("taskId")
    private String taskId;

    @JsonProperty("callId")
    private String callId;

    @JsonProperty("feilkode")
    private String feilkode;

    @JsonProperty("feilmelding")
    private String feilmelding;

    @JsonProperty("stacktrace")
    private String stackTrace;

    @JsonProperty("blokkertAv")
    private Long blokkerendeProsessTaskId;

    public ProsessTaskFeil() {
        // default ctor for proxy
    }

    public static ProsessTaskFeil lagProsessTaskFeil(ProsessTaskInfo taskInfo, Feil feil) {
        var taskFeil = new ProsessTaskFeil();
        if (feil != null) {
            var cause = getCause(feil);

            if (cause != null) {
                // bruker her unwrapped cause hvis finnes
                taskFeil.exceptionCauseClass = cause.getClass().getName();
                taskFeil.exceptionCauseMessage = cause.getMessage();
                taskFeil.feilkode = finnFeilkode(cause);
            }
            if (taskFeil.feilkode == null) {
                taskFeil.feilkode = feil.kode();
            }

            if (feil.cause() != null) {
                // her brukes original exception (ikke unwrapped) slik at vi får med hele historikken hvor eksakt dette inntraff
                taskFeil.stackTrace = getStacktraceAsString(feil.cause());// bruker original exception uansett (inkludert wrapping exceptions)
            }

            taskFeil.feilmelding = feil.feilmelding();
        }

        taskFeil.taskName = taskInfo.taskType().value();
        taskFeil.taskId = taskInfo.getId() == null ? null : taskInfo.getId().toString();
        taskFeil.callId = taskInfo.getPropertyValue(CallId.CALL_ID);
        return taskFeil;
    }

    public String getExceptionCauseClass() {
        return exceptionCauseClass;
    }

    public String getExceptionCauseMessage() {
        return exceptionCauseMessage;
    }

    public String getTaskName() {
        return taskName;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getCallId() {
        return callId;
    }

    public String getFeilkode() {
        return feilkode;
    }

    public String getFeilmelding() {
        return feilmelding;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    private static String getStacktraceAsString(Throwable cause) {
        if (cause == null) {
            return null;
        }
        StringWriter sw = new StringWriter(4096);
        PrintWriter pw = new PrintWriter(sw);
        cause.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private static Throwable getCause(Feil feil) {
        if (feil == null) {
            return null;
        }
        Throwable cause = feil.cause();
        if (cause instanceof SavepointRolledbackException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static String finnFeilkode(Throwable e) {
        return e instanceof VLException vle ? vle.getKode() : null;
    }

    public String writeValueAsString() {
        return OBJECT_WRITER.writeValueAsString(this);
    }

    public static ProsessTaskFeil readFrom(String str) {
        return OBJECT_READER.readValue(str);
    }

    public void setFeilkode(String feilkode) {
        this.feilkode = feilkode;
    }

    public void setBlokkerendeProsessTaskId(Long blokkerendeProsessTaskId) {
        this.blokkerendeProsessTaskId = blokkerendeProsessTaskId;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof ProsessTaskFeil that))
            return false;

        return Objects.equals(getTaskName(), that.getTaskName()) && Objects.equals(getTaskId(), that.getTaskId())
            && Objects.equals(getCallId(), that.getCallId()) && Objects.equals(getFeilmelding(), that.getFeilmelding());
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskName, taskId, callId, feilmelding);
    }

}
