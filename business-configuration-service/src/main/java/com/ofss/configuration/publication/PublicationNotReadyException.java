package com.ofss.configuration.publication;

import com.ofss.configuration.readiness.PublicationReadiness;

public class PublicationNotReadyException extends RuntimeException {
    private final PublicationReadiness readiness;
    public PublicationNotReadyException(PublicationReadiness readiness) {
        super("Configuration is not ready for publication");
        this.readiness = readiness;
    }
    public PublicationReadiness readiness() { return readiness; }
}
