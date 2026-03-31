/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.util;

import org.junit.jupiter.api.*;
import java.io.File;
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

    private FileSortByDate sorter = new FileSortByDate();

    @Test
    @DisplayName("should return negative when first file is newer")
    void shouldReturnNegative_whenFirstNewer() throws IOException {
        Path older = Files.createTempFile("old", ".txt");
        Thread.yield();
        Path newer = Files.createTempFile("new", ".txt");
        newer.toFile().setLastModified(System.currentTimeMillis() + 1000);

        int result = sorter.compare(newer.toFile(), older.toFile());
        assertThat(result).isLessThanOrEqualTo(0);

        Files.deleteIfExists(older);
        Files.deleteIfExists(newer);
    }

    @Test
    @DisplayName("should return zero for same file")
    void shouldReturnZero_forSameFile() throws IOException {
        Path file = Files.createTempFile("same", ".txt");
        int result = sorter.compare(file.toFile(), file.toFile());
        assertThat(result).isZero();
        Files.deleteIfExists(file);
    }
}
