package no.nav.vedtak.felles.prosesstask.impl;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity(name = "StartupData")
class StartupData {

    @Id
    @Column(name = "dbtz")
    String dbtz;

    @Column(name = "dbtid")
    String dbtid;

    @Column(name = "inputtid")
    String inputtid;

    @Column(name="inputtid2")
    String inputtid2;

    @Column(name="drift")
    String drift;

    StartupData() {
        // for hibernate
    }

    public StartupData(String dbtz, String dbtid, String inputtid, String inputtid2, String drift) {
        this.dbtz = dbtz;
        this.dbtid = dbtid;
        this.inputtid = inputtid;
        this.inputtid2 = inputtid2;
        this.drift = drift;
    }
}
