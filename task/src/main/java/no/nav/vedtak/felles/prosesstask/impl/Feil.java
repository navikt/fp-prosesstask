package no.nav.vedtak.felles.prosesstask.impl;

import org.slf4j.Logger;
import org.slf4j.event.Level;

public record Feil(String kode, String feilmelding, Level logLevel, Throwable cause) {

    private String toLogString() {
        return (kode + ":" + feilmelding).replaceAll("(\\r|\\n)", "");
    }

    @Override
    public String toString() {
        return toLogString();
    }

    public Feil log(Logger logger) {
        if (cause != null) {
            logMedCause(logger);
        } else {
            logUtenCause(logger);
        }
        return this;
    }

    private void logUtenCause(Logger logger) {
        String text = toLogString();
        switch (logLevel) {
            case ERROR -> logger.error(text);
            case WARN -> logger.warn(text);
            case INFO -> logger.info(text);
            default -> throw new IllegalArgumentException("Ikke-støttet LogLevel: " + logLevel);
        }
    }

    private void logMedCause(Logger logger) {
        String text = toLogString();
        switch (logLevel) {
            case ERROR -> logger.error(text, cause);
            case WARN -> logger.warn(text, cause);
            case INFO -> logger.info(text, cause);
            default -> throw new IllegalArgumentException("Ikke-støttet LogLevel: " + logLevel);
        }
    }
}