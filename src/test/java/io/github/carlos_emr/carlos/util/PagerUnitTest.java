/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.util;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link Pager} HTML pagination generator.
 *
 * @since 2026-03-31
 */
@DisplayName("Pager Unit Tests")
@Tag("unit") @Tag("fast") @Tag("utility")
class PagerUnitTest {

    @Test
    @DisplayName("should return empty string when all results fit on one page")
    void shouldReturnEmpty_whenAllFitOnOnePage() {
        String result = Pager.generate(0, 5, 10, "/search");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should generate pagination HTML when results exceed page size")
    void shouldGenerateHtml_whenResultsExceedPageSize() {
        String result = Pager.generate(0, 100, 10, "/search");
        assertThat(result).isNotEmpty();
        assertThat(result).contains("href=");
        assertThat(result).contains("pager.offset");
    }

    @Test
    @DisplayName("should include next link when not on last page")
    void shouldIncludeNextLink_whenNotOnLastPage() {
        String result = Pager.generate(0, 100, 10, "/search");
        assertThat(result).contains("pager.offset=10");
    }

    @Test
    @DisplayName("should bold current page number")
    void shouldBoldCurrentPage() {
        String result = Pager.generate(0, 100, 10, "/search");
        assertThat(result).contains("<b>1</b>");
    }

    @Test
    @DisplayName("should handle URL with existing query parameters")
    void shouldHandleUrl_withExistingQueryParams() {
        String result = Pager.generate(0, 100, 10, "/search?q=test");
        assertThat(result).contains("&pager.offset=");
    }

    @Test
    @DisplayName("should handle URL without query parameters")
    void shouldHandleUrl_withoutQueryParams() {
        String result = Pager.generate(0, 100, 10, "/search");
        assertThat(result).contains("?pager.offset=");
    }
}
