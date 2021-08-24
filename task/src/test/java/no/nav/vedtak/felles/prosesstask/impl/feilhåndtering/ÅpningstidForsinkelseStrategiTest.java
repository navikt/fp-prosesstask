package no.nav.vedtak.felles.prosesstask.impl.feilhåndtering;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.assertj.core.api.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ÅpningstidForsinkelseStrategiTest {

    final static int klokkeslettÅpning = 7;
    final static int klokkeslettStenging = 18;

    ÅpningstidForsinkelseStrategi strategi = new ÅpningstidForsinkelseStrategi();

    @Test
    public void utenforÅpningsTidTest() {
        LocalDateTime localDateTime = LocalDateTime.of(2017, 6, 14, 5, 5, 5);
        LocalDateTime expected = LocalDateTime.of(2017, 6, 14, klokkeslettÅpning, 5, 5);
        int i = strategi.sekunderTilNesteForsøk(localDateTime, 120, klokkeslettÅpning, klokkeslettStenging);
        long diff = expected.toEpochSecond(ZoneOffset.UTC) - localDateTime.toEpochSecond(ZoneOffset.UTC) + 120;
        Assertions.assertEquals(i, diff);
    }

    @Test
    public void innenforÅpningsTidTest() {
        LocalDateTime localDateTime = LocalDateTime.of(2017, 6, 14, 10, 5, 5);
        int i = strategi.sekunderTilNesteForsøk(localDateTime, 120, klokkeslettÅpning, klokkeslettStenging);
        Assertions.assertTrue(i == 120);
    }

    @Test
    public void helgenÅpningsTidTest() {
        LocalDateTime localDateTime = LocalDateTime.of(2017, 6, 17, 10, 5, 5);
        LocalDateTime expected = LocalDateTime.of(2017, 6, 19, klokkeslettÅpning, 5, 5);
        int i = strategi.sekunderTilNesteForsøk(localDateTime, 120, klokkeslettÅpning, klokkeslettStenging);
        long diff = expected.toEpochSecond(ZoneOffset.UTC) - localDateTime.toEpochSecond(ZoneOffset.UTC) + 120;
        Assertions.assertEquals(i, diff);
    }
}
