package no.nav.vedtak.felles.prosesstask.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import no.nav.vedtak.felles.prosesstask.JpaPostgresTestcontainerExtension;
import no.nav.vedtak.felles.testutilities.db.EntityManagerAwareTest;

@ExtendWith(JpaPostgresTestcontainerExtension.class)
class TaskManagerTest extends EntityManagerAwareTest {

    private TaskManagerRepositoryImpl taskManagerRepository;

    @BeforeEach
    void setUp() {
        taskManagerRepository = new TaskManagerRepositoryImpl(getEntityManager());
    }

    @Test
    void sjekk_startup() {
        taskManagerRepository.verifyStartup();
    }
}
