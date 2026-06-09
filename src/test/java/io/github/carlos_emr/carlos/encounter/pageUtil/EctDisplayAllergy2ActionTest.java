package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.provider.web.CppPreferencesUIBean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for allergy navbar description construction in
 * {@link EctDisplayAllergy2Action}.
 *
 * @since 2026-06-09
 */
@DisplayName("EctDisplayAllergy2Action - Description Building")
@Tag("unit")
@Tag("fast")
@Tag("encounter")
class EctDisplayAllergy2ActionTest {

    @Test
    @DisplayName("Null description should not render literal null when start date is enabled")
    void shouldCoalesceNullDescription_whenStartDatePreferenceIsEnabled() {
        CppPreferencesUIBean prefsBean = mock(CppPreferencesUIBean.class);
        when(prefsBean.getAllergyStartDate()).thenReturn("on");

        String description = EctDisplayAllergy2Action.buildCustomDescription(
                null,
                Locale.CANADA,
                prefsBean,
                new Date(0L),
                null
        );

        assertThat(description)
                .startsWith(" Start Date:")
                .doesNotContain("null");
    }

    @Test
    @DisplayName("Null description should not render literal null when severity is enabled")
    void shouldCoalesceNullDescription_whenSeverityPreferenceIsEnabled() {
        CppPreferencesUIBean prefsBean = mock(CppPreferencesUIBean.class);
        when(prefsBean.getAllergySeverity()).thenReturn("on");

        String description = EctDisplayAllergy2Action.buildCustomDescription(
                null,
                Locale.CANADA,
                prefsBean,
                new Date(0L),
                "Critical"
        );

        assertThat(description)
                .isEqualTo(" Severity:Critical")
                .doesNotContain("null");
    }

    @Test
    @DisplayName("Null severity should not render literal null when severity is enabled")
    void shouldCoalesceNullSeverityDescription_whenSeverityPreferenceIsEnabled() {
        CppPreferencesUIBean prefsBean = mock(CppPreferencesUIBean.class);
        when(prefsBean.getAllergySeverity()).thenReturn("on");

        String description = EctDisplayAllergy2Action.buildCustomDescription(
                "Peanuts",
                Locale.CANADA,
                prefsBean,
                new Date(0L),
                null
        );

        assertThat(description)
                .isEqualTo("Peanuts Severity:")
                .doesNotContain("null");
    }
}
