package no.nav.vedtak.felles.prosesstask.spi;

import no.nav.vedtak.felles.prosesstask.impl.ProsessTaskType;

public interface ForsinkelseStrategi {
    int sekunderTilNesteForsøk(ProsessTaskType taskType, int runde, ProsessTaskFeilHåndteringParametere feilhåndteringAlgoritme);
}
