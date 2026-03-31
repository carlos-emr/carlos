/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.util;

import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link SqlUtils} SQL clause building utilities.
 *
 * <p>Tests the pure string-manipulation methods. Skips DB-dependent methods.</p>
 *
 * @since 2026-03-31
 */
@DisplayName("SqlUtils Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("utility")
class SqlUtilsUnitTest {

    @Nested
    @DisplayName("constructInClauseString")
    class ConstructInClauseString {

        @Test
        @DisplayName("should build IN clause with quotes")
        void shouldBuildInClause_withQuotes() {
            String result = SqlUtils.constructInClauseString(new String[]{"a", "b", "c"}, true);
            assertThat(result).isEqualTo("in ('a','b','c')");
        }

        @Test
        @DisplayName("should build IN clause without quotes")
        void shouldBuildInClause_withoutQuotes() {
            String result = SqlUtils.constructInClauseString(new String[]{"1", "2", "3"}, false);
            assertThat(result).isEqualTo("in (1,2,3)");
        }

        @Test
        @DisplayName("should handle single element")
        void shouldHandleSingleElement() {
            String result = SqlUtils.constructInClauseString(new String[]{"only"}, true);
            assertThat(result).isEqualTo("in ('only')");
        }

        @Test
        @DisplayName("should handle empty array")
        void shouldHandleEmptyArray() {
            String result = SqlUtils.constructInClauseString(new String[]{}, true);
            assertThat(result).isEqualTo(")");
        }
    }

    @Nested
    @DisplayName("constructInClauseForStatements")
    class ConstructInClauseForStatements {

        @Test
        @DisplayName("should build parenthesized clause without quotes")
        void shouldBuildClause_withoutQuotes() {
            String result = SqlUtils.constructInClauseForStatements(new Object[]{1, 2, 3});
            assertThat(result).isEqualTo("(1,2,3)");
        }

        @Test
        @DisplayName("should build clause with quotes when requested")
        void shouldBuildClause_withQuotes() {
            String result = SqlUtils.constructInClauseForStatements(new Object[]{"a", "b"}, true);
            assertThat(result).isEqualTo("('a','b')");
        }

        @Test
        @DisplayName("should handle single element")
        void shouldHandleSingleElement() {
            String result = SqlUtils.constructInClauseForStatements(new Object[]{42});
            assertThat(result).isEqualTo("(42)");
        }

        @Test
        @DisplayName("should throw for empty array")
        void shouldThrow_forEmptyArray() {
            assertThatThrownBy(() -> SqlUtils.constructInClauseForStatements(new Object[]{}))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("closeResources")
    class CloseResources {

        @Test
        @DisplayName("should not throw for null resources")
        void shouldNotThrow_forNullResources() {
            assertThatCode(() -> SqlUtils.closeResources((Connection) null, (Statement) null, (ResultSet) null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should not throw for null statement and resultset")
        void shouldNotThrow_forNullStatementAndResultset() {
            assertThatCode(() -> SqlUtils.closeResources((Statement) null, (ResultSet) null))
                    .doesNotThrowAnyException();
        }
    }
}
