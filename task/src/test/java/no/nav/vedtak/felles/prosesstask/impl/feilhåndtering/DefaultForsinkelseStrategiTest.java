package no.nav.vedtak.felles.prosesstask.impl.feilhåndtering;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskHandler;

public class DefaultForsinkelseStrategiTest {

    @Test
    public void forsøk_0_gir_0s() {
        int i = new ForsinkelseDummyProsessTask().secondsToNextRun(0);
        assertThat(i).isZero();
    }

    @Test
    public void forsøk_1_gir_30s() {
        int i = new ForsinkelseDummyProsessTask().secondsToNextRun(1);
        assertThat(i).isEqualTo(30);
    }

    @Test
    public void forsøk_2_gir_60s() {
        int i = new ForsinkelseDummyProsessTask().secondsToNextRun(2);
        assertThat(i).isEqualTo(60);
    }

    @Test
    public void forsøk_3_gir_90s() {
        int i = new ForsinkelseDummyProsessTask().secondsToNextRun(3);
        assertThat(i).isEqualTo(90);
    }

    static class ForsinkelseDummyProsessTask implements ProsessTaskHandler {

        @Override
        public int maxFailedRuns() {
            return 4;
        }

        @Override
        public void doTask(ProsessTaskData data) {

        }
    }

}
