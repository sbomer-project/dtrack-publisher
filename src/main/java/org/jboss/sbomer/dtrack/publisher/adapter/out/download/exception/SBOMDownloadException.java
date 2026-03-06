package org.jboss.sbomer.dtrack.publisher.adapter.out.download.exception;

public class SBOMDownloadException extends RuntimeException {
    
    private final String errorCode;

    public SBOMDownloadException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SBOMDownloadException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}