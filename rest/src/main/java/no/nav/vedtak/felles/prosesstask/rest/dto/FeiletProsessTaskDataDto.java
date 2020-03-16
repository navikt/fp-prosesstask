package no.nav.vedtak.felles.prosesstask.rest.dto;

import java.util.Optional;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import io.swagger.v3.oas.annotations.media.Schema;
import no.nav.vedtak.log.sporingslogg.Sporingsdata;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@JsonAutoDetect(getterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE, fieldVisibility = Visibility.ANY)
@Schema
public class FeiletProsessTaskDataDto implements ProsessTaskDataInfo {

    @JsonProperty(value="sisteKjøringServerProsess")
    @Size(max=100)
    @Pattern(regexp = "^[\\p{Alnum}_.\\-]*$")
    private String sisteKjøringServerProsess;
    
    @JsonProperty(value="sisteFeilTekst")
    @Size(max=100_000)
    @Pattern(regexp = "^[\\p{Alnum}_.\\-]*$")
    private String sisteFeilTekst;
    
    @JsonProperty(value="feiledeForsøk")
    private Integer feiledeForsøk;

    @JsonUnwrapped
    private ProsessTaskDataDto prosessTaskDataDto;

    public FeiletProsessTaskDataDto() {
    }

    public String getSisteKjøringServerProsess() {
        return sisteKjøringServerProsess;
    }

    public void setSisteKjøringServerProsess(String sisteKjøringServerProsess) {
        this.sisteKjøringServerProsess = sisteKjøringServerProsess;
    }

    public String getSisteFeilTekst() {
        return sisteFeilTekst;
    }

    public void setSisteFeilTekst(String sisteFeilTekst) {
        this.sisteFeilTekst = sisteFeilTekst;
    }

    public Integer getFeiledeForsøk() {
        return feiledeForsøk;
    }

    public void setFeiledeForsøk(Integer feiledeForsøk) {
        this.feiledeForsøk = feiledeForsøk;
    }

    public ProsessTaskDataDto getProsessTaskDataDto() {
        return prosessTaskDataDto;
    }

    public void setProsessTaskDataDto(ProsessTaskDataDto prosessTaskDataDto) {
        this.prosessTaskDataDto = prosessTaskDataDto;
    }

    @Override
    public Optional<Sporingsdata> lagSporingsloggData(String action) {
        return prosessTaskDataDto.lagSporingsloggData(action);
    }
}
