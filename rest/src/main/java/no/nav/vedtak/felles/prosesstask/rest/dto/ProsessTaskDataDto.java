package no.nav.vedtak.felles.prosesstask.rest.dto;

import java.time.LocalDateTime;
import java.util.Properties;

import jakarta.validation.Valid;
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
public class ProsessTaskDataDto {

    @JsonProperty(value = "id", required = true)
    private Long id;

    @JsonProperty(value = "taskType", required = true)
    @Size(max = 200)
    @Pattern(regexp = "^[\\p{Alnum}æøåÆØÅ_.\\-]*$")
    private String taskType;

    @JsonProperty(value = "nesteKjøringEtter")
    @Valid
    private LocalDateTime nesteKjøringEtter;

    @JsonProperty(value = "gruppe", required = true)
    @Size(max = 200)
    @Pattern(regexp = "^[\\p{Alnum}_.\\-]*$")
    private String gruppe;

    @JsonProperty(value = "sekvens", required = true)
    @Size(max = 200)
    @Pattern(regexp = "^[\\p{Alnum}_.\\-]*$")
    private String sekvens;

    @JsonProperty(value = "status", required = true)
    @Size(max = 20)
    @Pattern(regexp = "^[\\p{Alnum}_.\\-]*$")
    private String status;

    @JsonProperty(value = "sistKjørt")
    @Valid
    private LocalDateTime sistKjørt;

    @JsonProperty(value = "sisteFeilKode")
    @Size(max = 200)
    @Pattern(regexp = "^[\\p{Alnum}_.\\-]*$")
    private String sisteFeilKode;

    @JsonProperty(value = "taskParametre")
    @Size(max = 20)
    private Properties taskParametre = new Properties();

    public ProsessTaskDataDto() {
        // Jackson
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public LocalDateTime getNesteKjøringEtter() {
        return nesteKjøringEtter;
    }

    public void setNesteKjøringEtter(LocalDateTime nesteKjøringEtter) {
        this.nesteKjøringEtter = nesteKjøringEtter;
    }

    public String getGruppe() {
        return gruppe;
    }

    public void setGruppe(String gruppe) {
        this.gruppe = gruppe;
    }

    public String getSekvens() {
        return sekvens;
    }

    public void setSekvens(String sekvens) {
        this.sekvens = sekvens;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getSistKjørt() {
        return sistKjørt;
    }

    public void setSistKjørt(LocalDateTime sistKjørt) {
        this.sistKjørt = sistKjørt;
    }

    public String getSisteFeilKode() {
        return sisteFeilKode;
    }

    public void setSisteFeilKode(String sisteFeilKode) {
        this.sisteFeilKode = sisteFeilKode;
    }

    public Properties getTaskParametre() {
        return taskParametre;
    }

    public void setTaskParametre(Properties taskParametre) {
        this.taskParametre = taskParametre;
    }

}
