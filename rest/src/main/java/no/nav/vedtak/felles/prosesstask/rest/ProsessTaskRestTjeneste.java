package no.nav.vedtak.felles.prosesstask.rest;

import static no.nav.vedtak.sikkerhet.abac.BeskyttetRessursActionAttributt.CREATE;
import static no.nav.vedtak.sikkerhet.abac.BeskyttetRessursActionAttributt.READ;

import java.util.List;
import java.util.Optional;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@OpenAPIDefinition(tags = @Tag(name = "prosesstask", description = "Håndtering av asynkrone oppgaver i form av prosesstask"))
@Path("/prosesstask")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@Transactional
public class ProsessTaskRestTjeneste {

    private static final Logger logger = LoggerFactory.getLogger(ProsessTaskRestTjeneste.class);
    private static final String ABAC_DRIFT_ATTRIBUTT = "abac.attributt.drift";

    private ProsessTaskApplikasjonTjeneste prosessTaskApplikasjonTjeneste;

    public ProsessTaskRestTjeneste() {
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
    @BeskyttetRessurs(action = CREATE, property = ABAC_DRIFT_ATTRIBUTT)
    public ProsessTaskDataDto createProsessTask(@Parameter(description = "Informasjon for restart en eksisterende prosesstask") @TilpassetAbacAttributt(supplierClass = AbacEmptySupplier.class) @Valid ProsessTaskOpprettInputDto inputDto) {
        // kjøres manuelt for å avhjelpe feilsituasjon, da er det veldig greit at det blir logget!
        logger.info("Oppretter prossess task av type {}", inputDto.getTaskType());

        return prosessTaskApplikasjonTjeneste.opprettTask(inputDto);
    }

    @POST
    @Path("/launch")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(description = "Restarter en eksisterende prosesstask.", summary = "En allerede FERDIG prosesstask kan ikke restartes. En prosesstask har normalt et gitt antall forsøk den kan kjøres automatisk. "
            +
            "Dette endepunktet vil tvinge tasken til å trigge uavhengig av maks antall forsøk", tags = "prosesstask", responses = {
            @ApiResponse(responseCode = "200", description = "Prosesstaskens oppdatert informasjon", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProsessTaskRestartResultatDto.class))),
            @ApiResponse(responseCode = "500", description = "Feilet pga ukjent feil eller tekniske/funksjonelle feil")
    })
    @BeskyttetRessurs(action = CREATE, property = ABAC_DRIFT_ATTRIBUTT)
    public ProsessTaskRestartResultatDto restartProsessTask(@Parameter(description = "Informasjon for restart en eksisterende prosesstask") @TilpassetAbacAttributt(supplierClass = AbacEmptySupplier.class) @Valid ProsessTaskRestartInputDto restartInputDto) {
        // kjøres manuelt for å avhjelpe feilsituasjon, da er det veldig greit at det blir logget!
        logger.info("Restarter prossess task {}", restartInputDto.getProsessTaskId());

        return prosessTaskApplikasjonTjeneste.flaggProsessTaskForRestart(restartInputDto);
    }

    @POST
    @Path("/retryall")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(description = "Restarter alle prosesstask med status FEILET.", summary = "Dette endepunktet vil tvinge feilede tasks til å trigge ett forsøk uavhengig av maks antall forsøk", tags = "prosesstask", responses = {
            @ApiResponse(responseCode = "200", description = "Response med liste av prosesstasks som restartes", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProsessTaskRetryAllResultatDto.class))),
            @ApiResponse(responseCode = "500", description = "Feilet pga ukjent feil eller tekniske/funksjonelle feil")
    })
    @BeskyttetRessurs(action = CREATE, property = ABAC_DRIFT_ATTRIBUTT)
    public ProsessTaskRetryAllResultatDto retryAllProsessTask() {
        // kjøres manuelt for å avhjelpe feilsituasjon, da er det veldig greit at det blir logget!
        logger.info("Restarter alle prossess task i status FEILET");

        return prosessTaskApplikasjonTjeneste.flaggAlleFeileteProsessTasksForRestart();
    }

    @POST
    @Path("/list")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(description = "Søker etter prosesstask med mulighet for filtrert søk.", tags = "prosesstask", responses = {
            @ApiResponse(responseCode = "200", description = "Liste over prosesstasker, eller tom liste når angitt/default søkefilter ikke finner noen prosesstasker", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProsessTaskDataDto.class)))
    })
    @BeskyttetRessurs(action = READ, property = ABAC_DRIFT_ATTRIBUTT)
    public List<ProsessTaskDataDto> finnProsessTasks(@Parameter(description = "Søkefilter for å begrense resultatet av returnerte prosesstask.") @TilpassetAbacAttributt(supplierClass = AbacEmptySupplier.class) @Valid SokeFilterDto sokeFilterDto) {
        List<ProsessTaskDataDto> resultat = prosessTaskApplikasjonTjeneste.finnAlle(sokeFilterDto);

        return resultat;
    }

    @POST
    @Path("/feil")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(description = "Henter informasjon om feilet prosesstask med angitt prosesstask-id", tags = "prosesstask", responses = {
            @ApiResponse(responseCode = "200", description = "Angit prosesstask-id finnes", content = @Content(mediaType = "application/json", schema = @Schema(implementation = FeiletProsessTaskDataDto.class))),
            @ApiResponse(responseCode = "404", description = "Tom respons når angitt prosesstask-id ikke finnes"),
            @ApiResponse(responseCode = "400", description = "Feil input")
    })
    @BeskyttetRessurs(action = READ, property = ABAC_DRIFT_ATTRIBUTT)
    public Response finnFeiletProsessTask(@NotNull @Parameter(description = "Prosesstask-id for feilet prosesstask") @TilpassetAbacAttributt(supplierClass = AbacEmptySupplier.class) @Valid ProsessTaskIdDto prosessTaskIdDto) {
        Optional<FeiletProsessTaskDataDto> resultat = prosessTaskApplikasjonTjeneste.finnFeiletProsessTask(prosessTaskIdDto.getProsessTaskId());
        if (resultat.isPresent()) {
            return Response.ok(resultat.get()).build();
        }
        return Response.status(HttpStatus.SC_NOT_FOUND).build();
    }

    @POST
    @Path("/setferdig")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(description = "Setter feilet prosesstask med angitt prosesstask-id til FERDIG (kjøres ikke)", tags = "prosesstask", responses = {
            @ApiResponse(responseCode = "200", description = "Angitt prosesstask-id satt til status FERDIG"),
            @ApiResponse(responseCode = "500", description = "Feilet pga ukjent feil eller tekniske/funksjonelle feil")
    })
    @BeskyttetRessurs(action = CREATE, property = ABAC_DRIFT_ATTRIBUTT)
    public Response setFeiletProsessTaskFerdig(@NotNull @Parameter(description = "Prosesstask-id for feilet prosesstask") @TilpassetAbacAttributt(supplierClass = AbacEmptySupplier.class) @Valid ProsessTaskSetFerdigInputDto prosessTaskIdDto) {
        prosessTaskApplikasjonTjeneste.setProsessTaskFerdig(prosessTaskIdDto.getProsessTaskId(), ProsessTaskStatus.valueOf(prosessTaskIdDto.getNaaVaaerendeStatus()));
        return Response.ok().build();
    }

}
