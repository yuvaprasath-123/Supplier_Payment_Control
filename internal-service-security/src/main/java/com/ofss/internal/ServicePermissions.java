package com.ofss.internal;

import java.util.Set;

public final class ServicePermissions {
    private ServicePermissions() {}
    public static final String CONFIG_ACTIVE = "CONFIGURATION_ACTIVE_READ";
    public static final String CONFIG_VERSION = "CONFIGURATION_VERSION_READ";
    public static final String RISK_ASSESSMENT = "RISK_ASSESSMENT_READ";
    public static final String RISK_FACTORS = "RISK_FACTORS_READ";
    public static final String RISK_CONFIGURATION = "RISK_CONFIGURATION_READ";
    public static final String RISK_SUBMIT = "RISK_ASSESSMENT_SUBMIT";
    public static final Set<String> CONFIGURATION = Set.of(CONFIG_ACTIVE, CONFIG_VERSION);
    public static final Set<String> RISK = Set.of(RISK_ASSESSMENT, RISK_FACTORS, RISK_CONFIGURATION, RISK_SUBMIT);
    public static String authority(String permission) { return "SERVICE_PERMISSION_" + permission; }
}
