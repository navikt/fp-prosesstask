package no.nav.vedtak.felles.prosesstask.impl;

import java.time.LocalDateTime;
import java.util.function.Consumer;

import javax.persistence.PersistenceException;

import org.slf4j.MDC;

import no.nav.vedtak.felles.prosesstask.api.CallId;

class TaskManagerRunnableTask implements Runnable {
    private final TaskType taskType;
    private final RunTaskInfo taskInfo;
    private final String callId;
    private Consumer<IdentRunnable> fatalErrorSubmitFunc;

    TaskManagerRunnableTask(TaskType taskType, RunTaskInfo taskInfo, String callId, Consumer<IdentRunnable> fatalErrorSubmitFunc) {
        this.taskType = taskType;
        this.taskInfo = taskInfo;
        this.callId = callId;
        this.fatalErrorSubmitFunc = fatalErrorSubmitFunc;
    }

    @Override
    public void run() {
        MDC.clear();
        
        RunTask runSingleTask = newRunTaskInstance();
        IdentRunnable errorCallback = null;
        try {
            initLogContext(callId, taskType, taskInfo.getId());

            runSingleTask.doRun(taskInfo);

        } catch (PersistenceException fatal) {
            // transaksjonen er rullet tilbake, markert for rollback eller inaktiv nå. Submitter derfor en oppdatering som en separat oppgave (gjennom
            // en callback).
            errorCallback = lagErrorCallback(taskInfo, callId, fatal);
        } catch (Exception e) {
            errorCallback = lagErrorCallback(taskInfo, callId, e);
        } catch (Throwable t) { // NOSONAR
            errorCallback = lagErrorCallback(taskInfo, callId, t);
        } finally {
            clearLogContext();
            // dispose CDI etter bruk
            TaskManagerGenerateRunnableTasks.CURRENT.destroy(runSingleTask);
            // kjør etter at runTask er destroyed og logcontext renset
            handleErrorCallback(errorCallback);
        }
    }
    
    IdentRunnable lagErrorCallback(final RunTaskInfo taskInfo, final String callId, final Throwable fatal) {
        Runnable errorCallback;
        var mdcCopy = MDC.getCopyOfContextMap();
        errorCallback = () -> {
            MDC.setContextMap(mdcCopy); // later som vi fortsetter med samme MDC nøkler, men nå i ny tråd
            final FatalErrorTask errorTask = TaskManagerGenerateRunnableTasks.CURRENT.select(FatalErrorTask.class).get();
            try {
                initLogContext(callId, taskInfo.getTaskType(), taskInfo.getId());
                errorTask.doRun(taskInfo, fatal);
            } catch (Throwable t) {  // NOSONAR
                // logg at vi ikke klarte å registrer feilen i db
                TaskManagerGenerateRunnableTasks.log.error(
                        "PT-415565 Kunne ikke registrere feil på task pga uventet feil ved oppdatering av status/feil, id={}, taskName={}.", taskInfo.getId(), taskInfo.getTaskType(), t);
            } finally {
                clearLogContext();
                TaskManagerGenerateRunnableTasks.CURRENT.destroy(errorTask);
            }
        };

        // logg at vi kommer til å skrive dette i ny transaksjon pga fatal feil.
        TaskManagerGenerateRunnableTasks.log.warn("PT-876628 Kritisk database feil som gir rollback. Kan ikke prosessere task, vil logge til db i ny transaksjon, id={}, taskName={} pga uventet feil.",
                taskInfo.getId(), taskInfo.getTaskType(), fatal);


        return new IdentRunnableTask(taskInfo.getId(), errorCallback, LocalDateTime.now());
    }

    static void clearLogContext() {
        MDC.clear();
    }

    static void initLogContext(final String callId, TaskType taskType, Long taskId) {
        if (callId != null) {
            MDC.put(CallId.CALL_ID, callId);
        } else {
            MDC.put(CallId.CALL_ID, CallId.generateCallId());
        }
        MDC.put(TaskManager.TASK_PROP, taskType.value());
        MDC.put(TaskManager.TASK_ID_PROP, taskId.toString());
    }
    
    void handleErrorCallback(IdentRunnable errorCallback) {
        if (errorCallback != null) {
            // NB - kjøres i annen transaksjon enn opprinnelig
            fatalErrorSubmitFunc.accept(errorCallback);
        }
    }

    RunTask newRunTaskInstance() {
        return TaskManagerGenerateRunnableTasks.CURRENT.select(RunTask.class).get();
    }

}