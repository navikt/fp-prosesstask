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
            case ERROR -> logger.error(text); // NOSONAR
            case WARN -> logger.warn(text); // NOSONAR
            case INFO -> logger.info(text); // NOSONAR
            default -> throw new IllegalArgumentException("Ikke-støttet LogLevel: " + logLevel);
        }
    }

    private void logMedCause(Logger logger) {
        String text = toLogString();
        switch (logLevel) {
            case ERROR -> logger.error(text, cause); // NOSONAR
            case WARN -> logger.warn(text, cause); // NOSONAR
            case INFO -> logger.info(text, cause); // NOSONAR
            default -> throw new IllegalArgumentException("Ikke-støttet LogLevel: " + logLevel);
        }
    }
}