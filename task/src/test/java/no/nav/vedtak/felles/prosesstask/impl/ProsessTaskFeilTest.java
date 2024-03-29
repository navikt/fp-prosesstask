package no.nav.vedtak.felles.prosesstask.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskFeil;
import no.nav.vedtak.felles.prosesstask.api.TaskType;

class ProsessTaskFeilTest {

    @Test
    void skal_skrive_feil_til_json() throws Exception {
        // Arrange
        Exception exception = lagEnFeilFraEnMetode();
        Feil feil = TaskManagerFeil.kunneIkkeProsessereTaskVilPrøveIgjenEnkelFeilmelding(1L, new TaskType("hello.world"), 1, LocalDateTime.now(), exception);
        ProsessTaskData prosessTaskData = ProsessTaskData.forTaskType(new TaskType("hello.world"));
        prosessTaskData.setId(1L);
        
        // Act
        ProsessTaskFeil prosessTaskFeil = new ProsessTaskFeil(prosessTaskData, feil);
        
        assertThat(prosessTaskFeil.getStackTrace()).isNotNull().contains("IllegalArgumentException").contains("lagEnFeilFraEnMetode");
        assertThat(prosessTaskFeil.getFeilkode()).isEqualTo("PT-415564");
        assertThat(prosessTaskFeil.getFeilmelding()).contains("Kunne ikke prosessere task, id=1");
        
        String json = prosessTaskFeil.writeValueAsString();
        
        System.out.println(json);  
    }

    private Exception lagEnFeilFraEnMetode() {
        return new IllegalArgumentException("dette er en feil");
    }
}
