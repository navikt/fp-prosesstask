package no.nav.vedtak.felles.prosesstask.rest.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
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
public class SokeFilterDto {

    @JsonProperty(value = "prosessTaskStatuser")
    @Size(max = 10)
    @Valid
    private List<@NotNull ProsessTaskStatusDto> prosessTaskStatuser = new ArrayList<>();
    
    @JsonProperty(value = "sisteKjoeretidspunktFraOgMed")
    @Valid
    private LocalDateTime sisteKjoeretidspunktFraOgMed = LocalDateTime.now().minusHours(24);
    
    @JsonProperty(value = "sisteKjoeretidspunktTilOgMed")
    @Valid
    private LocalDateTime sisteKjoeretidspunktTilOgMed = LocalDateTime.now();

    public SokeFilterDto() {
    }

    @Schema(description = "Angi liste over prosesstask-statuser som skal søkes på, blant KLAR, FERDIG, VENTER_SVAR, SUSPENDERT, eller FEILET")
    public List<ProsessTaskStatusDto> getProsessTaskStatuser() {
        return prosessTaskStatuser;
    }

    public void setProsessTaskStatuser(List<ProsessTaskStatusDto> prosessTaskStatuser) {
        this.prosessTaskStatuser = prosessTaskStatuser;
    }

    @Schema(description = "Søker etter prosesstask med siste kjøring fra og med dette tidspunktet")
    public LocalDateTime getSisteKjoeretidspunktFraOgMed() {
        return sisteKjoeretidspunktFraOgMed;
    }

    public void setSisteKjoeretidspunktFraOgMed(LocalDateTime sisteKjoeretidspunktFraOgMed) {
        this.sisteKjoeretidspunktFraOgMed = sisteKjoeretidspunktFraOgMed;
    }

    @Schema(description = "Søker etter prosesstask med siste kjøring til og med dette tidspunktet")
    public LocalDateTime getSisteKjoeretidspunktTilOgMed() {
        return sisteKjoeretidspunktTilOgMed;
    }

    public void setSisteKjoeretidspunktTilOgMed(LocalDateTime sisteKjoeretidspunktTilOgMed) {
        this.sisteKjoeretidspunktTilOgMed = sisteKjoeretidspunktTilOgMed;
    }

}
