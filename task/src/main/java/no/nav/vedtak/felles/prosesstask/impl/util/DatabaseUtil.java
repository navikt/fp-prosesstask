package no.nav.vedtak.felles.prosesstask.impl.util;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.hibernate.engine.spi.SessionFactoryImplementor;

import jakarta.persistence.EntityManager;

public class DatabaseUtil {

    private static final Map<String, String> CACHE_DIALECT = new ConcurrentHashMap<>();

    private DatabaseUtil() {
    }

    public static boolean isPostgres(EntityManager entityManager) {
        return getDatabaseDialect(entityManager).filter(DatabaseUtil::isPostgres).isPresent();
    }

    public static boolean isOracle(EntityManager entityManager) {
        return getDatabaseDialect(entityManager).filter(DatabaseUtil::isOracle).isPresent();
    }

    public static String getDialect(EntityManager entityManager) {
        return getDatabaseDialect(entityManager).orElse("unknown");
    }

    public static String getSqlForUniktGruppeNavn(EntityManager entityManager) {
        var dialect = getDatabaseDialect(entityManager);
        if (dialect.filter(DatabaseUtil::isPostgres).isPresent()) {
            return "SELECT nextval('seq_prosess_task_gruppe')";
        }
        if (dialect.filter(DatabaseUtil::isOracle).isPresent()) {
            return "SELECT seq_prosess_task_gruppe.nextval FROM dual";
        }
        throw new UnsupportedOperationException("Unsupported Database: " + getDatabaseDialect(entityManager).orElse("unknown"));
    }

    private static Optional<String> getDatabaseDialect(EntityManager entityManager) {
        if (entityManager.getEntityManagerFactory() instanceof SessionFactoryImplementor sfi) {
            var key = sfi.getUuid();
            if (!CACHE_DIALECT.containsKey(key)) {
                var dialectClass = sfi.getJdbcServices().getDialect();
                if (dialectClass != null) {
                    CACHE_DIALECT.put(key, dialectClass.getClass().getSimpleName().toLowerCase());
                }
            }
            return Optional.ofNullable(CACHE_DIALECT.get(key));
        }
        return Optional.empty();
    }

    private static boolean isOracle(String s) {
        return s.startsWith("oracle");
    }

    private static boolean isPostgres(String s) {
        return s.startsWith("postgres");
    }
}
