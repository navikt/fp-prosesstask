package no.nav.vedtak.felles.prosesstask.api;

/**
 * Rekkefølge / ordning av tasks på tvers av tasks og taskgrupper (enkelt-tasks lagres som singleton gruppe)
 * Brukes for å styre parallellisme / serialisering av taskgrupper med nøkkel.
 * - GRUPPE: Lokal sekvens innen taskgruppe.
 * - GLOBAL: Global ordning basert på parameter som må angis i task. Støtter sekvens innen taskgruppe.
 * - SAKSNUMMER: Global ordning basert på saksnummer. Støtter sekvens innen taskgruppe.
 * - TASK_TYPE: Alle tasks har samme rekkefølge (uordnet outbox). Bør ikke brukes sammen med nesteKjøringEtter eller ved regelmessige feil/timeouts
 */
public enum TaskRekkefølge {

    GRUPPE,
    GLOBAL,
    SAKSNUMMER, // Convenience for saksnummer som global nøkkel
    TASK_TYPE
    ;

    public boolean global() {
        return this == SAKSNUMMER || this == GLOBAL;
    }

}
