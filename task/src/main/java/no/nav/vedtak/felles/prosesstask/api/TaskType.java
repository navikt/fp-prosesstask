package no.nav.vedtak.felles.prosesstask.api;

import java.util.regex.Pattern;

import no.nav.vedtak.exception.TekniskException;

public record TaskType(String value) {

    private static final Pattern VALID_TASK_NAME =Pattern.compile("[a-zA-ZæøåÆØÅ0-9_.\\-]+$");

    public TaskType {
        if (value == null || value.isBlank() || !VALID_TASK_NAME.matcher(value).matches()) {
            throw new TekniskException("PT-617482", "Ugyldig tasknavn");
        }
    }

    public static TaskType forProsessTask(Class<? extends ProsessTaskHandler> clazz) {
        return new TaskType(clazz.getAnnotation(ProsessTask.class).value());
    }
}
