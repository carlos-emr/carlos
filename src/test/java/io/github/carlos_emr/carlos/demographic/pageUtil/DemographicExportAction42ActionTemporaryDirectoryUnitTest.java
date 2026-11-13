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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The legacy export writes each patient's XML into its own random directory under TMP_DIR before
 * the archive exists. When a reader fails part way through (the prevention readers now fail
 * visibly), that directory and every file in it must be removed (review F8).
 *
 * @since 2026-11-13
 */
@Tag("unit")
@Tag("demographic")
@DisplayName("DemographicExportAction42Action temporary export directory")
class DemographicExportAction42ActionTemporaryDirectoryUnitTest
        extends DemographicExportActionUnitTestBase {

    @TempDir Path root;

    @Test
    @DisplayName("should remove every written file without following a link out of the directory")
    void shouldRemoveTheExportDirectory_withoutFollowingLinks() throws Exception {
        Path outside = Files.writeString(root.resolve("outside.txt"), "kept", StandardCharsets.UTF_8);
        Path export = Files.createDirectory(root.resolve("AbCdEfGh"));
        Files.writeString(export.resolve("FAKE_patient_1.xml"), "<x/>", StandardCharsets.UTF_8);
        Files.createDirectories(export.resolve("nested"));
        Files.writeString(export.resolve("nested/FAKE_patient_2.xml"), "<x/>", StandardCharsets.UTF_8);
        Files.createSymbolicLink(export.resolve("link"), outside);

        DemographicExportAction42Action.discardExportDirectory(export.toString());

        assertThat(export).doesNotExist();
        assertThat(outside).hasContent("kept");
    }

    @Test
    @DisplayName("should do nothing when the export directory was never created")
    void shouldIgnoreAMissingDirectory_forAnExportThatFailedBeforeWriting() {
        DemographicExportAction42Action.discardExportDirectory(root.resolve("missing").toString());

        assertThat(root.resolve("missing")).doesNotExist();
    }
}
