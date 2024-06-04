package no.nav.vedtak.felles.prosesstask.api;

import java.time.LocalDateTime;
import java.util.Properties;
import java.util.Set;

import org.slf4j.MDC;

/**
 * Buider to define new task parameters
 */
public class ProsessTaskDataBuilder {

    private ProsessTaskData taskData;


    private ProsessTaskDataBuilder(TaskType taskType) {
        this.taskData = new ProsessTaskData(taskType);
        this.taskData.setStatus(ProsessTaskStatus.KLAR);
    }

    public static ProsessTaskDataBuilder forProsessTask(Class<? extends ProsessTaskHandler> clazz) {
        return new ProsessTaskDataBuilder(TaskType.forProsessTask(clazz)).medPrioritet(TaskType.prioritet(clazz));
    }

    public static ProsessTaskDataBuilder forTaskType(TaskType taskType) {
        return new ProsessTaskDataBuilder(taskType);
    }

    public ProsessTaskDataBuilder medProperties(Properties properties) {
        this.taskData.setProperties(properties);
        return this;
    }

    public ProsessTaskDataBuilder medProperty(String key, String value) {
        this.taskData.setProperty(key, value);
        return this;
    }

    public ProsessTaskDataBuilder medPayload(String payload) {
        this.taskData.setPayload(payload);
        return this;
    }

    public ProsessTaskDataBuilder medVenterHendelse(String hendelseKey) {
        this.taskData.setStatus(ProsessTaskStatus.VENTER_SVAR);
        this.taskData.setVentetHendelse(hendelseKey);
        return this;
    }

    public ProsessTaskDataBuilder medNesteKjøringEtter(LocalDateTime tidsstempel) {
        this.taskData.setNesteKjøringEtter(tidsstempel);
        return this;
    }

    public ProsessTaskDataBuilder medCallId(String callId) {
        this.taskData.setCallId(callId);
        return this;
    }

    public ProsessTaskDataBuilder medCallIdFraEksisterende() {
        this.taskData.setCallId(MDC.get(CallId.CALL_ID));
        return this;
    }

    public ProsessTaskDataBuilder medPrioritet(int prioritet) {
        this.taskData.setPrioritet(prioritet);
        return this;
    }

    public ProsessTaskData build() {
        return taskData;
    }

    public ProsessTaskData buildValidert(Set<String> requiredProperties) {
        taskData.validerProperties(requiredProperties);
        return taskData;
    }


    /*
     * TODO: Evaluer behov for disse to. Ganske eksotiske. Kan henvise til set'er.
     */
    public ProsessTaskDataBuilder medGruppe(String gruppe) {
        this.taskData.setGruppe(gruppe);
        return this;
    }

    public ProsessTaskDataBuilder medSekvens(String sekvens) {
        this.taskData.setSekvens(sekvens);
        return this;
    }
}
