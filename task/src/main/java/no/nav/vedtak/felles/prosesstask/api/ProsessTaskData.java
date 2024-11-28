package no.nav.vedtak.felles.prosesstask.api;

import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;

import no.nav.vedtak.exception.TekniskException;

/**
 * Task info describing the task to run, including error handling.
 */
public class ProsessTaskData implements ProsessTaskInfo {

    public static final String MANGLER_PROPS = "PT-492717";
    public static final Pattern VALID_KEY_PATTERN = Pattern.compile("[a-zA-Z0-9_\\.]+$");

    private final Properties props = new Properties();
    private final TaskType taskType;
    private int antallFeiledeForsøk;
    private String gruppe;
    private Long id;
    private LocalDateTime nesteKjøringEtter = LocalDateTime.now();
    private String payload;
    private int prioritet = 1;
    private String sekvens;
    private String sisteFeil;
    private String sisteFeilKode;
    private String sisteKjøringServerProsess;
    private LocalDateTime sistKjørt;
    private ProsessTaskStatus status = ProsessTaskStatus.KLAR;
    private Long blokkertAvProsessTaskId;
    private LocalDateTime opprettetTid;

    ProsessTaskData(TaskType taskType) {
        this.taskType = taskType;
    }

    public static ProsessTaskData forProsessTask(Class<? extends ProsessTaskHandler> clazz) {
        var prosessTaskData = new ProsessTaskData(TaskType.forProsessTask(clazz));
        prosessTaskData.setPrioritet(TaskType.prioritet(clazz));
        return prosessTaskData;
    }

    public static ProsessTaskData forTaskType(TaskType taskType) {
        return new ProsessTaskData(taskType);
    }

    public void validerProperties(Set<String> requiredProperties) {
        var validert = requiredProperties.stream().allMatch(p -> getPropertyValue(p) != null);
        if (!validert) {
            var mangler = requiredProperties.stream()
                    .filter(p -> getPropertyValue(p) == null)
                    .toList();
            throw new TekniskException(MANGLER_PROPS, "Mangler properties " + mangler);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ProsessTaskData other)) {
            return false;
        } else if (obj == this) {
            return true;
        }
        return Objects.equals(taskType, other.taskType)
            && Objects.equals(props, other.props);

    }

    @Override
    public int getAntallFeiledeForsøk() {
        return antallFeiledeForsøk;
    }

    public void setAntallFeiledeForsøk(int antallForsøk) {
        this.antallFeiledeForsøk = antallForsøk;
    }

    @Override
    public String getGruppe() {
        return gruppe;
    }

    @Override
    public Long getBlokkertAvProsessTaskId() {
        return blokkertAvProsessTaskId;
    }

    /**
     * Trengs normalt ikke settes.
     * <p>
     * Hvis settes, bør være globalt unik for tasker som henger sammen (eks. kan settes til
     * behandlingId, fagsakId - da vil alle prosess tasks på disse kjøres sekvensielt), men ikke ellers.
     * <p>
     * En spesiell pattern er å sette null første gang, for deretter få tildelt en gruppe og deretter bruke samme gruppe
     * om igjen på flere tasker.
     */
    public void setGruppe(String gruppe) {
        this.gruppe = gruppe;
    }

    @Override
    public Optional<String> getVentetHendelse() {
        return Optional.ofNullable(getPropertyValue(CommonTaskProperties.HENDELSE_PROPERTY));
    }

    void setVentetHendelse(String hendelse) {
        setProperty(CommonTaskProperties.HENDELSE_PROPERTY, hendelse);
    }

    @Override
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public LocalDateTime getNesteKjøringEtter() {
        return this.nesteKjøringEtter;
    }

    public void setNesteKjøringEtter(LocalDateTime nesteKjøringEtter) {
        this.nesteKjøringEtter = nesteKjøringEtter;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    @Override
    public String getPayloadAsString() {
        return payload;
    }

    @Override
    public int getPriority() {
        return prioritet;
    }

    @Override
    public Properties getProperties() {
        return props;
    }

    public void setProperties(Properties props) {
        this.props.putAll(props);
    }

    @Override
    public String getPropertyValue(String key) {
        return props.getProperty(key);
    }

    @Override
    public String getSekvens() {
        return sekvens;
    }

    /**
     * Trengs normalt ikke settes.
     * <p>
     * Vil defaulte til 1.
     * <p>
     * Kun interessant å sette dersom man oppretter flere tasker samtidig som man ønsker skal kjøres
     * sekvensielt (i økende sekvens) eller parallelt(med samme sekvens nr). Dette kan mixes og matches.
     */
    public void setSekvens(String sekvens) {
        this.sekvens = sekvens;
    }

    @Override
    public String getSisteFeil() {
        return sisteFeil;
    }

    public void setSisteFeil(String sisteFeil) {
        this.sisteFeil = sisteFeil;
    }

    @Override
    public String getSisteFeilKode() {
        return sisteFeilKode;
    }

    public void setSisteFeilKode(String sisteFeilKode) {
        this.sisteFeilKode = sisteFeilKode;
    }

    public String getSisteKjøringServerProsess() {
        return sisteKjøringServerProsess;
    }

    public void setSisteKjøringServerProsess(String sisteKjøringServerProsess) {
        this.sisteKjøringServerProsess = sisteKjøringServerProsess;
    }

    @Override
    public LocalDateTime getSistKjørt() {
        return sistKjørt;
    }

    public void setSistKjørt(LocalDateTime sistKjørt) {
        this.sistKjørt = sistKjørt;
    }

    @Override
    public ProsessTaskStatus getStatus() {
        return status;
    }

    public void setStatus(ProsessTaskStatus status) {
        this.status = status;
    }

    @Override
    public String getTaskType() {
        return taskType != null ? taskType.value() : null;
    }

    @Override
    public TaskType taskType() {
        return taskType;
    }

    @Override
    public LocalDateTime getOpprettetTid() {
        return opprettetTid;
    }

    public void setBlokkertAvProsessTaskId(Long blokkertAvProsessTaskId) {
        this.blokkertAvProsessTaskId = blokkertAvProsessTaskId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskType, props);
    }

    public ProsessTaskData medNesteKjøringEtter(LocalDateTime tidsstempel) {
        this.nesteKjøringEtter = tidsstempel;
        return this;
    }

    public ProsessTaskData medSekvens(String sekvens) {
        this.sekvens = sekvens;
        return this;
    }

    /*
     * Hele denne seksjons flyttes til applikasjon så man kan mappe t/f lokale valueobjects
     */

    @Override
    public String getAktørId() {
        return getPropertyValue(CommonTaskProperties.AKTØR_ID);
    }

    public void setAktørId(String id) {
        setProperty(CommonTaskProperties.AKTØR_ID, id);
    }

    @Override
    public String getBehandlingId() {
        return getPropertyValue(CommonTaskProperties.BEHANDLING_ID);
    }

    protected void setBehandlingId(String id) {
        setProperty(CommonTaskProperties.BEHANDLING_ID, id);
    }

    @Override
    public UUID getBehandlingUuid() {
        var uuidString = getPropertyValue(CommonTaskProperties.BEHANDLING_UUID);
        return uuidString != null ? UUID.fromString(uuidString) : null;
    }

    protected void setBehandlingUUid(UUID uuid) {
        setProperty(CommonTaskProperties.BEHANDLING_UUID, uuid.toString());
    }

    /**
     * @deprecated Bruk heller saksnummer
     */
    @Deprecated(forRemoval = true)
    @Override
    public Long getFagsakId() {
        return getPropertyValue(CommonTaskProperties.FAGSAK_ID) != null ? Long.valueOf(getPropertyValue(CommonTaskProperties.FAGSAK_ID)) : null;
    }

    /**
     * @deprecated Bruk heller saksnummer
     */
    @Deprecated(forRemoval = true)
    public void setFagsakId(Long id) {
        setProperty(CommonTaskProperties.FAGSAK_ID, id.toString());
    }

    @Override
    public String getSaksnummer() {
        return getPropertyValue(CommonTaskProperties.SAKSNUMMER);
    }

    public void setSaksnummer(String saksnummer) {
        setProperty(CommonTaskProperties.SAKSNUMMER, Objects.requireNonNull(saksnummer, CommonTaskProperties.SAKSNUMMER));
    }

    /**
     * Convenience API - setBehandling/setFagsak med flere parametere
     *
     * saksnummer   offisielt saksnummer
     * fagsakId     intern tabell-Id for sak - trengs en stund pga taskrammeverk for saksrekkefølge
     * behandlingId angitt behandlingId definert av fagsystem (kan være Long, UUID, etc)
     */

    @Deprecated(forRemoval = true) // Fagsakprosesstask må fikses - PTv2
    public void setBehandling(Long fagsakId, Long behandlingId) {
        Objects.requireNonNull(fagsakId, CommonTaskProperties.FAGSAK_ID);
        Objects.requireNonNull(behandlingId, CommonTaskProperties.BEHANDLING_ID);

        setFagsakId(fagsakId);
        setBehandlingId(behandlingId.toString());
    }

    @Deprecated(forRemoval = true) // Fagsakprosesstask må fikses - PTv2
    public void setBehandling(String saksnummer, Long fagsakId, Long behandlingId) {
        Objects.requireNonNull(saksnummer, CommonTaskProperties.SAKSNUMMER);
        Objects.requireNonNull(fagsakId, CommonTaskProperties.FAGSAK_ID);
        Objects.requireNonNull(behandlingId, CommonTaskProperties.BEHANDLING_ID);

        setSaksnummer(saksnummer);
        setFagsakId(fagsakId);
        setBehandlingId(behandlingId.toString());
    }

    @Deprecated(forRemoval = true) // Impending removal next release
    public void setBehandling(Long fagsakId, Long behandlingId, String aktørId) {
        Objects.requireNonNull(fagsakId, CommonTaskProperties.FAGSAK_ID);
        Objects.requireNonNull(behandlingId, CommonTaskProperties.BEHANDLING_ID);
        Objects.requireNonNull(aktørId, CommonTaskProperties.AKTØR_ID);

        setFagsakId(fagsakId);
        setBehandlingId(behandlingId.toString());
        setAktørId(aktørId);
    }

    @Deprecated(forRemoval = true) // Impending removal next release
    public void setBehandling(String saksnummer, String behandlingId) {
        Objects.requireNonNull(saksnummer, CommonTaskProperties.SAKSNUMMER);
        Objects.requireNonNull(behandlingId, CommonTaskProperties.BEHANDLING_ID);

        setSaksnummer(saksnummer);
        setBehandlingId(behandlingId);
    }

    /**
     * Convenience API - Angi (optional) hvilken behandling/sak denne prosesstasken kjøres for.
     *
     * @param saksnummer offisielt saksnummer
     * @param behandlingId angitt behandlingId definert av fagsystem (kan være Long, UUID, etc)
     * @param aktørId angitt AktørId gyldig i AktørRegisteret.
     */
    @Deprecated(forRemoval = true) // Impending removal next release
    public void setBehandling(String saksnummer, String behandlingId, String aktørId) {
        Objects.requireNonNull(saksnummer, CommonTaskProperties.SAKSNUMMER);
        Objects.requireNonNull(behandlingId, CommonTaskProperties.BEHANDLING_ID);
        Objects.requireNonNull(aktørId, CommonTaskProperties.AKTØR_ID);

        setSaksnummer(saksnummer);
        setBehandlingId(behandlingId);
        setAktørId(aktørId);
    }

    @Deprecated(forRemoval = true) // Impending removal next release
    public void setFagsak(Long fagsakId, String aktørId) {
        Objects.requireNonNull(fagsakId, CommonTaskProperties.FAGSAK_ID);
        Objects.requireNonNull(aktørId, CommonTaskProperties.AKTØR_ID);

        setFagsakId(fagsakId);
        setAktørId(aktørId);
    }

    @Deprecated(forRemoval = true) // Fagsakprosesstask må fikses - PTv2
    public void setFagsak(String saksnummer, Long fagsakId) {
        Objects.requireNonNull(saksnummer, CommonTaskProperties.SAKSNUMMER);
        Objects.requireNonNull(fagsakId, CommonTaskProperties.FAGSAK_ID);

        setSaksnummer(saksnummer);
        setFagsakId(fagsakId);
    }

    public void setOpprettetTid(LocalDateTime opprettetTid) {
        this.opprettetTid = opprettetTid;
    }

    public void setPrioritet(int prioritet) {
        this.prioritet = prioritet;
    }

    public void setProperty(String key, String value) {
        if (!VALID_KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid key:" + key);
        }
        if (value == null) {
            this.props.remove(key);
        } else {
            this.props.setProperty(key, value);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
            + "<id=" + getId()
            + ", taskType=" + getTaskType()
            + ", props=" + getProperties()
            + ", status=" + getStatus()
            + ">";
    }

    public void venterPåHendelse(String hendelse) {
        setVentetHendelse(hendelse);
        setStatus(ProsessTaskStatus.VENTER_SVAR);
    }

    public void setCallId(String callId) {
        setProperty(CallId.CALL_ID, callId);
    }

    public void setCallIdFraEksisterende() {
        setCallId(MDC.get(CallId.CALL_ID));
    }

    String getPropertiesAsString() throws IOException {
        if (props.isEmpty()) {
            return null;
        } else {
            var sw = new StringWriter(200);
            props.store(sw, null);
            return sw.toString();
        }
    }
}
