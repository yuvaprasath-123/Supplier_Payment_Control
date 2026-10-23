package com.ofss.configuration.activation;

import com.ofss.configuration.publication.PublishConfigurationRequest;
import com.ofss.configuration.rule.InvalidConfigurationRequestException;
import java.util.*;

/** Null explicitly asserts that the singleton pointer does not exist yet. */
public record ActivationRequest(Long expectedPointerRowVersion) {
    public ActivationRequest {
        if (expectedPointerRowVersion != null && expectedPointerRowVersion < 0) throw new InvalidConfigurationRequestException();
    }
    public static ActivationRequest from(Map<String,Object> body) {
        if (body == null || !body.keySet().equals(Set.of("expectedPointerRowVersion"))) throw new InvalidConfigurationRequestException();
        Object value = body.get("expectedPointerRowVersion");
        return new ActivationRequest(value == null ? null : PublishConfigurationRequest.from(Map.of("expectedRowVersion", value)).expectedRowVersion());
    }
}
