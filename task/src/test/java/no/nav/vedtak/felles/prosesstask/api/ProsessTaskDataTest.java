package no.nav.vedtak.felles.prosesstask.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ProsessTaskDataTest {

    private static final String ORIGINALTYPE = "ORIGINALTYPE";

    private ProsessTaskData original;

    @BeforeEach
    public void oppsett() {
        original = new ProsessTaskData(ORIGINALTYPE);
    }

    @Test
    public void testVenterPåHendelse() {
        // Arrange
        // Act
        original.venterPåHendelse(ProsessTaskHendelse.ØKONOMI_OPPDRAG_KVITTERING);
        // Assert
        assertThat(original.getStatus()).isEqualTo(ProsessTaskStatus.VENTER_SVAR);
        Optional<ProsessTaskHendelse> venterPå = original.getHendelse();
        assertThat(venterPå.isPresent()).isEqualTo(true);
        assertThat(venterPå.get()).isEqualTo(ProsessTaskHendelse.ØKONOMI_OPPDRAG_KVITTERING);
    }

    @Test
    public void testVenterIkkePåHendelse() {
        // Arrange
        // Act
        // Assert
        Optional<ProsessTaskHendelse> venterPå = original.getHendelse();
        assertThat(venterPå.isPresent()).isEqualTo(false);
    }

}
