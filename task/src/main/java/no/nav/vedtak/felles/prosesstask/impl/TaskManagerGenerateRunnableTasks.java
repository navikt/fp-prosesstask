package no.nav.vedtak.felles.prosesstask.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import jakarta.enterprise.inject.spi.CDI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import no.nav.vedtak.felles.prosesstask.api.CallId;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskDispatcher;
import no.nav.vedtak.felles.prosesstask.api.TaskType;
import no.nav.vedtak.felles.prosesstask.impl.TaskManager.ReadTaskFunksjon;

/** Poller for tilgjengelige tasks og omsetter disse til Runnable som kan kjøres på andre tråder. */
public class TaskManagerGenerateRunnableTasks {
    static final Logger log = LoggerFactory.getLogger(TaskManagerGenerateRunnableTasks.class);
    static final CDI<Object> CURRENT = CDI.current();

    private final BiFunction<Integer, ReadTaskFunksjon, List<IdentRunnable>> availableTasksFunc;
    private final ProsessTaskDispatcher taskDispatcher;
    private Consumer<IdentRunnable> fatalErrorSubmitFunc;

    /**
     * Constructor
     *
     * @param taskDispatcher - dispatcher som skal velge implementasjon og kjøre en spesifikk task
     * @param availableTasksFunc - funksjon for å polle tilgenglige tasks
     */
    TaskManagerGenerateRunnableTasks(ProsessTaskDispatcher taskDispatcher,
                                     BiFunction<Integer, ReadTaskFunksjon, List<IdentRunnable>> availableTasksFunc,
                                     Consumer<IdentRunnable> errorSubmitFunc) {
        this.taskDispatcher = taskDispatcher;
        this.availableTasksFunc = availableTasksFunc;
        this.fatalErrorSubmitFunc = errorSubmitFunc;
    }

    public List<IdentRunnable> execute(int numberOfTasksToPoll) {
        return availableTasksFunc.apply(numberOfTasksToPoll, this::readTask);
    }

    IdentRunnable readTask(ProsessTaskEntitet pte) {
        var prosessTaskData = pte.tilProsessTask();
        final var taskInfo = new RunTaskInfo(taskDispatcher, prosessTaskData);
        final var callId = pte.getPropertyValue(CallId.CALL_ID);
        var taskType = pte.getTaskType();
        return createRunnable(taskInfo, callId, taskType);
    }

    private IdentRunnable createRunnable(final RunTaskInfo taskInfo, final String callId, TaskType taskType) {
        Runnable r = createTaskManagerRunnableTask(taskInfo, callId, taskType);
        return new IdentRunnableTask(taskInfo.getId(), r, LocalDateTime.now());
    }

    TaskManagerRunnableTask createTaskManagerRunnableTask(final RunTaskInfo taskInfo, final String callId, TaskType taskType) {
        return new TaskManagerRunnableTask(taskType, taskInfo, callId, fatalErrorSubmitFunc);
    }

}
