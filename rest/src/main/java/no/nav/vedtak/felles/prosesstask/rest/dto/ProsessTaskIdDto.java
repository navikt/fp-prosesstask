package no.nav.vedtak.felles.prosesstask.rest.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

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
public class ProsessTaskIdDto {

    @JsonProperty(value = "prosessTaskId", required = true)
    @NotNull
    @Min(0)
    @Max(Long.MAX_VALUE)
    private Long prosessTaskId;

    public ProsessTaskIdDto() { // NOSONAR Input-dto, ingen behov for initialisering
    }

    public ProsessTaskIdDto(Long prosessTaskId) { // NOSONAR Input-dto, ingen behov for initialisering
        this.prosessTaskId = prosessTaskId;
    }

    @Schema(required = true, description = "Prosesstask-id for en eksisterende prosesstask")
    public Long getProsessTaskId() {
        return prosessTaskId;
    }
}
