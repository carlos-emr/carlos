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
package io.github.carlos_emr.carlos.report.data;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RptReportCreator#validateSqlIdentifier(String)}.
 * Ensures the method accepts valid SQL identifiers (table/column names) and
 * rejects any string that could be used for SQL injection via identifier
 * manipulation in report configurations.
 *
 * @since 2026-04-14
 */
@Tag("unit")
@Tag("report")
class RptReportCreatorValidateSqlIdentifierTest {

    @Test
    @DisplayName("should accept simple lowercase identifier")
    void shouldAccept_simpleLowercaseIdentifier() {
        assertThatCode(() -> RptReportCreator.validateSqlIdentifier("demographic"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should accept identifier with underscore")
    void shouldAccept_identifierWithUnderscore() {
        assertThatCode(() -> RptReportCreator.validateSqlIdentifier("last_name"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should accept identifier starting with underscore")
    void shouldAccept_identifierStartingWithUnderscore() {
        assertThatCode(() -> RptReportCreator.validateSqlIdentifier("_admin"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should accept mixed-case identifier with digits")
    void shouldAccept_mixedCaseIdentifierWithDigits() {
        assertThatCode(() -> RptReportCreator.validateSqlIdentifier("formBCAR2007"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should accept uppercase identifier")
    void shouldAccept_uppercaseIdentifier() {
        assertThatCode(() -> RptReportCreator.validateSqlIdentifier("ID"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should accept single character identifier")
    void shouldAccept_singleCharIdentifier() {
        assertThatCode(() -> RptReportCreator.validateSqlIdentifier("x"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should reject null identifier")
    void shouldReject_nullIdentifier() {
        assertThatThrownBy(() -> RptReportCreator.validateSqlIdentifier(null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid SQL identifier");
    }

    @Test
    @DisplayName("should reject empty identifier")
    void shouldReject_emptyIdentifier() {
        assertThatThrownBy(() -> RptReportCreator.validateSqlIdentifier(""))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid SQL identifier");
    }

    @Test
    @DisplayName("should reject identifier starting with digit")
    void shouldReject_identifierStartingWithDigit() {
        assertThatThrownBy(() -> RptReportCreator.validateSqlIdentifier("1table"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should reject identifier containing space")
    void shouldReject_identifierWithSpace() {
        assertThatThrownBy(() -> RptReportCreator.validateSqlIdentifier("table name"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should reject identifier containing single quote — SQL injection via alias")
    void shouldReject_identifierWithSingleQuote() {
        assertThatThrownBy(() -> RptReportCreator.validateSqlIdentifier("name' UNION SELECT 1--"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should reject identifier containing semicolon — SQL injection via statement termination")
    void shouldReject_identifierWithSemicolon() {
        assertThatThrownBy(() -> RptReportCreator.validateSqlIdentifier("col; DROP TABLE demographic"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should reject identifier containing hyphen")
    void shouldReject_identifierWithHyphen() {
        assertThatThrownBy(() -> RptReportCreator.validateSqlIdentifier("my-column"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should reject identifier containing dot — dotted names not allowed as single identifiers")
    void shouldReject_identifierWithDot() {
        assertThatThrownBy(() -> RptReportCreator.validateSqlIdentifier("table.column"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should reject identifier containing parentheses — SQL function injection")
    void shouldReject_identifierWithParentheses() {
        assertThatThrownBy(() -> RptReportCreator.validateSqlIdentifier("count(*)"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should reject identifier containing SQL comment syntax")
    void shouldReject_identifierWithCommentSyntax() {
        assertThatThrownBy(() -> RptReportCreator.validateSqlIdentifier("col--"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should accept typical report column names from reportConfig")
    void shouldAccept_typicalReportColumnNames() {
        // These are real column names from the CARLOS report configuration
        String[] validNames = {
                "c_EDD", "pg1_famPhy", "pg1_partnerName", "pg1_ethOrig",
                "last_name", "first_name", "date_joined", "hin",
                "hc_type", "address", "city", "postal", "phone",
                "phone2", "email", "prefer_language", "formBCAR",
                "formBCAR2007", "demographic", "demographicExt",
                "ga", "b_primiparous"
        };
        for (String name : validNames) {
            assertThatCode(() -> RptReportCreator.validateSqlIdentifier(name))
                    .as("Expected '%s' to be accepted as valid SQL identifier", name)
                    .doesNotThrowAnyException();
        }
    }
}
