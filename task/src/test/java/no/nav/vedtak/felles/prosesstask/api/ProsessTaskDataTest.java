package no.nav.vedtak.felles.prosesstask.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProsessTaskDataTest {

    private static final TaskType ORIGINALTYPE = new TaskType("ORIGINALTYPE");

    private static final String HENDELSE_KEY = "ØKONOMI_OPPDRAG_KVITTERING";

    private ProsessTaskData original;

    @BeforeEach
    public void oppsett() {
        original = new ProsessTaskData(ORIGINALTYPE);
    }

    @Test
    void testVenterPåHendelse() {
        // Arrange
        // Act
        original.venterPåHendelse(HENDELSE_KEY);
        // Assert
        assertThat(original.getStatus()).isEqualTo(ProsessTaskStatus.VENTER_SVAR);
        var venterPå = original.getVentetHendelse();
        assertThat(venterPå).isPresent().hasValue(HENDELSE_KEY);
    }

    @Test
    void testVenterIkkePåHendelse() {
        // Arrange
        // Act
        // Assert
        var venterPå = original.getVentetHendelse();
        assertThat(venterPå).isEmpty();
    }

}
