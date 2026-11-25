package no.nav.vedtak.felles.prosesstask.api;

public enum ProsessTaskStatus {

    KLAR,
    KJOERT,
    FERDIG,
    VENTER_SVAR,
    FEILET
    ;

    public String getDbKode() {
        return this.name();
    }

    @Override
    public String toString() {
        return getDbKode();
    }

    public boolean erKjørt() {
        return this == FERDIG || this == KJOERT;
    }

    public boolean erIkkeFerdig() {
        return this != FERDIG;
    }

    public boolean erIkkeKjørt() {
        return this != FERDIG && this != KJOERT;
    }
}
