package no.nav.vedtak.felles.prosesstask.api;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TestTaskDispatcher implements ProsessTaskDispatcher {

    @Override
    public void dispatch(ProsessTaskData task) throws Exception {
        System.out.println("HELLO " + task);
    }

}
