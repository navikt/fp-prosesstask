package no.nav.vedtak.felles.prosesstask.rest.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Resultatet av asynkron-restart av en eksisterende prosesstask")
public record ProsessTaskRestartResultatDto(@NotNull Long prosessTaskId,
                                            @NotNull @Schema(description = "Nåværende status (KLAR)") @Pattern(regexp = "^[\\p{Alnum}_.\\-]*$") String prosessTaskStatus,
                                            @NotNull @Schema(description = "Kjøretidspunkt for restart av prosessen") LocalDateTime nesteKjoeretidspunkt) {
}
