package com.ofss.configuration.rule;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Numeric-value assertions, independent of spelling/scale/exponent formatting. */
final class ExactParameterNumbers {
    static final String JSON = """
            {"nested":{"precise":1.0000000000000001,"integer":9007199254740993,
                        "hugeInteger":9223372036854775808123456789},
             "array":[{"scientific":1.234567890123456789e40},
                      [1.0000000000000001e-30,-9007199254740993,0],null,true,"unchanged"]}
            """;
    private static final ObjectReader READER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS,
                    DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .build().readerFor(Object.class);

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(String json) { return (Map<String, Object>) READER.readValue(json); }

    static void assertValues(Map<String, Object> value) {
        var nested = (Map<?, ?>) value.get("nested");
        assertThat(nested.get("precise")).isInstanceOf(BigDecimal.class);
        assertThat((BigDecimal) nested.get("precise")).isEqualByComparingTo("1.0000000000000001");
        assertThat(nested.get("integer")).isInstanceOf(BigInteger.class).isEqualTo(new BigInteger("9007199254740993"));
        assertThat(nested.get("hugeInteger")).isInstanceOf(BigInteger.class)
                .isEqualTo(new BigInteger("9223372036854775808123456789"));
        var array = (List<?>) value.get("array");
        Object scientific = ((Map<?, ?>) array.getFirst()).get("scientific");
        assertThat(scientific).isInstanceOf(BigDecimal.class);
        assertThat((BigDecimal) scientific).isEqualByComparingTo("1.234567890123456789e40");
        var numbers = (List<?>) array.get(1);
        assertThat(numbers.getFirst()).isInstanceOf(BigDecimal.class);
        assertThat((BigDecimal) numbers.getFirst()).isEqualByComparingTo("1.0000000000000001e-30");
        assertThat(numbers.get(1)).isEqualTo(new BigInteger("-9007199254740993"));
        assertThat(numbers.get(2)).isEqualTo(BigInteger.ZERO);
        assertThat(array.get(2)).isNull();
        assertThat(array.get(3)).isEqualTo(true);
        assertThat(array.get(4)).isEqualTo("unchanged");
    }

    @SuppressWarnings("unchecked")
    static void assertPutResponse(String json) {
        var rule = (Map<?, ?>) object(json).get("rule");
        assertValues((Map<String, Object>) rule.get("parameters"));
    }

    @SuppressWarnings("unchecked")
    static void assertListResponse(String json) {
        var rows = (List<?>) READER.readValue(json);
        assertValues((Map<String, Object>) ((Map<?, ?>) rows.getFirst()).get("parameters"));
    }
}
