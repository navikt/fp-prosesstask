package no.nav.vedtak.felles.prosesstask.impl.feilhåndtering;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import no.nav.vedtak.felles.prosesstask.api.ProsessTask;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskHandler;
import no.nav.vedtak.felles.prosesstask.api.TaskType;
import no.nav.vedtak.felles.prosesstask.impl.ProsessTaskHandlerRef;
import no.nav.vedtak.felles.testutilities.cdi.CdiAwareExtension;

@ExtendWith(CdiAwareExtension.class)
public class DefaultForsinkelseStrategiTest {

    private ProsessTaskHandlerRef handler;

    @BeforeEach
    public void setup() {
        handler = ProsessTaskHandlerRef.lookup(TaskType.forProsessTask(ForsinkelseDummyProsessTask.class));
    }

    @Test
    public void forsøk_0_gir_0s() {
        var retryPolicy = handler.retryPolicy();
        assertThat(retryPolicy.retryTask(0, null)).isTrue();
        assertThat(retryPolicy.secondsToNextRun(0)).isZero();
    }

    @Test
    public void forsøk_1_gir_30s() {
        var retryPolicy = handler.retryPolicy();
        assertThat(retryPolicy.retryTask(1, null)).isTrue();
        assertThat(retryPolicy.secondsToNextRun(1)).isEqualTo(30);
    }

    @Test
    public void forsøk_2_gir_60s() {
        var retryPolicy = handler.retryPolicy();
        assertThat(retryPolicy.retryTask(2, null)).isTrue();
        assertThat(retryPolicy.secondsToNextRun(2)).isEqualTo(60);
    }

    @Test
    public void forsøk_3_gir_120s() {
        var retryPolicy = handler.retryPolicy();
        assertThat(retryPolicy.retryTask(3, null)).isTrue();
        assertThat(retryPolicy.secondsToNextRun(3)).isEqualTo(120);
    }

    @Test
    public void forsøk_4_gir_no_retry() {
        var retryPolicy = handler.retryPolicy();
        assertThat(retryPolicy.retryTask(4, null)).isFalse();
    }

    @ProsessTask(value = "local.dummy.task", maxFailedRuns = 4, firstDelay = 30, thenDelay = 60)
    static class ForsinkelseDummyProsessTask implements ProsessTaskHandler {

        @Override
        public void doTask(ProsessTaskData data) {

        }
    }

}
