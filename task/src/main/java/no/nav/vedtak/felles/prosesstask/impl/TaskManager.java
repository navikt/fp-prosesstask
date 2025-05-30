package no.nav.vedtak.felles.prosesstask.impl;

import static java.lang.System.getenv;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hibernate.exception.JDBCConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.trace.SpanKind;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import no.nav.vedtak.exception.TekniskException;
import no.nav.vedtak.felles.jpa.TransactionHandler;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskDispatcher;
import no.nav.vedtak.felles.prosesstask.api.TaskMonitor;
import no.nav.vedtak.felles.prosesstask.api.TaskType;
import no.nav.vedtak.felles.prosesstask.impl.util.OtelUtil;
import no.nav.vedtak.server.Controllable;

/**
 * Main class handling polling tasks and dispatching these.
 */
@ApplicationScoped
public class TaskManager implements Controllable {

    public static final String TASK_MANAGER_POLLING_WAIT = "task.manager.polling.wait";
    public static final String TASK_MANAGER_POLLING_DELAY = "task.manager.polling.delay";
    public static final String TASK_MANAGER_POLLING_TASKS_SIZE = "task.manager.polling.tasks.size";
    public static final String TASK_MANAGER_RUNNER_THREADS = "task.manager.runner.threads";

    private static final Logger LOG = LoggerFactory.getLogger(TaskManager.class);

    private TaskManagerRepositoryImpl taskManagerRepository;

    /**
     * Prefix every thread in pool with given name.
     */
    private final String threadPoolNamePrefix = getClass().getSimpleName();

    /**
     * Antall parallelle tråder for å plukke tasks.
     */
    private int numberOfTaskRunnerThreads = getSystemPropertyWithLowerBoundry(TASK_MANAGER_RUNNER_THREADS, 10, 0);
    /**
     * Delay between each interval of polling. (millis)
     */
    private long delayBetweenPollingMillis = getSystemPropertyWithLowerBoundry(TASK_MANAGER_POLLING_DELAY, 500L, 1L);

    /**
     * Max number of tasks that will be attempted to poll on every try.
     */
    private int maxNumberOfTasksToPoll = getSystemPropertyWithLowerBoundry(TASK_MANAGER_POLLING_TASKS_SIZE, numberOfTaskRunnerThreads, 0);

    /**
     * Ventetid før neste polling forsøk (antar dersom task ikke plukkes raskt nok,
     * kan en annen poller ta over). (sekunder)
     */
    private final long waitTimeBeforeNextPollingAttemptSecs = getSystemPropertyWithLowerBoundry(TASK_MANAGER_POLLING_WAIT, 30L, 1L);

    /**
     * Executor for å håndtere tråder for å kjøre tasks.
     */
    private IdentExecutorService runTaskService;

    /**
     * Single scheduled thread handling polling.
     */
    private ScheduledExecutorService pollingService;

    /**
     * Future for å kunne kansellere polling.
     */
    private List<ScheduledFuture<?>> pollingServiceScheduledFutures;

    /**
     * Implementasjon av dispatcher for å faktisk kjøre tasks.
     */
    private ProsessTaskDispatcher taskDispatcher;

    private final AtomicLong pollerRoundNoCapacityRounds = new AtomicLong();
    private final AtomicReference<LocalDateTime> pollerRoundNoCapacitySince = new AtomicReference<>(LocalDateTime.now());
    private final AtomicReference<LocalDateTime> pollerRoundNoneFoundSince = new AtomicReference<>(LocalDateTime.now());
    private final AtomicReference<LocalDateTime> pollerRoundNoneLastReported = new AtomicReference<>(LocalDateTime.now());

    /**
     * trenger ikke ha denne som static siden TaskManager er ApplicationScoped.
     */
    private final ThreadLocal<ProsessTaskData> currentTask = new ThreadLocal<>();

    static final String TASK_PROP = "prosess_task";
    static final String TASK_ID_PROP = "prosess_task_id";

    public TaskManager() {
    }

    @Inject
    public TaskManager(TaskManagerRepositoryImpl taskManagerRepository, @Any Instance<ProsessTaskDispatcher> dispatcher) {
        Objects.requireNonNull(taskManagerRepository, "taskManagerRepository");
        this.taskManagerRepository = taskManagerRepository;

        if (dispatcher != null) {

            /**
             * Holder styr på kjørende task per tråd slik at vi kan i enkelttilfeller hente
             * ut info om det på en tråd.
             */
            class TrackCurrentDispatchedTask implements ProsessTaskDispatcher {
                final ProsessTaskDispatcher delegate = selectProsessTaskDispatcher(dispatcher);

                @Override
                public boolean feilhåndterException(Throwable e) {
                    return delegate.feilhåndterException(e);
                }

                @Override
                public void dispatch(ProsessTaskData task) throws Exception {
                    try {
                        currentTask.set(task);
                        delegate.dispatch(task);
                    } finally {
                        currentTask.remove();
                    }
                }

                @Override
                public ProsessTaskHandlerRef taskHandler(TaskType taskType) {
                    return delegate.taskHandler(taskType);
                }
            }

            this.taskDispatcher = new TrackCurrentDispatchedTask();
        }
    }

    private static ProsessTaskDispatcher selectProsessTaskDispatcher(Instance<ProsessTaskDispatcher> dispatcher) {
        if (dispatcher.isResolvable()) {
            return dispatcher.get();
        } else if (dispatcher.isAmbiguous()) {
            List<ProsessTaskDispatcher> dispatcherList = new ArrayList<>();
            for (var disp : dispatcher) {
                if (!(disp instanceof DefaultProsessTaskDispatcher)) {
                    dispatcherList.add(disp);
                }
            }
            if (dispatcherList.size() == 1) {
                return dispatcherList.get(0);
            } else {
                // kast exception har fler enn 2 instanser tilgjengelig, vet ikke hvilken vi
                // skal velge
                throw new IllegalArgumentException(
                    "Utvikler-feil: har flere mulige instanser å velge mellom, vet ikke hvilken som skal benyttes: " + dispatcherList);
            }
        } else {
            throw new IllegalArgumentException("Utvikler-feil: skal ikke komme hit (unsatifisied dependency) - har ingen ProsessTaskDispatcher");
        }
    }

    public synchronized void setProsessTaskDispatcher(ProsessTaskDispatcher taskDispatcher) {
        Objects.requireNonNull(taskDispatcher, "taskDispatcher");
        this.taskDispatcher = taskDispatcher;
    }

    synchronized ProsessTaskDispatcher getTaskDispatcher() {
        return this.taskDispatcher;
    }

    public synchronized void setDelayBetweenPolling(long delayBetweenPolling) {
        if (delayBetweenPolling < 0) {
            throw new IllegalArgumentException("delayBetweenPolling < 0" + delayBetweenPolling);
        }
        this.delayBetweenPollingMillis = delayBetweenPolling;
    }

    public synchronized void configureTaskThreads(int numberOfTaskRunnerThreads, int maxNumberOfTasksToPoll) {
        this.numberOfTaskRunnerThreads = numberOfTaskRunnerThreads;

        if (maxNumberOfTasksToPoll <= 0) {
            throw new TekniskException("PT-955392", "maxNumberOfTasksToPoll<=0: ugyldig");
        }
        this.maxNumberOfTasksToPoll = maxNumberOfTasksToPoll;
    }

    @Override
    public synchronized void start() {
        if (this.numberOfTaskRunnerThreads > 0) {
            getTransactionManagerRepository().verifyStartup();
            startTaskThreads();
            startPollerThread();
        } else {
            LOG.info(
                "Kan ikke starte {}, ingen tråder konfigurert, sjekk om du har konfigurert kontaineren din riktig bør ha minst en cpu tilgjengelig.",
                getClass().getSimpleName());
        }
    }

    @Override
    public synchronized void stop() {
        if (pollingServiceScheduledFutures != null) {
            pollingServiceScheduledFutures.forEach(s -> s.cancel(true));
            pollingServiceScheduledFutures = null;
        }
        if (runTaskService != null) {
            runTaskService.stop();
            runTaskService = null;
        }
    }

    public ProsessTaskData getCurrentTask() {
        return currentTask.get();
    }

    synchronized void startPollerThread() {
        if (pollingServiceScheduledFutures != null) {
            throw new IllegalStateException("Service allerede startet, stopp først");
        }
        if (pollingService == null) {
            this.pollingService = Executors
                .newSingleThreadScheduledExecutor(new NamedThreadFactory(threadPoolNamePrefix + "-poller", false));
        }
        this.pollingServiceScheduledFutures = List.of(
            pollingService.scheduleWithFixedDelay(new PollAvailableTasks(), delayBetweenPollingMillis / 2, delayBetweenPollingMillis,
                TimeUnit.MILLISECONDS),
            pollingService.scheduleWithFixedDelay(new FreeBlockedTasks(), 750L, 7500L, TimeUnit.MILLISECONDS));

        // schedulerer første runde, reschedulerer seg selv senere.
        pollingService.schedule(new MoveToDonePartition(), 30, TimeUnit.SECONDS);
        pollingService.schedule(new UpdateTaskMonitor(), 10, TimeUnit.SECONDS);
    }

    synchronized void startTaskThreads() {
        if (runTaskService != null) {
            throw new IllegalStateException("Service allerede startet, stopp først");
        }
        this.runTaskService = new IdentExecutorService();
    }

    interface ReadTaskFunksjon extends Function<ProsessTaskEntitet, IdentRunnable> {
    }

    /**
     * Poller for tasks og logger jevnlig om det ikke er ledig kapasitet (i
     * in-memory queue) eller ingen tasks funnet (i db).
     */
    protected synchronized List<IdentRunnable> pollForAvailableTasks() {

        var now = LocalDateTime.now();

        var capacity = getRunTaskService().remainingCapacity();
        if (reportRegularlyAndSkipIfNoAvailableCapacity(now, capacity)) {
            return Collections.emptyList();
        }

        var numberOfTasksToPoll = Math.min(capacity, maxNumberOfTasksToPoll);
        var pollDatabaseToRunnable = new TaskManagerGenerateRunnableTasks(getTaskDispatcher(), this::pollTasksFunksjon,
            this::submitTask);
        var tasksFound = pollDatabaseToRunnable.execute(numberOfTasksToPoll);

        reportRegularlyIfNoTasksFound(now, tasksFound);
        return tasksFound;

    }

    private void reportRegularlyIfNoTasksFound(LocalDateTime now, List<IdentRunnable> tasksFound) {
        if (tasksFound.isEmpty()) {
            if (pollerRoundNoneLastReported.get().plusHours(1).isBefore(now)) {
                pollerRoundNoneLastReported.set(now);
                LOG.info("Ingen tasks funnet siden [{}].", pollerRoundNoneFoundSince.get());
            }
        } else {
            pollerRoundNoneFoundSince.set(now); // reset
            pollerRoundNoneLastReported.set(now); // reset
        }
    }

    private boolean reportRegularlyAndSkipIfNoAvailableCapacity(LocalDateTime now, int capacity) {
        if (capacity < 1) {
            var round = pollerRoundNoCapacityRounds.incrementAndGet();
            if (round % 60 == 0) {
                LOG.warn(
                    "Ingen ledig kapasitet i siste polling runder siden [{}].  Sjekk eventuelt om tasks blir kjørt eller om de henger/er treghet under kjøring.",
                    pollerRoundNoCapacitySince.get());
            }
            // internal work queue already full, no point trying to push more
            return true;
        } else {
            pollerRoundNoCapacityRounds.set(0L); // reset
            pollerRoundNoCapacitySince.set(now); // reset
            return false;
        }
    }

    List<IdentRunnable> pollTasksFunksjon(int numberOfTasksToPoll, ReadTaskFunksjon readTaskFunksjon) {

        var inmemoryTaskIds = getRunTaskService().getTaskIds();

        var tasksEntiteter = taskManagerRepository
            .pollNesteScrollingUpdate(numberOfTasksToPoll, waitTimeBeforeNextPollingAttemptSecs, inmemoryTaskIds);

        return tasksEntiteter.stream().map(readTaskFunksjon).collect(Collectors.toList());
    }

    synchronized TaskManagerRepositoryImpl getTransactionManagerRepository() {
        return taskManagerRepository;
    }

    /**
     * Poll database table for tasks to run. Handled in a single thread.
     */
    protected class PollAvailableTasks implements Callable<Integer>, Runnable {

        private final class PollInNewTransaction extends TransactionHandler<List<IdentRunnable>> {

            List<IdentRunnable> doWork() throws Exception {

                var entityManager = getTransactionManagerRepository().getEntityManager();
                try {
                    return super.apply(entityManager);
                } finally {
                    CDI.current().destroy(entityManager);
                }
            }

            @Override
            protected List<IdentRunnable> doWork(EntityManager entityManager) {
                return pollForAvailableTasks();
            }
        }

        /**
         * simple backoff interval in seconds per round to account for transient
         * database errors.
         */
        private final int[] backoffInterval = new int[]{1, 2, 5, 5, 10, 10, 10, 10, 30};
        private final AtomicInteger backoffRound = new AtomicInteger();

        /**
         * @return number of tasks polled, -1 if errors logged
         */
        @Override
        public Integer call() {
            return OtelUtil.wrapper().span("POLL_TASKS", spanBuilder -> spanBuilder.setSpanKind(SpanKind.INTERNAL).setNoParent(),
                    () -> RequestContextHandler.doWithRequestContext(this::doPollingWithEntityManager));
        }

        public Integer doPollingWithEntityManager() {
            try {
                if (backoffRound.get() > 0) {
                    Thread.sleep(getBackoffIntervalSeconds());
                }
                var availableTasks = new PollInNewTransaction().doWork();

                // dispatch etter commit
                dispatchTasks(availableTasks);

                backoffRound.set(0);

                return availableTasks.size();
            } catch (InterruptedException e) {
                backoffRound.incrementAndGet();
                Thread.currentThread().interrupt();
            } catch (JDBCConnectionException e) {
                backoffRound.incrementAndGet();
                LOG.warn("PT-739415 Transient datase connection feil, venter til neste runde (runde={}): {}: {}",
                    backoffRound.get(), e.getClass(), e.getMessage());
            } catch (Exception e) {
                backoffRound.set(backoffInterval.length - 1); // force max delay (skal kun havne her for Exception/RuntimeException)
                LOG.warn("PT-996896 Kunne ikke polle database, venter til neste runde(runde={})", backoffRound.get(), e);
            } catch (Throwable t) { // NOSONAR
                backoffRound.set(backoffInterval.length - 1); // force max delay (skal kun havne her for Error)
                LOG.error("PT-996897 Kunne ikke polle grunnet kritisk feil, venter ({}s)", getBackoffIntervalSeconds(), t);
            }

            return -1;
        }

        private long getBackoffIntervalSeconds() {
            return backoffInterval[Math.min(backoffRound.get(), backoffInterval.length) - 1] * 1000L;
        }

        @Override
        public void run() {
            try {
                call();
            } catch (Throwable fatal) {
                /**
                 * skal aldri komme hit, men fange og håndtere exception før
                 *
                 * @see ScheduledExecutorService#scheduleWithFixedDelay
                 */
                LOG.error("Polling fatal feil, logger exception men tråden vil bli drept");
                throw fatal;
            }
        }

        private void dispatchTasks(List<IdentRunnable> availableTasks) {
            for (var task : availableTasks) {
                @SuppressWarnings("unused")
                Future<?> future = submitTask(task);
                // lar futures ligge, feil fanges i task
            }
        }

    }

    Future<Boolean> submitTask(IdentRunnable task) {
        return getRunTaskService().submit(task);
    }

    /**
     * For testing. Kjørere synkront med kallende tråd
     */
    int doSinglePolling() {
        return new PollAvailableTasks().call();
    }

    /**
     * Kjører en polling runde (async)
     */
    void doSinglePollingAsync() {
        if (pollingService != null) {
            pollingService.submit(new PollAvailableTasks(), Boolean.TRUE);
        } // else - ignoreres hvis ikke startet
    }

    /**
     * For testing.
     */
    synchronized IdentExecutorService getRunTaskService() {
        return runTaskService;
    }

    interface Work<R> {
        R doWork(EntityManager em) throws Exception;
    }

    private static int getSystemPropertyWithLowerBoundry(String key, int defaultValue, int lowerBoundry) {
        final var property = Optional.ofNullable(getenv(key.toUpperCase().replace(".", "_")))
            .orElseGet(() -> System.getProperty(key, String.valueOf(defaultValue)));
        final var systemPropertyValue = Integer.parseInt(property);
        return Math.max(systemPropertyValue, lowerBoundry);
    }

    private static long getSystemPropertyWithLowerBoundry(String key, long defaultValue, long lowerBoundry) {
        final var property = Optional.ofNullable(getenv(key.toUpperCase().replace(".", "_")))
            .orElseGet(() -> System.getProperty(key, String.valueOf(defaultValue)));
        final var systemPropertyValue = Long.parseLong(property);
        return Math.max(systemPropertyValue, lowerBoundry);
    }

    /**
     * Flytter fra KJOERT til FERDIG status i en separat tråd/transaksjon.
     */
    class MoveToDonePartition implements Runnable {

        /**
         * splittet fra for å kjøre i {@link TransactionHandler}.
         */
        private final class DoInNewTransaction extends TransactionHandler<Integer> {

            Integer doWork() throws Exception {
                var entityManager = getTransactionManagerRepository().getEntityManager();
                try {
                    return super.apply(entityManager);
                } finally {
                    CDI.current().destroy(entityManager);
                }
            }

            @Override
            protected Integer doWork(EntityManager entityManager) throws Exception {
                getTransactionManagerRepository().moveToDonePartition();
                return 0;
            }
        }

        /**
         * splittet fra {@link #run()} for å kjøre med ActivateRequestContext.
         */
        public Integer doWithContext() {
            try {
                return new DoInNewTransaction().doWork();
            } catch (Throwable t) {
                // logg, ikke rethrow feil her da det dreper trådene
                LOG.error("Kunne ikke flytte KJOERT tasks til FERDIG partisjoner", t);
            }
            return 1;
        }

        @Override
        public void run() {
            OtelUtil.wrapper().span("MoveToDonePartition", spanBuilder -> spanBuilder.setSpanKind(SpanKind.INTERNAL).setNoParent(), () -> {
                RequestContextHandler.doWithRequestContext(this::doWithContext);
                // neste kjører mellom 1-10 min fra nå.
                var min = 60L * 1000;
                var delay = System.currentTimeMillis() % (9 * min);
                pollingService.schedule(this, min + delay, TimeUnit.MILLISECONDS);
            });
        }
    }

    /**
     * Unblokkerer tasks som har blitt konservativt blokkert (der den som kjører
     * ikke ser nye veto påga Read Committed tx isolation level.
     */
    class FreeBlockedTasks implements Runnable {

        /**
         * splittet fra for å kjøre i {@link TransactionHandler}.
         */
        private final class DoInNewTransaction extends TransactionHandler<Integer> {

            Integer doWork() throws Exception {
                var entityManager = getTransactionManagerRepository().getEntityManager();
                try {
                    return super.apply(entityManager);
                } finally {
                    CDI.current().destroy(entityManager);
                }
            }

            @Override
            protected Integer doWork(EntityManager entityManager) throws Exception {
                getTransactionManagerRepository().unblockTasks();
                return 0;
            }
        }

        /**
         * splittet fra {@link #run()} for å kjøre med ActivateRequestContext.
         */
        public Integer doWithContext() {
            try {
                return new DoInNewTransaction().doWork();
            } catch (Throwable t) {
                // logg, ikke rethrow feil her da det dreper trådene
                LOG.error("Kunne ikke unblokkerer tasks som kan frigis", t);
            }
            return 1;
        }

        @Override
        public void run() {
            OtelUtil.wrapper().span("FreeBlockedTasks", spanBuilder -> spanBuilder.setSpanKind(SpanKind.INTERNAL).setNoParent(),
                () -> RequestContextHandler.doWithRequestContext(this::doWithContext));
        }

    }

    /**
     * Oppdaterer TaskMonitor med ferske tall for tasks pr status.
     */
    class UpdateTaskMonitor implements Runnable {

        /**
         * splittet fra for å kjøre i {@link TransactionHandler}.
         */
        private final class DoInNewTransaction extends TransactionHandler<Integer> {

            Integer doWork() throws Exception {
                var entityManager = getTransactionManagerRepository().getEntityManager();
                try {
                    return super.apply(entityManager);
                } finally {
                    CDI.current().destroy(entityManager);
                }
            }

            @Override
            protected Integer doWork(EntityManager entityManager) throws Exception {
                var counts = getTransactionManagerRepository().countTasksForStatus(TaskMonitor.monitoredStatuses());
                TaskMonitor.monitoredStatuses()
                    .forEach(s -> TaskMonitor.setStatusCount(s, Optional.ofNullable(counts.get(s)).orElse(0)));
                return counts.entrySet().size();
            }
        }

        /**
         * splittet fra {@link #run()} for å kjøre med ActivateRequestContext.
         */
        public Integer doWithContext() {
            try {
                return new DoInNewTransaction().doWork();
            } catch (Throwable t) {
                // logg, ikke rethrow feil her da det dreper trådene
                LOG.error("Kunne ikke telle tasks pr status", t);
            }
            return 1;
        }

        @Override
        public void run() {
            OtelUtil.wrapper().span("UpdateTaskMonitor", spanBuilder -> spanBuilder.setSpanKind(SpanKind.INTERNAL), () -> {
                RequestContextHandler.doWithRequestContext(this::doWithContext);
                // neste kjører mellom 3-9 min fra nå.
                var min = 3L * 60 * 1000;
                var delay = System.currentTimeMillis() % (2 * min);
                pollingService.schedule(this, min + delay, TimeUnit.MILLISECONDS);
            });
        }
    }

    /**
     * Internal executor that also tracks ids of currently queue or running tasks.
     */
    class IdentExecutorService {

        private final ThreadPoolExecutor executor;

        IdentExecutorService() {
            executor = new ThreadPoolExecutor(numberOfTaskRunnerThreads, numberOfTaskRunnerThreads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(maxNumberOfTasksToPoll),
                new NamedThreadFactory(threadPoolNamePrefix + "-runtask", true)) {

                @Override
                protected void afterExecute(Runnable r, Throwable t) {
                    if (getQueue().isEmpty()) {
                        // gi oss selv en head start ifht. neste polling runde
                        doSinglePollingAsync();
                    }
                }

                @Override
                protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
                    throw new UnsupportedOperationException("Alle kall skal gå til andre #newTaskFor(Runnable, T value)");
                }

                @Override
                protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
                    return new IdentFutureTask<>((IdentRunnable) runnable, value);
                }

            };
        }

        int remainingCapacity() {
            return executor.getQueue().remainingCapacity();
        }

        Future<Boolean> submit(IdentRunnable command) {
            return executor.submit(command, Boolean.TRUE);
        }

        Set<Long> getTaskIds() {
            return executor.getQueue().stream()
                .map(IdentFutureTask.class::cast)
                .map(IdentFutureTask::getId)
                .collect(Collectors.toSet());
        }

        void stop() {
            executor.shutdownNow();
            try {
                executor.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

    }

    static final class IdentFutureTask<T> extends FutureTask<T> implements IdentRunnable {

        private final Long id;
        private final LocalDateTime createTime;

        IdentFutureTask(IdentRunnable runnable, T value) {
            super(runnable, value);
            this.id = runnable.getId();
            this.createTime = runnable.getCreateTime();
        }

        @Override
        public Long getId() {
            return id;
        }

        /**
         * Tid denne future tasken ble opprettet (i minne).
         */
        @Override
        public LocalDateTime getCreateTime() {
            return createTime;
        }
    }
}
