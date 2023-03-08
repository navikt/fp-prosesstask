package no.nav.vedtak.felles.prosesstask.api;

public enum ProsessTaskStatus {

    KLAR("KLAR"), 
    KJOERT("KJOERT"),
    FERDIG("FERDIG"), 
    VENTER_SVAR("VENTER_SVAR"), 
    VETO("VETO"), 
    SUSPENDERT("SUSPENDERT"), 
    FEILET("FEILET") 
    ;

    private String dbKode;

    ProsessTaskStatus(String dbKode) {
        this.dbKode = dbKode;
    }

    public String getDbKode() {
        return dbKode;
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
}
