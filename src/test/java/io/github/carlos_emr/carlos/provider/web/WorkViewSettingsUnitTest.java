/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.provider.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.commn.model.View;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link WorkViewSettings}, the allowlist behind {@code /saveWorkView} (#2778).
 */
@Tag("unit")
@DisplayName("WorkViewSettings allowlist")
class WorkViewSettingsUnitTest {

    private static Map<String, String[]> params(String... keysAndValues) {
        Map<String, String[]> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], new String[] {keysAndValues[i + 1]});
        }
        return map;
    }

    @Test
    void shouldUseTheViewNameTheTicklerPageReads_forTicklerView() {
        assertThat(WorkViewSettings.TICKLER_VIEW).isEqualTo(View.TICKLER_VIEW);
    }

    @Test
    void shouldAcceptAllFourTicklerFilters_whenValuesAreValid() {
        Map<String, String> accepted = WorkViewSettings.accept("tickler",
                params("ticklerview", "C", "providerview", "all", "assignedTo", "999998", "mrpview", "12-3_"));

        assertThat(accepted).containsOnly(
                Map.entry("ticklerview", "C"), Map.entry("providerview", "all"),
                Map.entry("assignedTo", "999998"), Map.entry("mrpview", "12-3_"));
    }

    @Test
    void shouldAcceptOnlyTheSettingsSubmitted_whenTheRestAreAbsent() {
        assertThat(WorkViewSettings.accept("tickler", params("ticklerview", "D")))
                .containsOnly(Map.entry("ticklerview", "D"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"all", "", "1", "999998", "-1", "ab.c_d", "ABC123"})
    void shouldAcceptAllProvidersEmptyOrAProviderNumber_forProviderFilters(String value) {
        assertThat(WorkViewSettings.accept("tickler", params("assignedTo", value)))
                .containsOnly(Map.entry("assignedTo", value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "C", "D"})
    void shouldAcceptActiveCompletedAndDeleted_forTheStatusFilter(String value) {
        assertThat(WorkViewSettings.accept("tickler", params("ticklerview", value)))
                .containsOnly(Map.entry("ticklerview", value));
    }

    @Test
    void shouldIgnoreRoutingAndUnrelatedParameters_insteadOfStoringThem() {
        Map<String, String> accepted = WorkViewSettings.accept("tickler", params(
                "method", "save", "view_name", "tickler", "userrole", "admin", "providerno", "999999",
                "<script>alert(1)</script>", "x", "ticklerview", "A"));

        assertThat(accepted).containsOnlyKeys("ticklerview");
    }

    @Test
    void shouldUseOnlyTheFirstValue_ofARepeatedSetting() {
        Map<String, String[]> repeated = new LinkedHashMap<>();
        repeated.put("ticklerview", new String[] {"A", "<img src=x onerror=alert(1)>"});

        assertThat(WorkViewSettings.accept("tickler", repeated)).containsOnly(Map.entry("ticklerview", "A"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a", "X", "AC", "A\n", " A", "A ", "<b>", "all"})
    void shouldRejectTheRequest_whenTheStatusFilterIsNotAOrCOrD(String value) {
        assertThatThrownBy(() -> WorkViewSettings.accept("tickler", params("ticklerview", value)))
                .isInstanceOf(WorkViewSettings.Rejected.class)
                .hasMessage("invalid value for work view setting ticklerview");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567", "a b", "all\n", "<script>", "\"><img src=x onerror=alert(1)>",
            "a;b", "a'b", "a/b", "\u00e9", "\uff41\uff4c\uff4c"})
    void shouldRejectTheRequest_whenAProviderFilterIsNeitherAllNorAProviderNumber(String value) {
        for (String setting : new String[] {"providerview", "assignedTo", "mrpview"}) {
            assertThatThrownBy(() -> WorkViewSettings.accept("tickler", params(setting, value)))
                    .isInstanceOf(WorkViewSettings.Rejected.class)
                    .hasMessage("invalid value for work view setting " + setting);
        }
    }

    @Test
    void shouldRejectTheWholeRequest_whenOneOfSeveralValuesIsInvalid() {
        assertThatThrownBy(() -> WorkViewSettings.accept("tickler",
                params("ticklerview", "A", "providerview", "all", "assignedTo", "<script>")))
                .isInstanceOf(WorkViewSettings.Rejected.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Tickler", "TICKLER", "tickler ", "provider", "../tickler", "view_name", "<script>"})
    void shouldRejectTheRequest_whenTheViewIsNotOneCarlosReads(String viewName) {
        assertThatThrownBy(() -> WorkViewSettings.accept(viewName, params("ticklerview", "A")))
                .isInstanceOf(WorkViewSettings.Rejected.class)
                .hasMessage("unknown work view");
    }

    @Test
    void shouldRejectTheRequest_whenTheViewNameIsMissing() {
        assertThatThrownBy(() -> WorkViewSettings.accept(null, params("ticklerview", "A")))
                .isInstanceOf(WorkViewSettings.Rejected.class);
    }

    @Test
    void shouldRejectTheRequest_whenNoSettingOfTheViewWasSubmitted() {
        assertThatThrownBy(() -> WorkViewSettings.accept("tickler", params("method", "save", "foo", "bar")))
                .isInstanceOf(WorkViewSettings.Rejected.class)
                .hasMessage("no work view settings submitted");
        assertThatThrownBy(() -> WorkViewSettings.accept("tickler", Map.of()))
                .isInstanceOf(WorkViewSettings.Rejected.class);
        assertThatThrownBy(() -> WorkViewSettings.accept("tickler", null))
                .isInstanceOf(WorkViewSettings.Rejected.class);
    }

    @Test
    void shouldTreatAnEmptyValueArray_asNotSubmitted() {
        Map<String, String[]> empty = new LinkedHashMap<>();
        empty.put("assignedTo", new String[0]);
        empty.put("ticklerview", new String[] {"A"});

        assertThat(WorkViewSettings.accept("tickler", empty)).containsOnlyKeys("ticklerview");
    }

    @Test
    void shouldNeverRepeatASubmittedValue_inTheRejectionMessage() {
        String hostile = "<script>alert('x')</script>\r\nFORGED LOG LINE";

        assertThatThrownBy(() -> WorkViewSettings.accept("tickler", params("assignedTo", hostile)))
                .hasMessageNotContaining("script").hasMessageNotContaining("FORGED");
        assertThatThrownBy(() -> WorkViewSettings.accept(hostile, params("ticklerview", "A")))
                .hasMessageNotContaining("script").hasMessageNotContaining("FORGED");
    }

    @Test
    void shouldReturnTheSettings_asAnUnmodifiableMap() {
        Map<String, String> accepted = WorkViewSettings.accept("tickler", params("ticklerview", "A"));

        assertThatThrownBy(() -> accepted.put("injected", "x")).isInstanceOf(UnsupportedOperationException.class);
    }
}
