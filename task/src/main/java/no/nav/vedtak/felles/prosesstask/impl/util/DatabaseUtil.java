package no.nav.vedtak.felles.prosesstask.impl.util;

import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

public class DatabaseUtil {
    public static boolean isPostgres(EntityManager entityManager) {
        EntityManagerFactory emf = entityManager.getEntityManagerFactory();
        Map<String, Object> emfProperties = emf.getProperties();
        String dialect = (String) emfProperties.get("hibernate.dialect");
        return dialect.contains("PostgreSQL");
    }
}
