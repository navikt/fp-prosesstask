package no.nav.vedtak.felles.prosesstask.impl.util;

import jakarta.persistence.EntityManager;

public class DatabaseUtil {

    private DatabaseUtil() {
    }

    public static boolean isPostgres(EntityManager entityManager) {
        return getDialect(entityManager).contains("PostgreSQL");
    }

    public static boolean isOracle(EntityManager entityManager) {
        return getDialect(entityManager).contains("Oracle");
    }

    public static String getDialect(EntityManager entityManager) {
        return (String) entityManager.getEntityManagerFactory().getProperties().get("hibernate.dialect");
    }

    public static String getSqlForUniktGruppeNavn(EntityManager entityManager) {
        if (DatabaseUtil.isPostgres(entityManager)) {
            return "SELECT nextval('seq_prosess_task_gruppe')";
        }
        if (DatabaseUtil.isOracle(entityManager)) {
            return "SELECT seq_prosess_task_gruppe.nextval FROM dual";
        }
        throw new UnsupportedOperationException("Unsupported Database: " + DatabaseUtil.getDialect(entityManager));

    }
}
