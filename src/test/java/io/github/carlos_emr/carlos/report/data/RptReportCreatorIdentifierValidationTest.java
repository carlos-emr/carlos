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
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.report.data;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RptReportCreator#validateSqlIdentifier(String, String)}.
 * Validates that SQL identifiers from {@code reportConfig} are allowlisted
 * before being concatenated into SQL — prevents SQL injection via poisoned
 * report configuration rows (Flow 1 of issue #1683).
 *
 * @since 2026-04-14
 */
@Tag("unit")
@Tag("report")
class RptReportCreatorIdentifierValidationTest {

    @Test
    @DisplayName("should accept valid simple identifier")
    void shouldAccept_validSimpleIdentifier() {
        assertThatCode(() -> RptReportCreator.validateSqlIdentifier("formBCAR", "table name"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should accept identifier starting with underscore")
    void shouldAccept_identifierStartingWithUnderscore() {
        assertThatCode(() -> RptReportCreator.validateSqlIdentifier("_my_column", "column name"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should accept identifier with digits")
    void shouldAccept_identifierWithDigits() {
        assertThatCode(() -> RptReportCreator.validateSqlIdentifier("pg1_ethOrig", "column name"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should reject null identifier")
    void shouldReject_nullIdentifier() {
        assertThatThrownBy(() -> RptReportCreator.validateSqlIdentifier(null, "table name"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("table name");
    }

    @Test
    @DisplayName("should reject empty identifier")
    void shouldReject_emptyIdentifier() {
        assertThatThrownBy(() -> RptReportCreator.validateSqlIdentifier("", "column name"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("column name");
    }

    @Test
    @DisplayName("should reject identifier starting with digit")
    void shouldReject_identifierStartingWithDigit() {
        assertThatThrownBy(() -> RptReportCreator.validateSqlIdentifier("1table", "table name"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should reject identifier containing spaces")
    void shouldReject_identifierContainingSpaces() {
        assertThatThrownBy(() -> RptReportCreator.validateSqlIdentifier("table name", "table name"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should reject identifier containing SQL injection")
    void shouldReject_identifierWithSqlInjection() {
        assertThatThrownBy(() -> RptReportCreator.validateSqlIdentifier(
                "name; DROP TABLE demographic--", "column name"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should reject identifier with single quotes")
    void shouldReject_identifierWithSingleQuotes() {
        assertThatThrownBy(() -> RptReportCreator.validateSqlIdentifier(
                "name' OR '1'='1", "column name"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should accept schema-qualified identifier")
    void shouldAccept_schemaQualifiedIdentifier() {
        // reportConfig.table_name may contain schema-qualified names
        assertThatCode(() -> RptReportCreator.validateSqlIdentifier("schema.formBCAR", "table name"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should reject identifier with more than one dot")
    void shouldReject_identifierWithMultipleDots() {
        assertThatThrownBy(() -> RptReportCreator.validateSqlIdentifier(
                "a.b.c", "table name"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should reject identifier with leading dot")
    void shouldReject_identifierWithLeadingDot() {
        assertThatThrownBy(() -> RptReportCreator.validateSqlIdentifier(
                ".table", "table name"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should reject identifier with trailing dot")
    void shouldReject_identifierWithTrailingDot() {
        assertThatThrownBy(() -> RptReportCreator.validateSqlIdentifier(
                "schema.", "table name"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should reject identifier with parentheses (function call attempt)")
    void shouldReject_identifierWithParentheses() {
        assertThatThrownBy(() -> RptReportCreator.validateSqlIdentifier(
                "SLEEP(5)", "column name"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should reject identifier with comma")
    void shouldReject_identifierWithComma() {
        assertThatThrownBy(() -> RptReportCreator.validateSqlIdentifier(
                "a,b", "column name"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should reject identifier with leading whitespace")
    void shouldReject_identifierWithLeadingWhitespace() {
        assertThatThrownBy(() -> RptReportCreator.validateSqlIdentifier(
                " tableName", "table name"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should reject identifier with trailing whitespace")
    void shouldReject_identifierWithTrailingWhitespace() {
        assertThatThrownBy(() -> RptReportCreator.validateSqlIdentifier(
                "tableName ", "table name"))
                .isInstanceOf(SecurityException.class);
    }
}
