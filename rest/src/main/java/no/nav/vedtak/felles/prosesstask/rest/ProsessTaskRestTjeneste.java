package no.nav.vedtak.felles.prosesstask.rest;

import java.net.HttpURLConnection;
import java.util.List;

import jakarta.ws.rs.BeanParam;

import jakarta.ws.rs.PathParam;

import no.nav.vedtak.felles.prosesstask.rest.dto.IkkeFerdigProsessTaskStatusEnum;

import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskStatusDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.StatusFilterDto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;
import no.nav.vedtak.felles.prosesstask.rest.app.ProsessTaskApplikasjonTjeneste;
import no.nav.vedtak.felles.prosesstask.rest.dto.FeiletProsessTaskDataDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskDataDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskIdDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskOpprettInputDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskRestartInputDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskRestartResultatDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskRetryAllResultatDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskSetFerdigInputDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.SokeFilterDto;
import no.nav.vedtak.sikkerhet.abac.BeskyttetRessurs;
import no.nav.vedtak.sikkerhet.abac.TilpassetAbacAttributt;
import no.nav.vedtak.sikkerhet.abac.beskyttet.ActionType;

@OpenAPIDefinition(tags = @Tag(name = "prosesstask", description = "Håndtering av asynkrone oppgaver i form av prosesstask"))
@Path("/prosesstask")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@Transactional
public class ProsessTaskRestTjeneste {

    private static final Logger LOG = LoggerFactory.getLogger(ProsessTaskRestTjeneste.class);
    private static final String ABAC_DRIFT_ATTRIBUTT = "abac.attributt.drift";

    private ProsessTaskApplikasjonTjeneste prosessTaskApplikasjonTjeneste;

    ProsessTaskRestTjeneste() {
        // REST CDI
    }

    @Inject
    public ProsessTaskRestTjeneste(ProsessTaskApplikasjonTjeneste prosessTaskApplikasjonTjeneste) {
        this.prosessTaskApplikasjonTjeneste = prosessTaskApplikasjonTjeneste;
    }

    @POST
    @Path("/create")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(description = "Oppretter en prosess task i henhold til request", summary = "Oppretter en ny task klar for kjøring.", tags = "prosesstask", responses = {
        @ApiResponse(responseCode = "202", description = "Prosesstaskens oppdatert informasjon", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProsessTaskDataDto.class))),
        @ApiResponse(responseCode = "500", description = "Feilet pga ukjent feil eller tekniske/funksjonelle feil")
    })
    @BeskyttetRessurs(actionType = ActionType.CREATE, property = ABAC_DRIFT_ATTRIBUTT)
    public ProsessTaskDataDto createProsessTask(@Parameter(description = "Informasjon for restart en eksisterende prosesstask") @TilpassetAbacAttributt(supplierClass = AbacEmptySupplier.class) @Valid ProsessTaskOpprettInputDto inputDto) {
        // kjøres manuelt for å avhjelpe feilsituasjon, da er det veldig greit at det blir logget!
        var sanitizedTaskType = inputDto.getTaskType().replace("\n", "").replace("\r", "").trim();
        LOG.info("Oppretter prossess task {}", sanitizedTaskType);
        return prosessTaskApplikasjonTjeneste.opprettTask(inputDto);
    }

    @POST
    @Path("/launch/{prosessTaskId}/{prosessTaskStatus}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(description = "Restarter en eksisterende prosesstask.", summary =
        "En allerede FERDIG prosesstask kan ikke restartes. Dette endepunktet vil tvinge tasken til å trigge uavhengig av maks antall forsøk", tags = "prosesstask", responses = {
        @ApiResponse(responseCode = "200", description = "Prosesstaskens oppdatert informasjon", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProsessTaskRestartResultatDto.class))),
        @ApiResponse(responseCode = "500", description = "Feilet pga ukjent feil eller tekniske/funksjonelle feil")
    })
    @BeskyttetRessurs(actionType = ActionType.CREATE, property = ABAC_DRIFT_ATTRIBUTT, sporingslogg = false)
    public ProsessTaskRestartResultatDto restartProsessTask(@Parameter(description = "Informasjon for restart en eksisterende prosesstask") @TilpassetAbacAttributt(supplierClass = AbacEmptySupplier.class) @Valid @BeanParam ProsessTaskRestartInputDto restartInputDto) {
        // kjøres manuelt for å avhjelpe feilsituasjon, da er det veldig greit at det blir logget!
        LOG.info("Restarter prossess task {}", restartInputDto.getProsessTaskId());
        return prosessTaskApplikasjonTjeneste.flaggProsessTaskForRestart(restartInputDto);
    }

    @POST
    @Path("/retryall")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(description = "Restarter alle prosesstask med status FEILET.", summary = "Dette endepunktet vil tvinge feilede tasks til å trigge ett forsøk uavhengig av maks antall forsøk", tags = "prosesstask", responses = {
        @ApiResponse(responseCode = "200", description = "Response med liste av prosesstasks som restartes", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProsessTaskRetryAllResultatDto.class))),
        @ApiResponse(responseCode = "500", description = "Feilet pga ukjent feil eller tekniske/funksjonelle feil")
    })
    @BeskyttetRessurs(actionType = ActionType.CREATE, property = ABAC_DRIFT_ATTRIBUTT, sporingslogg = false)
    public ProsessTaskRetryAllResultatDto retryAllProsessTask() {
        // kjøres manuelt for å avhjelpe feilsituasjon, da er det veldig greit at det blir logget!
        LOG.info("Restarter alle prossess task i status FEILET");
        return prosessTaskApplikasjonTjeneste.flaggAlleFeileteProsessTasksForRestart();
    }

    @POST
    @Path("/list/{prosessTaskStatus}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(description = "Lister prosesstasker med angitt status.", tags = "prosesstask", responses = {
        @ApiResponse(responseCode = "200", description = "Liste over prosesstasker, eller tom liste når angitt/default søkefilter ikke finner noen prosesstasker", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProsessTaskDataDto.class)))
    })
    @BeskyttetRessurs(actionType = ActionType.READ, property = ABAC_DRIFT_ATTRIBUTT)
    public List<ProsessTaskDataDto> finnProsessTasks(@Parameter(description = "Liste av statuser som skal hentes.") @TilpassetAbacAttributt(supplierClass = AbacEmptySupplier.class) @Valid @PathParam("prosessTaskStatus")
                                                     IkkeFerdigProsessTaskStatusEnum finnTaskStatus) {
        var statusFilterDto = new StatusFilterDto();
        statusFilterDto.setProsessTaskStatuser(List.of(new ProsessTaskStatusDto(finnTaskStatus.name())));
        return prosessTaskApplikasjonTjeneste.finnAlle(statusFilterDto);
    }

    @POST
    @Path("/search")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(description = "Søker etter prosesstask med angitt tekst i properties.", tags = "prosesstask", responses = {
        @ApiResponse(responseCode = "200", description = "Liste over prosesstasker, eller tom liste når angitt/default søkefilter ikke finner noen prosesstasker", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProsessTaskDataDto.class)))
    })
    @BeskyttetRessurs(actionType = ActionType.READ, property = ABAC_DRIFT_ATTRIBUTT)
    public List<ProsessTaskDataDto> searchProsessTasks(@Parameter(description = "Søkefilter for å begrense resultatet av returnerte prosesstask.") @TilpassetAbacAttributt(supplierClass = AbacEmptySupplier.class) @Valid SokeFilterDto sokeFilterDto) {
        return prosessTaskApplikasjonTjeneste.søk(sokeFilterDto);
    }

    @POST
    @Path("/feil/{prosessTaskId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(description = "Henter informasjon om feilet prosesstask med angitt prosesstask-id", tags = "prosesstask", responses = {
        @ApiResponse(responseCode = "200", description = "Angitt prosesstask-id finnes", content = @Content(mediaType = "application/json", schema = @Schema(implementation = FeiletProsessTaskDataDto.class))),
        @ApiResponse(responseCode = "404", description = "Tom respons når angitt prosesstask-id ikke finnes"),
        @ApiResponse(responseCode = "400", description = "Feil input")
    })
    @BeskyttetRessurs(actionType = ActionType.READ, property = ABAC_DRIFT_ATTRIBUTT)
    public Response finnFeiletProsessTask(@NotNull @Parameter(description = "Prosesstask-id for feilet prosesstask") @TilpassetAbacAttributt(supplierClass = AbacEmptySupplier.class) @Valid @BeanParam ProsessTaskIdDto prosessTaskIdDto) {
        var resultat = prosessTaskApplikasjonTjeneste.finnFeiletProsessTask(prosessTaskIdDto.getProsessTaskId());
        if (resultat.isPresent()) {
            return Response.ok(resultat.get()).build();
        }
        return Response.status(HttpURLConnection.HTTP_NOT_FOUND).build();
    }

    @POST
    @Path("/setferdig/{prosessTaskId}/{prosessTaskStatus}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(description = "Setter feilet prosesstask med angitt prosesstask-id til FERDIG (kjøres ikke)", tags = "prosesstask", responses = {
        @ApiResponse(responseCode = "200", description = "Angitt prosesstask-id satt til status FERDIG"),
        @ApiResponse(responseCode = "500", description = "Feilet pga ukjent feil eller tekniske/funksjonelle feil")
    })
    @BeskyttetRessurs(actionType = ActionType.CREATE, property = ABAC_DRIFT_ATTRIBUTT)
    public Response setFeiletProsessTaskFerdig(@NotNull @Parameter(description = "Prosesstask-id for feilet prosesstask") @TilpassetAbacAttributt(supplierClass = AbacEmptySupplier.class) @Valid @BeanParam ProsessTaskSetFerdigInputDto prosessTaskIdDto) {
        prosessTaskApplikasjonTjeneste.setProsessTaskFerdig(prosessTaskIdDto.getProsessTaskId(), mapToProsessTaskStatus(prosessTaskIdDto.getNaaVaaerendeStatus()));
        return Response.ok().build();
    }

    private ProsessTaskStatus mapToProsessTaskStatus(IkkeFerdigProsessTaskStatusEnum ikkeFerdigStatus) {
        return switch (ikkeFerdigStatus) {
            case FEILET -> ProsessTaskStatus.FEILET;
            case VENTER_SVAR -> ProsessTaskStatus.VENTER_SVAR;
            case KLAR -> ProsessTaskStatus.KLAR;
            case SUSPENDERT -> ProsessTaskStatus.SUSPENDERT;
            case VETO -> ProsessTaskStatus.VETO;
        };
    }
}
