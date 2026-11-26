package no.nav.vedtak.felles.prosesstask.rest.dto;

import java.time.LocalDateTime;
import java.util.Properties;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema
public record ProsessTaskDataDto(Long id,
                                 @Size(max = 200) @Pattern(regexp = "^[\\p{Alnum}æøåÆØÅ_.\\-]*$") String taskType,
                                 @Valid LocalDateTime nesteKjøringEtter,
                                 @Size(max = 200) @Pattern(regexp = "^[\\p{Alnum}_.\\-]*$") String gruppe,
                                 @Size(max = 200) @Pattern(regexp = "^[\\p{Alnum}_.\\-]*$") String sekvens,
                                 @Size(max = 20) @Pattern(regexp = "^[\\p{Alnum}_.\\-]*$") String status,
                                 @Valid LocalDateTime opprettetTid,
                                 @Valid LocalDateTime sistKjørt,
                                 @Size(max = 200) @Pattern(regexp = "^[\\p{Alnum}_.\\-]*$") String sisteFeilKode,
                                 @Size(max = 20) Properties taskParametre) {

    public ProsessTaskDataDto {
        taskParametre = taskParametre != null ? taskParametre : new Properties();
    }
}
