package no.nav.vedtak.felles.prosesstask.rest.dto;

import java.util.Properties;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@JsonAutoDetect(getterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE, fieldVisibility = Visibility.ANY)
public class ProsessTaskOpprettInputDto {

    @JsonProperty(value="taskType", required=true)
    @NotNull
    @Size(min = 1, max = 100)
    @Pattern(regexp = "^[\\p{Alnum}_.\\-]*$")
    private String taskType;

    @JsonProperty(value="taskParametre", required = true)
    @NotNull
    @Size(max = 100)
    @Valid
    private Properties taskParametre = new Properties();

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public Properties getTaskParametre() {
        return taskParametre;
    }

    public void setTaskParametre(Properties taskParametre) {
        this.taskParametre = taskParametre;
    }

}
