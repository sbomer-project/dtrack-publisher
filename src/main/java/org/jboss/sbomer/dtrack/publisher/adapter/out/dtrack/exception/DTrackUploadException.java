package org.jboss.sbomer.dtrack.publisher.adapter.out.dtrack.exception;

public class DTrackUploadException extends RuntimeException {
    
    private final String errorCode;

    public DTrackUploadException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}