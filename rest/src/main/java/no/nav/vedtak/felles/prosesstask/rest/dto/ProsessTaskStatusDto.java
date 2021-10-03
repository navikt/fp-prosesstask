package no.nav.vedtak.felles.prosesstask.rest.dto;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@JsonAutoDetect(getterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE, fieldVisibility = Visibility.ANY)
@Schema
public class ProsessTaskStatusDto {

    @JsonProperty(value = "prosessTaskStatusName", required = true)
    @NotNull
    @Schema(description = "Status som slås opp.", allowableValues = "FEILET, VENTER_SVAR, SUSPENDERT, VETO, KLAR")
    @Pattern(regexp = "FEILET|VENTER_SVAR|SUSPENDERT|VETO|KLAR")
    @Size(min = 1, max = 15)
    private String prosessTaskStatusName;

    public ProsessTaskStatusDto() {
    }

    public ProsessTaskStatusDto(String prosessTaskStatusName) {
        this.prosessTaskStatusName = prosessTaskStatusName;
    }

    @Schema(required = true, description = "Navn på prosesstask-status")
    public String getProsessTaskStatusName() {
        return prosessTaskStatusName;
    }
}
