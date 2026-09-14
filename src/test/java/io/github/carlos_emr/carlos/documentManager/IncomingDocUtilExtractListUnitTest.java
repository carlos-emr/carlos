/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the page-specification parser behind {@code IncomingDocUtil.extractPage}.
 *
 * <p>This parser decides which pages are removed from a queued patient document and which are
 * split into the extracted copy, so an accepted-but-wrong specification silently rewrites a
 * clinical record. The parser is private; it is exercised reflectively because driving it through
 * {@code extractPage} would require a configured incoming-document directory and real PDFs.
 *
 * @since 2026-09-14
 */
@Tag("unit")
@Tag("document")
@DisplayName("IncomingDocUtil page-extraction specification parsing")
class IncomingDocUtilExtractListUnitTest {

    private static final int PAGE_COUNT = 5;

    private static Method buildExtractList;

    @BeforeAll
    static void resolveParser() throws NoSuchMethodException {
        buildExtractList = IncomingDocUtil.class.getDeclaredMethod("buildExtractList", String.class, int.class);
        buildExtractList.setAccessible(true);
    }

    @SuppressWarnings("unchecked")
    private static List<String> parse(String pageSpec) throws Exception {
        return (List<String>) buildExtractList.invoke(null, pageSpec, PAGE_COUNT);
    }

    /** extractList is 1-based; index 0 is an unused placeholder, and "1" marks a page for extraction. */
    private static List<String> marks(Integer... extractedPages) {
        List<String> expected = new ArrayList<>();
        for (int index = 0; index <= PAGE_COUNT; index++) {
            expected.add("0");
        }
        for (Integer page : extractedPages) {
            expected.set(page, "1");
        }
        return expected;
    }

    @Test
    @DisplayName("should mark a single page for extraction")
    void shouldMarkSinglePage_forOnePageSpec() throws Exception {
        assertThat(parse("2")).isEqualTo(marks(2));
    }

    @Test
    @DisplayName("should mark several individual pages for extraction")
    void shouldMarkSeveralPages_forCommaSeparatedSpec() throws Exception {
        assertThat(parse("1,3")).isEqualTo(marks(1, 3));
    }

    @Test
    @DisplayName("should mark an inclusive range for extraction")
    void shouldMarkInclusiveRange_forRangeSpec() throws Exception {
        assertThat(parse("2-4")).isEqualTo(marks(2, 3, 4));
    }

    @Test
    @DisplayName("should combine ranges with individual pages")
    void shouldCombineRangeAndSinglePage_forMixedSpec() throws Exception {
        assertThat(parse("1,3-4")).isEqualTo(marks(1, 3, 4));
    }

    @Test
    @DisplayName("should ignore empty segments between separators")
    void shouldIgnoreEmptySegments_forRepeatedSeparators() throws Exception {
        assertThat(parse("1,,3")).isEqualTo(marks(1, 3));
    }

    @Test
    @DisplayName("should accept a single-page range")
    void shouldAcceptRange_forEqualBounds() throws Exception {
        assertThat(parse("3-3")).isEqualTo(marks(3));
    }

    @ParameterizedTest
    @DisplayName("should reject malformed or out-of-bounds page specifications")
    @ValueSource(strings = {
            "1-",        // incomplete range: must not collapse to page 1
            "-3",        // missing lower bound
            "4-2",       // reversed bounds
            "0",         // pages are 1-based
            "6",         // beyond the document
            "3-6",       // range beyond the document
            "abc",       // not numeric
            "2-x",       // non-numeric bound
            "1-2-3",     // too many bounds
            "  ",        // blank
            "",          // empty
            "1,2,3,4,5"  // every page: nothing would remain in the queued document
    })
    void shouldRejectSpec_whenMalformedOrOutOfBounds(String pageSpec) {
        assertThatThrownBy(() -> parse(pageSpec))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject a null page specification")
    void shouldRejectSpec_whenNull() {
        assertThatThrownBy(() -> parse(null))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject a full range covering every page")
    void shouldRejectRange_whenItCoversEveryPage() {
        assertThatThrownBy(() -> parse("1-5"))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should not echo the rejected specification in the failure message")
    void shouldNotEchoSpec_inRejectionMessage() {
        assertThatThrownBy(() -> parse("9999"))
                .isInstanceOf(InvocationTargetException.class)
                .extracting(Throwable::getCause)
                .extracting(Throwable::getMessage)
                .asString()
                .contains("Invalid Pages to Extract");
    }
}
