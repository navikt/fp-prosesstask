package no.nav.vedtak.felles.prosesstask.rest.dto;

import java.util.ArrayList;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@JsonAutoDetect(getterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE, fieldVisibility = Visibility.ANY)
@Schema(description = "Resultatet av asynkron-restart av feilede prosesstasks")
public class ProsessTaskRetryAllResultatDto {

    @JsonInclude(value = Include.ALWAYS)
    @JsonProperty(value="prosessTaskIds")
    @Schema(description = "Prosesstasks som restartes")
    @Valid
    private List<@NotNull Long> prosessTaskIds = new ArrayList<>();

    public ProsessTaskRetryAllResultatDto() {
        // Jackson
    }

    public List<Long> getProsessTaskIds() {
        if (this.prosessTaskIds == null) {
            prosessTaskIds = new ArrayList<>();
        }
        return prosessTaskIds;
    }

    public void setProsessTaskIds(List<Long> prosessTaskIds) {
        this.prosessTaskIds = prosessTaskIds;
    }

    public void addProsessTaskId(Long prosessTaskId) {
        if (this.prosessTaskIds == null) {
            prosessTaskIds = new ArrayList<>();
        }
        this.prosessTaskIds.add(prosessTaskId);
    }

}
