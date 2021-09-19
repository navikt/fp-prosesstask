package no.nav.vedtak.felles.prosesstask.api;

public record TaskType(String value) {

    public static TaskType forProsessTaskHandler(Class<? extends ProsessTaskHandler> clazz) {
        return new TaskType(clazz.getAnnotation(ProsessTask.class).value());
    }
}
