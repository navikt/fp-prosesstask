package no.nav.vedtak.felles.prosesstask.api;

/**
 * Utvalgte read-only attributter for en prosesstasktype
 */
public record ProsessTaskTypeInfo(String kode, int maksForsøk) {

    public int getMaksForsøk() {
        return maksForsøk;
    }
}
