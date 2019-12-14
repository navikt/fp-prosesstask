package no.nav.vedtak.felles.prosesstask;

import java.util.Properties;

import javax.naming.NamingException;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import javax.sql.DataSource;

import org.eclipse.jetty.plus.jndi.EnvEntry;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import no.nav.vedtak.felles.prosesstask.impl.util.TransactionHandler;
import no.nav.vedtak.felles.prosesstask.impl.util.TransactionHandler.Work;

public class UnittestRepositoryRule implements MethodRule {

    private static EntityManagerFactory emf; // NOSONAR
    static {
        String scriptLocation = "classpath:/db/migration/defaultDS";

        DataSource ds = opprettDatasource();
        settOppJndiDataSource("defaultDS", ds);

        var config = new FluentConfiguration()
            .dataSource(ds)
            .locations(scriptLocation)
            .baselineOnMigrate(true);

        var flyway = new Flyway(config);
        flyway.clean();
        flyway.migrate();

        emf = Persistence.createEntityManagerFactory("pu-default");

    }

    private EntityManager entityManager;
    private boolean useTx = true;

    public UnittestRepositoryRule() {
        super();
    }

    public UnittestRepositoryRule(boolean useTx) {
        this.useTx = useTx;
    }

    private static DataSource opprettDatasource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://127.0.0.1:5432/unittest");
        config.setUsername("unittest");
        config.setPassword("unittest");

        config.setConnectionTimeout(2000);
        config.setMinimumIdle(0);
        config.setMaximumPoolSize(2);

        Properties dsProperties = new Properties();
        config.setDataSourceProperties(dsProperties);

        HikariDataSource hikariDataSource = new HikariDataSource(config);
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                hikariDataSource.close();
            }
        }));

        return hikariDataSource;
    }

    private static void settOppJndiDataSource(String dsName, DataSource ds) {
        try {
            new EnvEntry("jdbc/" + dsName, ds); // NOSONAR
        } catch (NamingException e) {
            throw new IllegalStateException("Feil under registrering av JDNI-entry for default datasource", e); // NOSONAR
        }
    }

    @Override
    public Statement apply(Statement statement, FrameworkMethod method, Object target) {
        if (!useTx) {
            return statement;
        }
        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                WeldContext.getInstance().doWithScope(() -> {
                    Statement stmt = adaptStatementWithTx(statement, method);
                    try {
                        stmt.evaluate();
                        return null;
                    } catch (RuntimeException | Error e) {
                        throw e;
                    } catch (Throwable e) {
                        throw new IllegalStateException(e);
                    }
                });
            }

        };

    }

    public <R> R doInTransaction(EntityManager entityManager, Work<R> func) throws Exception {
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

    public EntityManager getEntityManager() {
        if (entityManager == null) {
            entityManager = createEntityManager();
        }
        return entityManager;
    }

    private Statement adaptStatementWithTx(Statement statement, @SuppressWarnings("unused") FrameworkMethod method) {
        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                EntityTransaction transaction = startTransaction();

                try {
                    statement.evaluate();
                } catch (Exception e) {
                    throw e; // NOSONAR (må tillate dette pga ExceptionRule i test)
                } finally {
                    transaction.rollback();
                    getEntityManager().clear();
                }
            }

            private EntityTransaction startTransaction() {
                EntityTransaction transaction = getEntityManager().getTransaction();

                transaction.begin();
                return transaction;
            }
        };
    }

    protected EntityManager createEntityManager() {
        return emf.createEntityManager();
    }

}
