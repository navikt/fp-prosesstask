package no.nav.vedtak.felles.prosesstask.impl.util;
import javax.annotation.Priority;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.spi.CDI;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import javax.persistence.EntityManager;
import javax.transaction.Transaction;
import javax.transaction.Transactional;

/**
 * Basic Local Transactional Interceptor. 
 */
@Transactional
@Interceptor
@Priority(Interceptor.Priority.APPLICATION + 10)
@Dependent
public class TransactionInterceptor {

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
    private EntityManager getEntityManager(InvocationContext ic) {
        EntityManager em = CURRENT.select(EntityManager.class).get();
        return em;
    }

    private void destroyEntityManager(EntityManager entityManager) {
        CURRENT.destroy(entityManager);
    }

    @AroundInvoke
    public Object wrapTransaction(final InvocationContext invocationContext) throws Exception {

        EntityManager entityManager = getEntityManager(invocationContext);

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
