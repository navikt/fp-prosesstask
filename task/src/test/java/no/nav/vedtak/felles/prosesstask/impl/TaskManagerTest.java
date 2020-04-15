package no.nav.vedtak.felles.prosesstask.impl;

import javax.inject.Inject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import no.nav.vedtak.felles.prosesstask.CdiRunner;
import no.nav.vedtak.felles.prosesstask.UnittestRepositoryRule;

@RunWith(CdiRunner.class)
public class TaskManagerTest {

    @Rule
    public final UnittestRepositoryRule repoRule = new UnittestRepositoryRule();

    @Inject
    private TaskManagerRepositoryImpl taskManagerRepository;
    
    @Test
    public void sjekk_startup() throws Exception {
        taskManagerRepository.verifyStartup();
    }
}
