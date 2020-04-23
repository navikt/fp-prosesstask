package no.nav.vedtak.felles.prosesstask.impl;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.control.ActivateRequestContext;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TemporalType;
import javax.transaction.Transactional;

import org.hibernate.FlushMode;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.jpa.QueryHints;
import org.jboss.weld.interceptor.util.proxy.TargetInstanceProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;
import no.nav.vedtak.felles.prosesstask.impl.util.DatabaseUtil;

@ApplicationScoped
public class TaskManagerRepositoryImpl {

    private static final Logger log = LoggerFactory.getLogger(TaskManagerRepositoryImpl.class);

    private String jvmUniqueProcessName = getJvmUniqueProcessName();
    private String sqlFraFil = getSqlFraFil(TaskManager.class.getSimpleName() + "_pollTask.sql");

    private EntityManager entityManager;

    protected TaskManagerRepositoryImpl() {
        // for CDI proxying
    }

    public TaskManagerRepositoryImpl(EntityManager entityMangager) {
        Objects.requireNonNull(entityMangager, "entityManager");
        this.entityManager = entityMangager;
    }

    @Inject
    public TaskManagerRepositoryImpl(@Any Instance<EntityManager> entityMangager) {
        Objects.requireNonNull(entityMangager, "entityManager");
        this.entityManager = entityMangager.get();
    }

    EntityManager getEntityManager() {
        return entityManager;
    }

    String getSqlForPolling() {
        return getSqlForPollingTemplate();
    }

    String getSqlForPollingTemplate() {
        return sqlFraFil;
    }

    static String getSqlFraFil(String filNavn) {
        try (InputStream is = TaskManager.class.getResourceAsStream(filNavn);
                Scanner s = is == null ? null : new Scanner(is, "UTF8")) {//$NON-NLS-1$

            if (s == null) {
                throw new IllegalStateException("Finner ikke sql fil: " + filNavn);//$NON-NLS-1$
            }
            s.useDelimiter("\\Z");
            if (!s.hasNext()) {
                throw new IllegalStateException("Finner ikke sql fil: " + filNavn);//$NON-NLS-1$
            }
            return s.next();
        } catch (IOException e) {
            throw TaskManagerFeil.FACTORY.finnerIkkeSqlFil(filNavn, e).toException();
        }
    }

    /**
     * Henter alle tasks som er klare til å kjøre ved angitt tidspunkt.
     */
    List<ProsessTaskData> pollNeste(LocalDateTime etterTid) {
        final String sqlForPolling = getSqlForPolling();

        @SuppressWarnings("resource")
        List<ProsessTaskEntitet> resultList = getEntityManagerAsSession()
            .createNativeQuery(sqlForPolling, ProsessTaskEntitet.class) // NOSONAR - statisk SQL
            .setParameter("neste_kjoering", etterTid, TemporalType.TIMESTAMP)
            .setParameter("skip_ids", Set.of(-1))
            .setHint(QueryHints.HINT_CACHE_MODE, "IGNORE")
            .getResultList();
        return tilProsessTask(resultList);
    }

    @SuppressWarnings("rawtypes")
    private Session getEntityManagerAsSession() {
        EntityManager em = entityManager;
        // workaround for hibernate issue HHH-11020
        if (em instanceof TargetInstanceProxy) {
            em = (EntityManager) ((TargetInstanceProxy) em).weld_getTargetInstance();
        }
        return em.unwrap(Session.class);
    }

    void frigiVeto(ProsessTaskEntitet blokkerendeTask) {
        String updateSql = "update PROSESS_TASK SET "
            + " status='KLAR'"
            + ", blokkert_av=NULL"
            + ", siste_kjoering_feil_kode=NULL"
            + ", siste_kjoering_feil_tekst=NULL"
            + ", neste_kjoering_etter=NULL"
            + ", versjon = versjon +1"
            + " WHERE blokkert_av=:id";

        @SuppressWarnings("resource")
        int tasks = getEntityManagerAsSession()
            .createNativeQuery(updateSql)
            .setParameter("id", blokkerendeTask.getId())
            .executeUpdate();
        if (tasks > 0) {
            log.info("ProsessTask [{}] FERDIG. Frigitt {} tidligere blokkerte tasks", blokkerendeTask.getId(), tasks);
        }
    }

    void oppdaterStatusOgNesteKjøring(Long prosessTaskId, ProsessTaskStatus taskStatus, LocalDateTime nesteKjøringEtter, String feilkode, String feiltekst,
                                      int feilforsøk) {
        String updateSql = "update PROSESS_TASK set" +
            " status =:status" +
            " ,blokkert_av = NULL" +
            " ,neste_kjoering_etter=:neste" +
            " ,feilede_forsoek = :forsoek" +
            " ,siste_kjoering_feil_kode = :feilkode" +
            " ,siste_kjoering_feil_tekst = :feiltekst" +
            ", siste_kjoering_slutt_ts = :status_ts" +
            " ,versjon=versjon+1 " +
            " WHERE id = :id AND status NOT IN ('VETO', 'SUSPENDERT', 'KJOERT', 'FERDIG')";

        LocalDateTime now = LocalDateTime.now();
        String status = taskStatus.getDbKode();
        @SuppressWarnings("resource")
        int tasks = getEntityManagerAsSession().createNativeQuery(updateSql)
            .setParameter("id", prosessTaskId) // NOSONAR
            .setParameter("status", status) // NOSONAR
            .setParameter("status_ts", now, TemporalType.TIMESTAMP) // NOSONAR
            .setParameter("neste", nesteKjøringEtter, TemporalType.TIMESTAMP) // NOSONAR
            .setParameter("feilkode", feilkode)// NOSONAR
            .setParameter("feiltekst", feiltekst)// NOSONAR
            .setParameter("forsoek", feilforsøk)// NOSONAR
            .executeUpdate();

        if (tasks > 0) {
            log.info("Oppdatert task [{}], ny status[{}], feilkode[{}], nesteKjøringEtter[{}]", prosessTaskId, status, feilkode, nesteKjøringEtter);
        }
    }

    void oppdaterStatus(Long prosessTaskId, ProsessTaskStatus taskStatus) {
        String updateSql = "update PROSESS_TASK set" +
            " status =:status" +
            " ,neste_kjoering_etter= NULL" +
            " ,siste_kjoering_feil_kode = NULL" +
            " ,siste_kjoering_feil_tekst = NULL" +
            ", siste_kjoering_slutt_ts = :status_ts" +
            " ,versjon=versjon+1 " +
            " WHERE id = :id";

        String status = taskStatus.getDbKode();
        LocalDateTime now = LocalDateTime.now();
        @SuppressWarnings({ "unused", "resource" })
        int tasks = getEntityManagerAsSession().createNativeQuery(updateSql) // NOSONAR
            .setParameter("id", prosessTaskId)
            .setParameter("status", status)
            .setParameter("status_ts", now, TemporalType.TIMESTAMP)
            .executeUpdate();

    }

    /** Markere task under arbeid (kjøres nå). */
    @SuppressWarnings("resource")
    void oppdaterTaskUnderArbeid(Long prosessTaskId, LocalDateTime now) {
        String updateSql = "update PROSESS_TASK set" +
            "  siste_kjoering_ts = :naa" +
            " ,versjon=versjon+1 " +
            " WHERE id = :id";

        @SuppressWarnings("unused")
        int tasks = getEntityManagerAsSession().createNativeQuery(updateSql) // NOSONAR
            .setParameter("id", prosessTaskId)
            .setParameter("naa", now, TemporalType.TIMESTAMP)
            .executeUpdate();

    }

    /** Markere tasks om er kjørt FERDIG. Dette har som konsekvens at det flytter tasks fra default partisjon til FERDIG partisjoner. */
    void moveToDonePartition() {
        String updateSql = "update PROSESS_TASK set status = 'FERDIG' WHERE status='KJOERT'";

        @SuppressWarnings("unused")
        int tasks = entityManager.createNativeQuery(updateSql)
            .executeUpdate();

    }

    /** Markere task plukket til arbeid (ligger på in-memory kø). */
    void oppdaterTaskPlukket(Long prosessTaskId, LocalDateTime nesteKjøring, LocalDateTime now) {
        String updateSql = "update PROSESS_TASK set" +
            "  neste_kjoering_etter= :neste_kjoering" +
            " ,siste_kjoering_plukk_ts = :naa" +
            " ,siste_kjoering_server = :server" +
            " ,versjon=versjon+1 " +
            " WHERE id = :id";

        @SuppressWarnings("unused")
        int tasks = entityManager.createNativeQuery(updateSql) // NOSONAR
            .setParameter("id", prosessTaskId)
            .setParameter("neste_kjoering", nesteKjøring)
            .setParameter("naa", now)
            .setParameter("server", jvmUniqueProcessName)
            .executeUpdate();

    }

    /**
     * Poll neste vha. scrolling. Dvs. vi plukker en og en task og håndterer den får vi laster mer fra databasen. Sikrer
     * at flere pollere kan opere samtidig og uavhengig av hverandre.
     */
    @SuppressWarnings({ "resource" })
    List<ProsessTaskEntitet> pollNesteScrollingUpdate(int numberOfTasks, long waitTimeBeforeNextPollingAttemptSecs, Set<Long> skipIds) {
        int numberOfTasksStillToGo = numberOfTasks;
        List<ProsessTaskEntitet> tasksToRun = new ArrayList<>(numberOfTasks);

        // bruker JDBC/SQL + Scrolling For å kunne streame resultat (henter spesifikt kun en og en rad om
        // gangen) og definere eksakt spørring.

        // Scroller for å kunne oppdatere en og en rad uten å ta lås på neste.
        var timestamp = LocalDateTime.now();
        try (ScrollableResults results = getEntityManagerAsSession()
            .createNativeQuery(getSqlForPolling(), ProsessTaskEntitet.class)
            .setFlushMode(FlushMode.MANUAL)
            // hent kun 1 av gangen for å la andre pollere slippe til
            .setHint(QueryHints.HINT_FETCH_SIZE, 1)
            .setParameter("neste_kjoering", timestamp, TemporalType.TIMESTAMP)
            .setParameter("skip_ids", skipIds.isEmpty() ? Set.of(-1) : skipIds)
            .scroll(ScrollMode.FORWARD_ONLY);) {

            LocalDateTime now = getNåTidSekundOppløsning();
            LocalDateTime nyNesteTid = now.plusSeconds(waitTimeBeforeNextPollingAttemptSecs);

            while (results.next() && --numberOfTasksStillToGo >= 0) {
                Object[] resultObjects = results.get();
                if (resultObjects.length > 0) {
                    ProsessTaskEntitet pte = (ProsessTaskEntitet) resultObjects[0];
                    tasksToRun.add(pte);
                    oppdaterTaskPlukket(pte.getId(), nyNesteTid, now);
                    logTaskPollet(pte);
                }
            }
        }

        return tasksToRun;
    }

    LocalDateTime getNåTidSekundOppløsning() {
        // nåtid trunkeres til seconds siden det er det nestekjøring presisjon i db tilsier. Merk at her må også sistekjøring settes
        // med sekund oppløsning siden disse sammenlignes i hand-over til RunTask
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    void logTaskPollet(ProsessTaskEntitet pte) {
        log.info("Pollet task for kjøring: id={}, type={}, gruppe={}, sekvens={}, status={}, tidligereFeiledeForsøk={}, angitt nesteKjøringEtter={}", // NOSONAR
            pte.getId(), pte.getTaskName(), pte.getGruppe(), pte.getSekvens(), pte.getStatus(), pte.getFeiledeForsøk(), pte.getNesteKjøringEtter());
    }

    List<ProsessTaskData> tilProsessTask(List<ProsessTaskEntitet> resultList) {
        return resultList.stream().map(pte -> pte.tilProsessTask()).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    Optional<ProsessTaskEntitet> finnOgLås(RunTaskInfo taskInfo) {
        // plukk task kun dersom id og task er samme (ellers er den allerede håndtert av andre).
        String sql = " select pte.* from PROSESS_TASK pte " //$NON-NLS-1$
            + " WHERE pte.id=:id"//$NON-NLS-1$
            + "   AND pte.task_type=:taskType"//$NON-NLS-1$
            + "   AND pte.status=:status"//$NON-NLS-1$
            + "   AND ( pte.siste_kjoering_ts IS NULL OR pte.siste_kjoering_ts >=:sisteTs )"
            + "   FOR UPDATE SKIP LOCKED" //$NON-NLS-1$
        ;

        @SuppressWarnings("resource")
        Query query = getEntityManagerAsSession().createNativeQuery(sql, ProsessTaskEntitet.class)
            .setHint(org.hibernate.annotations.QueryHints.FETCH_SIZE, 1)
            .setHint("javax.persistence.cache.storeMode", "REFRESH") //$NON-NLS-1$ //$NON-NLS-2$
            .setParameter("id", taskInfo.getId())// NOSONAR
            .setParameter("taskType", taskInfo.getTaskType())// NOSONAR
            .setParameter("status", ProsessTaskStatus.KLAR.getDbKode())// NOSONAR
            .setParameter("sisteTs", taskInfo.getTimestampLowWatermark(), TemporalType.TIMESTAMP);

        return query.getResultList().stream().findFirst();

    }

    @ActivateRequestContext
    @Transactional
    void verifyStartup() {
        boolean isPostgres = DatabaseUtil.isPostgres(entityManager);
        String sql;
        if (isPostgres){
            sql = "select current_setting('TIMEZONE') as dbtz,"
                    + "  to_char(current_timestamp, 'YYYY-MM-DD HH24:MI:SS.US+TZ') as dbtid,"
                    + "  to_char(cast(:inputTid as timestamp with time zone), 'YYYY-MM-DD HH24:MI:SS.US+TZ') as inputtid,"
                    + "  :inputTid as inputtid2,"
                    + "  (current_timestamp - :inputTid) as drift";
        } else {
            sql = "select DBTIMEZONE as dbtz,"
                    + "  to_char(current_timestamp, 'YYYY-MM-DD HH24:MI:SSxFF6+TZH:TZM') as dbtid,"
                    + "  to_char(cast(:inputTid as timestamp with time zone), 'YYYY-MM-DD HH24:MI:SSxFF6+TZH:TZM') as inputtid,"
                    + "  :inputTid as inputtid2,"
                    + "  (current_timestamp - :inputTid) as drift"
                    + " from dual";
        }

        LocalDateTime now = LocalDateTime.now();
        @SuppressWarnings({ "resource", "cast" })
        var result = (StartupData) getEntityManagerAsSession().createNativeQuery(sql, StartupData.class)
            .setParameter("inputTid", now, TemporalType.TIMESTAMP)
            .getSingleResult();

        Object hibernateTz = entityManager.getEntityManagerFactory().getProperties().get("hibernate.jdbc.time_zone");
        String userTz = System.getProperty("user.timezone");
        log.info("Startup: DB(tz={}, current_timestamp={}), App(user.timezone={}, hibernate.jdbc.time_zone={}, inputtid={}, inputtid2={}). Drift={}",
            result.dbtz, result.dbtid, userTz, hibernateTz, result.inputtid, result.inputtid2,result.drift);
    }

    ProsessTaskType getTaskType(String taskName) {
        return entityManager.find(ProsessTaskType.class, taskName);
    }

    static synchronized String getJvmUniqueProcessName() {
        return ManagementFactory.getRuntimeMXBean().getName();
    }

}
