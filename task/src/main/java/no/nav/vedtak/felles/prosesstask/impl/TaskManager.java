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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
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
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import no.nav.vedtak.exception.TekniskException;
import no.nav.vedtak.felles.jpa.TransactionHandler;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskDispatcher;
import no.nav.vedtak.felles.prosesstask.api.TaskMonitor;
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

    /*
     * Factory for RunTask/FatalErrorTask (begge @Dependent) - én ny instans per kjøring av en task.
     * - Instance er en trådsikker referanse, ikke en bønne-instans.
     * - get() lager en ny instans. Den blir en avhengig (dependent) av TaskManager og lever like lenge som den
     *   (dvs til applikasjonen stopper) - med mindre den eksplisitt destrueres.
     * - destroy() MÅ derfor kalles etter hver kjøring (i finally). Da kalles @PreDestroy og avhengige bønner
     *   (bl.a. interceptorer for @Transactional/@ActivateRequestContext) frigjøres.
     * - CDI-kontekster er trådbundne: RequestScoped (EntityManager) aktiveres av @ActivateRequestContext på
     *   RunTask/FatalErrorTask på worker-tråden, ikke av tråden som pollet tasken.
     */
    private Instance<RunTask> runTasks;
    private Instance<FatalErrorTask> fatalErrorTasks;

    private final AtomicLong pollerRoundNoCapacityRounds = new AtomicLong();
    private final AtomicReference<LocalDateTime> pollerRoundNoCapacitySince = new AtomicReference<>(LocalDateTime.now());
    private final AtomicReference<LocalDateTime> pollerRoundNoneFoundSince = new AtomicReference<>(LocalDateTime.now());
    private final AtomicReference<LocalDateTime> pollerRoundNoneLastReported = new AtomicReference<>(LocalDateTime.now());

    static final String TASK_PROP = "prosess_task";
    static final String TASK_ID_PROP = "prosess_task_id";

    public TaskManager() {
    }

    @Inject
    public TaskManager(TaskManagerRepositoryImpl taskManagerRepository,
                       @Any Instance<ProsessTaskDispatcher> dispatcher,
                       Instance<RunTask> runTasks,
                       Instance<FatalErrorTask> fatalErrorTasks) {
        Objects.requireNonNull(taskManagerRepository, "taskManagerRepository");
        this.taskManagerRepository = taskManagerRepository;
        this.runTasks = runTasks;
        this.fatalErrorTasks = fatalErrorTasks;

        if (dispatcher != null) {
            this.taskDispatcher = selectProsessTaskDispatcher(dispatcher);
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
                return dispatcherList.getFirst();
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
    public void stop() {
        ScheduledExecutorService ps;
        IdentExecutorService rts;
        synchronized (this) {
            if (pollingServiceScheduledFutures != null) {
                pollingServiceScheduledFutures.forEach(s -> s.cancel(true));
                pollingServiceScheduledFutures = null;
            }
            ps = pollingService;
            pollingService = null;
            rts = runTaskService;
            runTaskService = null;
        }
        if (ps != null) {
            ps.shutdownNow();
        }
        if (rts != null) {
            rts.stop();
        }
    }

    synchronized void startPollerThread() {
        if (pollingServiceScheduledFutures != null) {
            throw new IllegalStateException("Service allerede startet, stopp først");
        }
        if (pollingService == null) {
            this.pollingService = Executors
                .newSingleThreadScheduledExecutor(new NamedThreadFactory(threadPoolNamePrefix + "-poller", false));
        }
        // Randomiser initialDelay siden alle pods (minst 2) starter med 15-45s mellomrom.
        var moveToDoneInitialDelaySecs = ThreadLocalRandom.current().nextLong(60, 600);
        var taskMonitorInitialDelaySecs = ThreadLocalRandom.current().nextLong(180, 540);
        this.pollingServiceScheduledFutures = List.of(
            pollingService.scheduleWithFixedDelay(new PollAvailableTasks(), delayBetweenPollingMillis / 2, delayBetweenPollingMillis,
                TimeUnit.MILLISECONDS),
            pollingService.scheduleWithFixedDelay(new MoveToDonePartition(), moveToDoneInitialDelaySecs, 300, TimeUnit.SECONDS),
            pollingService.scheduleWithFixedDelay(new UpdateTaskMonitor(), taskMonitorInitialDelaySecs, 300, TimeUnit.SECONDS));
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

        var rts = getRunTaskService();
        if (rts == null) {
            return Collections.emptyList();
        }

        var now = LocalDateTime.now();

        var capacity = rts.remainingCapacity();
        if (reportRegularlyAndSkipIfNoAvailableCapacity(now, capacity)) {
            return Collections.emptyList();
        }

        var numberOfTasksToPoll = Math.min(capacity, maxNumberOfTasksToPoll);
        var pollDatabaseToRunnable = new TaskManagerGenerateRunnableTasks(getTaskDispatcher(), this::pollTasksFunksjon,
            runTasks, fatalErrorTasks);
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

        var rts = getRunTaskService();
        if (rts == null) {
            return Collections.emptyList();
        }
        var inmemoryTaskIds = rts.getTaskIds();

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
                    getTransactionManagerRepository().destroyEntityManager();
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
                    TimeUnit.SECONDS.sleep(getBackoffIntervalSeconds());
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

        private int getBackoffIntervalSeconds() {
            return backoffInterval[Math.min(backoffRound.get(), backoffInterval.length) - 1];
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
            var rts = getRunTaskService();
            if (rts == null) {
                return;
            }
            for (var task : availableTasks) {
                try {
                    rts.submit(task);
                    // lar futures ligge, feil fanges i task
                } catch (RejectedExecutionException _) {
                    // ignoreres under shutdown
                    return;
                }
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
        var ps = getPollingService();
        if (ps != null && !ps.isShutdown()) {
            try {
                ps.submit(new PollAvailableTasks(), Boolean.TRUE);
            } catch (RejectedExecutionException _) {
                // ignoreres under shutdown
            }
        } // else - ignoreres hvis ikke startet eller stoppet
    }

    private synchronized ScheduledExecutorService getPollingService() {
        return pollingService;
    }

    /**
     * For testing.
     */
    synchronized IdentExecutorService getRunTaskService() {
        return runTaskService;
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
                    getTransactionManagerRepository().destroyEntityManager();
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
            OtelUtil.wrapper().span("MoveToDonePartition", spanBuilder -> spanBuilder.setSpanKind(SpanKind.INTERNAL).setNoParent(),
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
                    getTransactionManagerRepository().destroyEntityManager();
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
            OtelUtil.wrapper().span("UpdateTaskMonitor", spanBuilder -> spanBuilder.setSpanKind(SpanKind.INTERNAL),
                () -> RequestContextHandler.doWithRequestContext(this::doWithContext));
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
            executor.getQueue().clear();
            executor.shutdown();
            try {
                if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException _) {
                executor.shutdownNow();
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
