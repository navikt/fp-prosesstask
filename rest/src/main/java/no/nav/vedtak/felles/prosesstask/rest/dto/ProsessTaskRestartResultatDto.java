package no.nav.vedtak.felles.prosesstask.rest.dto;

import java.time.LocalDateTime;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@JsonAutoDetect(getterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE, fieldVisibility = Visibility.ANY)
@Schema(description = "Resultatet av asynkron-restart av en eksisterende prosesstask")
public class ProsessTaskRestartResultatDto {

    @JsonProperty(value = "prosessTaskId", required = true)
    @NotNull
    private Long prosessTaskId;

    @JsonProperty(value = "prosessTaskStatus", required = true)
    @NotNull
    @Schema(description = "Nåværende status (KLAR)")
    @Pattern(regexp = "^[\\p{Alnum}_.\\-]*$")
    private String prosessTaskStatus;

    @JsonProperty(value = "nesteKjoeretidspunkt", required = true)
    @NotNull
    @Schema(description = "Kjøretidspunkt for restart av prosessen")
    private LocalDateTime nesteKjoeretidspunkt;

    public ProsessTaskRestartResultatDto() { // NOSONAR Input-dto, ingen behov for initialisering
    }

    public Long getProsessTaskId() {
        return prosessTaskId;
    }

    public void setProsessTaskId(Long prosessTaskId) {
        this.prosessTaskId = prosessTaskId;
    }

    public String getProsessTaskStatus() {
        return prosessTaskStatus;
    }

    public void setProsessTaskStatus(String prosessTaskStatus) {
        this.prosessTaskStatus = prosessTaskStatus;
    }

    public LocalDateTime getNesteKjoeretidspunkt() {
        return nesteKjoeretidspunkt;
    }

    public void setNesteKjoeretidspunkt(LocalDateTime nesteKjoeretidspunkt) {
        this.nesteKjoeretidspunkt = nesteKjoeretidspunkt;
    }
}
