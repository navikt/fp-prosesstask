package no.nav.vedtak.felles.prosesstask.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Kort beskrivelse av en prosesstask for dokumentasjon i kode
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Documented
public @interface ProsessTaskDocumentation {

    /**
     * Beskrivelse av prosesstask
     */
    String description() default "";

}
