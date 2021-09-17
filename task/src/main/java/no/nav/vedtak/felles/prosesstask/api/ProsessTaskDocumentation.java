package no.nav.vedtak.felles.prosesstask.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Beskrivelse/dokumentasjon av en prosesstask. Primært for dokumentasjon i koden
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
