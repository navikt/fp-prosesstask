package no.nav.vedtak.felles.prosesstask.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.spi.CDI;
import no.nav.vedtak.felles.prosesstask.api.ProsessTask;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskHandler;
import no.nav.vedtak.felles.prosesstask.api.TaskType;
import no.nav.vedtak.felles.testutilities.cdi.CdiAwareExtension;

@ExtendWith(CdiAwareExtension.class)
class ProsessTaskHandlerRefTest {

    @Test
    void destroysInterceptedDependentHandlerExactlyOnce() {
        DependentHandler.DESTROYED.set(0);
        var reference = ProsessTaskHandlerRef.lookup(new TaskType("cdi.dependent"));
        assertThat(reference.getBean().getClass()).isNotEqualTo(DependentHandler.class);
        assertThat(reference.getHandlerClass()).isEqualTo(DependentHandler.class);
        assertThat(reference.requiredProperties()).containsExactly("required");
        assertThat(reference.cronExpression()).isNotNull();
        assertThat(reference.retryPolicy()).isNotNull();
        reference.close();
        reference.close();
        assertThat(DependentHandler.DESTROYED.get()).isEqualTo(1);
    }

    @Test
    void readsMetadataWithoutInstantiatingHandler() {
        DependentHandler.CREATED.set(0);
        DependentHandler.DESTROYED.set(0);
        try (var reference = ProsessTaskHandlerRef.lookup(new TaskType("cdi.dependent"))) {
            assertThat(reference.getHandlerClass()).isEqualTo(DependentHandler.class);
            assertThat(reference.cronExpression()).isNotNull();
        }
        assertThat(DependentHandler.CREATED.get()).isZero();
        assertThat(DependentHandler.DESTROYED.get()).isZero();
    }

    @Test
    void preservesApplicationScopedHandler() {
        ApplicationHandler.DESTROYED.set(0);
        var instance = CDI.current().select(ApplicationHandler.class, new ProsessTaskHandlerRef.ProsessTaskLiteral("cdi.application")).get();
        try (var reference = ProsessTaskHandlerRef.lookup(new TaskType("cdi.application"))) {
            assertThat(reference.getBean()).isSameAs(instance);
            assertThat(reference.getHandlerClass()).isEqualTo(ApplicationHandler.class);
            reference.doTask(ProsessTaskData.forTaskType(new TaskType("cdi.application")));
        }
        assertThat(ApplicationHandler.DESTROYED.get()).isZero();
        assertThat(instance.executions()).isEqualTo(1);
    }

    @Dependent
    @ActivateRequestContext
    @ProsessTask(value = "cdi.dependent", cronExpression = "0 0 0 * * *", maxFailedRuns = 5)
    public static class DependentHandler implements ProsessTaskHandler {
        static final AtomicInteger CREATED = new AtomicInteger();
        static final AtomicInteger DESTROYED = new AtomicInteger();

        @PostConstruct
        void init() {
            CREATED.incrementAndGet();
        }

        @Override
        public Set<String> requiredProperties() {
            return Set.of("required");
        }

        @Override
        public void doTask(ProsessTaskData data) {}

        @PreDestroy
        void destroy() {
            DESTROYED.incrementAndGet();
        }
    }

    @ApplicationScoped
    @ProsessTask("cdi.application")
    public static class ApplicationHandler implements ProsessTaskHandler {
        static final AtomicInteger DESTROYED = new AtomicInteger();
        private int executions;

        @Override
        public void doTask(ProsessTaskData data) {
            executions++;
        }

        public int executions() {
            return executions;
        }

        @PreDestroy
        void destroy() {
            DESTROYED.incrementAndGet();
        }
    }
}
