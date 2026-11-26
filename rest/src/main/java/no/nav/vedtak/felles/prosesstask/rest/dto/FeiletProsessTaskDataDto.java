package no.nav.vedtak.felles.prosesstask.rest.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema
public record FeiletProsessTaskDataDto(@Size(max = 100) @Pattern(regexp = "^[\\p{Alnum}_.\\-]*$") String sisteKjøringServerProsess,
                                       @Size(max = 100_000) @Pattern(regexp = "^[\\p{Alnum}_.\\-]*$") String sisteFeilTekst,
                                       Integer feiledeForsøk,
                                       @JsonUnwrapped ProsessTaskDataDto prosessTaskDataDto) {
}
