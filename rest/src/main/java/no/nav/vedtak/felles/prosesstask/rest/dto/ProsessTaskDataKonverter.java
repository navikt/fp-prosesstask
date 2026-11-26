package no.nav.vedtak.felles.prosesstask.rest.dto;

import java.util.Properties;
import java.util.Set;

import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;

public class ProsessTaskDataKonverter {

    private static final Set<String> EXTRACT_PARAMS = Set.of("behandlingid", "behandlinguuid", "fagsakid", "saksnummer");

    private ProsessTaskDataKonverter() {
    }

    public static ProsessTaskDataDto tilProsessTaskDataDto(ProsessTaskData data) {
        return new ProsessTaskDataDto(
            data.getId(),
            data.taskType().value(),
            data.getNesteKjøringEtter(),
            data.getGruppe(),
            data.getSekvens(),
            data.getStatus().getDbKode(),
            data.getOpprettetTid(),
            data.getSistKjørt(),
            data.getSisteFeilKode(),
            filtrerProperties(data.getProperties()));
    }

    public static FeiletProsessTaskDataDto tilFeiletProsessTaskDataDto(ProsessTaskData data) {
        return new FeiletProsessTaskDataDto(
            data.getSisteKjøringServerProsess(),
            data.getSisteFeil(),
            data.getAntallFeiledeForsøk(),
            tilProsessTaskDataDto(data));
    }

    private static Properties filtrerProperties(Properties input) {
        var output = new Properties();
        input.stringPropertyNames().stream()
                .filter(p -> EXTRACT_PARAMS.contains(p.toLowerCase()))
                .forEach(p -> output.setProperty(p, input.getProperty(p)));
        return output;
    }

}
