package no.nav.vedtak.felles.prosesstask.impl.feilhåndtering;

import static no.nav.vedtak.felles.prosesstask.impl.feilhåndtering.ÅpningstidForsinkelseStrategiTest.klokkeslettÅpning;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import no.nav.vedtak.felles.prosesstask.impl.ProsessTaskType;

public class DefaultForsinkelseStrategiTest {

    private static final ProsessTaskType TASK_TYPE = new ProsessTaskType("test", "Test", 4, 15);

    BackoffFeilhåndteringStrategi strategi = new BackoffFeilhåndteringStrategi();

    @Test
    public void forsøk_0_gir_0s() {
        int i = strategi.sekunderTilNesteForsøk(TASK_TYPE, 0, null);
        assertThat(i).isZero();
    }

    @Test
    public void forsøk_1_gir_15s() {
        int i = strategi.sekunderTilNesteForsøk(TASK_TYPE, 1, null);
        assertThat(i).isEqualTo(15);
    }

    @Test
    public void forsøk_2_gir_30s() {
        int i = strategi.sekunderTilNesteForsøk(TASK_TYPE, 2, null);
        assertThat(i).isEqualTo(30);
    }

    @Test
    public void forsøk_3_gir_60s() {
        int i = strategi.sekunderTilNesteForsøk(TASK_TYPE, 3, null);
        assertThat(i).isEqualTo(60);
    }


}
