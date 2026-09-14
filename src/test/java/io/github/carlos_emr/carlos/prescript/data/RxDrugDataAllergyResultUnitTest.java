/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.prescript.data;

import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.prescript.util.RxDrugRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("rx")
@DisplayName("DrugRef allergy-result completeness")
class RxDrugDataAllergyResultUnitTest {
    @Test
    @DisplayName("should preserve confirmed and unresolved results with nullable legacy allergy fields")
    void shouldPreserveResults_whenLegacyFieldsAreNull() throws Exception {
        Allergy first = new Allergy();
        first.setId(1);
        Allergy second = new Allergy();
        second.setId(2);
        List<Allergy> missing = new ArrayList<>();
        try (MockedConstruction<RxDrugRef> ignored = response(List.of(Map.of("warnings", List.of("0"), "missing", List.of("1"))))) {
            assertThat(new RxDrugData().getAllergyWarnings("J01FA09", new Allergy[]{first, second}, missing)).containsExactly(first);
            assertThat(missing).containsExactly(second);
        }
    }

    @Test
    @DisplayName("should reject absent result envelopes instead of returning all clear")
    void shouldRejectAbsentResults_whenReferenceReturnsNoEnvelope() {
        for (List<?> result : java.util.Arrays.asList(null, List.of(), List.of(Map.of()), List.of(Map.of("warnings", List.of())))) {
            try (MockedConstruction<RxDrugRef> ignored = response(result)) {
                assertThatThrownBy(() -> new RxDrugData().getAllergyWarnings("J01FA09", new Allergy[]{new Allergy()}, new ArrayList<>()))
                        .isInstanceOf(IllegalStateException.class);
            }
        }
    }

    @Test
    @DisplayName("should reject invalid allergy indices instead of silently dropping warnings")
    void shouldRejectInvalidIndices_whenReferenceReturnsMalformedResults() {
        for (Object index : List.of("-1", "1", "not-an-index", 0)) {
            try (MockedConstruction<RxDrugRef> ignored = response(List.of(Map.of("warnings", List.of(index), "missing", List.of())))) {
                assertThatThrownBy(() -> new RxDrugData().getAllergyWarnings("J01FA09", new Allergy[]{new Allergy()}, new ArrayList<>()))
                        .isInstanceOf(RuntimeException.class);
            }
        }
    }

    @Test
    @DisplayName("should accept a complete negative response")
    void shouldAcceptNegativeResponse_whenBothResultListsAreEmpty() throws Exception {
        try (MockedConstruction<RxDrugRef> ignored = response(List.of(Map.of("warnings", List.of(), "missing", List.of())))) {
            List<Allergy> missing = new ArrayList<>();
            assertThat(new RxDrugData().getAllergyWarnings("J01FA09", new Allergy[]{new Allergy()}, missing)).isEmpty();
            assertThat(missing).isEmpty();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private MockedConstruction<RxDrugRef> response(List<?> result) {
        return mockConstruction(RxDrugRef.class, (mock, context) ->
                when(mock.getAlergyWarnings(eq("J01FA09"), any())).thenReturn(result == null ? null : new Vector(result)));
    }
}
