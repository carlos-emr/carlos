/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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

import java.lang.reflect.Method;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused tests for {@code Util.sanitizeZipFileName}, the ZIP-specific filename helper that survived
 * the issue #2213 audit. Its path-safety check now delegates to
 * {@code PathValidationUtils.validatePathComponent}, but it deliberately keeps two ZIP-specific
 * traits its callers depend on: it defaults the extension to {@code .zip} when none is supplied, and
 * it returns an empty string (never throws) when the name cannot be made safe, so {@code Util.zipFiles}
 * can abort the export on an empty result.
 *
 * <p>The helper is private; it is exercised by reflection because the surrounding {@code Util.zipFiles}
 * entrypoints require a configured export directory and real files on disk.</p>
 */
@Tag("unit")
@Tag("security")
@DisplayName("Util.sanitizeZipFileName (issue #2213)")
class UtilZipFileNameUnitTest extends CarlosUnitTestBase {

    private static String sanitize(String input) throws Exception {
        Method method = Util.class.getDeclaredMethod("sanitizeZipFileName", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, input);
    }

    @Test
    @DisplayName("defaults the extension to .zip when the caller supplied none")
    void shouldDefaultToZipExtension_whenNoneSupplied() throws Exception {
        assertThat(sanitize("export")).isEqualTo("export.zip");
    }

    @Test
    @DisplayName("keeps an existing extension instead of forcing .zip")
    void shouldPreserveExistingExtension_whenPresent() throws Exception {
        assertThat(sanitize("report.txt")).isEqualTo("report.txt");
        assertThat(sanitize("backup.zip")).isEqualTo("backup.zip");
    }

    @Test
    @DisplayName("strips the leading dot of a hidden name and defaults to .zip")
    void shouldNeutralizeHiddenName_byStrippingLeadingDot() throws Exception {
        assertThat(sanitize(".env")).isEqualTo("env.zip");
    }

    @Test
    @DisplayName("neutralizes a path-like name so no separator or traversal remains")
    void shouldNeutralizeTraversal_whenNameContainsPathSegments() throws Exception {
        String result = sanitize("../secret.txt");
        assertThat(result)
                .isNotEmpty()
                .doesNotContain("..")
                .doesNotContain("/")
                .doesNotContain("\\");
    }

    @Test
    @DisplayName("collapses repeated dots without leaving a traversal sequence")
    void shouldCollapseRepeatedDots_withoutTraversal() throws Exception {
        String result = sanitize("my..report.zip");
        assertThat(result).isNotEmpty().doesNotContain("..");
    }

    @Test
    @DisplayName("returns empty (never throws) when the name cleans to an unusable value")
    void shouldReturnEmpty_whenNameCleansToUnusable() throws Exception {
        // "." strips to empty, so the defaulted ".zip" is a hidden name the centralized validator
        // rejects; the helper honours its empty-string failure contract rather than throwing.
        assertThat(sanitize(".")).isEqualTo("");
    }

    @Test
    @DisplayName("returns empty for null or blank input")
    void shouldReturnEmpty_forNullOrBlankInput() throws Exception {
        assertThat(sanitize(null)).isEqualTo("");
        assertThat(sanitize("   ")).isEqualTo("");
    }
}
