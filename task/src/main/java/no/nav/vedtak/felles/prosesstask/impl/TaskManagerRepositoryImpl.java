package no.nav.vedtak.felles.prosesstask.impl;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hibernate.ScrollMode;
import org.hibernate.Session;
import org.hibernate.jpa.HibernateHints;
import org.hibernate.query.NativeQuery;
import org.jboss.weld.interceptor.util.proxy.TargetInstanceProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import no.nav.vedtak.exception.TekniskException;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;
import no.nav.vedtak.felles.prosesstask.api.TaskType;
import no.nav.vedtak.felles.prosesstask.impl.util.DatabaseUtil;

@Dependent
public class TaskManagerRepositoryImpl {

    private static final EnumSet<ProsessTaskStatus> KJØRT_STATUSER = EnumSet.of(ProsessTaskStatus.FERDIG, ProsessTaskStatus.KJOERT);
    private static final Set<ProsessTaskStatus> IKKE_KJØRT_STATUSER = EnumSet.complementOf(KJØRT_STATUSER)
            .stream().collect(Collectors.toUnmodifiableSet());
    private static final String STATUS = "status";
    private static final String NESTE_KJØRING = "neste_kjoering";

    private static final Logger LOG = LoggerFactory.getLogger(TaskManagerRepositoryImpl.class);
    private static final String JAVAX_PERSISTENCE_CACHE_STORE_MODE = "jakarta.persistence.cache.storeMode";
    private static final String REFRESH = "REFRESH";

    private static String SQL_FRA_FIL;

    private static String POSTGRESQL_FRA_FIL;

    private final String jvmUniqueProcessName = getJvmUniqueProcessName();
    private String sqlFraFil;

    private final EntityManager entityManager;

    public TaskManagerRepositoryImpl(EntityManager entityManager) {
        Objects.requireNonNull(entityManager, "entityManager");
        this.entityManager = entityManager;
    }

    @Inject
    public TaskManagerRepositoryImpl(@Any Instance<EntityManager> entityManager) {
        this(entityManager.get());
    }

    EntityManager getEntityManager() {
        return entityManager;
    }

    String getSqlForPolling() {
        return getSqlForPollingTemplate();
    }

    String getSqlForPollingTemplate() {
        if (sqlFraFil == null) {
            sqlFraFil = getSqlForPollingTemplate(entityManager);
        }
        return sqlFraFil;
    }

    private static synchronized String getSqlForPollingTemplate(EntityManager entityManager) {
        if (DatabaseUtil.isPostgres(entityManager)) {
            if (POSTGRESQL_FRA_FIL == null) {
                POSTGRESQL_FRA_FIL = getSqlFraFil(TaskManager.class.getSimpleName() + "postgres_pollTask.sql");
            }
            return POSTGRESQL_FRA_FIL;
        } else {
            if (SQL_FRA_FIL == null) {
                SQL_FRA_FIL = getSqlFraFil(TaskManager.class.getSimpleName() + "_pollTask.sql");
            }
            return SQL_FRA_FIL;
        }
    }

    static String getSqlFraFil(String filNavn) {
        try (var is = TaskManager.class.getResourceAsStream(filNavn);
             var s = is == null ? null : new Scanner(is, StandardCharsets.UTF_8)) {

            if (s == null) {
                throw new IllegalStateException("Finner ikke sql fil: " + filNavn);
            }
            s.useDelimiter("\\Z");
            if (!s.hasNext()) {
                throw new IllegalStateException("Finner ikke sql fil: " + filNavn);
            }
            return s.next();
        } catch (IOException e) {
            throw new TekniskException("PT-314160",
                    String.format("Finner ikke sql i fil=%s. Konfigurasjon er ugyldig.", filNavn), e);
        }
    }

    /**
     * Henter alle tasks som er klare til å kjøre ved angitt tidspunkt.
     */
    List<ProsessTaskData> pollNeste(LocalDateTime etterTid) {
        final var sqlForPolling = getSqlForPolling();

        var resultList = getEntityManagerAsSession()
                .createNativeQuery(sqlForPolling, ProsessTaskEntitet.class) // NOSONAR  - statisk SQL
                .setParameter(NESTE_KJØRING, etterTid)
                .setParameter("skip_ids", Set.of(-1))
                .setHint(HibernateHints.HINT_CACHE_MODE, "IGNORE")
                .getResultList();
        return tilProsessTask(resultList);
    }

    @SuppressWarnings("rawtypes")
    private Session getEntityManagerAsSession() {
        var em = entityManager;
        // workaround for hibernate issue HHH-11020
        if (em instanceof TargetInstanceProxy tip) {
            em = (EntityManager) tip.weld_getTargetInstance();
        }
        return em.unwrap(Session.class);
    }

    void oppdaterStatusOgNesteKjøring(Long prosessTaskId, ProsessTaskStatus taskStatus, LocalDateTime nesteKjøringEtter, String feilkode,
            String feiltekst,
            int feilforsøk) {
        String updateSql = """
            update PROSESS_TASK set
             status =:status
            ,blokkert_av = NULL
            ,neste_kjoering_etter=:neste
            ,feilede_forsoek = :forsoek
            ,siste_kjoering_feil_kode = :feilkode
            ,siste_kjoering_feil_tekst = :feiltekst
            , siste_kjoering_slutt_ts = :status_ts
            ,versjon=versjon+1
            WHERE id = :id AND status NOT IN ('VETO', 'SUSPENDERT', 'KJOERT', 'FERDIG')
            """;

        var now = LocalDateTime.now();
        var status = taskStatus.getDbKode();
        var tasks = getEntityManagerAsSession().createNativeQuery(updateSql)
                .setParameter("id", prosessTaskId)
                .setParameter(STATUS, status)
                .setParameter("status_ts", now)
                .setParameter("neste", nesteKjøringEtter)
                .setParameter("feilkode", feilkode)
                .setParameter("feiltekst", feiltekst)
                .setParameter("forsoek", feilforsøk)
                .executeUpdate();

        if (tasks > 0) {
            LOG.info("Oppdatert task [{}], ny status[{}], feilkode[{}], nesteKjøringEtter[{}]", prosessTaskId, status, feilkode, nesteKjøringEtter);
        }
    }

    void oppdaterStatus(Long prosessTaskId, ProsessTaskStatus taskStatus) {
        var updateSql = """
            update PROSESS_TASK set
             status =:status
            ,neste_kjoering_etter= NULL
            ,siste_kjoering_feil_kode = NULL
            ,siste_kjoering_feil_tekst = NULL
            ,siste_kjoering_slutt_ts = :status_ts
            ,versjon=versjon+1
            WHERE id = :id
            """;

        var status = taskStatus.getDbKode();
        var now = LocalDateTime.now();
        @SuppressWarnings({ "unused", "resource" })
        var tasks = getEntityManagerAsSession().createNativeQuery(updateSql)
                .setParameter("id", prosessTaskId)
                .setParameter(STATUS, status)
                .setParameter("status_ts", now)
                .executeUpdate();

    }

    /** Markere task under arbeid (kjøres nå). */
    @SuppressWarnings("resource")
    void oppdaterTaskUnderArbeid(Long prosessTaskId, LocalDateTime now) {
        var updateSql = "update PROSESS_TASK set" +
                "  siste_kjoering_ts = :naa" +
                " ,versjon=versjon+1 " +
                " WHERE id = :id";

        @SuppressWarnings("unused")
        var tasks = getEntityManagerAsSession().createNativeQuery(updateSql)
                .setParameter("id", prosessTaskId)
                .setParameter("naa", now)
                .executeUpdate();

    }

    /**
     * Markere tasks om er kjørt FERDIG. Dette har som konsekvens at det flytter
     * tasks fra default partisjon til FERDIG partisjoner.
     */
    void moveToDonePartition() {
        var updateSql = "update PROSESS_TASK set status = 'FERDIG' WHERE status='KJOERT'";

        @SuppressWarnings("unused")
        var tasks = entityManager.createNativeQuery(updateSql)
                .executeUpdate();

    }

    /** Markere task plukket til arbeid (ligger på in-memory kø). */
    void oppdaterTaskPlukket(Long prosessTaskId, LocalDateTime nesteKjøring, LocalDateTime now) {
        var updateSql = "update PROSESS_TASK set" +
                "  neste_kjoering_etter= :neste_kjoering" +
                " ,siste_kjoering_plukk_ts = :naa" +
                " ,siste_kjoering_server = :server" +
                " ,versjon=versjon+1 " +
                " WHERE id = :id";

        @SuppressWarnings("unused")
        var tasks = entityManager.createNativeQuery(updateSql)
                .setParameter("id", prosessTaskId)
                .setParameter(NESTE_KJØRING, nesteKjøring)
                .setParameter("naa", now)
                .setParameter("server", jvmUniqueProcessName)
                .executeUpdate();

    }

    /**
     * Poll neste vha. scrolling. Dvs. vi plukker en og en task og håndterer den får
     * vi laster mer fra databasen. Sikrer at flere pollere kan opere samtidig og
     * uavhengig av hverandre.
     */
    @SuppressWarnings({ "resource" })
    List<ProsessTaskEntitet> pollNesteScrollingUpdate(int numberOfTasks, long waitTimeBeforeNextPollingAttemptSecs, Set<Long> skipIds) {
        var numberOfTasksStillToGo = numberOfTasks;
        List<ProsessTaskEntitet> tasksToRun = new ArrayList<>(numberOfTasks);

        // bruker JDBC/SQL + Scrolling For å kunne streame resultat (henter spesifikt
        // kun en og en rad om
        // gangen) og definere eksakt spørring.

        // Scroller for å kunne oppdatere en og en rad uten å ta lås på neste.
        var timestamp = LocalDateTime.now();
        try (var results = getEntityManagerAsSession()
                .createNativeQuery(getSqlForPolling(), ProsessTaskEntitet.class)
                .setFlushMode(FlushModeType.COMMIT)
                // hent kun 1 av gangen for å la andre pollere slippe til
                .setHint(HibernateHints.HINT_FETCH_SIZE, 1)
                .setParameter(NESTE_KJØRING, timestamp)
                .setParameter("skip_ids", skipIds.isEmpty() ? Set.of(-1) : skipIds)
                .scroll(ScrollMode.FORWARD_ONLY);) {

            var now = getNåTidSekundOppløsning();
            var nyNesteTid = now.plusSeconds(waitTimeBeforeNextPollingAttemptSecs);

            while (results.next() && --numberOfTasksStillToGo >= 0) {
                var pte = results.get();
                if (pte != null) {
                    tasksToRun.add(pte);
                    oppdaterTaskPlukket(pte.getId(), nyNesteTid, now);
                    logTaskPollet(pte);
                }
            }
        }

        return tasksToRun;
    }

    LocalDateTime getNåTidSekundOppløsning() {
        // nåtid trunkeres til seconds siden det er det nestekjøring presisjon i db
        // tilsier. Merk at her må også sistekjøring settes
        // med sekund oppløsning siden disse sammenlignes i hand-over til RunTask
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    void logTaskPollet(ProsessTaskEntitet pte) {
        if (LOG.isInfoEnabled()) {
            LOG.info("Pollet task for kjøring: id={}, type={}, gruppe={}, sekvens={}, status={}, tidligereFeiledeForsøk={}, angitt nesteKjøringEtter={}",
                    pte.getId(), pte.getTaskType().value(), pte.getGruppe(), pte.getSekvens(), pte.getStatus(), pte.getFeiledeForsøk(),
                    pte.getNesteKjøringEtter());
        }
    }

    List<ProsessTaskData> tilProsessTask(List<ProsessTaskEntitet> resultList) {
        return resultList.stream().map(ProsessTaskEntitet::tilProsessTask).toList();
    }

    Optional<ProsessTaskEntitet> finnOgLås(RunTaskInfo taskInfo) {
        var taskId = taskInfo.getId();
        var status = ProsessTaskStatus.KLAR.getDbKode();
        var sistKjørtLowWatermark = taskInfo.getTimestampLowWatermark();
        var taskType = taskInfo.getTaskType();

        return finnOgLås(taskId, status, sistKjørtLowWatermark, taskType);

    }

    @SuppressWarnings("unchecked")
    private Optional<ProsessTaskEntitet> finnOgLås(Long taskId, String status, LocalDateTime sistKjørtLowWatermark, TaskType taskType) {
        // plukk task kun dersom id og task er samme (ellers er den allerede håndtert av
        // andre).
        var sql = " select pte.* from PROSESS_TASK pte "
                + " WHERE pte.id=:id"
                + "   AND pte.task_type=:taskType"
                + "   AND pte.status=:status"
                + "   AND ( pte.siste_kjoering_ts IS NULL OR pte.siste_kjoering_ts >=:sisteTs )"
                + "   FOR UPDATE SKIP LOCKED";

        @SuppressWarnings("resource")
        Query query = getEntityManagerAsSession().createNativeQuery(sql, ProsessTaskEntitet.class)
                .setHint(HibernateHints.HINT_FETCH_SIZE, 1)
                .setHint(JAVAX_PERSISTENCE_CACHE_STORE_MODE, REFRESH)
                .setParameter("id", taskId)
                .setParameter("taskType", taskType.value())
                .setParameter(STATUS, status)
                .setParameter("sisteTs", sistKjørtLowWatermark);

        return query.getResultList().stream().findFirst();
    }

    /**
     * @param blokkerendeTaskId
     * @return blokkerende task hvis ikke låst eller er allerede ferdig
     */
    @SuppressWarnings("unchecked")
    Optional<ProsessTaskEntitet> finnOgLåsBlokker(Long blokkerendeTaskId) throws LockTimeoutException {
        var ikkeKjørtStatuser = IKKE_KJØRT_STATUSER.stream().map(ProsessTaskStatus::getDbKode).collect(Collectors.toList());

        var sql = " select pte.* from PROSESS_TASK pte WHERE pte.id=:id AND pte.status IN (:statuser) FOR UPDATE SKIP LOCKED";

        @SuppressWarnings("resource")
        Query query = getEntityManagerAsSession().createNativeQuery(sql, ProsessTaskEntitet.class)
                .setHint(HibernateHints.HINT_FETCH_SIZE, 1)
                .setHint(JAVAX_PERSISTENCE_CACHE_STORE_MODE, REFRESH)
                .setParameter("id", blokkerendeTaskId)
                .setParameter("statuser", ikkeKjørtStatuser);

        Stream<ProsessTaskEntitet> stream = query.getResultStream();
        return stream.findFirst();
    }

    @ActivateRequestContext
    @Transactional
    void verifyStartup() {
        logDatabaseDetaljer();
    }

    List<ProsessTaskEntitet> finnFeiletTasks() {
        @SuppressWarnings("unchecked")
        var query = (NativeQuery<ProsessTaskEntitet>) entityManager
                .createNativeQuery(
                        "SELECT pt.* from PROSESS_TASK pt where pt.status IN ('FEILET')", ProsessTaskEntitet.class);
        return query.getResultList();
    }

    private void logDatabaseDetaljer() {
        String sql;
        if (DatabaseUtil.isPostgres(entityManager)) {
            sql = "select current_setting('TIMEZONE') as dbtz,"
                    + "  to_char(current_timestamp, 'YYYY-MM-DD HH24:MI:SS.US+TZ') as dbtid,"
                    + "  to_char(cast(:inputTid as timestamp with time zone), 'YYYY-MM-DD HH24:MI:SS.US+TZ') as inputtid,"
                    + "  :inputTid as inputtid2,"
                    + "  (current_timestamp - :inputTid) as drift";
        } else if (DatabaseUtil.isOracle(entityManager)) {
            sql = "select DBTIMEZONE as dbtz,"
                    + "  to_char(current_timestamp, 'YYYY-MM-DD HH24:MI:SSxFF6+TZH:TZM') as dbtid,"
                    + "  to_char(cast(:inputTid as timestamp with time zone), 'YYYY-MM-DD HH24:MI:SSxFF6+TZH:TZM') as inputtid,"
                    + "  :inputTid as inputtid2,"
                    + "  (current_timestamp - :inputTid) as drift"
                    + " from dual";
        } else {
            throw new UnsupportedOperationException("Unsupported Database: " + DatabaseUtil.getDialect(entityManager));
        }

        var now = LocalDateTime.now();
        @SuppressWarnings({ "resource", "cast" })
        var result = getEntityManagerAsSession().createNativeQuery(sql, StartupData.class)
                .setParameter("inputTid", now)
                .getSingleResult();

        var hibernateTz = entityManager.getEntityManagerFactory().getProperties().get("hibernate.jdbc.time_zone");
        var userTz = System.getProperty("user.timezone");
        LOG.info("Startup: DB(tz={}, current_timestamp={}), App(user.timezone={}, hibernate.jdbc.time_zone={}, inputtid={}, inputtid2={}). Drift={}",
                result.dbtz, result.dbtid, userTz, hibernateTz, result.inputtid, result.inputtid2, result.drift);
    }

    /**
     * Når vi setter veto/blokkert på en task er vi ikke garantert at ikke den som
     * blokkerer ikke er i ferd med å kjøres/snart er ferdig (fordi vi opererer med
     * read committed / ikke serialzable rea) istdf å ta lås på blokkerende tasks
     * før hvert veto, ettergår vi bare de som har blitt blokkert og opphever veto
     * dersom det ikke er nødvendig lenger.
     */
    void unblockTasks() {
        var sqlUnveto = """
            update prosess_task a set
               status='KLAR'
             , blokkert_av=NULL
             , feilede_forsoek=0
             , siste_kjoering_feil_kode=NULL
             , siste_kjoering_feil_tekst=NULL
             , neste_kjoering_etter=NULL
             , versjon = versjon +1
             WHERE status = 'VETO'
              AND blokkert_av IS NOT NULL
              AND EXISTS (select 1 from prosess_task b where b.id=a.blokkert_av AND b.status IN ('KJOERT', 'FERDIG'))
            """;

        var unvetoed = entityManager.createNativeQuery(sqlUnveto)
                .executeUpdate();
        if (unvetoed > 0) {
            LOG.info("Fjernet veto fra {} tasks som var blokkert av andre tasks som allerede er ferdig", unvetoed);
        }
    }

    Map<ProsessTaskStatus, Integer> countTasksForStatus(Set<ProsessTaskStatus> statusSet) {
        var monitorer = statusSet.stream()
            .filter(t -> !t.erKjørt())
            .map(ProsessTaskStatus::getDbKode)
            .toList();
        Map<ProsessTaskStatus, Integer> resultat = new EnumMap<>(ProsessTaskStatus.class);
        var query = entityManager
            .createNativeQuery("select status, count(1) from prosess_task where status in (:statuses) group by status")
            .setHint(HibernateHints.HINT_READ_ONLY, "true")
            .setParameter("statuses", monitorer);
        @SuppressWarnings("unchecked")
        List<Object[]> resultatList = query.getResultList();
        resultatList.forEach(o -> resultat.put(ProsessTaskStatus.valueOf((String) o[0]), intValueFromCount(o[1])));
        return resultat;
    }

    private int intValueFromCount(Object o) {
        return o instanceof BigInteger bi ? bi.intValue() : (o instanceof BigDecimal bd ? bd.intValue() : 0);
    }


    static synchronized String getJvmUniqueProcessName() {
        return ManagementFactory.getRuntimeMXBean().getName();
    }

    ProsessTaskEntitet finn(Long id) {
        var sql = " select pte.* from PROSESS_TASK pte WHERE pte.id=:id";
        @SuppressWarnings("resource")
        Query query = getEntityManagerAsSession().createNativeQuery(sql, ProsessTaskEntitet.class)
                .setHint(HibernateHints.HINT_FETCH_SIZE, 1)
                .setHint(JAVAX_PERSISTENCE_CACHE_STORE_MODE, REFRESH)
                .setParameter("id", id);
        return (ProsessTaskEntitet) query.getSingleResult();
    }

}
