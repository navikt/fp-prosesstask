package no.nav.vedtak.felles.prosesstask.rest.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Informasjon for restart av en eksisterende prosesstask")
public class ProsessTaskRestartInputDto {

    @Min(0)
    @Max(Long.MAX_VALUE)
    @NotNull
    private Long prosessTaskId;

    @Schema(
        description = "Nåværende status. Angis hviss prosessen som skal restartes har en annen status enn KLAR.",
        allowableValues = "VENTER_SVAR, SUSPENDERT, FEILET"
    )
    @Size(max = 15)
    @Pattern(regexp = "VENTER_SVAR|FEILET|SUSPENDERT")
    private String naaVaaerendeStatus;

    public ProsessTaskRestartInputDto() { // NOSONAR Input-dto, ingen behov for initialisering
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
