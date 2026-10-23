package com.ofss.configuration.rule;

import java.util.Map;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.json.JsonMapper;

/** Endpoint-scoped decoding: do not change the application's global mapper. */
@JsonDeserialize(using = ExactRiskRuleBody.Decoder.class)
public record ExactRiskRuleBody(Map<String, Object> values) {
    public static final class Decoder extends StdDeserializer<ExactRiskRuleBody> {
        private static final ObjectReader READER = JsonMapper.builder()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS,
                        DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
                .build().readerFor(Object.class);

        public Decoder() { super(ExactRiskRuleBody.class); }

        @Override
        @SuppressWarnings("unchecked")
        public ExactRiskRuleBody deserialize(JsonParser parser, DeserializationContext context) {
            // Decode directly from the HTTP parser, before any Double conversion.
            Object value = READER.readValue(parser);
            if (!(value instanceof Map<?, ?>)) throw new InvalidConfigurationRequestException();
            return new ExactRiskRuleBody((Map<String, Object>) value);
        }
    }
}
