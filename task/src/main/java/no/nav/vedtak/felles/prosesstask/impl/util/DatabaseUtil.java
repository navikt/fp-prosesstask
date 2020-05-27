package no.nav.vedtak.felles.prosesstask.impl.util;

import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

public class DatabaseUtil {
    public static boolean isPostgres(EntityManager entityManager) {
        String dialect = getDialect(entityManager);
        return dialect.contains("PostgreSQL");
    }
    
    public static boolean isOracle(EntityManager entityManager) {
        String dialect = getDialect(entityManager);
        return dialect.contains("Oracle");
    }

    public static String getDialect(EntityManager entityManager) {
        EntityManagerFactory emf = entityManager.getEntityManagerFactory();
        Map<String, Object> emfProperties = emf.getProperties();
        String dialect = (String) emfProperties.get("hibernate.dialect");
        return dialect;
    }
    
    public static String getSqlForUniktGruppeNavn(EntityManager entityManager) {
        if (DatabaseUtil.isPostgres(entityManager)) {
            return "SELECT nextval('seq_prosess_task_gruppe')";
        } else if (DatabaseUtil.isOracle(entityManager)) {
            return "SELECT seq_prosess_task_gruppe.nextval FROM dual";
        } else {
            throw new UnsupportedOperationException("Unsupported Database: " + DatabaseUtil.getDialect(entityManager));
        }
    }
}
