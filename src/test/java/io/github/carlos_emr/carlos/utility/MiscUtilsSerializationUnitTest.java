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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for {@link MiscUtils} file (de)serialization after the read path was
 * routed through {@link PathValidationUtils#validateConfiguredFile}. They confirm the
 * happy-path round trip still works and that a missing file is surfaced as an
 * {@link IOException} (the contract callers rely on), not as an unchecked
 * {@link SecurityException}.
 */
@DisplayName("MiscUtils serialization")
@Tag("unit")
@Tag("fast")
class MiscUtilsSerializationUnitTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("should round-trip a serializable object through file write then read")
    void shouldRoundTripSerializable_whenWritingThenReadingFile() throws Exception {
        Path target = tempDir.resolve("payload.ser");
        String original = "carlos-serialized-payload";

        MiscUtils.serializeToFile(original, target.toString());
        Serializable restored = MiscUtils.deserializeFromFile(target.toString());

        assertThat(restored).isEqualTo(original);
    }

    @Test
    @DisplayName("should throw IOException when deserializing a missing file")
    void shouldThrowIOException_whenDeserializingMissingFile() {
        Path missing = tempDir.resolve("does-not-exist.ser");

        // The fix maps PathValidationUtils' SecurityException back to IOException so callers
        // catching "not found" keep working, as they did before validation was added.
        assertThatThrownBy(() -> MiscUtils.deserializeFromFile(missing.toString()))
                .isInstanceOf(IOException.class)
                .hasCauseInstanceOf(SecurityException.class);
    }
}
