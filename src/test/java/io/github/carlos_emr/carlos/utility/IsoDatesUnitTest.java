/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class IsoDatesUnitTest {
    @Test
    void shouldKeepLeniencyAndPrefixParsing_forLegacyRestDates() throws Exception {
        assertThat(IsoDates.parseIso8601Date("2026-13-01"))
                .isEqualTo(IsoDates.parseIso8601Date("2027-01-01"));
        assertThat(IsoDates.parseIso8601Date("2026-02-30 trailing"))
                .isEqualTo(IsoDates.parseIso8601Date("2026-03-02"));
        assertThat(IsoDates.parseIso8601Date("2026-01-02T10:00"))
                .isEqualTo(IsoDates.parseIso8601Date("2026-01-02"));
        assertThatThrownBy(() -> IsoDates.parseIso8601Date("invalid"))
                .isInstanceOf(ParseException.class);
        assertThatThrownBy(() -> IsoDates.parseIso8601Date(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldReadJvmDefaults_onEveryCall() throws Exception {
        TimeZone zone = TimeZone.getDefault();
        Locale locale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            for (String id : new String[]{"UTC", "America/Toronto", "Pacific/Auckland"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(id));
                Calendar expected = Calendar.getInstance();
                expected.clear();
                expected.set(2026, Calendar.JANUARY, 2);
                assertThat(IsoDates.parseIso8601Date("2026-01-02"))
                        .as("default time zone %s", id).isEqualTo(expected.getTime());
            }
        } finally {
            TimeZone.setDefault(zone);
            Locale.setDefault(locale);
        }
    }
}
