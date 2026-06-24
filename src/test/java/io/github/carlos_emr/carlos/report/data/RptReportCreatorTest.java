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
        assertThat(RptReportCreator.validateReportIdentifier("schema.formBCAR")).isEqualTo("schema.formBCAR");
    }

    @Test
    @DisplayName("should reject report SQL identifiers when identifier contains injection")
    void shouldRejectReportSqlIdentifiers_whenIdentifierContainsInjection() {
        assertThatThrownBy(() -> RptReportCreator.validateReportIdentifier("formBCAR;drop"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid report SQL identifier");
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
