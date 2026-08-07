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

import java.util.Properties;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Tag("unit")
@Tag("report")
@Tag("security")
class QueryByExampleSqlValidatorUnitTest {
    private final Properties properties = properties("oscar_mcmaster?useUnicode=true");

    @Test
    @DisplayName("allows one SELECT using application tables, joins, and subqueries")
    void shouldAllowReadOnlyApplicationSelects_whenSchemaIsConfigured() {
        assertThatCode(() -> QueryByExampleSqlValidator.validate(
                "select d.demographic_no from demographic d join provider p on p.provider_no=d.provider_no "
                        + "where exists (select 1 from appointment a where a.demographic_no=d.demographic_no)",
                properties)).doesNotThrowAnyException();
        assertThatCode(() -> QueryByExampleSqlValidator.validate(
                "select * from `oscar_mcmaster`.`demographic`", properties)).doesNotThrowAnyException();
        assertThatCode(() -> QueryByExampleSqlValidator.validate(
                "select count(*), date_format(date_of_birth, '%Y') from demographic", properties))
                .doesNotThrowAnyException();
        assertThatCode(() -> QueryByExampleSqlValidator.validate(
                "select row_number() over (order by demographic_no) from demographic", properties))
                .doesNotThrowAnyException();
        assertThatCode(() -> QueryByExampleSqlValidator.validate(
                "select `into` from demographic", properties)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("does not treat prohibited function names inside string literals as invocations")
    void shouldAllowBlockedFunctionNameInsideLiteral_whenFunctionTextIsQuoted() {
        assertThatCode(() -> QueryByExampleSqlValidator.validate("select 'sleep(1)'", properties))
                .doesNotThrowAnyException();
        assertThatCode(() -> QueryByExampleSqlValidator.validate(
                "select 'update delete create drop' as instruction", properties))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("structurally rejects set-operation SELECTs")
    void shouldRejectSetOperationSelects_whenMultipleQueriesAreCombined() {
        assertThatThrownBy(() -> QueryByExampleSqlValidator.validate(
                "select demographic_no from demographic union select provider_no from provider", properties))
                .isInstanceOf(QueryByExampleValidationException.class)
                .hasMessage("Only one SELECT statement is allowed");
    }

    @Test
    @DisplayName("fails closed when the application schema is not configured")
    void shouldRejectMissingApplicationSchema_whenSchemaIsUnavailable() {
        Properties missing = new Properties();
        Properties blank = properties("   ?useUnicode=true");

        assertThatThrownBy(() -> QueryByExampleSqlValidator.applicationSchema(missing))
                .isInstanceOf(QueryByExampleValidationException.class);
        assertThatThrownBy(() -> QueryByExampleSqlValidator.applicationSchema(blank))
                .isInstanceOf(QueryByExampleValidationException.class);
    }

    @Test
    @DisplayName("rejects over-limit SQL before parsing")
    void shouldRejectOverLimitSql_whenSubmissionIsTooLarge() {
        String sql = "select '" + "x".repeat(QueryByExampleSqlValidator.MAX_SQL_CHARACTERS) + "'";

        assertThatThrownBy(() -> QueryByExampleSqlValidator.validate(sql, properties))
                .isInstanceOf(QueryByExampleValidationException.class)
                .hasMessage("SQL query exceeds the allowed length");
    }

    @ParameterizedTest(name = "rejects: {0}")
    @MethodSource("unsafeQueries")
    @DisplayName("rejects unsafe or out-of-scope SQL")
    void shouldRejectUnsafeQueries(String sql) {
        assertThatThrownBy(() -> QueryByExampleSqlValidator.validate(sql, properties))
                .isInstanceOf(QueryByExampleValidationException.class);
    }

    @ParameterizedTest(name = "rejects sensitive table: {0}")
    @MethodSource("securitySensitiveTables")
    @DisplayName("rejects tables containing authentication secrets")
    void shouldRejectSecuritySensitiveTables(String table) {
        assertThatThrownBy(() -> QueryByExampleSqlValidator.validate("select * from " + table, properties))
                .isInstanceOf(QueryByExampleValidationException.class)
                .hasMessage("Queries may not read tables containing authentication secrets");
    }

    @Test
    @DisplayName("rejects sensitive tables through qualified, quoted, and nested references")
    void shouldRejectSecuritySensitiveTables_whenReferenceIsObscured() {
        assertThatThrownBy(() -> QueryByExampleSqlValidator.validate(
                "select * from `oscar_mcmaster`.`Security`", properties))
                .isInstanceOf(QueryByExampleValidationException.class);
        assertThatThrownBy(() -> QueryByExampleSqlValidator.validate(
                "select * from demographic where exists (select 1 from ServiceAccessToken)", properties))
                .isInstanceOf(QueryByExampleValidationException.class);
        assertThatThrownBy(() -> QueryByExampleSqlValidator.validate(
                "with tokens as (select * from SecurityToken) select * from tokens", properties))
                .isInstanceOf(QueryByExampleValidationException.class);
    }

    private static Stream<String> unsafeQueries() {
        return Stream.of(
                "show tables",
                "describe demographic",
                "explain select * from demographic",
                "update demographic set last_name='x'",
                "select * from demographic; select * from provider",
                "(select demographic_no from demographic union select provider_no from provider)",
                "select * from demographic -- comment",
                "select * from other_database.demographic",
                "select * from o\u017Fcar_mcmaster.demographic",
                "select sleep(1)",
                "select benchmark(1000, md5('x'))",
                "select repeat('x', 2147483647)",
                "select space(2147483647)",
                "select lpad('x', 2147483647, 'x')",
                "select rpad('x', 2147483647, 'x')",
                "select get_lock('qbe', 1)",
                "select release_lock('qbe')",
                "select is_free_lock('qbe')",
                "select load_file('/etc/passwd')",
                "select custom_reporting_udf(demographic_no) from demographic",
                "select oscar_mcmaster.custom_reporting_udf(demographic_no) from demographic",
                "select custom_reporting_udf(demographic_no) over () from demographic",
                "select @query_by_example_variable",
                "select @@version",
                "select next value for report_sequence",
                "select * from demographic for update",
                "select '\\\\' as value from demographic for update",
                "select * from demographic for share",
                "select demographic_no into @number from demographic");
    }

    private static Stream<Arguments> securitySensitiveTables() {
        return Stream.of(
                "AppDefinition",
                "AppUser",
                "emailConfig",
                "emailLog",
                "fax_config",
                "oscarCommLocations",
                "oscarKeys",
                "professionalSpecialists",
                "property",
                "publicKeys",
                "security",
                "SecurityArchive",
                "SecurityToken",
                "ServiceAccessToken",
                "ServiceClient",
                "ServiceOAuthNonce",
                "ServiceRequestToken")
                .map(Arguments::of);
    }

    private static Properties properties(String databaseName) {
        Properties properties = new Properties();
        properties.setProperty("db_name", databaseName);
        return properties;
    }
}
