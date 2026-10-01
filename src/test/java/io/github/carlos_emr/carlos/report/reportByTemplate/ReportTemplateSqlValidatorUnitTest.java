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
package io.github.carlos_emr.carlos.report.reportByTemplate;

import java.io.StringReader;

import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the save-time SQL policy for Report by Template (carlos-emr/carlos#4133): every
 * {@code <query>} statement and every {@code <param-query>} must be a single SELECT.
 */
@DisplayName("ReportTemplateSqlValidator")
@Tag("unit")
@Tag("report")
@Tag("security")
class ReportTemplateSqlValidatorUnitTest {

    private static Element report(String xml) throws Exception {
        return new SAXBuilder().build(new StringReader(xml)).getRootElement();
    }

    @Test
    @DisplayName("should accept a parameterised SELECT with a SELECT param-query")
    void shouldAccept_whenEveryStatementIsSelect() throws Exception {
        Element report = report("<report title='FAKE' description='FAKE'>"
                + "<query>SELECT demographic_no, last_name FROM demographic WHERE sex = '{sexpick}'"
                + " AND date_joined &gt;= '{since}'</query>"
                + "<param id='sexpick' type='list' description='Sex'><choice id='F'>Female</choice></param>"
                + "<param id='prov' type='list' description='Provider'>"
                + "<param-query>SELECT provider_no, last_name FROM provider</param-query></param>"
                + "</report>");

        assertThat(ReportTemplateSqlValidator.validateReport(report)).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "UPDATE reportTemplates SET templatedescription = 'FAKE-PW-written'",
            "DELETE FROM demographic",
            "SELECT 1; DELETE FROM demographic",
            "SELECT 1 -- trailing comment",
            "SELECT * FROM demographic INTO OUTFILE '/tmp/FAKE'",
            "SELECT * FROM demographic INTO\nOUTFILE '/tmp/FAKE'",
            "SELECT * FROM demographic INTO\tDUMPFILE '/tmp/FAKE'",
            "SELECT LOAD_FILE('/etc/passwd')"})
    @DisplayName("should refuse a <query> that is not a single plain SELECT")
    void shouldRefuseQuery_whenNotASingleSelect(String sql) throws Exception {
        Element report = report("<report title='FAKE' description='FAKE'><query/></report>");
        report.getChild("query").setText(sql);

        assertThat(ReportTemplateSqlValidator.validateReport(report))
                .startsWith("Error: The <query> was refused: ")
                .doesNotContain(sql);
    }

    @Test
    @DisplayName("should refuse a non-SELECT param-query, which would run as soon as the template is opened")
    void shouldRefuseParamQuery_whenNotSelect() throws Exception {
        Element report = report("<report title='FAKE' description='FAKE'><query>SELECT 1</query>"
                + "<param id='who' type='list' description='Who'>"
                + "<param-query>DELETE FROM provider</param-query></param></report>");

        assertThat(ReportTemplateSqlValidator.validateReport(report))
                .isEqualTo("Error: The <param-query> of parameter 'who' was refused: Only SELECT statements are allowed");
    }

    @Test
    @DisplayName("should check each statement of a sequenced template separately")
    void shouldValidateEachStatement_forSequencedTemplate() throws Exception {
        Element ok = report("<report title='FAKE' description='FAKE' sequence='true'>"
                + "<query>SELECT 1; SELECT ';' AS separator_in_literal;</query></report>");
        Element bad = report("<report title='FAKE' description='FAKE' sequence='true'>"
                + "<query>SELECT 1; UPDATE provider SET status = '0'</query></report>");

        assertThat(ReportTemplateSqlValidator.validateReport(ok)).isNull();
        assertThat(ReportTemplateSqlValidator.validateReport(bad))
                .startsWith("Error: The <query> (statement 2) was refused:");
    }

    @Test
    @DisplayName("should treat a non-sequenced template with two statements as stacked SQL")
    void shouldRefuseStackedStatements_whenNotSequenced() throws Exception {
        Element report = report("<report title='FAKE' description='FAKE'>"
                + "<query>SELECT 1; SELECT 2</query></report>");

        assertThat(ReportTemplateSqlValidator.validateReport(report)).startsWith("Error: The <query> was refused:");
    }

    @Test
    @DisplayName("should refuse a blank query on a SQL template, which could never run")
    void shouldRefuseBlankQuery_forSqlTemplate() throws Exception {
        Element report = report("<report title='FAKE' description='FAKE'><query>   </query></report>");

        assertThat(ReportTemplateSqlValidator.validateReport(report))
                .isEqualTo("Error: The <query> was refused: SQL query must not be empty");
    }

    @Test
    @DisplayName("should accept file-access words that are only quoted text")
    void shouldAcceptFileAccessWords_whenInsideQuotedLiterals() throws Exception {
        Element report = report("<report title='FAKE' description='FAKE'>"
                + "<query>SELECT 'load_file' AS label, 'into outfile' AS other FROM demographic</query></report>");

        assertThat(ReportTemplateSqlValidator.validateReport(report)).isNull();
    }

    @Test
    @DisplayName("should accept a non-SQL report type with a blank query")
    void shouldAccept_whenNonSqlTypeHasBlankQuery() throws Exception {
        Element report = report("<report title='FAKE' description='FAKE'><type>inr</type><query> </query></report>");

        assertThat(ReportTemplateSqlValidator.validateReport(report)).isNull();
    }

    @Test
    @DisplayName("should refuse a default-type template with no <query> element, which SQLReporter would run")
    void shouldRefuseMissingQuery_forDefaultTypeTemplate() throws Exception {
        Element report = report("<report title='FAKE' description='FAKE'></report>");

        assertThat(ReportTemplateSqlValidator.validateReport(report))
                .isEqualTo("Error: The <query> was refused: SQL query must not be empty");
    }

    @Test
    @DisplayName("should expose the same rule for a single stored statement")
    void shouldMatchRunTimeRule_forSingleStatement() {
        assertThat(ReportTemplateSqlValidator.isAllowedStatement("SELECT provider_no FROM provider")).isTrue();
        assertThat(ReportTemplateSqlValidator.isAllowedStatement("UPDATE provider SET status='0'")).isFalse();
        assertThat(ReportTemplateSqlValidator.isAllowedStatement(null)).isFalse();
    }
}
