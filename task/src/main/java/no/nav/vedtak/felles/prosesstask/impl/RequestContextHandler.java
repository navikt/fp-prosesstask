package no.nav.vedtak.felles.prosesstask.impl;

import java.util.function.Supplier;

import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.inject.spi.CDI;

/**
 * Kjør angitt funksjon med RequestScope aktivt.
 */
final class RequestContextHandler {

    private RequestContextHandler() {
    }

    public static <V> V doWithRequestContext(Supplier<V> supplier) {

        try (var handle = CDI.current().select(RequestContextController.class).getHandle()) {
            var controller = handle.get();
            var activated = controller.activate();
            try {
                return supplier.get();
            } finally {
                if (activated) {
                    controller.deactivate();
                }
            }
        }
    }

}
