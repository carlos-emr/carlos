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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ImportDemographicDataAction42Action}.
 *
 * @since 2026-05-03
 */
@DisplayName("ImportDemographicDataAction42Action Tests")
@Tag("unit")
@Tag("web")
@Tag("demographic")
class ImportDemographicDataAction42ActionUnitTest {

    @Test
    @DisplayName("should return false when no upload is present")
    void shouldReturnFalse_whenNoUploadIsPresent() {
        assertThat(ImportDemographicDataAction42Action.hasUploadedImportFile(null, null))
                .isFalse();
    }

    @Test
    @DisplayName("should return false when uploaded filename is blank")
    void shouldReturnFalse_whenUploadedFilenameIsBlank() {
        // The helper only checks for a non-null File; no real file is required here.
        assertThat(ImportDemographicDataAction42Action.hasUploadedImportFile(new File("dummy-upload"), " "))
                .isFalse();
    }

    @Test
    @DisplayName("should return true when upload file and filename are present")
    void shouldReturnTrue_whenUploadFileAndFilenameArePresent() {
        // The helper only checks for a non-null File; file existence/content is not under test.
        assertThat(ImportDemographicDataAction42Action.hasUploadedImportFile(new File("dummy-upload"), "patient.xml"))
                .isTrue();
    }
}
