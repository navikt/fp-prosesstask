package no.nav.vedtak.felles.prosesstask.impl;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import org.hibernate.jpa.QueryHints;
import org.hibernate.query.NativeQuery;
import org.hibernate.type.StringType;
import org.slf4j.MDC;

import no.nav.vedtak.exception.TekniskException;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskGruppe;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskGruppe.Entry;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskRepository;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskTypeInfo;
import no.nav.vedtak.felles.prosesstask.api.TaskStatus;
import no.nav.vedtak.felles.prosesstask.impl.util.DatabaseUtil;

/**
 * Implementasjon av repository som er tilgjengelig for å lagre og opprette nye tasks.
 */
@ApplicationScoped
public class ProsessTaskRepositoryImpl implements ProsessTaskRepository {

    private EntityManager entityManager;
    private ProsessTaskEventPubliserer eventPubliserer;
    private HandleProsessTaskLifecycleObserver handleLifecycleObserver;
    private SubjectProvider subjectProvider;

    ProsessTaskRepositoryImpl() {
        // for CDI proxying
    }

    public ProsessTaskRepositoryImpl(EntityManager entityManager,
                                     SubjectProvider subjectProvider,
                                     ProsessTaskEventPubliserer eventPubliserer) {
        // for kompatibilitet og forenkling av tester
        Objects.requireNonNull(entityManager, "entityManager");
        this.entityManager = entityManager;
        this.eventPubliserer = eventPubliserer;
        this.subjectProvider = subjectProvider;
        this.handleLifecycleObserver = eventPubliserer == null ? null : new HandleProsessTaskLifecycleObserver() /* init kun dersom eventer lyttes på. */;
    }

    /**
     * Ctor - oppretter repository basert på CDI injection av relevante beans.
     *
     * @param entityManager - Required - kan p.t. bare finnes en av. Dersom en ønsker flere, subklass dette repoet
     * @param subjectProvider - Optional - hvis definert og eksisterer vil sette brukernavn som endrer tasks ved lagring
     * @param eventPubliserer
     * @param handleLifecycleObserver
     */
    @Inject
    public ProsessTaskRepositoryImpl(@Any Instance<EntityManager> entityManager,
                                     @Any Instance<SubjectProvider> subjectProvider,
                                     ProsessTaskEventPubliserer eventPubliserer,
                                     HandleProsessTaskLifecycleObserver handleLifecycleObserver) {
        Objects.requireNonNull(entityManager, "entityManager");

        if (subjectProvider.isAmbiguous()) {
            // vil kaste exception siden flere mulige instanser med detaljert feilmelding
            @SuppressWarnings("unused")
            var instans = subjectProvider.get();
        }

        this.entityManager = entityManager.get();
        this.eventPubliserer = eventPubliserer;
        this.handleLifecycleObserver = handleLifecycleObserver;
        this.subjectProvider = subjectProvider.isResolvable() ? subjectProvider.get() : null;

    }

    /**
     * Lager enkelt unik gruppenavn basert på en sekvens.
     * Aller helst skulle vært UUID type 3?
     */
    public static String getUniktProsessTaskGruppeNavn(EntityManager entityManager) throws SQLException {
        String sqlForGruppe = DatabaseUtil.getSqlForUniktGruppeNavn(entityManager);
        Query query = entityManager.createNativeQuery(sqlForGruppe); // $NON-NLS-1$
        return String.valueOf(query.getSingleResult());
    }

    @Override
    public String lagre(ProsessTaskGruppe sammensattTask) {
        String unikGruppeId = null;
        boolean lifecycleOpprettet = true;
        for (Entry entry : sammensattTask.getTasks()) {
            ProsessTaskData task = entry.task();
            if (task.getId() != null) {
                lifecycleOpprettet = false;
            }

            try {
                if (unikGruppeId == null && task.getGruppe() == null) {
                    // mangler et gruppenavn så la oss finne på et
                    unikGruppeId = getUniktProsessTaskGruppeNavn(entityManager);
                }
                if (task.getGruppe() == null) {
                    // bevar eksisterende gruppenavn hvis satt, ellers bruk nytt
                    task.setGruppe(unikGruppeId);
                }
                // ta sekvens fra rekkefølge definert i gruppen
                task.setSekvens(entry.sekvens());
                Long id = doLagreTask(task);
                task.setId(id);
            } catch (SQLException e) {
                throw new TekniskException("PT-265358",
                        String.format("Kunne ikke lagre task, skriving til database mislykkes: task=%s, kjøresEtter=%s, parametere=%s.",
                                task.getTaskType(), task.getNesteKjøringEtter(), task.getProperties()), e);
            }
        }

        if (lifecycleOpprettet) {
            if (handleLifecycleObserver != null) {
                handleLifecycleObserver.opprettetProsessTaskGruppe(sammensattTask);
            }
        }

        entityManager.flush();
        return unikGruppeId;
    }

    @Override
    public String lagre(ProsessTaskData task) {
        // prosesserer enkelt task som en gruppe av 1
        ProsessTaskGruppe gruppe = new ProsessTaskGruppe(task);
        return lagre(gruppe);
    }

    /**
     * Lagre og returner id.
     */
    protected Long doLagreTask(ProsessTaskData task) {
        ProsessTaskEntitet pte;
        if (task.getId() != null) {
            trackTaskLineage("latest", task);
            ProsessTaskStatus nyStatus = task.getStatus();
            pte = entityManager.find(ProsessTaskEntitet.class, task.getId());
            ProsessTaskStatus status = pte.getStatus();

            pte.kopierFraEksisterende(task);
            pte.setSubjectProvider(subjectProvider);
            entityManager.persist(pte);
            entityManager.flush();

            if (eventPubliserer != null && !Objects.equals(status, nyStatus)) {
                eventPubliserer.fireEvent(pte.tilProsessTask(), status, nyStatus);
            }
        } else {
            trackTaskLineage("parent", task);

            pte = new ProsessTaskEntitet();
            pte.kopierFraNy(task);
            pte.setSubjectProvider(subjectProvider);
            entityManager.persist(pte);
            entityManager.flush();
            task.setId(pte.getId()); // oppdaterer input med Id slik at denne kan brukes
            if (eventPubliserer != null) {
                eventPubliserer.fireEvent(pte.tilProsessTask(), null, task.getStatus());
            }
        }
        return pte.getId();
    }

    @Override
    public ProsessTaskData finn(Long id) {
        ProsessTaskEntitet prosessTaskEntitet = this.entityManager.createQuery("from ProsessTaskEntitet pt where pt.id=:id", ProsessTaskEntitet.class)
            .setParameter("id", id)
            .setHint(QueryHints.HINT_CACHE_MODE, "IGNORE")
            .getSingleResult();
        return prosessTaskEntitet == null ? null : prosessTaskEntitet.tilProsessTask();
    }

    @Override
    public List<ProsessTaskData> finnAlle(ProsessTaskStatus... statuses) {
        List<String> statusNames = Arrays.stream(statuses).map(ProsessTaskStatus::getDbKode).collect(Collectors.toList());
        TypedQuery<ProsessTaskEntitet> query = entityManager
            .createQuery("from ProsessTaskEntitet pt where pt.status in(:statuses)", ProsessTaskEntitet.class)
            .setParameter("statuses", statusNames); // NOSONAR $NON-NLS-1$
        return tilProsessTask(query.getResultList());
    }

    @Override
    public List<ProsessTaskData> finnIkkeStartet() {
        TypedQuery<ProsessTaskEntitet> query = entityManager
            .createQuery("from ProsessTaskEntitet pt where pt.status in(:statuses) and pt.sisteKjøring is NULL", ProsessTaskEntitet.class)
            .setParameter("statuses", ProsessTaskStatus.KLAR.getDbKode()); // NOSONAR $NON-NLS-1$
        return tilProsessTask(query.getResultList());

    }

    @Override
    public List<ProsessTaskData> finnAlle(List<ProsessTaskStatus> statuser, LocalDateTime sisteKjoeringFraOgMed, LocalDateTime sisteKjoeringTilOgMed) {
        // trunker til millis (<Java9 oppførsel) før sammenligning
        sisteKjoeringFraOgMed = sisteKjoeringFraOgMed.truncatedTo(ChronoUnit.MILLIS);
        sisteKjoeringTilOgMed = sisteKjoeringTilOgMed.truncatedTo(ChronoUnit.MILLIS);
        List<String> statusNames = statuser.stream().map(ProsessTaskStatus::getDbKode).collect(Collectors.toList());
        TypedQuery<ProsessTaskEntitet> query = entityManager
            .createQuery(
                "from ProsessTaskEntitet pt where pt.status in(:statuses) and pt.sisteKjøring >= (:sisteKjoeringFraOgMed) and pt.sisteKjøring <= (:sisteKjoeringTilOgMed)",
                ProsessTaskEntitet.class)
            .setParameter("statuses", statusNames) // NOSONAR $NON-NLS-1$
            .setParameter("sisteKjoeringFraOgMed", sisteKjoeringFraOgMed) // NOSONAR $NON-NLS-1$
            .setParameter("sisteKjoeringTilOgMed", sisteKjoeringTilOgMed); // NOSONAR $NON-NLS-1$
        return tilProsessTask(query.getResultList());
    }

    @Override
    public List<ProsessTaskData> finnAlleForAngittSøk(List<ProsessTaskStatus> statuser,
                                                      String gruppeId,
                                                      LocalDateTime nesteKjoeringFraOgMed,
                                                      LocalDateTime nesteKjoeringTilOgMed,
                                                      String paramLikeSearch) {
        List<String> statusNames = statuser.stream().map(ProsessTaskStatus::getDbKode).collect(Collectors.toList());
        /*
         * TODO (FC): task_parametere er ikke indeksert, hjelper uansett lite med LIKE search. Vurder å splitte til egen tabell og kolonner for
         * bedre ytelse dersom yter dårlig med store mengder.
         * en slik koblingstabell kan frikobles ved å lytte til ProsessTaskEvent for når tasks opprettes/ferdigstilles/feiles
         */

        // native sql for å håndtere like search for task_parametere felt,
        // samt cast til hibernate spesifikk håndtering av parametere som kan være NULL
        @SuppressWarnings("unchecked")
        NativeQuery<ProsessTaskEntitet> query = (NativeQuery<ProsessTaskEntitet>) entityManager
            .createNativeQuery(
                "SELECT pt.* FROM PROSESS_TASK pt"
                    + " WHERE pt.status IN (:statuses)"
                    + " AND pt.task_gruppe = coalesce(:gruppe, pt.task_gruppe)"
                    + " AND (pt.neste_kjoering_etter IS NULL"
                    + "      OR ("
                    + "           pt.neste_kjoering_etter >= cast(:nesteKjoeringFraOgMed as timestamp(0)) AND pt.neste_kjoering_etter <= cast(:nesteKjoeringTilOgMed as timestamp(0))"
                    + "      ))"
                    + " AND pt.task_parametere like :likeSearch",
                ProsessTaskEntitet.class);

        query.setParameter("statuses", statusNames) // NOSONAR $NON-NLS-1$
            .setParameter("gruppe", gruppeId, StringType.INSTANCE) // NOSONAR $NON-NLS-1$
            .setParameter("nesteKjoeringFraOgMed", nesteKjoeringFraOgMed.atZone(ZoneId.systemDefault())) // max oppløsning på neste_kjoering_etter er sekunder
            .setParameter("nesteKjoeringTilOgMed", nesteKjoeringTilOgMed.atZone(ZoneId.systemDefault())) // NOSONAR $NON-NLS-1$
            .setParameter("likeSearch", paramLikeSearch) // NOSONAR $NON-NLS-1$
            .setHint(QueryHints.HINT_READONLY, "true");

        List<ProsessTaskEntitet> resultList = query.getResultList();
        return tilProsessTask(resultList);
    }

    @Override
    public List<ProsessTaskData> finnUferdigeBatchTasks(String task) {
        TypedQuery<ProsessTaskEntitet> query = entityManager
            .createQuery("from ProsessTaskEntitet pt where pt.status NOT IN ('FERDIG', 'KJOERT') and pt.taskType = :task", ProsessTaskEntitet.class)
            .setParameter("task", task); // NOSONAR $NON-NLS-1$

        return tilProsessTask(query.getResultList());
    }

    @Override
    public Map<ProsessTaskType, ProsessTaskEntitet> finnStatusForBatchTasks() {
        TypedQuery<ProsessTaskType> query = entityManager
            .createQuery("SELECT ptt from ProsessTaskType ptt " +
                "where ptt.cronExpression is not null", ProsessTaskType.class); // NOSONAR $NON-NLS-1$

        List<ProsessTaskType> resultList = query.getResultList();
        Map<ProsessTaskType, ProsessTaskEntitet> result = new HashMap<>();
        for (ProsessTaskType prosessTaskType : resultList) {
            result.put(prosessTaskType, finnStatusForTaskType(prosessTaskType));
        }
        return result;
    }

    private ProsessTaskEntitet finnStatusForTaskType(ProsessTaskType taskType) {
        TypedQuery<ProsessTaskEntitet> query = entityManager
            .createQuery("SELECT pt " +
                "from ProsessTaskEntitet pt " +
                "where pt.taskType = :task " +
                "AND pt.status in ('KLAR', 'FEILET')" +
                "ORDER BY pt.nesteKjøringEtter DESC", ProsessTaskEntitet.class)
            .setParameter("task", taskType.getKode())
            .setHint(QueryHints.HINT_READONLY, true)
            .setMaxResults(1); // NOSONAR $NON-NLS-1$
        return query.getResultList().stream()
            .findFirst()
            .orElse(null);
    }

    @Override
    public boolean suspenderAlle(Collection<ProsessTaskData> tasks) {
        return oppdaterStatusForAlleEllerIngen(tasks, ProsessTaskStatus.SUSPENDERT);
    }

    // returnerer false dersom angitte tasks ikke kan settes til angitt status (typisk fordi de allerede har et sluttstatus som er likt.)
    private boolean oppdaterStatusForAlleEllerIngen(Collection<ProsessTaskData> tasks, ProsessTaskStatus status) {
        List<ProsessTaskEntitet> entiteter = new ArrayList<>();

        for (ProsessTaskData pt : tasks) {
            // lås alle først
            TypedQuery<ProsessTaskEntitet> query = entityManager
                .createQuery("from ProsessTaskEntitet pt where pt.status NOT IN (:status, 'FERDIG', 'KJOERT') and pt.taskType = :task AND pt.id=:id",
                    ProsessTaskEntitet.class)
                .setHint(org.hibernate.annotations.QueryHints.FETCH_SIZE, 1)
                .setParameter("status", status.getDbKode()) // NOSONAR $NON-NLS-1$
                .setParameter("task", pt.getTaskType()) // NOSONAR $NON-NLS-1$
                .setParameter("id", pt.getId()) // NOSONAR $NON-NLS-1$
                // viktig å låse alle entiter for å garantere at vi kan oppdatere de.
                .setLockMode(LockModeType.PESSIMISTIC_FORCE_INCREMENT);

            List<ProsessTaskEntitet> resultList = query.getResultList();
            if (resultList.size() != 1) {
                return false;
            } else {
                entiteter.add(resultList.get(0));
            }
        }

        // oppdater status og lagre alle (når vi har klart å låse alle)
        for (ProsessTaskEntitet entity : entiteter) {
            entity.setStatus(status);
            entityManager.persist(entity);
        }
        entityManager.flush();

        return true;
    }

    @Override
    public List<TaskStatus> finnStatusForTaskIGruppe(String task, String gruppe) {

        final Query query = entityManager
            .createNativeQuery("SELECT pt.status, count(*) FROM PROSESS_TASK pt WHERE pt.task_type = :task AND pt.TASK_GRUPPE = :gruppe GROUP BY pt.status")
            .setParameter("task", task)
            .setParameter("gruppe", gruppe);

        List<TaskStatus> statuser = new ArrayList<>();

        @SuppressWarnings("unchecked")
        List<Object[]> result = query.getResultList();
        for (Object[] objects : result) {
            statuser.add(new TaskStatus(ProsessTaskStatus.valueOf((String) objects[0]), (BigDecimal) objects[1])); // NOSONAR
        }
        return statuser;
    }

    @Override
    public Optional<ProsessTaskTypeInfo> finnProsessTaskType(String kode) {
        ProsessTaskType prosessTaskType = entityManager.find(ProsessTaskType.class, kode);
        if (prosessTaskType != null) {
            return Optional.of(prosessTaskType.tilProsessTaskTypeInfo());
        }
        return Optional.empty();
    }

    private List<ProsessTaskData> tilProsessTask(List<ProsessTaskEntitet> resultList) {
        return resultList.stream().map(ProsessTaskEntitet::tilProsessTask).collect(Collectors.toList());
    }

    void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }

    private static void trackTaskLineage(String keyPrefix, ProsessTaskData task) {
        Set<String> lineageProps = Set.of(TaskManager.TASK_ID_PROP, TaskManager.TASK_PROP);
        lineageProps.forEach(v -> {
            var prop = MDC.get(v);
            var key = keyPrefix + "." + v;
            if (prop != null && task.getPropertyValue(key) == null) {
                task.setProperty(key, prop);
            }
        });
    }

}
