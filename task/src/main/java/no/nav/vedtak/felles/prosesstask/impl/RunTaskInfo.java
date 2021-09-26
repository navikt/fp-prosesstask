package no.nav.vedtak.felles.prosesstask.impl;

import java.time.LocalDateTime;
import java.util.Objects;

import no.nav.vedtak.felles.prosesstask.api.ProsessTaskDispatcher;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskInfo;
import no.nav.vedtak.felles.prosesstask.api.TaskType;

/**
 * Info knyttet til en enkelt kjøring av en task.
 * Denne opprettes ved polling, køes og kjøres i tråd som kaller metoden
 */
class RunTaskInfo {

    private static final LocalDateTime MIN_TIMESTAMP = LocalDateTime.of(1970, 01, 01, 00, 00);

    private final ProsessTaskDispatcher taskDispatcher;
    private final Long id;
    private final TaskType taskType;
    private final LocalDateTime timestampLowWatermark;

    RunTaskInfo(ProsessTaskDispatcher dispatcher, ProsessTaskInfo task) {
        this(dispatcher, task.getId(), task.taskType(), task.getSistKjørt());
    }

    RunTaskInfo(ProsessTaskDispatcher dispatcher, Long id, TaskType taskType, LocalDateTime timestampLowWatermark) {
        Objects.requireNonNull(id, "id"); //$NON-NLS-1$
        Objects.requireNonNull(taskType, "taskName"); //$NON-NLS-1$

        this.id = id;
        this.taskType = taskType;
        if (timestampLowWatermark != null) {
            this.timestampLowWatermark = timestampLowWatermark.withNano(0); // rundt av nedover til sekund for kunne match med neste kjøring (som har sekund oppløsning)
        } else {
            this.timestampLowWatermark = MIN_TIMESTAMP;
        }
        this.taskDispatcher = dispatcher;
    }

    ProsessTaskDispatcher getTaskDispatcher() {
        return taskDispatcher;
    }

    Long getId() {
        return id;
    }

    TaskType getTaskType() {
        return taskType;
    }

    LocalDateTime getTimestampLowWatermark() {
        return timestampLowWatermark;
    }

    /** Skal benytte feilhåndtering algoritme for angitt exception. */
    public boolean feilhåndterException(Throwable e) {
        return taskDispatcher.feilhåndterException(e);
    }
}