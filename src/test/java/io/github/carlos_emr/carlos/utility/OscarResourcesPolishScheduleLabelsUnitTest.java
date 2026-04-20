/*
 * Copyright (c) 2026 CARLOS EMR Project
 *
 * This file is part of CARLOS EMR.
 *
 * CARLOS EMR is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * CARLOS EMR is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with CARLOS EMR. If not, see <https://www.gnu.org/licenses/>.
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
