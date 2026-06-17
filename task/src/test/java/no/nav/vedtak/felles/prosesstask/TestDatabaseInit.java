package no.nav.vedtak.felles.prosesstask;

import no.nav.foreldrepenger.konfig.Environment;
import no.nav.vedtak.felles.jpa.jdbc.DataSourceHolder;
import no.nav.vedtak.felles.testutilities.db.MigrationUtil;

/**
 * Initielt skjemaoppsett + migrering av unittest-skjemaer
 */
public final class TestDatabaseInit {
    private static final String DB_SCRIPT_LOCATION = "db/migration";

    public static synchronized void settOppDatasourceOgMigrer(String jdbcUrl, String username, String password, String databaseNavn) {
        var ds =  MigrationUtil.createLocalBuildTestDataSource(jdbcUrl, username, password);
        MigrationUtil.migrateLocalBuildTest(ds, getScriptLocation(databaseNavn));
        DataSourceHolder.initialize(ds);
    }

    private static String getScriptLocation(String databaseNavn) {
        if (Environment.current().getProperty("maven.cmd.line.args") != null) {
            return classpathScriptLocation(databaseNavn);
        }
        return fileScriptLocation(databaseNavn);
    }

    private static String classpathScriptLocation(String databaseNavn) {
        return String.format("classpath:/%s/%s/", DB_SCRIPT_LOCATION, databaseNavn);
    }

    private static String fileScriptLocation(String databaseNavn) {
        var relativePath = String.format("task/src/test/resources/%s/%s", DB_SCRIPT_LOCATION, databaseNavn);
        return MigrationUtil.getScriptLocation(relativePath);
    }

}
