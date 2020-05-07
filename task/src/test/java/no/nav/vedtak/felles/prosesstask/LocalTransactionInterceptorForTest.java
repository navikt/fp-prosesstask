package no.nav.vedtak.felles.prosesstask;
import javax.annotation.Priority;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.spi.CDI;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import no.nav.vedtak.felles.jpa.TransactionHandler;

/**
 * Local Transaction Manager, kun for test.
 */
@Transactional
@Interceptor
@Priority(Interceptor.Priority.APPLICATION + 10)
@Dependent
public class LocalTransactionInterceptorForTest {

    private static final class TransactionHandlerInvocation extends TransactionHandler<Object> {
        private final InvocationContext invocationContext;

        private TransactionHandlerInvocation(InvocationContext invocationContext) {
            this.invocationContext = invocationContext;
        }

        @Override
        protected Object doWork(EntityManager em) throws Exception {
            return invocationContext.proceed();
        }
    }

    private static final CDI<Object> CURRENT = CDI.current();

    /**
     * Velger riktig EntityManager avh. av annotasjon på Transaction
     */
    private EntityManager getEntityManager() {
        EntityManager em = CURRENT.select(EntityManager.class).get();
        return em;
    }

    private void destroyEntityManager(EntityManager entityManager) {
        CURRENT.destroy(entityManager);
    }

    @AroundInvoke
    public Object wrapTransaction(final InvocationContext invocationContext) throws Exception {

        EntityManager entityManager = getEntityManager();

        boolean isActiveTx = entityManager.getTransaction().isActive();

        try {
            Object result = new TransactionHandlerInvocation(invocationContext).apply(entityManager);
            return result;
        } finally {
            if (!isActiveTx) {
                destroyEntityManager(entityManager);
            }
        }

    }

}
