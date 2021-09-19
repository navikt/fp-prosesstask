package no.nav.vedtak.felles.prosesstask.api;

import javax.enterprise.context.ApplicationScoped;

import no.nav.vedtak.felles.prosesstask.impl.ProsessTaskHandlerRef;

@ApplicationScoped
public class TestTaskDispatcher implements ProsessTaskDispatcher {

    @Override
    public void dispatch(ProsessTaskHandlerRef taskHandler, ProsessTaskData task) throws Exception {
        System.out.println("HELLO " + task);
    }
    
    @Override
    public boolean feilhåndterException(Throwable e) {
        return false;
    }

    @Override
    public ProsessTaskHandlerRef taskHandler(TaskType taskType) {
        return null;
    }

}
