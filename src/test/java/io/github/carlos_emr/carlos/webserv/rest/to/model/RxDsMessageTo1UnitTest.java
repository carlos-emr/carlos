// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.webserv.rest.to.model;

import java.util.HashMap;
import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class RxDsMessageTo1UnitTest {
    private final ListResourceBundle messages = new ListResourceBundle() {
        @Override protected Object[][] getContents() {
            return new Object[][] {
                {"oscarRx.interactions.msgAugmentsNoClinical", "augments without clinical effect"},
                {"oscarRx.interactions.msgAugments", "augments"},
                {"oscarRx.interactions.msgInhibitsNoClinical", "inhibits without clinical effect"},
                {"oscarRx.interactions.msgInhibits", "inhibits"},
                {"oscarRx.interactions.msgNoEffect", "has no effect"},
                {"oscarRx.interactions.msgUnknownEffect", "has unknown effect"}
            };
        }
    };
    @ParameterizedTest
    @CsvSource({"a,augments without clinical effect", "A,augments", "i,inhibits without clinical effect",
        "I,inhibits", "n,has no effect", "N,has no effect", "' ',has unknown effect"})
    void shouldTranslateEffect_withoutLosingOriginalCode(String code, String description) {
        RxDsMessageTo1 dto = new RxDsMessageTo1(Map.of("effect", code, "name", "FixtureA", "drug2", "FixtureB"), messages, Locale.ENGLISH);
        assertThat(dto.getEffect()).isEqualTo(description);
        assertThat(dto.getEffectStr()).isEqualTo(code);
        assertThat(dto.getInteractStr()).isEqualTo("FixtureA " + description + " FixtureB");
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"bad", "2147483648"})
    void shouldDefaultSignificance_whenValueIsNotAnInteger(String significance) {
        Map<String, Object> data = new HashMap<>(); data.put("significance", significance);
        assertThat(new RxDsMessageTo1(data, messages, Locale.ENGLISH).getSignificance()).isZero();
    }
    @ParameterizedTest @ValueSource(strings = {"1", "2", "3"})
    void shouldRetainSignificance_whenSeverityIsValid(String significance) {
        RxDsMessageTo1 dto = new RxDsMessageTo1(Map.of("significance", significance), messages, Locale.ENGLISH);
        assertThat(dto.getSignificance()).isEqualTo(Integer.parseInt(significance));
    }
    @Test void shouldNormalizeIdentifier_whenUpstreamUsesStringOrInteger() {
        for (Object id : new Object[] {77, "77"}) {
            assertThat(new RxDsMessageTo1(Map.of("id", id), messages, Locale.ENGLISH).getId()).isEqualTo("77");
        }
    }
    @Test void shouldRetainDescription_whenEffectCodeIsAbsent() {
        RxDsMessageTo1 dto = new RxDsMessageTo1(Map.of("effectdesc", "Synthetic description", "trusted", true, "agree", false), messages, Locale.ENGLISH);
        assertThat(dto.getEffect()).isEqualTo("Synthetic description");
        assertThat(dto.getTrustedResource()).isTrue(); assertThat(dto.getAgree()).isFalse();
    }
}
