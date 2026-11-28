/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prescript.util;

import io.github.carlos_emr.carlos.test.logging.LogCapture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

/**
 * {@link RxDrugRef#getDrugByDIN} reads every way DrugRef says "no product carries this DIN" as
 * {@code null}, so Rx Info falls back to its name search, and logs nothing for it (#5006).
 *
 * @since 2026-10-18
 */
@Tag("unit")
@DisplayName("DrugRef DIN lookup")
class RxDrugRefDinLookupUnitTest {
    private static final String ENDPOINT = "http://localhost/drugref2/DrugrefService";
    private static final String DIN = "00123456";

    private static Object lookup(Object answer) throws Exception {
        try (MockedConstruction<SimpleXmlRpcClient> _ = mockConstruction(SimpleXmlRpcClient.class,
                (client, context) -> when(client.execute(eq("get_drug_by_DIN"), any())).thenReturn(answer))) {
            return new RxDrugRef(ENDPOINT).getDrugByDIN(DIN, Boolean.TRUE);
        }
    }

    @Test
    @DisplayName("should read an empty array for an unknown DIN as not found, logging nothing")
    void shouldReturnNull_whenDrugRefAnswersEmptyArray() throws Exception {
        try (LogCapture logs = LogCapture.forLogger(RxDrugRef.class)) {
            assertThat(lookup(new Vector<>())).isNull();
            assertThat(logs.messages()).isEmpty();
        }
    }

    @Test
    @DisplayName("should read the empty string an older DrugRef sends for a null answer as not found")
    void shouldReturnNull_whenDrugRefAnswersEmptyString() throws Exception {
        assertThat(lookup("")).isNull();
        assertThat(lookup(null)).isNull();
    }

    @Test
    @DisplayName("should return the product of a known DIN")
    void shouldReturnProduct_whenDinIsKnown() throws Exception {
        var product = product(DIN);
        assertThat(lookup(new Vector<>(List.of(product)))).isSameAs(product);
    }

    @Test
    @DisplayName("should discard a monograph whose DIN is different, missing or numeric")
    void shouldReturnNull_whenTheResponseCannotVerifyTheRequestedDin() throws Exception {
        try (LogCapture logs = LogCapture.forLogger(RxDrugRef.class)) {
            for (var product : List.of(product("00629367"), product("123456"), product(null), product(123456))) {
                assertThat(lookup(new Vector<>(List.of(product)))).isNull();
            }
            assertThat(logs.messages()).hasSize(4).allSatisfy(message ->
                    assertThat(message).doesNotContain(DIN, "00629367", "SYNTHETIC PRODUCT"));
        }
    }

    @Test
    @DisplayName("should keep any other answer shape a failure")
    void shouldThrow_whenDrugRefAnswersUnexpectedShape() {
        assertThatThrownBy(() -> lookup("not a list"))
                .hasMessageContaining("unexpected get_drug_by_DIN answer (String)");
        assertThatThrownBy(() -> lookup(new Hashtable<>()))
                .hasMessageContaining("unexpected get_drug_by_DIN answer (Hashtable)");
        assertThatThrownBy(() -> lookup(new Vector<>(List.of("not a struct"))))
                .hasMessageContaining("expected a single product");
        assertThatThrownBy(() -> lookup(new Vector<>(List.of(product(DIN), product("00629367")))))
                .hasMessageContaining("expected a single product");
    }

    private static Hashtable<String, Object> product(Object din) {
        Hashtable<String, Object> product = new Hashtable<>();
        product.put("name", "SYNTHETIC PRODUCT");
        if (din != null) {
            product.put("regional_identifier", din);
        }
        return product;
    }
}
