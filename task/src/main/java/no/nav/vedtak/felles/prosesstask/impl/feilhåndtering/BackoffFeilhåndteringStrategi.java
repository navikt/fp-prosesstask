package no.nav.vedtak.felles.prosesstask.impl.feilhåndtering;

import no.nav.vedtak.felles.prosesstask.impl.ProsessTaskType;
import no.nav.vedtak.felles.prosesstask.spi.ForsinkelseStrategi;
import no.nav.vedtak.felles.prosesstask.spi.ProsessTaskFeilHåndteringParametere;

public class BackoffFeilhåndteringStrategi implements ForsinkelseStrategi {

    @Override
    public int sekunderTilNesteForsøk(ProsessTaskType taskType, int runde, ProsessTaskFeilHåndteringParametere feilhåndteringAlgoritme) {
        if (runde >= taskType.getMaksForsøk()) throw new IllegalStateException("Manglende limitsjekk");
        if (runde == 0) return 0;
        return taskType.getSekunderFørNesteForsøk() * (int) Math.pow(2, (double) runde - 1);
    }
}
