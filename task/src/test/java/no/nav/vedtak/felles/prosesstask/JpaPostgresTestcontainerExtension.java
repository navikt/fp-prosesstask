package no.nav.vedtak.felles.prosesstask;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import no.nav.foreldrepenger.konfig.Environment;

public class JpaPostgresTestcontainerExtension extends JpaExtension {
    private static final String TEST_DB_CONTAINER = Environment.current().getProperty("testcontainer.postgres.test.db", String.class, "postgres:17-alpine");
    private static final JdbcDatabaseContainer TEST_DATABASE;

    static {
        TEST_DATABASE = new PostgreSQLContainer<>(DockerImageName.parse(TEST_DB_CONTAINER)).withReuse(true);
        TEST_DATABASE.start();
        TestDatabaseInit.settOppDatasourceOgMigrer(TEST_DATABASE.getJdbcUrl(), TEST_DATABASE.getUsername(), TEST_DATABASE.getPassword(), "postgres");
    }
}
