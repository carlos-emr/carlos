/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.util;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link FileSortByDate} file comparator.
 *
 * @since 2026-03-31
 */
@DisplayName("FileSortByDate Unit Tests")
@Tag("unit") @Tag("fast") @Tag("utility")
class FileSortByDateUnitTest {

    @TempDir
    private Path tempDir;

    private final FileSortByDate sorter = new FileSortByDate();

    @Test
    @DisplayName("should return negative when first file is newer")
    void shouldReturnNegative_whenFirstNewer() throws IOException {
        Path older = Files.createFile(tempDir.resolve("old.txt"));
        Path newer = Files.createFile(tempDir.resolve("new.txt"));
        newer.toFile().setLastModified(System.currentTimeMillis() + 1000);

        int result = sorter.compare(newer.toFile(), older.toFile());
        assertThat(result).isNegative();
    }

    @Test
    @DisplayName("should return zero for same file")
    void shouldReturnZero_forSameFile() throws IOException {
        Path file = Files.createFile(tempDir.resolve("same.txt"));
        int result = sorter.compare(file.toFile(), file.toFile());
        assertThat(result).isZero();
    }
}
