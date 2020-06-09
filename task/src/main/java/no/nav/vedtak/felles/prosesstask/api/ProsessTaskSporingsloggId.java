package no.nav.vedtak.felles.prosesstask.api;

/**
 * Inneholder sporingslogg id'er brukt av prosess task for sikkerhet/sporingslogging.
 */
public enum ProsessTaskSporingsloggId {

    FNR("fnr"),
    
    AKTOR_ID("aktorId"),

    FAGSAK_ID("fagsakId"),
    
    BEHANDLING_ID("behandlingId"),

    BEHANDLING_UUID("behandlingUuid"),

    PROSESS_TASK_STATUS("prosesstask.status"),
    
    PROSESS_TASK_KJORETIDSINTERVALL("prosesstask.kjoretidsintervall"),
    ;

    private String eksternKode;

    ProsessTaskSporingsloggId(String eksternKode) {
        this.eksternKode = eksternKode;
    }

    public String getSporingsloggKode() {
        return eksternKode;
    }
}
