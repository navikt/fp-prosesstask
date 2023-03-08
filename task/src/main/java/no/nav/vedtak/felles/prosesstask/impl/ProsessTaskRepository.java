package no.nav.vedtak.felles.prosesstask.impl;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import org.hibernate.jpa.QueryHints;
import org.hibernate.query.NativeQuery;
import org.slf4j.MDC;

import no.nav.vedtak.exception.TekniskException;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskGruppe;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskGruppe.Entry;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;
import no.nav.vedtak.felles.prosesstask.impl.util.DatabaseUtil;

/**
 * Implementasjon av repository som er tilgjengelig for å lagre og opprette nye tasks.
 */
@ApplicationScoped
public class ProsessTaskRepository {

    private EntityManager entityManager;
    private ProsessTaskEventPubliserer eventPubliserer;
    private HandleProsessTaskLifecycleObserver handleLifecycleObserver;
    private SubjectProvider subjectProvider;

    ProsessTaskRepository() {
        // for CDI proxying
    }

    public ProsessTaskRepository(EntityManager entityManager,
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
    public ProsessTaskRepository(@Any Instance<EntityManager> entityManager,
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
        Query query = entityManager.createNativeQuery(sqlForGruppe);
        return String.valueOf(query.getSingleResult());
    }

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

    public ProsessTaskData finn(Long id) {
        ProsessTaskEntitet prosessTaskEntitet = this.entityManager.createQuery("from ProsessTaskEntitet pt where pt.id=:id", ProsessTaskEntitet.class)
            .setParameter("id", id)
            .setHint(QueryHints.HINT_CACHE_MODE, "IGNORE")
            .getSingleResult();
        return prosessTaskEntitet == null ? null : prosessTaskEntitet.tilProsessTask();
    }

    public List<ProsessTaskData> finnAlle(List<ProsessTaskStatus> statuses) {
        if (statuses == null || statuses.isEmpty() || statuses.contains(ProsessTaskStatus.FERDIG)) {
            throw new IllegalArgumentException("Ugyldig søk etter tasks");
        }
        List<String> statusNames = statuses.stream().map(ProsessTaskStatus::getDbKode).toList();
        TypedQuery<ProsessTaskEntitet> query = entityManager
            .createQuery("from ProsessTaskEntitet pt where pt.status in(:statuses)", ProsessTaskEntitet.class)
            .setParameter("statuses", statusNames);
        return tilProsessTask(query.getResultList());
    }

    public List<ProsessTaskData> finnGruppeIkkeFerdig(String gruppe) {
        if (gruppe == null) {
            throw new IllegalArgumentException("Ugyldig søk etter tasks");
        }
        List<String> ikkeFerdigStatusNames = Arrays.stream(ProsessTaskStatus.values())
                .filter(ProsessTaskStatus::erIkkeFerdig)
                .map(ProsessTaskStatus::getDbKode)
                .toList();
        TypedQuery<ProsessTaskEntitet> query = entityManager
                .createQuery("from ProsessTaskEntitet pt where pt pt.status in(:statuses) and pt.task_gruppe = :gruppe",
                        ProsessTaskEntitet.class)
                .setParameter("statuses", ikkeFerdigStatusNames)
                .setParameter("gruppe", gruppe);
        return tilProsessTask(query.getResultList());
    }

    public List<ProsessTaskData> finnAlleForAngittSøk(String paramSearchText,
                                                      LocalDate opprettetFraOgMed,
                                                      LocalDate opprettetTilOgMed) {
        if (paramSearchText == null || paramSearchText.isBlank()) {
            throw new IllegalArgumentException("Tom søkestreng");
        }
        // native sql for å håndtere like search for task_parametere felt,
        @SuppressWarnings("unchecked")
        NativeQuery<ProsessTaskEntitet> query = (NativeQuery<ProsessTaskEntitet>) entityManager
            .createNativeQuery("""
                    SELECT pt.* FROM PROSESS_TASK pt
                    WHERE pt.opprettet_tid > :opprettetFraOgMed AND pt.opprettet_tid < :opprettetTilOgMed
                      AND pt.task_parametere LIKE :likeSearch
                    """, ProsessTaskEntitet.class)
            .setParameter("opprettetFraOgMed", opprettetFraOgMed.minusDays(1))
            .setParameter("opprettetTilOgMed", opprettetTilOgMed.plusDays(1))
            .setParameter("likeSearch", "%"+paramSearchText+"%")
            .setHint(QueryHints.HINT_READONLY, "true");

        List<ProsessTaskEntitet> resultList = query.getResultList();
        return tilProsessTask(resultList);
    }

    public List<Long> hentIdForAlleFeilet() {
        TypedQuery<ProsessTaskEntitet> query = entityManager
                .createQuery("from ProsessTaskEntitet pt where pt.status = :feilet", ProsessTaskEntitet.class)
                .setParameter("feilet", ProsessTaskStatus.FEILET.getDbKode());
        return query.getResultList().stream().map(ProsessTaskEntitet::getId).collect(Collectors.toList());
    }

    public int settAlleFeiledeTasksKlar() {
        Query query = entityManager.createNativeQuery("UPDATE PROSESS_TASK " +
                "SET status = :status, " +
                "feilede_forsoek = feilede_forsoek-1, " +
                "neste_kjoering_etter = :naa " +
                "WHERE STATUS = :feilet")
                .setParameter("naa", LocalDateTime.now())
                .setParameter("status", ProsessTaskStatus.KLAR.getDbKode())
                .setParameter("feilet", ProsessTaskStatus.FEILET.getDbKode());
        int updatedRows = query.executeUpdate();
        entityManager.flush();

        return updatedRows;
    }


    public int slettGamleFerdige() {
        Query query = entityManager.createNativeQuery("DELETE FROM PROSESS_TASK WHERE STATUS = :ferdig AND OPPRETTET_TID < :aar")
                .setParameter("ferdig", ProsessTaskStatus.FERDIG.getDbKode())
                .setParameter("aar", LocalDateTime.now().minusYears(1));
        int deletedRows = query.executeUpdate();
        entityManager.flush();

        return deletedRows;
    }

    public int tømNestePartisjon() {
        String partisjonsNr = utledPartisjonsNr(LocalDate.now());
        Query query = entityManager.createNativeQuery("TRUNCATE prosess_task_partition_ferdig_" + partisjonsNr); // NOSONAR  - denne er OK
        int updatedRows = query.executeUpdate();
        entityManager.flush();

        return updatedRows;
    }

    static String utledPartisjonsNr(LocalDate date) {
        int måned = date.plusMonths(1).getMonth().getValue();
        if (måned < 10) {
            return "0" + måned;
        }
        return "" + måned;
    }

    private List<ProsessTaskData> tilProsessTask(List<ProsessTaskEntitet> resultList) {
        return resultList.stream().map(ProsessTaskEntitet::tilProsessTask).collect(Collectors.toList());
    }

    void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
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
