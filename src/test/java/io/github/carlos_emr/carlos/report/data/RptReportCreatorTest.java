/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.report.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("report")
@Tag("security")
class RptReportCreatorTest {

    @Test
    @DisplayName("should accept report SQL identifiers when identifier is valid")
    void shouldAcceptReportSqlIdentifiers_whenIdentifierIsValid() {
        assertThat(RptReportCreator.requireValidReportIdentifier("schema.formBCAR")).isEqualTo("schema.formBCAR");
    }

    @Test
    @DisplayName("should reject report SQL identifiers when identifier contains injection")
    void shouldRejectReportSqlIdentifiers_whenIdentifierContainsInjection() {
        assertThatThrownBy(() -> RptReportCreator.requireValidReportIdentifier("formBCAR;drop"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid report SQL identifier");
    }

    @Test
    @DisplayName("should return trimmed report table identifier when value is padded")
    void shouldReturnTrimmedReportIdentifier_whenValueIsPadded() {
        // Matches RptFormQuery.validateTableName, which tolerates padded legacy
        // configs, so the same reportConfig.table_name validates in both paths.
        assertThat(RptReportCreator.requireValidReportIdentifier("  schema.formBCAR  ")).isEqualTo("schema.formBCAR");
    }

    @Test
    @DisplayName("should reject report table identifiers with more than one dot")
    void shouldRejectReportIdentifiers_whenNameHasMultipleDots() {
        // table_name qualifies columns in the SELECT list, so a multi-dot value
        // would emit an invalid multi-part reference.
        assertThatThrownBy(() -> RptReportCreator.requireValidReportIdentifier("db.schema.formBCAR"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid report SQL identifier");
    }

    @Test
    @DisplayName("should accept simple report column identifiers")
    void shouldAcceptReportColumnIdentifiers_whenColumnIsSimple() {
        assertThat(RptReportCreator.requireValidReportColumnIdentifier("demographic_no")).isEqualTo("demographic_no");
    }

    @Test
    @DisplayName("should return trimmed report column identifier when value is padded")
    void shouldReturnTrimmedReportColumnIdentifier_whenValueIsPadded() {
        assertThat(RptReportCreator.requireValidReportColumnIdentifier("  demographic_no  ")).isEqualTo("demographic_no");
    }

    @Test
    @DisplayName("should reject dotted report column identifiers because the column is already qualified")
    void shouldRejectReportColumnIdentifiers_whenColumnIsDotted() {
        assertThatThrownBy(() -> RptReportCreator.requireValidReportColumnIdentifier("t.col"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid report column SQL identifier");
    }

    @Test
    @DisplayName("should reject report column identifiers when value contains injection")
    void shouldRejectReportColumnIdentifiers_whenColumnContainsInjection() {
        assertThatThrownBy(() -> RptReportCreator.requireValidReportColumnIdentifier("col;drop"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid report column SQL identifier");
    }

    @Test
    @DisplayName("should quote report aliases when alias contains SQL punctuation")
    void shouldQuoteReportAliases_whenAliasContainsSqlPunctuation() {
        assertThat(RptReportCreator.quoteSqlStringLiteral("Name\\'; drop table demographic; --"))
                .isEqualTo("'Name\\\\''; drop table demographic; --'");
    }

    @Test
    @DisplayName("should reject report aliases when alias contains null byte")
    void shouldRejectReportAliases_whenAliasContainsNullByte() {
        assertThatThrownBy(() -> RptReportCreator.quoteSqlStringLiteral("bad\0alias"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid report SQL alias");
    }
}
