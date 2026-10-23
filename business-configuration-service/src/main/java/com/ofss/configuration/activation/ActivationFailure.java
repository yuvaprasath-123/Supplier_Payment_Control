package com.ofss.configuration.activation;

public class ActivationFailure extends RuntimeException {
    public enum Reason {
        STALE_ACTIVE_CONFIGURATION, VERSION_NOT_PUBLISHED, ACTIVATION_REVISION_EXHAUSTED, NO_ACTIVE_CONFIGURATION
    }
    private final Reason reason;
    public ActivationFailure(Reason reason) { super(reason.name()); this.reason = reason; }
    public Reason reason() { return reason; }
}
