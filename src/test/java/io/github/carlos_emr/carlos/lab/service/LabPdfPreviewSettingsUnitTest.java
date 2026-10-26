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
package io.github.carlos_emr.carlos.lab.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link LabPdfPreviewSettings}: preference parsing, defaults and clamping.
 *
 * @since 2026-09-30
 */
@Tag("unit")
@Tag("fast")
@Tag("lab")
@DisplayName("LabPdfPreviewSettings")
class LabPdfPreviewSettingsUnitTest {

    @ParameterizedTest
    @CsvSource({
            "10485760, 10485760",
            "512K, 524288",
            "5MB, 5242880",
            "5 mb, 5242880",
            "1G, 104857600",
            "2048, 2048",
            "5KB, 5120",
            "999999999G, 104857600"
    })
    @DisplayName("should parse byte counts and K/M/G sizes, clamped to the allowed maximum")
    void shouldParseSize_withUnits(String stored, long expected) {
        assertThat(LabPdfPreviewSettings.fromPreferences(null, stored).maxBytes()).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "-5", "0", "5TB", "1.5M", "9999999999", "5B", "5b", "5 B",
            "99999999999999999999", "99999999999999999999G"})
    @DisplayName("should use the 10 MiB default for missing, invalid or non-positive sizes")
    void shouldUseDefaultSize_forInvalidValue(String stored) {
        assertThat(LabPdfPreviewSettings.fromPreferences(null, stored).maxBytes())
                .isEqualTo(LabPdfPreviewSettings.DEFAULT_MAX_BYTES);
    }

    @Test
    @DisplayName("should use the default, without matching, for a value longer than the length cap")
    void shouldUseDefaultSize_forOverLongValue() {
        String overLong = "1" + " ".repeat(LabPdfPreviewSettings.MAX_SIZE_LENGTH) + "M";
        String atCap = "5" + " ".repeat(LabPdfPreviewSettings.MAX_SIZE_LENGTH - 3) + "MB";

        assertThat(LabPdfPreviewSettings.fromPreferences(null, overLong).maxBytes())
                .isEqualTo(LabPdfPreviewSettings.DEFAULT_MAX_BYTES);
        assertThat(LabPdfPreviewSettings.fromPreferences(null, "  " + "9".repeat(40) + "  ").maxBytes())
                .isEqualTo(LabPdfPreviewSettings.DEFAULT_MAX_BYTES);
        // Exactly at the cap still parses, so the cap rejects only over-long input.
        assertThat(atCap).hasSize(LabPdfPreviewSettings.MAX_SIZE_LENGTH);
        assertThat(LabPdfPreviewSettings.fromPreferences(null, atCap).maxBytes()).isEqualTo(5L * 1024 * 1024);
    }

    @Test
    @DisplayName("should leave the preview on unless the preference is exactly false")
    void shouldEnablePreview_unlessStoredFalse() {
        assertThat(LabPdfPreviewSettings.fromPreferences(null, null).inlinePreviewEnabled()).isTrue();
        assertThat(LabPdfPreviewSettings.fromPreferences("true", null).inlinePreviewEnabled()).isTrue();
        assertThat(LabPdfPreviewSettings.fromPreferences(" false ", null).inlinePreviewEnabled()).isFalse();
        assertThat(LabPdfPreviewSettings.DEFAULTS).isEqualTo(new LabPdfPreviewSettings(true, 10L * 1024 * 1024));
    }

    @Test
    @DisplayName("should round the limit up to whole MiB for display")
    void shouldRoundUpMegabytes_forDisplay() {
        assertThat(new LabPdfPreviewSettings(true, 1).maxMegabytes()).isEqualTo(1);
        assertThat(new LabPdfPreviewSettings(true, 3L * 1024 * 1024).maxMegabytes()).isEqualTo(3);
        assertThat(new LabPdfPreviewSettings(true, 3L * 1024 * 1024 + 1).maxMegabytes()).isEqualTo(4);
    }
}
