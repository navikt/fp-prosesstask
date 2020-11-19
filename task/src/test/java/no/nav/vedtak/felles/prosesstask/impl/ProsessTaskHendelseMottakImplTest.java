package no.nav.vedtak.felles.prosesstask.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

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
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskHendelse;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;
import no.nav.vedtak.felles.prosesstask.impl.TaskManager.PollAvailableTasks;
import no.nav.vedtak.felles.testutilities.cdi.CdiAwareExtension;
import no.nav.vedtak.log.util.MemoryAppender;

@ExtendWith(CdiAwareExtension.class)
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ProsessTaskHendelseMottakImplTest {

    @RegisterExtension
    public static final JpaExtension repoRule = new JpaExtension();
    
    private MemoryAppender logSniffer = MemoryAppender.sniff(TaskManager.class);

    private static final Long UKJENT_TASK_ID = 999L;
    private static final Long TASK_ID = 111L;
    private static final Long TASK_ID_SOM_IKKE_VENTER = 666L;
    private ProsessTaskHendelseMottakImpl prosessTaskHendelseMottak;

    @Mock
    private ProsessTaskRepositoryImpl repo;

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
        prosessTaskHendelseMottak = new ProsessTaskHendelseMottakImpl(repo);
        when(taskSomVenterØkonomiKvittering.getHendelse()).thenReturn(Optional.of(ProsessTaskHendelse.ØKONOMI_OPPDRAG_KVITTERING));
        when(taskSomIkkeVenter.getHendelse()).thenReturn(Optional.empty());
    }

    @Test
    public void testMottaHendelseHappyDay() {
        // Arrange
        when(taskSomVenterØkonomiKvittering.getStatus()).thenReturn(ProsessTaskStatus.VENTER_SVAR);
        when(repo.finn(TASK_ID)).thenReturn(taskSomVenterØkonomiKvittering);
        // Act
        prosessTaskHendelseMottak.mottaHendelse(TASK_ID, ProsessTaskHendelse.ØKONOMI_OPPDRAG_KVITTERING);
        // Assert
        verify(taskSomVenterØkonomiKvittering).setStatus(ProsessTaskStatus.KLAR);
        verify(repo).lagre(taskSomVenterØkonomiKvittering);
    }

    public void testMottaHendelseUkjentTask() {

        assertThrows(IllegalStateException.class, () -> {
            // Arrange
            when(repo.finn(UKJENT_TASK_ID)).thenReturn(null);
            // Act
            prosessTaskHendelseMottak.mottaHendelse(UKJENT_TASK_ID, ProsessTaskHendelse.ØKONOMI_OPPDRAG_KVITTERING);
            // Assert
        });
    }

    public void testMottaUventetHendelse() {
        assertThrows(IllegalStateException.class, () -> {
            // Arrange
            when(repo.finn(TASK_ID)).thenReturn(taskSomVenterØkonomiKvittering);
            // Act
            prosessTaskHendelseMottak.mottaHendelse(TASK_ID, ProsessTaskHendelse.UKJENT_HENDELSE);
            // Assert
        });
    }

    public void testMottaHendelseITaskSomIkkeVenter() {
        assertThrows(IllegalStateException.class, () -> {
            // Arrange
            when(repo.finn(TASK_ID_SOM_IKKE_VENTER)).thenReturn(taskSomIkkeVenter);
            // Act
            prosessTaskHendelseMottak.mottaHendelse(TASK_ID_SOM_IKKE_VENTER, ProsessTaskHendelse.ØKONOMI_OPPDRAG_KVITTERING);
            // Assert
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
