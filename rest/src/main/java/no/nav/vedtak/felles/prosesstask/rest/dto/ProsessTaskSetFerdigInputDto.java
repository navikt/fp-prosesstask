package no.nav.vedtak.felles.prosesstask.rest.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@JsonAutoDetect(getterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE, fieldVisibility = Visibility.ANY)
@Schema(description = "Informasjon for restart av en eksisterende prosesstask")
public class ProsessTaskSetFerdigInputDto {

    @JsonProperty(value = "prosessTaskId", required = true)
    @Min(0)
    @Max(Long.MAX_VALUE)
    @NotNull
    private Long prosessTaskId;

    @JsonAlias(value = { "naaVaarendeStatus" })
    @JsonProperty(value = "inneværendeStatus", required = true)
    @Schema(description = "Nåværende status. Angis hviss prosessen som skal restartes har en annen status enn KLAR.", allowableValues = "FEILET, VENTER_SVAR, SUSPENDERT, VETO, KLAR")
    @Size(max = 15)
    @Pattern(regexp = "FEILET|VENTER_SVAR|SUSPENDERT|VETO|KLAR")
    private String naaVaaerendeStatus;

    public ProsessTaskSetFerdigInputDto() {
        // Jackson
    }

    public Long getProsessTaskId() {
        return prosessTaskId;
    }

    public void setProsessTaskId(Long prosessTaskId) {
        this.prosessTaskId = prosessTaskId;
    }

    public String getNaaVaaerendeStatus() {
        return naaVaaerendeStatus;
    }

    public void setNaaVaaerendeStatus(String naaVaaerendeStatus) {
        this.naaVaaerendeStatus = naaVaaerendeStatus;
    }
}
