package no.nav.vedtak.felles.prosesstask;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import jakarta.persistence.EntityManager;
import no.nav.foreldrepenger.konfig.Environment;
import no.nav.vedtak.felles.jpa.TransactionHandler;
import no.nav.vedtak.felles.jpa.TransactionHandler.Work;
import no.nav.vedtak.felles.testutilities.db.EntityManagerAwareExtension;

public class JpaTestcontainerExtension extends EntityManagerAwareExtension {

    private static final String TEST_DB_CONTAINER = Environment.current().getProperty("testcontainer.test.db", String.class, "postgres:15-alpine");
    private static final PostgreSQLContainer TEST_DATABASE;

    static {
        TEST_DATABASE = new PostgreSQLContainer<>(DockerImageName.parse(TEST_DB_CONTAINER)).withReuse(true);
        TEST_DATABASE.start();
        TestDatabaseInit.settOppDatasourceOgMigrer(TEST_DATABASE.getJdbcUrl(), TEST_DATABASE.getUsername(), TEST_DATABASE.getPassword());
    }

    /** Kan brukes til å kjøre egne ting i tx. */
    private <R> R doInTransaction(EntityManager entityManager, Work<R> func) throws Exception {
        return new TransactionHandler<R>() {

            @Override
            protected R doWork(EntityManager entityManager) throws Exception {
                return func.doWork(entityManager);
            }
        }.apply(entityManager);
    }

    /**
     * Kjør i transaksjon (når denne reglen var satt opp som ikke-transaksjonell.).
     * Starter ny transaksjon.
     */
    public <R> R doInTransaction(Work<R> func) throws Exception {
        return doInTransaction(getEntityManager(), func);
    }

}
