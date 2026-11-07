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
package io.github.carlos_emr.carlos.encounter.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the page window of the chart Forms list ({@code encounter/formlist.jsp}, issue #5684):
 * ten rows a page counted across every form type, Previous and Next only when rows lie
 * before or after the page, and request values that cannot name a page falling back to the
 * first page instead of failing it.
 */
@DisplayName("FormListPageCalculator")
@Tag("unit")
@Tag("encounter")
class FormListPageCalculatorUnitTest {

    private static int[] rowsShown(FormListPageCalculator.Page page) {
        return IntStream.range(0, page.totalRows()).filter(page::contains).toArray();
    }

    @Nested
    @DisplayName("first page")
    class FirstPage {

        @Test
        void shouldShowFirstTenRowsWithNextOnly_whenElevenRowsAndNoParameters() {
            FormListPageCalculator.Page page = FormListPageCalculator.calculate(null, null, 11);

            assertThat(page.offset()).isZero();
            assertThat(page.size()).isEqualTo(FormListPageCalculator.DEFAULT_PAGE_SIZE);
            assertThat(rowsShown(page)).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
            assertThat(page.hasPrevious()).isFalse();
            assertThat(page.hasNext()).isTrue();
            assertThat(page.nextOffset()).isEqualTo(10);
        }

        @Test
        void shouldShowEveryRowWithoutLinks_whenExactlyOnePageOfRows() {
            FormListPageCalculator.Page page = FormListPageCalculator.calculate("0", "10", 10);

            assertThat(rowsShown(page)).hasSize(10);
            assertThat(page.hasPrevious()).isFalse();
            assertThat(page.hasNext()).isFalse();
        }

        @Test
        void shouldShowNoRowsAndNoLinks_whenListIsEmpty() {
            FormListPageCalculator.Page page = FormListPageCalculator.calculate("20", "10", 0);

            assertThat(page.offset()).isZero();
            assertThat(page.contains(0)).isFalse();
            assertThat(page.hasPrevious()).isFalse();
            assertThat(page.hasNext()).isFalse();
        }
    }

    @Nested
    @DisplayName("later pages")
    class LaterPages {

        @Test
        void shouldShowRemainingRowWithPreviousToFirstPage_whenSecondPageOfElevenRows() {
            FormListPageCalculator.Page page = FormListPageCalculator.calculate("10", "10", 11);

            assertThat(rowsShown(page)).containsExactly(10);
            assertThat(page.hasPrevious()).isTrue();
            assertThat(page.previousOffset()).isZero();
            assertThat(page.hasNext()).isFalse();
        }

        @Test
        void shouldOfferPreviousAndNext_whenMiddlePageOfThirtyRows() {
            FormListPageCalculator.Page page = FormListPageCalculator.calculate("10", "10", 30);

            assertThat(rowsShown(page)).containsExactly(10, 11, 12, 13, 14, 15, 16, 17, 18, 19);
            assertThat(page.previousOffset()).isZero();
            assertThat(page.nextOffset()).isEqualTo(20);
            assertThat(page.hasPrevious()).isTrue();
            assertThat(page.hasNext()).isTrue();
        }

        @Test
        void shouldStartPreviousAtFirstRow_whenOffsetIsNotAMultipleOfPageSize() {
            FormListPageCalculator.Page page = FormListPageCalculator.calculate("5", "10", 30);

            assertThat(rowsShown(page)).containsExactly(5, 6, 7, 8, 9, 10, 11, 12, 13, 14);
            assertThat(page.hasPrevious()).isTrue();
            assertThat(page.previousOffset()).isZero();
            assertThat(page.nextOffset()).isEqualTo(15);
        }

        @Test
        void shouldStepPreviousBackOnePage_whenThirdPageOfThirtyRows() {
            FormListPageCalculator.Page page = FormListPageCalculator.calculate("20", "10", 30);

            assertThat(rowsShown(page)).containsExactly(20, 21, 22, 23, 24, 25, 26, 27, 28, 29);
            assertThat(page.hasPrevious()).isTrue();
            assertThat(page.previousOffset()).isEqualTo(10);
            assertThat(page.hasNext()).isFalse();
        }

        @Test
        void shouldStepPreviousBackOnePageSize_whenOffsetIsUnaligned() {
            FormListPageCalculator.Page page = FormListPageCalculator.calculate("15", "10", 30);

            assertThat(rowsShown(page)).containsExactly(15, 16, 17, 18, 19, 20, 21, 22, 23, 24);
            assertThat(page.previousOffset()).isEqualTo(5);
            assertThat(page.nextOffset()).isEqualTo(25);
        }

        @Test
        void shouldUseRequestedPageSize_whenLimit2IsPositive() {
            FormListPageCalculator.Page page = FormListPageCalculator.calculate("3", "3", 11);

            assertThat(rowsShown(page)).containsExactly(3, 4, 5);
            assertThat(page.previousOffset()).isZero();
            assertThat(page.nextOffset()).isEqualTo(6);
        }
    }

    @Nested
    @DisplayName("offsets past the end")
    class OffsetsPastTheEnd {

        @ParameterizedTest(name = "limit1={0}")
        @ValueSource(strings = {"11", "15", "20", "2147483647"})
        void shouldMoveToLastPage_whenOffsetIsAtOrPastTheEnd(String limit1) {
            FormListPageCalculator.Page page = FormListPageCalculator.calculate(limit1, "10", 11);

            assertThat(page.offset()).isEqualTo(10);
            assertThat(rowsShown(page)).containsExactly(10);
            assertThat(page.hasPrevious()).isTrue();
            assertThat(page.hasNext()).isFalse();
        }

        @Test
        void shouldMoveToFirstPage_whenEveryRowFitsOnOnePage() {
            FormListPageCalculator.Page page = FormListPageCalculator.calculate("50", "25", 11);

            assertThat(page.offset()).isZero();
            assertThat(rowsShown(page)).hasSize(11);
            assertThat(page.hasPrevious()).isFalse();
            assertThat(page.hasNext()).isFalse();
        }

        @Test
        void shouldMoveToLastFullPage_whenRowCountIsAMultipleOfPageSize() {
            FormListPageCalculator.Page page = FormListPageCalculator.calculate("40", "10", 20);

            assertThat(page.offset()).isEqualTo(10);
            assertThat(rowsShown(page)).containsExactly(10, 11, 12, 13, 14, 15, 16, 17, 18, 19);
            assertThat(page.hasNext()).isFalse();
        }
    }

    @Nested
    @DisplayName("request values that cannot name a page")
    class InvalidParameters {

        @ParameterizedTest(name = "limit1=[{0}]")
        @NullSource
        @ValueSource(strings = {"", "  ", "abc", "-1", "-10", "1.5", "99999999999"})
        void shouldStartAtFirstRow_whenLimit1IsInvalid(String limit1) {
            FormListPageCalculator.Page page = FormListPageCalculator.calculate(limit1, "10", 11);

            assertThat(page.offset()).isZero();
            assertThat(page.hasPrevious()).isFalse();
        }

        @ParameterizedTest(name = "limit2=[{0}]")
        @NullSource
        @ValueSource(strings = {"", "  ", "abc", "0", "-5", "2.5", "99999999999"})
        void shouldUseTenRowsPerPage_whenLimit2IsInvalid(String limit2) {
            FormListPageCalculator.Page page = FormListPageCalculator.calculate("0", limit2, 11);

            assertThat(page.size()).isEqualTo(10);
            assertThat(rowsShown(page)).hasSize(10);
        }

        @Test
        void shouldAcceptSurroundingWhitespace_whenParametersAreOtherwiseNumbers() {
            FormListPageCalculator.Page page = FormListPageCalculator.calculate(" 10 ", " 10 ", 11);

            assertThat(page.offset()).isEqualTo(10);
            assertThat(page.size()).isEqualTo(10);
        }

        @Test
        void shouldTreatNegativeRowCountAsEmpty_whenCalculating() {
            FormListPageCalculator.Page page = FormListPageCalculator.calculate("0", "10", -3);

            assertThat(page.totalRows()).isZero();
            assertThat(page.hasNext()).isFalse();
        }
    }

    @Nested
    @DisplayName("very large values")
    class Overflow {

        @Test
        void shouldNotOverflowNextOffset_whenPageSizeIsMaximum() {
            FormListPageCalculator.Page page = FormListPageCalculator.calculate("5", String.valueOf(Integer.MAX_VALUE), 11);

            assertThat(rowsShown(page)).containsExactly(5, 6, 7, 8, 9, 10);
            assertThat(page.contains(11)).isFalse();
            assertThat(page.contains(Integer.MAX_VALUE)).isFalse();
            assertThat(page.hasNext()).isFalse();
            assertThat(page.nextOffset()).isEqualTo(Integer.MAX_VALUE);
            assertThat(page.previousOffset()).isZero();
        }

        @Test
        void shouldNotTreatRowsBeforeOffsetAsShown_whenOffsetIsLarge() {
            FormListPageCalculator.Page page = new FormListPageCalculator.Page(Integer.MAX_VALUE - 1, 10, Integer.MAX_VALUE);

            assertThat(page.contains(0)).isFalse();
            assertThat(page.contains(Integer.MAX_VALUE - 1)).isTrue();
            assertThat(page.hasNext()).isFalse();
        }
    }

    @Nested
    @DisplayName("page record")
    class PageRecord {

        @ParameterizedTest(name = "offset={0}, size={1}, totalRows={2}")
        @CsvSource({"-1, 10, 11", "0, 0, 11", "0, -1, 11", "0, 10, -1"})
        void shouldRejectPage_whenFieldsAreOutOfRange(int offset, int size, int totalRows) {
            assertThatThrownBy(() -> new FormListPageCalculator.Page(offset, size, totalRows))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
