package no.nav.vedtak.felles.prosesstask.rest.dto;

import java.util.Properties;
import java.util.Set;

import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;

public class ProsessTaskDataKonverter {

    private static final Set<String> EXTRACT_PARAMS = Set.of("behandlingid", "behandlinguuid", "fagsakid", "saksnummer");

    private ProsessTaskDataKonverter() {
    }

    public static ProsessTaskDataDto tilProsessTaskDataDto(ProsessTaskData data) {
        var dto = new ProsessTaskDataDto();

        dto.setId(data.getId());
        dto.setTaskType(data.getTaskType());
        dto.setNesteKjøringEtter(data.getNesteKjøringEtter());
        dto.setGruppe(data.getGruppe());
        dto.setSekvens(data.getSekvens());
        dto.setStatus(data.getStatus().getDbKode());
        dto.setSistKjørt(data.getSistKjørt());
        dto.setSisteFeilKode(data.getSisteFeilKode());
        dto.setTaskParametre(filtrerProperties(data.getProperties()));

        return dto;
    }

    public static FeiletProsessTaskDataDto tilFeiletProsessTaskDataDto(ProsessTaskData data) {
        var dto = new FeiletProsessTaskDataDto();

        dto.setProsessTaskDataDto(tilProsessTaskDataDto(data));

        dto.setFeiledeForsøk(data.getAntallFeiledeForsøk());
        dto.setSisteFeilTekst(data.getSisteFeil());
        dto.setSisteKjøringServerProsess(data.getSisteKjøringServerProsess());

        return dto;
    }

    private static Properties filtrerProperties(Properties input) {
        var output = new Properties();
        input.stringPropertyNames().stream()
                .filter(p -> EXTRACT_PARAMS.contains(p.toLowerCase()))
                .forEach(p -> output.setProperty(p, input.getProperty(p)));
        return output;
    }

}
