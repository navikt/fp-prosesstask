package no.nav.vedtak.felles.prosesstask.impl;

import no.nav.vedtak.felles.prosesstask.api.ProsessTask;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskHandler;

@ProsessTask(DummyProsessTask.DUMMY_TASK_NAME)
public class DummyProsessTask implements ProsessTaskHandler {

    public static final String DUMMY_TASK_NAME = "hello.world.prosesstask";
    public static final TaskType DUMMY_TASK_TYPE = new TaskType(DUMMY_TASK_NAME);

    @Override
    public void doTask(ProsessTaskData data) {
        System.out.println("Hello world");
    }

}
