package no.nav.vedtak.felles.prosesstask.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Properties;

import org.assertj.core.api.Assertions;
import org.hibernate.exception.JDBCConnectionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import no.nav.vedtak.felles.prosesstask.JpaOracleTestcontainerExtension;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskTjeneste;
import no.nav.vedtak.felles.testutilities.db.EntityManagerAwareTest;
import no.nav.vedtak.log.util.MemoryAppender;

@ExtendWith(MockitoExtension.class)
@ExtendWith(JpaOracleTestcontainerExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProsessTaskHendelseMottakImplTest extends EntityManagerAwareTest {

    private static final String HENDELSE_KEY = "ØKONOMI_OPPDRAG_KVITTERING";

    private MemoryAppender logSniffer = MemoryAppender.sniff(TaskManager.class);

    private ProsessTaskTjeneste prosessTaskTjeneste;

    @Mock
    private ProsessTaskRepository repo;

    @Mock
    private ProsessTaskData taskSomVenterØkonomiKvittering;

    @Mock
    private ProsessTaskData taskSomIkkeVenter;

    private TaskManagerRepositoryImpl taskManagerRepo;

    @AfterEach
    public void afterEach() {
        var logger = (Logger) LoggerFactory.getLogger(TaskManager.class);
        logger.detachAppender(logSniffer.getName());
    }

    @BeforeEach
    public void setUp() {
        taskManagerRepo = new TaskManagerRepositoryImpl(getEntityManager());
        prosessTaskTjeneste = new ProsessTaskTjenesteImpl(repo);
        when(taskSomVenterØkonomiKvittering.getVentetHendelse()).thenReturn(Optional.of(HENDELSE_KEY));
        when(taskSomIkkeVenter.getVentetHendelse()).thenReturn(Optional.empty());
    }

    @Test
    void testMottaHendelseHappyDay() {
        // Arrange
        when(taskSomVenterØkonomiKvittering.getStatus()).thenReturn(ProsessTaskStatus.VENTER_SVAR);
        // Act
        prosessTaskTjeneste.mottaHendelse(taskSomVenterØkonomiKvittering, HENDELSE_KEY, new Properties());
        // Assert
        verify(taskSomVenterØkonomiKvittering).setStatus(ProsessTaskStatus.KLAR);
        verify(repo).lagre(taskSomVenterØkonomiKvittering);
    }
    @Test
    void testMottaHendelseUkjentTask() {
        assertThrows(NullPointerException.class, () -> {
            prosessTaskTjeneste.mottaHendelse(null, HENDELSE_KEY, null);
        });
    }

    @Test
    void testMottaUventetHendelse() {
        assertThrows(IllegalStateException.class, () -> {
            prosessTaskTjeneste.mottaHendelse(taskSomVenterØkonomiKvittering, "UKJENT", null);
        });
    }

    @Test
    void testMottaHendelseITaskSomIkkeVenter() {
        assertThrows(IllegalStateException.class, () -> {
            prosessTaskTjeneste.mottaHendelse(taskSomIkkeVenter, HENDELSE_KEY, null);
        });
    }

    @Test
    void skal_logge_transient_feil_under_polling() {
        var taskManager = new TaskManager(taskManagerRepo, null) {
            @Override
            protected List<IdentRunnable> pollForAvailableTasks() {
                throw new JDBCConnectionException("NOT AVAILABLE!", null);
            }
        };

        taskManager.new PollAvailableTasks().run();

        Assertions.assertThat(logSniffer.search("PT-739415", ch.qos.logback.classic.Level.WARN)).isNotEmpty();

    }

    @Test
    void skal_logge_annen_feil_under_polling() {
        var taskManager = new TaskManager(taskManagerRepo, null) {
            @Override
            protected List<IdentRunnable> pollForAvailableTasks() {
                throw new RuntimeException("HERE BE DRAGONS!");
            }
        };

        taskManager.new PollAvailableTasks().run();

        Assertions.assertThat(logSniffer.search("PT-996896", ch.qos.logback.classic.Level.WARN)).isNotEmpty();

    }
}
