package no.nav.vedtak.felles.prosesstask.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.enterprise.inject.Stereotype;
import javax.enterprise.util.Nonbinding;
import javax.inject.Qualifier;

/**
 * Marker type som implementerer interface {@link ProsessTaskHandler}.<br>
 * Dette er en CDI stereotype som også angir at den skal kjøres i en transaksjon.
 * <p>
 * <h3>Eksempel</h3>
 * Merk skal ha både {@link ProsessTask} annotation + {@link ProsessTaskHandler} interface!!!
 * <p>
 * <p>
 * <pre>
 * &#64;Dependent
 * &#64;ProsessTask("vuin.happyTask")
 * public class HappyTask implements ProsessTaskHandler {
 *
 *     private static final Logger log = LoggerFactory.getLogger(HappyTask.class);
 *
 *     &#64;Override
 *     public void doTask(ProsessTaskData prosessTaskData) {
 *         log.info("I am a HAPPY task :-)");
 *     }
 *
 * }
 * </pre>
 */
@Qualifier
@Stereotype
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Documented
public @interface ProsessTask {

    /**
     * Settes til task type og må mathe task_type i prosesstasktabellen.
     * Markerer implementasjonen slik at det kan oppdages runtime.
     * <p>
     * Må spesifiseres.
     */
    String value();

    /**
     * Cron-expression to schedule next instance of a repeating task.
     */
    @Nonbinding
    String cronExpression() default "";

    /**
     * Parameters to configure default retry policy
     * - Maximum number of failed runs before giving up = initial + n retries.
     * - Delay in seconds between initial run and first retry.
     * - Delay in seconds between first retry and later retries = retryNo * thenDelay.
     */
    @Nonbinding
    int maxFailedRuns() default 3;

    @Nonbinding
    int firstDelay() default 30;

    @Nonbinding
    int thenDelay() default 60;

}
