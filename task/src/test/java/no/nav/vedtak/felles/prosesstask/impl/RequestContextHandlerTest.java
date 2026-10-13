package no.nav.vedtak.felles.prosesstask.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.spi.CDI;
import no.nav.vedtak.felles.testutilities.cdi.WeldContext;

class RequestContextHandlerTest {

    @Test
    void nestedCallsPreserveTheOuterRequest() {
        WeldContext.getInstance();
        var first = RequestContextHandler.doWithRequestContext(() -> {
            var id = bean().id();
            assertThat(RequestContextHandler.doWithRequestContext(() -> bean().id())).isEqualTo(id);
            assertThat(bean().id()).isEqualTo(id);
            return id;
        });
        assertInactive();
        assertThat(RequestContextHandler.doWithRequestContext(() -> bean().id())).isNotEqualTo(first);
        assertInactive();
    }

    @Test
    void exceptionDeactivatesTheOwnedRequest() {
        WeldContext.getInstance();
        assertThatThrownBy(() -> RequestContextHandler.doWithRequestContext(() -> {
            bean().id();
            throw new IllegalStateException("test failure");
        })).isInstanceOf(IllegalStateException.class).hasMessage("test failure");
        assertInactive();
    }

    @Test
    void retainsAnExternallyOwnedRequest() {
        var weld = WeldContext.getInstance();
        weld.doWithScope(() -> {
            var id = bean().id();
            assertThat(RequestContextHandler.doWithRequestContext(() -> bean().id())).isEqualTo(id);
            assertThat(bean().id()).isEqualTo(id);
            return id;
        });
        assertInactive();
    }

    private static RequestBean bean() {
        return CDI.current().select(RequestBean.class).get();
    }

    private static void assertInactive() {
        assertThatThrownBy(() -> CDI.current().getBeanManager().getContext(RequestScoped.class))
            .isInstanceOf(ContextNotActiveException.class);
    }

    @RequestScoped
    public static class RequestBean {
        private final UUID id = UUID.randomUUID();

        public UUID id() {
            return id;
        }
    }
}
