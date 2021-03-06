package no.nav.vedtak.felles.prosesstask.impl;

import java.time.LocalDateTime;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;

import no.nav.vedtak.felles.prosesstask.spi.ProsessTaskFeilHåndteringParametere;

@Entity(name = "ProsessTaskFeilhand")
@Table(name = "PROSESS_TASK_FEILHAND")
public class ProsessTaskFeilhand implements ProsessTaskFeilHåndteringParametere {

    @Column(name = "INPUT_VARIABEL1", updatable = false, insertable = false, columnDefinition = "INT8")
    private Integer inputVariabel1;

    @Column(name = "INPUT_VARIABEL2", updatable = false, insertable = false, columnDefinition = "INT8")
    private Integer inputVariabel2;
    @Id
    @Column(name = "kode", updatable = false, insertable = false, nullable = false)
    private String kode;
    @Column(name = "beskrivelse", updatable = false, insertable = false)
    private String beskrivelse;
    
    /**
     * Navn registrert i databasen.
     */
    @Column(name = "navn", updatable = false, insertable = false, nullable = false)
    private String navn;

    @Column(name = "opprettet_tid", nullable = false)
    private LocalDateTime opprettetTidspunkt; // NOSONAR

    protected ProsessTaskFeilhand() {
        // Hibernate
    }


    @Override
    public Integer getInputVariabel1() {
        return inputVariabel1;
    }

    @Override
    public Integer getInputVariabel2() {
        return inputVariabel2;
    }

    public String getKode() {
        return kode;
    }

    public String getNavn() {
        return navn;
    }

    public String getBeskrivelse() {
        return beskrivelse;
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }
        if (!(object instanceof ProsessTaskFeilhand)) {
            return false;
        }
        ProsessTaskFeilhand other = (ProsessTaskFeilhand) object;
        return Objects.equals(getKode(), other.getKode());
    }

    @Override
    public int hashCode() {
        return Objects.hash(kode);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "<kode=" + getKode() + ", navn=" + getNavn() + ">"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }
    
    @PrePersist
    protected void onCreate() {
        this.opprettetTidspunkt = LocalDateTime.now();
    }

}
