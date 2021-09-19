package no.nav.vedtak.felles.prosesstask.log;

import java.util.HashSet;
import java.util.Set;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;

import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.log.audit.Auditdata;
import no.nav.vedtak.log.audit.AuditdataHeader;
import no.nav.vedtak.log.audit.Auditlogger;
import no.nav.vedtak.log.audit.CefField;
import no.nav.vedtak.log.audit.CefFieldName;
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
        fields.add(new CefField(CefFieldName.EVENT_TIME, System.currentTimeMillis()));
        fields.add(new CefField(CefFieldName.REQUEST, data.getTaskType()));
        if (data.getAktørId() != null) {
            fields.add(new CefField(CefFieldName.BERORT_BRUKER_ID, data.getAktørId()));
        }
        if (data.getSaksnummer() != null) {
            fields.addAll(CefFields.forSaksnummer(data.getSaksnummer()));
        } else if (data.getFagsakId() != null) {
            fields.addAll(CefFields.forSaksnummer(data.getFagsakId()));
        }
        if (data.getBehandlingId() != null) {
            fields.addAll(CefFields.forBehandling(data.getBehandlingId()));
        } else if (data.getBehandlingUuid() != null) {
            fields.addAll(CefFields.forBehandling(data.getBehandlingUuid().toString()));
        }
        return fields;
    }
    
}
