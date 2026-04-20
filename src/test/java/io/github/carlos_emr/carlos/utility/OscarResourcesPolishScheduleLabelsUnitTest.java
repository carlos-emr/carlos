/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the Polish schedule-header translations fixed by this change.
 *
 * <p>The schedule page mixes several resource keys from different sections of
 * {@code oscarResources}. This regression test ensures the visible top
 * navigation labels touched by this fix resolve to Polish strings instead of
 * English placeholders when the bundle is loaded for a Polish locale.</p>
 *
 * @since 2026-04-20
 */
@Tag("unit")
@DisplayName("Polish schedule header resource bundle entries")
class OscarResourcesPolishScheduleLabelsUnitTest {

    private static final ResourceBundle POLISH_BUNDLE = ResourceBundle.getBundle("oscarResources", Locale.forLanguageTag("pl-PL"));

    @Test
    @DisplayName("should resolve Polish schedule header labels that were English placeholders")
    void shouldResolvePolishScheduleHeaderLabels_whenPlaceholderEntriesAreLocalized() {
        assertThat(POLISH_BUNDLE.getString("global.lab")).isEqualTo("Laboratorium");
        assertThat(POLISH_BUNDLE.getString("global.btntickler")).isEqualTo("Przypomnienia");
        assertThat(POLISH_BUNDLE.getString("global.msg")).isEqualTo("Wiadomości");
        assertThat(POLISH_BUNDLE.getString("global.con")).isEqualTo("Konsultacje");
        assertThat(POLISH_BUNDLE.getString("global.report")).isEqualTo("Raporty");
        assertThat(POLISH_BUNDLE.getString("encounter.Index.clinicalResources")).isEqualTo("Zasoby kliniczne");
    }
}
