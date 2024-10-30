package no.nav.vedtak.felles.prosesstask;

import no.nav.foreldrepenger.konfig.Environment;

import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

public class JpaOracleTestcontainerExtension extends JpaExtension {
    private static final String TEST_DB_CONTAINER = Environment.current().getProperty("testcontainer.oracle.test.db", String.class, "gvenzl/oracle-free:23-slim-faststart");
    private static final OracleContainer TEST_DATABASE;

    static {
        TEST_DATABASE = new OracleContainer(DockerImageName.parse(TEST_DB_CONTAINER)).withReuse(true);
        TEST_DATABASE.start();
        TestDatabaseInit.settOppDatasourceOgMigrer(TEST_DATABASE.getJdbcUrl(), TEST_DATABASE.getUsername(), TEST_DATABASE.getPassword(), "oracle");
    }
}
