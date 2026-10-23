package com.ofss.configuration.publication;

import com.ofss.configuration.rule.InvalidConfigurationRequestException;
import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

public record PublishConfigurationRequest(long expectedRowVersion) {
    public PublishConfigurationRequest {
        if (expectedRowVersion < 0) throw new InvalidConfigurationRequestException();
    }
    public static PublishConfigurationRequest from(Map<String, Object> body) {
        if (body == null || !body.keySet().equals(Set.of("expectedRowVersion"))) throw new InvalidConfigurationRequestException();
        Object value = body.get("expectedRowVersion");
        try {
            if (value instanceof String text && text.matches("[0-9]+")) return new PublishConfigurationRequest(Long.parseLong(text));
            if (value instanceof Integer || value instanceof Long || value instanceof BigInteger)
                return new PublishConfigurationRequest(new BigInteger(value.toString()).longValueExact());
        } catch (ArithmeticException | NumberFormatException exception) { throw new InvalidConfigurationRequestException(); }
        throw new InvalidConfigurationRequestException();
    }
}
