/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.report.reportByTemplate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.report.data.ParameterizedSql;

@Tag("unit")
@DisplayName("Report template SQL parameterization")
class ReportObjectGenericUnitTest {

    @Test
    @DisplayName("should bind wildcard text outside the SQL literal")
    void shouldBindWildcardTextOutsideSqlLiteral() {
        ReportObjectGeneric report = new ReportObjectGeneric();

        ParameterizedSql sql = report.parameterizeTemplateSql(
                "select * from demographic where last_name like '%{name}%'",
                Map.of("name", new String[] {"Smith"}));

        assertThat(sql.getSql()).isEqualTo("select * from demographic where last_name like ?");
        assertThat(sql.getParams()).containsExactly("%Smith%");
    }

    @Test
    @DisplayName("should preserve escaped quotes in wildcard literals")
    void shouldPreserveEscapedQuotesInWildcardLiterals() {
        ReportObjectGeneric report = new ReportObjectGeneric();

        ParameterizedSql sql = report.parameterizeTemplateSql(
                "select * from demographic where last_name like 'O''%{name}%'",
                Map.of("name", new String[] {"Brien"}));

        assertThat(sql.getSql()).isEqualTo("select * from demographic where last_name like ?");
        assertThat(sql.getParams()).containsExactly("O'%Brien%");
    }

    @Test
    @DisplayName("should expand quoted list placeholders")
    void shouldExpandQuotedListPlaceholders() {
        ReportObjectGeneric report = new ReportObjectGeneric();

        ParameterizedSql sql = report.parameterizeTemplateSql(
                "select * from provider where provider_no in ('{providers}')",
                Map.of("providers", new String[] {"1", "2"}));

        assertThat(sql.getSql()).isEqualTo("select * from provider where provider_no in (?,?)");
        assertThat(sql.getParams()).containsExactly("1", "2");
    }

    @Test
    @DisplayName("should reject empty checkbox selections")
    void shouldRejectEmptyCheckboxSelections() {
        ReportObjectGeneric report = new ReportObjectGeneric();

        ParameterizedSql sql = report.parameterizeTemplateSql(
                "select * from provider where provider_no in ({providers})",
                Map.of("providers:check", new String[] {""}));

        assertThat(sql.getSql()).isEmpty();
        assertThat(sql.getParams()).isEmpty();
    }

    @Test
    @DisplayName("should reject empty direct parameter values")
    void shouldRejectEmptyDirectParameterValues() {
        ReportObjectGeneric report = new ReportObjectGeneric();

        ParameterizedSql sql = report.parameterizeTemplateSql(
                "select * from provider where provider_no in ({providers})",
                Map.of("providers", new String[0]));

        assertThat(sql.getSql()).isEmpty();
        assertThat(sql.getParams()).isEmpty();
    }

    @Test
    @DisplayName("should reject empty list parameter values")
    void shouldRejectEmptyListParameterValues() {
        ReportObjectGeneric report = new ReportObjectGeneric();

        ParameterizedSql sql = report.parameterizeTemplateSql(
                "select * from provider where provider_no in ({providers})",
                Map.of("providers:list", new String[0]));

        assertThat(sql.getSql()).isEmpty();
        assertThat(sql.getParams()).isEmpty();
    }

    @Test
    @DisplayName("should split sequenced SQL only on semicolons outside literals")
    void shouldSplitSequencedSqlOnlyOutsideLiterals() {
        assertThat(ReportObjectGeneric.splitSequencedSql(
                "select group_concat(name separator ';') from provider;select 'it'';works' as value"))
                .containsExactly(
                        "select group_concat(name separator ';') from provider",
                        "select 'it'';works' as value");
    }
}
