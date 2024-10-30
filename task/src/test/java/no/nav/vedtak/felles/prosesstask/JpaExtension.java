package no.nav.vedtak.felles.prosesstask;

import jakarta.persistence.EntityManager;
import no.nav.foreldrepenger.konfig.Environment;
import no.nav.vedtak.felles.jpa.TransactionHandler;
import no.nav.vedtak.felles.jpa.TransactionHandler.Work;
import no.nav.vedtak.felles.testutilities.db.EntityManagerAwareExtension;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

class JpaExtension extends EntityManagerAwareExtension {

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
