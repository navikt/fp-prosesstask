package no.nav.vedtak.felles.prosesstask.rest;

import java.util.function.Function;

import no.nav.vedtak.sikkerhet.abac.AbacDataAttributter;

/**
 * Mapper som returnerer default empty {@link AbacDataAttributter} slik at Abac tilgangskontroll gjøres(men uten custom nøkler, kun user sin
 * kontekst).
 * <p>
 * 
 * <pre>
 * public void myRestMethod(@NotNull @TilpassetAbacAttributt(supplierClass=AbacEmptySupplier.class) MyDto dtoWithNoAbacAttributtes) {
 *       ...
 * }
 * </pre>
 */
public class AbacEmptySupplier implements Function<Object, AbacDataAttributter> {

    @Override
    public AbacDataAttributter apply(Object obj) {
        return AbacDataAttributter.opprett();
    }
}