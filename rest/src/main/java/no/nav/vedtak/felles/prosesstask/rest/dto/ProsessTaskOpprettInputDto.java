package no.nav.vedtak.felles.prosesstask.rest.dto;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(getterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE, fieldVisibility = Visibility.ANY)
public class ProsessTaskOpprettInputDto {

    private static final String PARAMETRE_PATTERN = "^[\\p{Alnum}æøåÆØÅ_.\\-]*$";

    @JsonProperty(value="taskType", required=true)
    @NotNull
    @Size(min = 1, max = 100)
    @Pattern(regexp = PARAMETRE_PATTERN)
    private String taskType;

    @JsonProperty(value = "taskParametre", required = true)
    @NotNull
    @Size(max = 100)
    private Map<
        @NotBlank @Valid @Size(max = 100) @Pattern(regexp = PARAMETRE_PATTERN) String,
        @NotNull @Valid @Size(max = 1000) @Pattern(regexp = PARAMETRE_PATTERN) String
        > taskParametre = new HashMap<>();

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public Map<String, String> getTaskParametre() {
        return taskParametre;
    }

    public void setTaskParametre(Map<String, String> taskParametre) {
        this.taskParametre = taskParametre;
    }

}
