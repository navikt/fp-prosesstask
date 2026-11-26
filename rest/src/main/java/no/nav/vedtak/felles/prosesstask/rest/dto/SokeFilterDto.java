package no.nav.vedtak.felles.prosesstask.rest.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema
public record SokeFilterDto(
    @Schema(description = "Søketekst") @NotNull @Valid @Size(min = 5, max = 200) @Pattern(regexp = "^[\\p{Alnum}_.=\\-]*$") String tekst,
    @Schema(description = "Søker etter prosesstask opprettet fra og med denne datoen") @Valid LocalDate opprettetFraOgMed,
    @Schema(description = "Søker etter prosesstask opprettet til og med denne datoen") @Valid LocalDate opprettetTilOgMed) {

    public SokeFilterDto {
        opprettetFraOgMed = opprettetFraOgMed != null ? opprettetFraOgMed : LocalDate.now().minusMonths(12);
        opprettetTilOgMed = opprettetTilOgMed != null ? opprettetTilOgMed : LocalDate.now();
    }
}
