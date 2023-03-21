package no.nav.vedtak.felles.prosesstask.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import no.nav.vedtak.exception.TekniskException;
import no.nav.vedtak.felles.prosesstask.api.ProsessTask;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskDataBuilder;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskGruppe;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskHandler;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskTjeneste;
import no.nav.vedtak.felles.prosesstask.api.TaskType;
import no.nav.vedtak.felles.testutilities.cdi.CdiAwareExtension;

@ExtendWith(CdiAwareExtension.class)
class ProsessTaskTjenesteImplTest {

    private static final String REQUIRED_PROPERTY = "requiredId";

    private static final String TASK_TYPE_NAME_OPPR = "random-string-opprett";

    private ProsessTaskRepository prosessTaskRepositoryMock;

    private ProsessTaskTjeneste prosessTaskTjeneste;

    @ProsessTask(TASK_TYPE_NAME_OPPR)
    private static class DummyHandlerOpprett implements ProsessTaskHandler {
        @Override
        public void doTask(ProsessTaskData prosessTaskData) {
            //
        }
        @Override
        public Set<String> requiredProperties() {
            return Set.of(REQUIRED_PROPERTY);
        }
    }

    @BeforeEach
    public void setUp()  {
        prosessTaskRepositoryMock = mock(ProsessTaskRepository.class);
        prosessTaskTjeneste = new ProsessTaskTjenesteImpl(prosessTaskRepositoryMock);
    }

    @Test
    void skal_opprette_task() {
        var ptd = ProsessTaskDataBuilder.forProsessTask(DummyHandlerOpprett.class)
                .medProperty(REQUIRED_PROPERTY, "Verdi")
                .build();

        when(prosessTaskRepositoryMock.lagre(any(ProsessTaskData.class))).thenReturn("gruppe-id");

        prosessTaskTjeneste.lagreValidert(ptd);

        var argumentCaptor = ArgumentCaptor.forClass(ProsessTaskGruppe.class);
        verify(prosessTaskRepositoryMock).lagre(argumentCaptor.capture());

        var dataTilPersistering = argumentCaptor.getValue();

        verify(prosessTaskRepositoryMock, times(1)).lagre(any(ProsessTaskGruppe.class));
        assertThat(dataTilPersistering.getTasks().get(0).task().taskType()).isEqualTo(TaskType.forProsessTask(DummyHandlerOpprett.class));
    }

    @Test
    void skal_feile_ved_manglende_property() {
        var ptd = ProsessTaskDataBuilder.forProsessTask(DummyHandlerOpprett.class)
            .medProperty("ikkepaakrevd", "Verdi")
            .build();
        var message = Assertions.assertThrows(TekniskException.class, () -> {
            prosessTaskTjeneste.lagreValidert(ptd);
        });
        verify(prosessTaskRepositoryMock, never()).lagre(any(ProsessTaskData.class));
        assertThat(message).hasMessageContaining(ProsessTaskData.MANGLER_PROPS).hasMessageContaining(REQUIRED_PROPERTY);
    }

    @Test
    void skal_feile_ved_manglende_implementasjon() {
        var ptd = ProsessTaskDataBuilder.forTaskType(new TaskType("abc"))
            .medProperty("ikkepaakrevd", "Verdi")
            .build();

        var message = Assertions.assertThrows(TekniskException.class, () -> {
            prosessTaskTjeneste.lagreValidert(ptd);
        });
        verify(prosessTaskRepositoryMock, never()).lagre(any(ProsessTaskData.class));
        assertThat(message).hasMessageContaining(ProsessTaskTjenesteImpl.MANGLER_IMPL).hasMessageContaining("abc");
    }

}
