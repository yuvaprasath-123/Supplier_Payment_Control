package com.ofss.configuration.publication;

public class AlreadyPublishedException extends RuntimeException {
    public AlreadyPublishedException() { super("Configuration version is already published"); }
}
