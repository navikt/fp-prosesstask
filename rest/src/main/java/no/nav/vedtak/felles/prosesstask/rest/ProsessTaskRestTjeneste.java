package no.nav.vedtak.felles.prosesstask.rest;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Optional;

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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;
import no.nav.vedtak.felles.prosesstask.rest.app.ProsessTaskApplikasjonTjeneste;
import no.nav.vedtak.felles.prosesstask.rest.dto.FeiletProsessTaskDataDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.FeiletProsessTaskStatusEnum;
import no.nav.vedtak.felles.prosesstask.rest.dto.IkkeFerdigProsessTaskStatusEnum;
import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskDataDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskOpprettInputDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskRestartResultatDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.ProsessTaskRetryAllResultatDto;
import no.nav.vedtak.felles.prosesstask.rest.dto.SokeFilterDto;
import no.nav.vedtak.log.util.LoggerUtils;
import no.nav.vedtak.sikkerhet.abac.BeskyttetRessurs;
import no.nav.vedtak.sikkerhet.abac.TilpassetAbacAttributt;
import no.nav.vedtak.sikkerhet.abac.beskyttet.ActionType;
import no.nav.vedtak.sikkerhet.abac.beskyttet.ResourceType;

@OpenAPIDefinition(tags = @Tag(name = "prosesstask", description = "Håndtering av asynkrone oppgaver i form av prosesstask"))
@Path("/prosesstask")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@Transactional
public class ProsessTaskRestTjeneste {

    private static final Logger LOG = LoggerFactory.getLogger(ProsessTaskRestTjeneste.class);

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
    @BeskyttetRessurs(actionType = ActionType.CREATE, resourceType = ResourceType.DRIFT, sporingslogg = false) // Vurdere å ta med attributt for sak/behandling + logge - etter avklaring Drift/Write
    public ProsessTaskDataDto createProsessTask(@Parameter(description = "Informasjon for opprettelse av en prosesstask") @TilpassetAbacAttributt(supplierClass = AbacEmptySupplier.class) @Valid ProsessTaskOpprettInputDto inputDto) {
        // kjøres manuelt for å avhjelpe feilsituasjon, da er det veldig greit at det blir logget!
        LOG.info("Oppretter prossess task {}", LoggerUtils.toStringWithoutLineBreaks(inputDto.taskType()));
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
    @BeskyttetRessurs(actionType = ActionType.CREATE, resourceType = ResourceType.DRIFT, sporingslogg = false)
    public ProsessTaskRestartResultatDto restartProsessTask(
            @Parameter(description = "Prosesstask-id for eksisterende prosesstask") @TilpassetAbacAttributt(supplierClass = AbacEmptySupplier.class) @Valid @NotNull @Min(0) @Max(Long.MAX_VALUE) @PathParam("prosessTaskId") Long prosessTaskId,
            @Parameter(description = "Nåværende status for prosesstasken") @TilpassetAbacAttributt(supplierClass = AbacEmptySupplier.class) @Valid @PathParam("prosessTaskStatus") FeiletProsessTaskStatusEnum prosessTaskStatus) {
        // kjøres manuelt for å avhjelpe feilsituasjon, da er det veldig greit at det blir logget!
        LOG.info("Restarter prossess task {}", prosessTaskId);
        return prosessTaskApplikasjonTjeneste.flaggProsessTaskForRestart(prosessTaskId, mapToProsessTaskStatus(prosessTaskStatus));
    }

    @POST
    @Path("/retryall")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(description = "Restarter alle prosesstask med status FEILET.", summary = "Dette endepunktet vil tvinge feilede tasks til å trigge ett forsøk uavhengig av maks antall forsøk", tags = "prosesstask", responses = {
        @ApiResponse(responseCode = "200", description = "Response med antall prosesstasks som restartes", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProsessTaskRetryAllResultatDto.class))),
        @ApiResponse(responseCode = "500", description = "Feilet pga ukjent feil eller tekniske/funksjonelle feil")
    })
    @BeskyttetRessurs(actionType = ActionType.CREATE, resourceType = ResourceType.DRIFT, sporingslogg = false)
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
    @BeskyttetRessurs(actionType = ActionType.READ, resourceType = ResourceType.DRIFT, sporingslogg = false)
    public List<ProsessTaskDataDto> finnProsessTasks(@Parameter(description = "Liste av statuser som skal hentes.") @TilpassetAbacAttributt(supplierClass = AbacEmptySupplier.class) @Valid @PathParam("prosessTaskStatus")
                                                     IkkeFerdigProsessTaskStatusEnum finnTaskStatus) {
        var status = Optional.ofNullable(mapToProsessTaskStatus(finnTaskStatus)).map(List::of).orElseGet(List::of);
        return prosessTaskApplikasjonTjeneste.finnAlle(status);
    }

    @POST
    @Path("/search")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(description = "Søker etter prosesstask med angitt tekst i properties.", tags = "prosesstask", responses = {
        @ApiResponse(responseCode = "200", description = "Liste over prosesstasker, eller tom liste når angitt/default søkefilter ikke finner noen prosesstasker", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProsessTaskDataDto.class)))
    })
    @BeskyttetRessurs(actionType = ActionType.READ, resourceType = ResourceType.DRIFT, sporingslogg = false)
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
    @BeskyttetRessurs(actionType = ActionType.READ, resourceType = ResourceType.DRIFT, sporingslogg = false)
    public Response finnFeiletProsessTask(
            @Parameter(description = "Prosesstask-id for feilet prosesstask") @TilpassetAbacAttributt(supplierClass = AbacEmptySupplier.class) @Valid @NotNull @Min(0) @Max(Long.MAX_VALUE) @PathParam("prosessTaskId") Long prosessTaskId) {
        var resultat = prosessTaskApplikasjonTjeneste.finnFeiletProsessTask(prosessTaskId);
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
    @BeskyttetRessurs(actionType = ActionType.CREATE, resourceType = ResourceType.DRIFT, sporingslogg = false) // Vurdere å lese task + ta med attributt for sak/behandling + logge - etter avklaring Drift/Write
    public Response setFeiletProsessTaskFerdig(
            @Parameter(description = "Prosesstask-id for prosesstask") @TilpassetAbacAttributt(supplierClass = AbacEmptySupplier.class) @Valid @NotNull @Min(0) @Max(Long.MAX_VALUE) @PathParam("prosessTaskId") Long prosessTaskId,
            @Parameter(description = "Nåværende status for prosesstasken") @TilpassetAbacAttributt(supplierClass = AbacEmptySupplier.class) @Valid @PathParam("prosessTaskStatus") IkkeFerdigProsessTaskStatusEnum prosessTaskStatus) {
        prosessTaskApplikasjonTjeneste.setProsessTaskFerdig(prosessTaskId, mapToProsessTaskStatus(prosessTaskStatus));
        return Response.ok().build();
    }

    private ProsessTaskStatus mapToProsessTaskStatus(IkkeFerdigProsessTaskStatusEnum ikkeFerdigStatus) {
        return switch (ikkeFerdigStatus) {
            case FEILET -> ProsessTaskStatus.FEILET;
            case VENTER_SVAR -> ProsessTaskStatus.VENTER_SVAR;
            case KLAR -> ProsessTaskStatus.KLAR;
            case null -> null;
        };
    }

    private ProsessTaskStatus mapToProsessTaskStatus(FeiletProsessTaskStatusEnum feiletStatus) {
        return switch (feiletStatus) {
            case FEILET -> ProsessTaskStatus.FEILET;
            case VENTER_SVAR -> ProsessTaskStatus.VENTER_SVAR;
            case null -> null;
        };
    }
}
