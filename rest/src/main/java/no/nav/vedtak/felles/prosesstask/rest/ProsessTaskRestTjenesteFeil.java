package no.nav.vedtak.felles.prosesstask.rest;

import no.nav.vedtak.feil.Feil;
import no.nav.vedtak.feil.FeilFactory;
import no.nav.vedtak.feil.LogLevel;
import no.nav.vedtak.feil.deklarasjon.DeklarerteFeil;
import no.nav.vedtak.feil.deklarasjon.TekniskFeil;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;

public interface ProsessTaskRestTjenesteFeil extends DeklarerteFeil {
    ProsessTaskRestTjenesteFeil FACTORY = FeilFactory.create(ProsessTaskRestTjenesteFeil.class);

    String KAN_IKKE_RESTARTE_FERDIG_TASK_FEIL_ID = "PT-711948";
    String MAA_ANGI_NAVARENDE_STATUS_FEIL_ID = "PT-306456";
    String STATUS_IKKE_FEILET = "PT-507456";
    String UKJENT_TASK_FEIL_ID = "PT-752429";

    @TekniskFeil(feilkode = KAN_IKKE_RESTARTE_FERDIG_TASK_FEIL_ID,
            feilmelding = "Prosesstasken %s har allerede kjørt ferdig, og kan ikke kjøres på nytt",
            logLevel = LogLevel.WARN)
    Feil kanIkkeRestarteEnFerdigKjørtProsesstask(long prosessTaskId);

    @TekniskFeil(feilkode = STATUS_IKKE_FEILET,
            feilmelding = "Prosesstasken %s har ikke status %s og kan ikke settes FERDIG",
            logLevel = LogLevel.WARN)
    Feil taskIkkeIRettStatus(long prosessTaskId, ProsessTaskStatus status);

    @TekniskFeil(feilkode = MAA_ANGI_NAVARENDE_STATUS_FEIL_ID,
            feilmelding = "Prosesstasken %s har ikke status KLAR. For restart må nåværende status angis.",
            logLevel = LogLevel.WARN)
    Feil måAngiNåværendeProsesstaskStatusForRestart(long prosessTaskId);

    @TekniskFeil(feilkode = UKJENT_TASK_FEIL_ID,
            feilmelding = "Ingen prosesstask med id %s eksisterer",
            logLevel = LogLevel.WARN)
    Feil ukjentProsessTaskIdAngitt(long ukjentProsessTaskId);

}
