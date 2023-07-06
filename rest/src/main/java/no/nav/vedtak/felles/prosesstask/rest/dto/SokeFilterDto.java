package no.nav.vedtak.felles.prosesstask.rest.dto;

import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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

    @JsonProperty(value = "tekst")
    @NotNull
    @Valid
    @Size(min = 5, max=200)
    @Pattern(regexp = "^[\\p{Alnum}_.=\\-]*$")
    private String tekst;

    @JsonProperty(value = "sisteKjoeretidspunktFraOgMed")
    @Valid
    private LocalDate opprettetFraOgMed = LocalDate.now().minusMonths(12);

    @JsonProperty(value = "sisteKjoeretidspunktTilOgMed")
    @Valid
    private LocalDate opprettetTilOgMed = LocalDate.now();

    public SokeFilterDto() {
        // Jackson
    }

    @Schema(description = "Søketekst")
    public String getTekst() {
        return tekst;
    }

    public void setTekst(String tekst) {
        this.tekst = tekst;
    }

    @Schema(description = "Søker etter prosesstask med siste kjøring fra og med dette tidspunktet")
    public LocalDate getOpprettetFraOgMed() {
        return opprettetFraOgMed;
    }

    public void setOpprettetFraOgMed(LocalDate opprettetFraOgMed) {
        this.opprettetFraOgMed = opprettetFraOgMed;
    }

    @Schema(description = "Søker etter prosesstask med siste kjøring til og med dette tidspunktet")
    public LocalDate getOpprettetTilOgMed() {
        return opprettetTilOgMed;
    }

    public void setOpprettetTilOgMed(LocalDate opprettetTilOgMed) {
        this.opprettetTilOgMed = opprettetTilOgMed;
    }
}
