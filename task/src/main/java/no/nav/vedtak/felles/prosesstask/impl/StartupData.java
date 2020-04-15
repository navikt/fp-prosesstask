package no.nav.vedtak.felles.prosesstask.impl;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

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

}