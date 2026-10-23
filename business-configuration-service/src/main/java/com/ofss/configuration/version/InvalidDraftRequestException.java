package com.ofss.configuration.version;

public class InvalidDraftRequestException extends RuntimeException {
    public InvalidDraftRequestException() {
        super("Invalid draft configuration request");
    }
}
