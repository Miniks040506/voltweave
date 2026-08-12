package io.voltweave.telemetry.processing.application.exception;

public class TelemetryValidationException extends RuntimeException {
    private final String reasonCode;

    public TelemetryValidationException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public TelemetryValidationException(String reasonCode, String message, Throwable cause) {
        super(message, cause);
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
