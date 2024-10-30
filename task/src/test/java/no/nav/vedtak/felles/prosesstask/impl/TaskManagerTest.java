package no.nav.vedtak.felles.prosesstask.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import jakarta.inject.Inject;
import no.nav.vedtak.felles.prosesstask.JpaPostgresTestcontainerExtension;
import no.nav.vedtak.felles.testutilities.cdi.CdiAwareExtension;

@ExtendWith(CdiAwareExtension.class)
class TaskManagerTest {

    @RegisterExtension
    public static final JpaPostgresTestcontainerExtension repoRule = new JpaPostgresTestcontainerExtension();

    @Inject
    private TaskManagerRepositoryImpl taskManagerRepository;

    @Test
    void sjekk_startup() throws Exception {
        taskManagerRepository.verifyStartup();
    }
}
