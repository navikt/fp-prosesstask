package no.nav.vedtak.felles.prosesstask.impl;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Properties;

import javax.persistence.AttributeConverter;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Converter;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Version;

import org.hibernate.annotations.Type;

import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskInfo;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskStatus;

/**
 * Task info describing the task to run, including error handling.
 */
@Entity(name = "ProsessTaskEntitet")
@Table(name = "PROSESS_TASK")
public class ProsessTaskEntitet {

    @Column(name = "feilede_forsoek")
    private Integer feiledeForsøk = 0;

    @Column(name = "task_gruppe")
    private String gruppe;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_PROSESS_TASK")
    private Long id;

    @Column(name = "neste_kjoering_etter")
    private LocalDateTime nesteKjøringEtter;

    @Lob
    @Type(type = "org.hibernate.type.TextType")
    @Column(name = "task_payload")
    private String payload;

    @Column(name = "prioritet", nullable = false)
    private int prioritet;

    @Convert(converter = PropertiesToStringConverter.class)
    @Column(name = "task_parametere")
    private Properties props = new Properties();

    @Column(name = "task_sekvens", nullable = false)
    private String sekvens;

    @Column(name = "siste_kjoering_feil_kode")
    private String sisteFeilKode;

    @Lob
    @Type(type = "org.hibernate.type.TextType")
    @Column(name = "siste_kjoering_feil_tekst")
    private String sisteFeilTekst;

    @Column(name = "siste_kjoering_ts")
    private LocalDateTime sisteKjøring;

    @Column(name = "siste_kjoering_server")
    private String sisteKjøringServerProsess;

    @Column(name = "status", nullable = false)
    private String status = ProsessTaskStatus.KLAR.getDbKode();

    @Column(name = "task_type", nullable = false)
    private String taskType;

    @Column(name = "opprettet_av", nullable = false)
    private String opprettetAv = "SYSTEM";

    @Version
    @Column(name = "versjon", nullable = false)
    private Long versjon;

    ProsessTaskEntitet() {
        // for hibernate
    }

    ProsessTaskEntitet(String taskType) {
        this.taskType = taskType;
    }

    public int getFeiledeForsøk() {
        return feiledeForsøk == null ? 0 : feiledeForsøk;
    }

    /**
     * kun test, oppdateres vha repository direkte.
     */
    void setFeiledeForsøk(Integer feiledeForsøk) {
        this.feiledeForsøk = feiledeForsøk;
    }

    void setSisteKjøring(LocalDateTime sisteKjøring) {
        this.sisteKjøring = sisteKjøring;
    }

    public String getGruppe() {
        return gruppe;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getNesteKjøringEtter() {
        return this.nesteKjøringEtter;
    }

    public void setNesteKjøringEtter(LocalDateTime nesteKjøringEtter) {
        this.nesteKjøringEtter = nesteKjøringEtter;
    }

    public void setSisteKjøringServer(String sisteKjøringServerProsess) {
        this.sisteKjøringServerProsess = sisteKjøringServerProsess;
    }

    public String getPayload() {
        return payload;
    }

    public int getPrioritet() {
        return prioritet;
    }

    public Properties getProperties() {
        return props;
    }

    public String getPropertyValue(String key) {
        return props.getProperty(key);
    }

    public String getSekvens() {
        return sekvens;
    }

    public String getSisteFeilKode() {
        return sisteFeilKode;
    }

    public String getSisteFeilTekst() {
        return sisteFeilTekst;
    }

    public LocalDateTime getSisteKjøring() {
        return sisteKjøring;
    }

    public String getSisteKjøringServerProsess() {
        return sisteKjøringServerProsess;
    }

    public ProsessTaskStatus getStatus() {
        return ProsessTaskStatus.valueOf(status);
    }

    void setStatus(ProsessTaskStatus status) {
        this.status = status.getDbKode();
    }

    public String getTaskName() {
        return taskType;
    }

    public ProsessTaskData tilProsessTask() {
        ProsessTaskData task = new ProsessTaskData(getTaskName());
        task.setId(getId());

        task.setNesteKjøringEtter(getNesteKjøringEtter());
        task.setSistKjørt(getSisteKjøring());
        task.setPrioritet(getPrioritet());
        task.setSisteFeil(getSisteFeilTekst());
        task.setSisteFeilKode(getSisteFeilKode());
        task.setAntallFeiledeForsøk(getFeiledeForsøk());
        task.setProperties(getProperties());
        task.setStatus(getStatus());
        task.setGruppe(getGruppe());
        task.setSekvens(getSekvens());
        task.setPayload(getPayload());
        task.setSisteKjøringServerProsess(getSisteKjøringServerProsess());
        return task;
    }

    String getPropertiesAsString() throws IOException {
        if (props == null || props.isEmpty()) {
            return null;
        } else {
            StringWriter sw = new StringWriter(200);
            props.store(sw, null);
            return sw.toString();
        }

    }

    ProsessTaskEntitet kopierFraEksisterende(ProsessTaskInfo prosessTask) {
        Objects.requireNonNull(prosessTask, "prosessTask");
        Objects.requireNonNull(prosessTask.getId(), "prosessTask#id");

        this.id = prosessTask.getId();
        this.sisteFeilKode = prosessTask.getSisteFeilKode();
        this.sisteFeilTekst = prosessTask.getSisteFeil();
        this.feiledeForsøk = prosessTask.getAntallFeiledeForsøk();
        this.sisteKjøring = prosessTask.getSistKjørt();
        kopierBasicFelter(prosessTask);
        return this;
    }

    ProsessTaskEntitet kopierFraNy(ProsessTaskInfo prosessTask) {
        Objects.requireNonNull(prosessTask, "prosessTask");
        if (prosessTask.getId() != null) {
            throw new IllegalArgumentException("prosessTask#id må være null");
        }
        return kopierBasicFelter(prosessTask);
    }

    private ProsessTaskEntitet kopierBasicFelter(ProsessTaskInfo prosessTask) {
        this.id = prosessTask.getId();
        this.taskType = prosessTask.getTaskType();
        this.nesteKjøringEtter = prosessTask.getNesteKjøringEtter();
        this.prioritet = prosessTask.getPriority();

        this.props = prosessTask.getProperties();
        this.status = prosessTask.getStatus().getDbKode();
        this.gruppe = prosessTask.getGruppe();
        this.sekvens = prosessTask.getSekvens();
        this.payload = prosessTask.getPayloadAsString();
        return this;
    }

    void setSisteFeil(String kode, String feilBeskrivelse) {
        this.sisteFeilTekst = feilBeskrivelse;
        this.sisteFeilKode = kode;
    }

    /**
     * JPA konverterer for å skrive ned en key=value text til et databasefelt (output tilsvarer java.util.Properties
     * format).
     */
    @Converter
    public static class PropertiesToStringConverter implements AttributeConverter<Properties, String> {

        @Override
        public String convertToDatabaseColumn(Properties props) {
            if (props == null || props.isEmpty()) {
                return null;
            }
            StringWriter sw = new StringWriter(512);
            // custom istdf Properties.store slik at vi ikke får med default timestamp
            props.forEach((k, v) -> {
                sw.append((String) k).append('=').append((String) v).append('\n');
            });
            return sw.toString();

        }

        @Override
        public Properties convertToEntityAttribute(String dbData) {
            Properties props = new Properties();
            if (dbData != null) {
                try {
                    props.load(new StringReader(dbData));
                } catch (IOException e) {
                    throw new IllegalArgumentException("Kan ikke lese properties til string:" + props, e); //$NON-NLS-1$
                }
            }
            return props;
        }
    }

    void setSubjectProvider(SubjectProvider subjectProvider) {
        if (subjectProvider != null) {
            var ident = subjectProvider.getUserIdentity();
            if (ident != null) {
                this.opprettetAv = ident;
            }
        }
    }

}
