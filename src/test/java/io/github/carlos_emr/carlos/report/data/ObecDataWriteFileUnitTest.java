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
package io.github.carlos_emr.carlos.report.data;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ObecData#writeFile(String, Properties)} primary-write vs best-effort-outbox
 * semantics.
 *
 * <p>The OBEC file is the primary deliverable and is written to {@code DOCUMENT_DIR}; the EDT outbox
 * copy is best-effort and only attempted when {@code ONEDT_OUTBOX} is configured. The method must
 * return the generated filename only when the primary write succeeds, and an empty string when it
 * fails, so the caller/UI surfaces failure instead of a filename that was never written. The outbox
 * branch (which calls {@code ActionUtils}) is exercised by integration coverage, not here.</p>
 *
 * @since 2026-06-01
 */
@Tag("unit")
@Tag("fast")
@DisplayName("ObecData.writeFile primary-write vs outbox")
class ObecDataWriteFileUnitTest {

    @TempDir
    Path documentDir;

    @Test
    @DisplayName("should write to DOCUMENT_DIR and return the filename when the outbox is unconfigured")
    void shouldWriteToDocumentDir_whenOutboxUnconfigured() throws Exception {
        Properties pp = new Properties();
        pp.setProperty("DOCUMENT_DIR", documentDir.toString());
        // ONEDT_OUTBOX intentionally unset → best-effort copy is skipped; the primary write still happens.

        String name = new ObecData().writeFile("OBEC-CONTENT", pp);

        assertThat(name).startsWith("OBECE").endsWith(".TXT");
        Path written = documentDir.resolve(name);
        assertThat(written).exists();
        assertThat(Files.readString(written)).contains("OBEC-CONTENT");
    }

    @Test
    @DisplayName("should return an empty name and write nothing when DOCUMENT_DIR is misconfigured")
    void shouldReturnEmpty_whenDocumentDirBlank() {
        Properties pp = new Properties();
        pp.setProperty("DOCUMENT_DIR", "");   // blank → resolveConfiguredDirectory throws → primary write fails

        String name = new ObecData().writeFile("OBEC-CONTENT", pp);

        // A failed primary write must surface as an empty name, not a phantom filename.
        assertThat(name).isEmpty();
    }
}
