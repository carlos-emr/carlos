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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The stall window and the command line's renderer hand-off change only how a job runs, so any
 * value a {@code long} holds is accepted; only a value that is not a whole number is refused.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportConfigurationServiceUnitTest {
    private static CdsExportConfigurationService configuration(String key, String value) {
        var settings = new Properties();
        settings.setProperty(key, value);
        return new CdsExportConfigurationService(settings);
    }

    @Test
    void shouldAcceptAStallWindow_longerThanTheFormer31Days() {
        long days32 = 32L * 24 * 60 * 60 * 1000;
        assertThat(configuration("cds.export.maxDurationMillis", Long.toString(days32)).maxDurationMillis())
                .isEqualTo(days32);
        assertThat(
                        configuration("cds.export.maxDurationMillis", Long.toString(Long.MAX_VALUE))
                                .maxDurationMillis())
                .isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void shouldAcceptARendererHandoff_longerThanTheFormer86400Seconds() {
        assertThat(
                        configuration("cds.export.cliRendererHandoffSeconds", "86401")
                                .cliRendererHandoffMillis())
                .isEqualTo(86_401_000L);
        // Seconds no millisecond count can hold wait as long as a long can count.
        assertThat(
                        configuration(
                                        "cds.export.cliRendererHandoffSeconds",
                                        Long.toString(Long.MAX_VALUE))
                                .cliRendererHandoffMillis())
                .isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void shouldStillRefuseASettingThatIsNotAWholeNumber_forHowOnlySettings() {
        for (String value : new String[] {"0", "-1", "1.5", "1d", "", "9223372036854775808"}) {
            var settings = configuration("cds.export.maxDurationMillis", value);
            assertThatThrownBy(settings::maxDurationMillis)
                    .hasMessage("CDS_EXPORT_CONFIGURATION_INVALID");
        }
        for (String value : new String[] {"-1", "1.5", "2m", "", "9223372036854775808"}) {
            var settings = configuration("cds.export.cliRendererHandoffSeconds", value);
            assertThatThrownBy(settings::cliRendererHandoffMillis)
                    .hasMessage("CDS_EXPORT_CONFIGURATION_INVALID");
        }
    }
}
