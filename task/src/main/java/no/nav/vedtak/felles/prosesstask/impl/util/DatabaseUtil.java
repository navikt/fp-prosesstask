package no.nav.vedtak.felles.prosesstask.impl.util;

import java.sql.Connection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

public class DatabaseUtil {

    private static final Map<EntityManagerFactory, String> CACHE_DIALECT = Collections.synchronizedMap(new WeakHashMap<>());

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

    private static Optional<String> getDatabaseDialect(EntityManager entityManager) {
        var emf = entityManager.getEntityManagerFactory();
        if (emf == null) {
            return Optional.empty();
        }
        var cached = CACHE_DIALECT.get(emf);
        if (cached != null) {
            return Optional.of(cached);
        }
        var productName = entityManager.callWithConnection((Connection conn) -> conn.getMetaData().getDatabaseProductName());
        if (productName != null) {
            var normalized = productName.toLowerCase();
            CACHE_DIALECT.put(emf, normalized);
            return Optional.of(normalized);
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
