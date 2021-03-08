package no.nav.vedtak.felles.prosesstask.impl;

import org.slf4j.Logger;
import org.slf4j.event.Level;

public class Feil {

    private final String kode;
    private final String feilmelding;
    private final Level logLevel;
    private final Throwable cause;

    public Feil(String kode, String feilmelding, Level logLevel, Throwable cause) {
        this.kode = kode;
        this.feilmelding = feilmelding;
        this.logLevel = logLevel;
        this.cause = cause;
    }

    public String getKode() {
        return kode;
    }

    public String getFeilmelding() {
        return feilmelding;
    }

    public String toLogString() {
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
            case ERROR:
                logger.error(text); // NOSONAR
                break;
            case WARN:
                logger.warn(text); // NOSONAR
                break;
            case INFO:
                logger.info(text); // NOSONAR
                break;
            default:
                throw new IllegalArgumentException("Ikke-støttet LogLevel: " + logLevel);
        }
    }

    private void logMedCause(Logger logger) {
        String text = toLogString();
        switch (logLevel) {
            case ERROR:
                logger.error(text, cause); // NOSONAR
                break;
            case WARN:
                logger.warn(text, cause); // NOSONAR
                break;
            case INFO:
                logger.info(text, cause); // NOSONAR
                break;
            default:
                throw new IllegalArgumentException("Ikke-støttet LogLevel: " + logLevel);
        }
    }

    public Throwable getCause() {
        return cause;
    }
}