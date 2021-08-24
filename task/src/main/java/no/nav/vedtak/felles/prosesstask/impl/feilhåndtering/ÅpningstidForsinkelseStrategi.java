package no.nav.vedtak.felles.prosesstask.impl.feilhåndtering;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import no.nav.vedtak.felles.prosesstask.impl.ProsessTaskType;
import no.nav.vedtak.felles.prosesstask.spi.ForsinkelseStrategi;
import no.nav.vedtak.felles.prosesstask.spi.ProsessTaskFeilHåndteringParametere;

public class ÅpningstidForsinkelseStrategi implements ForsinkelseStrategi {

    private static final int MINIMUM_FORSINKELSE_SEKUNDER = 120;

    @Override
    public int sekunderTilNesteForsøk(ProsessTaskType taskType, int runde, ProsessTaskFeilHåndteringParametere feilhåndteringAlgoritme) {
        if (runde >= taskType.getMaksForsøk()) throw new IllegalStateException("Manglende limitsjekk");
        var sekunderTilNeste = Math.max(taskType.getSekunderFørNesteForsøk(), MINIMUM_FORSINKELSE_SEKUNDER);
        return sekunderTilNesteForsøk(LocalDateTime.now(), sekunderTilNeste,
                feilhåndteringAlgoritme.getInputVariabel1(), feilhåndteringAlgoritme.getInputVariabel2());
    }

    int sekunderTilNesteForsøk(LocalDateTime now, int sekunderTilNeste, int klokkeslettÅpning, int klokkeslettStenging) {
        LocalDateTime forsinket = now.plusSeconds(sekunderTilNeste);
        if (forsinket.getHour() < klokkeslettÅpning) {
            forsinket = forsinket.withHour(klokkeslettÅpning);
        } else if (forsinket.getHour() >= klokkeslettStenging) {
            forsinket = forsinket.withHour(klokkeslettÅpning).plusDays(1);
        }
        if (forsinket.getDayOfWeek().getValue() > DayOfWeek.FRIDAY.getValue()) {
            forsinket = forsinket.withHour(klokkeslettÅpning).plusDays(1L + DayOfWeek.SUNDAY.getValue() - forsinket.getDayOfWeek().getValue());
        }
        return (int) ChronoUnit.SECONDS.between(now, forsinket);
    }
}
