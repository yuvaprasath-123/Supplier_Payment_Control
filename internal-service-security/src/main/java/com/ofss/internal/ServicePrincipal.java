package com.ofss.internal;

/** A service identity never carries user roles, user IDs or bearer credentials. */
public record ServicePrincipal(String serviceId) {}
