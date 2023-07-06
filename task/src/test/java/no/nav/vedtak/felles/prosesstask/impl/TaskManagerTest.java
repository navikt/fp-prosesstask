package no.nav.vedtak.felles.prosesstask.impl;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import no.nav.vedtak.felles.prosesstask.JpaExtension;
import no.nav.vedtak.felles.testutilities.cdi.CdiAwareExtension;

@ExtendWith(CdiAwareExtension.class)
class TaskManagerTest {

    @RegisterExtension
    public static final JpaExtension repoRule = new JpaExtension();

    @Inject
    private TaskManagerRepositoryImpl taskManagerRepository;

    @Test
    void sjekk_startup() throws Exception {
        taskManagerRepository.verifyStartup();
    }
}
