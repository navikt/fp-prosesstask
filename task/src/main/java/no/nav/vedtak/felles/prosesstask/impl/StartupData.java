package no.nav.vedtak.felles.prosesstask.impl;

import java.time.LocalDateTime;

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
    LocalDateTime inputtid2;

    @Column(name="drift")
    String drift;

    StartupData() {
        // for hibernate
    }
}
