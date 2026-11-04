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
package io.github.carlos_emr.carlos.dashboard.handler;

import io.github.carlos_emr.carlos.dashboard.query.Parameter;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the substitution rules of {@link AbstractQueryHandler}: single values are author-written
 * SQL fragments inserted verbatim, multi-value lists are quoted and escaped by the handler, and
 * regex replacement metacharacters are never interpreted.
 *
 * @since 2026-09-24
 */
@DisplayName("AbstractQueryHandler placeholder substitution")
@Tag("unit")
@Tag("dashboard")
@Tag("security")
class AbstractQueryHandlerSubstitutionUnitTest {

    private static Parameter parameter(String id, String... values) {
        Parameter parameter = new Parameter();
        parameter.setId(id);
        parameter.setValue(values);
        return parameter;
    }

    @Test
    @DisplayName("should insert a single author-written literal verbatim")
    void shouldInsertSingleValueVerbatim_forAuthorWrittenLiteral() {
        String query = new IndicatorQueryHandler()
                .addParameters(List.of(parameter("pstatus", "'%AC%'")), "WHERE d.patient_status LIKE ${pstatus}");

        assertThat(query).isEqualTo("WHERE d.patient_status LIKE '%AC%'");
    }

    @Test
    @DisplayName("should quote each token of a multi-value list")
    void shouldQuoteEachToken_forMultiValueList() {
        String query = new IndicatorQueryHandler()
                .addParameters(List.of(parameter("dx", "250", " E10 ")), "dx.code IN ${dx}");

        assertThat(query).isEqualTo("dx.code IN ('250','E10')");
    }

    @Test
    @DisplayName("should escape quotes and backslashes inside multi-value tokens")
    void shouldEscapeQuotesAndBackslashes_forMultiValueTokens() {
        assertThat(AbstractQueryHandler.parseParameterValue(new String[] {"a'b", "c\\"}))
                .isEqualTo("('a''b','c\\\\')");
    }

    @Test
    @DisplayName("should not treat dollar signs or backslashes as regex group references")
    void shouldInsertLiterally_whenValueContainsRegexReplacementCharacters() {
        String query = new IndicatorQueryHandler()
                .addParameters(List.of(parameter("code", "'$1\\x'")), "code = ${code}");

        assertThat(query).isEqualTo("code = '$1\\x'");
    }

    @Test
    @DisplayName("should substitute empty text for missing values")
    void shouldReturnEmpty_whenValuesMissing() {
        assertThat(AbstractQueryHandler.parseParameterValue(null)).isEmpty();
        assertThat(AbstractQueryHandler.parseParameterValue(new String[0])).isEmpty();
        assertThat(AbstractQueryHandler.parseParameterValue(new String[] {null})).isEmpty();
    }
}
