package no.nav.vedtak.felles.prosesstask.rest.dto;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProsessTaskOpprettInputDto(
    @NotNull @Size(min = 1, max = 100) @Pattern(regexp = PARAMETRE_PATTERN) String taskType,
    @NotNull @Size(max = 100) Map<
        @NotBlank @Valid @Size(max = 100) @Pattern(regexp = PARAMETRE_PATTERN) String,
        @NotNull @Valid @Size(max = 1000) @Pattern(regexp = PARAMETRE_PATTERN) String
        > taskParametre) {

    private static final String PARAMETRE_PATTERN = "^[\\p{Alnum}æøåÆØÅ_.\\-]*$";

    public ProsessTaskOpprettInputDto {
        taskParametre = taskParametre != null ? taskParametre : Map.of();
    }
}
