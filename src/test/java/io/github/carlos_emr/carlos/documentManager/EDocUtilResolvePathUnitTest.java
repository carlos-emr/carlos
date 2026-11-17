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
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Unit tests for {@link EDocUtil#resolvePath(String)}, which accepts a file in the document
 * directory or in an allowed temp directory and refuses anything else.
 *
 * <p>Staged temp files are the common case on the fax path: every cover-page preview image of an
 * eForm fax resolves its staged PDF through here. Those must resolve without
 * {@link PathValidationUtils} logging a containment ERROR, which the packaged server-log audit
 * reports as an unexplained error on every fax preview.</p>
 *
 * @since 2026-10-22
 */
@DisplayName("EDocUtil.resolvePath containment")
@Tag("unit")
@Tag("security")
@Tag("fax")
class EDocUtilResolvePathUnitTest extends CarlosUnitTestBase {

    /** Under java.io.tmpdir, which PathValidationUtils always allows. */
    @TempDir
    Path stagingDir;

    private Path documentDir;
    private String originalDocumentDir;

    @BeforeEach
    void setUp() throws IOException {
        originalDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        // A document store outside every allowed temp directory, as on an installed system.
        Path target = Files.createDirectories(Path.of("target").toAbsolutePath());
        documentDir = Files.createTempDirectory(target, "edoc-resolve-path-");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        // Properties.setProperty rejects null, so a previously unset key is removed instead.
        if (originalDocumentDir == null) {
            CarlosProperties.getInstance().remove("DOCUMENT_DIR");
        } else {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", originalDocumentDir);
        }
        try (Stream<Path> paths = Files.walk(documentDir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    @DisplayName("should resolve a staged temp file without logging a containment error")
    void shouldResolveStagedTempFile_withoutLoggingContainmentError() throws IOException {
        Path staged = Files.writeString(stagingDir.resolve("staged-eform.pdf"), "%PDF-1.7");

        try (LogCapture logs = LogCapture.forLogger(PathValidationUtils.class)) {
            assertThat(EDocUtil.resolvePath(staged.toString())).isEqualTo(staged.toFile().getCanonicalPath());
            assertThat(logs.events()).noneMatch(event -> event.getLevel().isMoreSpecificThan(Level.ERROR));
        }
    }

    @Test
    @DisplayName("should resolve a file stored in the document directory")
    void shouldResolveStoredDocument_whenInsideDocumentDirectory() throws IOException {
        assumeFalse(PathValidationUtils.isInAllowedTempDirectory(documentDir.toFile()),
                "the document directory must not sit inside an allowed temp directory for this case");
        Path stored = Files.writeString(documentDir.resolve("stored.pdf"), "%PDF-1.7");

        assertThat(EDocUtil.resolvePath(stored.toString())).isEqualTo(stored.toFile().getCanonicalPath());
        // A relative name resolves against the document directory.
        assertThat(EDocUtil.resolvePath("stored.pdf")).isEqualTo(stored.toFile().getCanonicalPath());
    }

    @Test
    @DisplayName("should refuse a file outside both the document directory and the temp directories")
    void shouldRefuseFile_whenOutsideDocumentDirectoryAndTempDirectories() throws IOException {
        Path outside = Files.createTempFile(documentDir.getParent(), "edoc-outside-", ".pdf");
        try {
            assumeFalse(PathValidationUtils.isInAllowedTempDirectory(outside.toFile()),
                    "the probe file must not sit inside an allowed temp directory for this case");

            // Arguments are built outside the lambdas so only resolvePath can supply the exception.
            String absolute = outside.toString();
            String traversal = "../" + outside.getFileName();
            assertThatThrownBy(() -> EDocUtil.resolvePath(absolute))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("Access denied: File is outside the allowed directories");
            assertThatThrownBy(() -> EDocUtil.resolvePath(traversal))
                    .isInstanceOf(SecurityException.class);
        } finally {
            Files.deleteIfExists(outside);
        }
    }
}
