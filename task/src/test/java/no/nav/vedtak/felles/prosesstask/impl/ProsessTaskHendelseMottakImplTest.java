package no.nav.vedtak.felles.prosesstask.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Properties;

import javax.inject.Inject;

import org.assertj.core.api.Assertions;
import org.hibernate.exception.JDBCConnectionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import no.nav.vedtak.felles.prosesstask.JpaExtension;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskTjeneste;
import no.nav.vedtak.felles.testutilities.cdi.CdiAwareExtension;
import no.nav.vedtak.log.util.MemoryAppender;

@ExtendWith(CdiAwareExtension.class)
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ProsessTaskHendelseMottakImplTest {

    private static final String HENDELSE_KEY = "ØKONOMI_OPPDRAG_KVITTERING";

    @RegisterExtension
    public static final JpaExtension repoRule = new JpaExtension();
    
    private MemoryAppender logSniffer = MemoryAppender.sniff(TaskManager.class);

    private ProsessTaskTjeneste prosessTaskTjeneste;

    @Mock
    private ProsessTaskRepository repo;

    @Mock
    private ProsessTaskData taskSomVenterØkonomiKvittering;

    @Mock
    private ProsessTaskData taskSomIkkeVenter;
    
    @Inject
    private TaskManagerRepositoryImpl taskManagerRepo;

    @AfterEach
    public void afterEach() {
        var logger = (Logger) LoggerFactory.getLogger(TaskManager.class);
        logger.detachAppender(logSniffer.getName());
    }
    
    @BeforeEach
    public void setUp() throws Exception {
        prosessTaskTjeneste = new ProsessTaskTjenesteImpl(repo);
        when(taskSomVenterØkonomiKvittering.getVentetHendelse()).thenReturn(Optional.of(HENDELSE_KEY));
        when(taskSomIkkeVenter.getVentetHendelse()).thenReturn(Optional.empty());
    }

    @Test
    public void testMottaHendelseHappyDay() {
        // Arrange
        when(taskSomVenterØkonomiKvittering.getStatus()).thenReturn(ProsessTaskStatus.VENTER_SVAR);
        // Act
        prosessTaskTjeneste.mottaHendelse(taskSomVenterØkonomiKvittering, HENDELSE_KEY, new Properties());
        // Assert
        verify(taskSomVenterØkonomiKvittering).setStatus(ProsessTaskStatus.KLAR);
        verify(repo).lagre(taskSomVenterØkonomiKvittering);
    }
    @Test
    public void testMottaHendelseUkjentTask() {
        assertThrows(NullPointerException.class, () -> {
            prosessTaskTjeneste.mottaHendelse(null, HENDELSE_KEY, null);
        });
    }

    @Test
    public void testMottaUventetHendelse() {
        assertThrows(IllegalStateException.class, () -> {
            prosessTaskTjeneste.mottaHendelse(taskSomVenterØkonomiKvittering, "UKJENT", null);
        });
    }

    @Test
    public void testMottaHendelseITaskSomIkkeVenter() {
        assertThrows(IllegalStateException.class, () -> {
            prosessTaskTjeneste.mottaHendelse(taskSomIkkeVenter, HENDELSE_KEY, null);
        });
    }

    @Test
    public void skal_logge_transient_feil_under_polling() throws Exception {
        TaskManager taskManager = new TaskManager(taskManagerRepo, null) {
            @Override
            protected List<IdentRunnable> pollForAvailableTasks() {
                throw new JDBCConnectionException("NOT AVAILABLE!", null);
            }
        };

        taskManager.new PollAvailableTasks().run();

        Assertions.assertThat(logSniffer.search("PT-739415", ch.qos.logback.classic.Level.WARN)).isNotEmpty();

    }

    @Test
    public void skal_logge_annen_feil_under_polling() throws Exception {
        TaskManager taskManager = new TaskManager(taskManagerRepo, null) {
            @Override
            protected List<IdentRunnable> pollForAvailableTasks() {
                throw new RuntimeException("HERE BE DRAGONS!");
            }
        };

        taskManager.new PollAvailableTasks().run();

        Assertions.assertThat(logSniffer.search("PT-996896", ch.qos.logback.classic.Level.WARN)).isNotEmpty();

    }
}
