package no.nav.vedtak.felles.prosesstask;

import javax.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import no.nav.vedtak.felles.jpa.TransactionHandler;
import no.nav.vedtak.felles.jpa.TransactionHandler.Work;
import no.nav.vedtak.felles.testutilities.db.EntityManagerAwareExtension;
import no.nav.vedtak.util.env.Environment;

public class JpaExtension extends EntityManagerAwareExtension {

    private static final Logger LOG = LoggerFactory.getLogger(JpaExtension.class);
    private static final boolean isNotRunningUnderMaven = Environment.current().getProperty("maven.cmd.line.args") == null;

    static {
        if (isNotRunningUnderMaven) {
            LOG.info("Kjører IKKE under maven");
            // prøver alltid migrering hvis endring, ellers funker det dårlig i IDE.
            Databaseskjemainitialisering.migrerUnittestSkjemaer();
        }
        Databaseskjemainitialisering.settJdniOppslag();
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
