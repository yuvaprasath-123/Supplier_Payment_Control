package com.ofss.configuration.version;

public class VersionAllocationConflictException extends RuntimeException {
    public VersionAllocationConflictException() {
        super("A configuration version could not be allocated");
    }
}
