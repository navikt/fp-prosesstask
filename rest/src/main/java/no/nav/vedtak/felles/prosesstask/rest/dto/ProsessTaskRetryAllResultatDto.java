package no.nav.vedtak.felles.prosesstask.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resultatet av asynkron-restart av feilede prosesstasks")
public record ProsessTaskRetryAllResultatDto(@Schema(description = "Antall prosesstasks som restartes") int antall) {
}
