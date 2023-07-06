package no.nav.vedtak.felles.prosesstask.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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
public class ProsessTaskRestartInputDto {

    @JsonProperty(value = "prosessTaskId", required = true)
    @Min(0)
    @Max(Long.MAX_VALUE)
    @NotNull
    private Long prosessTaskId;

    @JsonAlias(value = { "naaVaarendeStatus" })
    @JsonProperty(value = "inneværendeStatus", required = true)
    @Schema(description = "Nåværende status. Angis hviss prosessen som skal restartes har en annen status enn KLAR.", allowableValues = "VENTER_SVAR, SUSPENDERT, FEILET")
    @Size(max = 15)
    @Pattern(regexp = "VENTER_SVAR|FEILET|SUSPENDERT")
    private String naaVaaerendeStatus;

    public ProsessTaskRestartInputDto() {
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
