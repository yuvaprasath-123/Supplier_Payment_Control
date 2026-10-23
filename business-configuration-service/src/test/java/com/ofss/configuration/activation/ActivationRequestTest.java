package com.ofss.configuration.activation;

import com.ofss.configuration.rule.InvalidConfigurationRequestException;
import java.math.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class ActivationRequestTest {
    @Test void nullMeansNoPointerButMustBeExplicit() {
        var body = new HashMap<String,Object>(); body.put("expectedPointerRowVersion",null);
        assertThat(ActivationRequest.from(body).expectedPointerRowVersion()).isNull();
        assertThatThrownBy(() -> ActivationRequest.from(Map.of())).isInstanceOf(InvalidConfigurationRequestException.class);
        assertThatThrownBy(() -> ActivationRequest.from(null)).isInstanceOf(InvalidConfigurationRequestException.class);
    }
    @ParameterizedTest @ValueSource(strings={"0","1","9007199254740993","9223372036854775807"})
    void exactRevisions(String text) {
        assertThat(ActivationRequest.from(Map.of("expectedPointerRowVersion",text)).expectedPointerRowVersion()).isEqualTo(Long.parseLong(text));
        assertThat(ActivationRequest.from(Map.of("expectedPointerRowVersion",new BigInteger(text))).expectedPointerRowVersion()).isEqualTo(Long.parseLong(text));
    }
    @ParameterizedTest @ValueSource(strings={"-1","1.0","1e0"," 1","+1","9223372036854775808",""})
    void badText(String text) { assertThatThrownBy(() -> ActivationRequest.from(Map.of("expectedPointerRowVersion",text))).isInstanceOf(InvalidConfigurationRequestException.class); }
    @Test void spoofingAndNonIntegerTypesRejected() {
        for(Object value:List.of(true,1.0,new BigDecimal("1.00"),List.of(1)))
            assertThatThrownBy(() -> ActivationRequest.from(Map.of("expectedPointerRowVersion",value))).isInstanceOf(InvalidConfigurationRequestException.class);
        assertThatThrownBy(() -> ActivationRequest.from(Map.of("expectedPointerRowVersion","0","actorId","1"))).isInstanceOf(InvalidConfigurationRequestException.class);
    }
}
