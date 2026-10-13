package no.nav.vedtak.felles.prosesstask.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import jakarta.enterprise.inject.spi.CDI;
import no.nav.vedtak.felles.jpa.savepoint.RunWithSavepoint;
import no.nav.vedtak.felles.jpa.savepoint.SavepointRolledbackException;
import no.nav.vedtak.felles.prosesstask.JpaPostgresTestcontainerExtension;
import no.nav.vedtak.felles.testutilities.db.EntityManagerAwareTest;

@ExtendWith(JpaPostgresTestcontainerExtension.class)
class CdiEntityManagerTest extends EntityManagerAwareTest {

    @Test
    void unwrapsRequestScopedEntityManagerWithoutWeldInternals() {
        var proxy = getEntityManager();
        var target = CDI.current().getBeanContainer().unwrapClientProxy(proxy);
        assertThat(proxy).isNotSameAs(target);
        assertThat(proxy.unwrap(Session.class)).isSameAs(target.unwrap(Session.class));
        var valid = proxy.unwrap(Session.class).doReturningWork(connection -> connection.isValid(5));
        assertThat(valid).isTrue();
    }

    @Test
    void savepointRollbackPreservesTheRequestTransaction() {
        var savepoint = new RunWithSavepoint(getEntityManager());
        assertThatThrownBy(() -> savepoint.doWork(() -> {
            throw new IllegalStateException("rollback");
        })).isInstanceOf(SavepointRolledbackException.class).hasRootCauseMessage("rollback");
        assertThat(getEntityManager().getTransaction().isActive()).isTrue();
        assertThat(getEntityManager().getTransaction().getRollbackOnly()).isFalse();
        var result = savepoint.doWork(() -> getEntityManager().createNativeQuery("select 1", Integer.class).getSingleResult());
        assertThat(result).isEqualTo(1);
    }
}
