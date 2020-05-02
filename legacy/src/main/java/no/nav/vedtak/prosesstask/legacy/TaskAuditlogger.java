package no.nav.vedtak.prosesstask.legacy;

import java.util.HashSet;
import java.util.Set;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;

import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.log.audit.Auditdata;
import no.nav.vedtak.log.audit.AuditdataHeader;
import no.nav.vedtak.log.audit.Auditlogger;
import no.nav.vedtak.log.audit.CefField;
import no.nav.vedtak.log.audit.CefFields;
import no.nav.vedtak.log.audit.EventClassId;

@Dependent
public class TaskAuditlogger {
    
    private final Auditlogger auditlogger;

    
    @Inject
    public TaskAuditlogger(Auditlogger auditLogger) {
        this.auditlogger = auditLogger;
    }
    

    public void logg(ProsessTaskData data) {       
        final AuditdataHeader header = createHeader();
        final Set<CefField> fields = createFields(data);
        
        final Auditdata auditdata = new Auditdata(header, fields);
        auditlogger.logg(auditdata);
    }
    
    public boolean isEnabled() {
        return auditlogger.isEnabled();
    }
    
    private AuditdataHeader createHeader() {
        return new AuditdataHeader.Builder()
                .medVendor(auditlogger.getDefaultVendor())
                .medProduct(auditlogger.getDefaultProduct())
                .medEventClassId(EventClassId.AUDIT_ACCESS)
                .medName("Systemoppgave")
                .medSeverity("INFO")
                .build();
    }
    
    private Set<CefField> createFields(ProsessTaskData data) {
        final Set<CefField> fields = new HashSet<>();
        fields.add(new CefField(CefFields.EVENT_TIME, System.currentTimeMillis()));
        fields.add(new CefField(CefFields.REQUEST, data.getTaskType()));
        if (data.getAktørId() != null) {
            fields.add(new CefField(CefFields.BERORT_BRUKER_ID, data.getAktørId()));
        }
        if (data.getFagsakId() != null) {
            fields.add(new CefField(CefFields.SAKSNUMMER_VERDI, data.getFagsakId()));
            fields.add(new CefField(CefFields.SAKSNUMMER_LABEL, CefFields.SAKSNUMMER_TEXT));
        }
        if (data.getBehandlingId() != null) {
            fields.add(new CefField(CefFields.BEHANDLING_VERDI, data.getBehandlingId()));
            fields.add(new CefField(CefFields.BEHANDLING_LABEL, CefFields.BEHANDLING_TEXT));   
        }
        return fields;
    }
    
}
