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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.engine;

import io.github.carlos_emr.carlos.test.logging.LogCapture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link AllowedDirectories}, the containment check the user agent and the image
 * planning share.
 */
@Tag("unit")
@Tag("htmlpdf")
@Tag("security")
@DisplayName("AllowedDirectories Unit Tests")
class AllowedDirectoriesUnitTest {

    @TempDir
    Path root;

    // ---- AllowedDirectories -----------------------------------------------------------------

    @Test
    @DisplayName("should allow a file inside a directory, the directory itself and a nested file")
    void shouldAllowFilesInside_theDirectories() throws IOException {
        Path allowed = Files.createDirectory(root.resolve("allowed"));
        Path nested = Files.createDirectories(allowed.resolve("a/b"));
        Path file = Files.writeString(nested.resolve("x.png"), "x");

        assertThat(AllowedDirectories.contains(List.of(allowed), file)).isTrue();
        assertThat(AllowedDirectories.contains(List.of(allowed), allowed)).isTrue();
        assertThat(AllowedDirectories.contains(List.of(root.resolve("other"), allowed), file)).isTrue();
    }

    @Test
    @DisplayName("should refuse a file outside every directory, a sibling that shares a name prefix and dot-dot paths")
    void shouldRefuseFilesOutside_theDirectories() throws IOException {
        Path allowed = Files.createDirectory(root.resolve("allowed"));
        Path sibling = Files.createDirectory(root.resolve("allowed-but-not"));
        Path outside = Files.writeString(root.resolve("secret.txt"), "s");
        Path inSibling = Files.writeString(sibling.resolve("x.png"), "x");

        assertThat(AllowedDirectories.contains(List.of(allowed), outside)).isFalse();
        assertThat(AllowedDirectories.contains(List.of(allowed), inSibling)).isFalse();
        assertThat(AllowedDirectories.contains(List.of(allowed), allowed.resolve("../secret.txt"))).isFalse();
        assertThat(AllowedDirectories.contains(List.of(), outside)).isFalse();
    }

    @Test
    @DisplayName("should follow a symbolic link to where it leads")
    void shouldFollowSymlinks_toTheirTarget() throws IOException {
        Path allowed = Files.createDirectory(root.resolve("allowed"));
        Path outside = Files.writeString(root.resolve("secret.txt"), "s");
        Path link = allowed.resolve("link.txt");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException e) {
            org.junit.jupiter.api.Assumptions.abort("symbolic links are not available here");
        }

        assertThat(AllowedDirectories.contains(List.of(allowed), link)).isFalse();
    }

    @Test
    @DisplayName("should refuse a path it cannot resolve without logging the path")
    void shouldFailClosed_andLogNoPath() {
        Path unresolvable = Mockito.mock(Path.class);
        Mockito.when(unresolvable.toFile()).thenThrow(new UnsupportedOperationException("FAKE-patient-file.png"));
        try (LogCapture capture = LogCapture.forLogger(AllowedDirectories.class)) {
            assertThat(AllowedDirectories.contains(List.of(root), unresolvable)).isFalse();
            assertThat(capture.messages()).singleElement().asString().contains("UnsupportedOperationException")
                    .doesNotContain("FAKE-patient");
        }
    }

    @Test
    @DisplayName("should reject null arguments")
    void shouldRejectNull_whenArgumentMissing() {
        assertThatNullPointerException().isThrownBy(() -> AllowedDirectories.contains(null, root));
        assertThatNullPointerException().isThrownBy(() -> AllowedDirectories.contains(List.of(root), null));
    }
}
