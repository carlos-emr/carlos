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
import org.junit.jupiter.params.provider.MethodSource;

@Tag("unit")
@Tag("report")
@Tag("security")
class QueryByExampleSqlValidatorTest {
    private final Properties properties = properties("oscar_mcmaster?useUnicode=true");

    @Test
    @DisplayName("allows one SELECT using application tables, joins, and subqueries")
    void shouldAllowReadOnlyApplicationSelects() {
        assertThatCode(() -> QueryByExampleSqlValidator.validate(
                "select d.demographic_no from demographic d join provider p on p.provider_no=d.provider_no "
                        + "where exists (select 1 from appointment a where a.demographic_no=d.demographic_no)",
                properties)).doesNotThrowAnyException();
        assertThatCode(() -> QueryByExampleSqlValidator.validate(
                "select * from `oscar_mcmaster`.`demographic`", properties)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("does not treat prohibited function names inside string literals as invocations")
    void shouldAllowBlockedFunctionNameInsideLiteral() {
        assertThatCode(() -> QueryByExampleSqlValidator.validate("select 'sleep(1)'", properties))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "rejects: {0}")
    @MethodSource("unsafeQueries")
    @DisplayName("rejects unsafe or out-of-scope SQL")
    void shouldRejectUnsafeQueries(String sql) {
        assertThatThrownBy(() -> QueryByExampleSqlValidator.validate(sql, properties))
                .isInstanceOf(QueryByExampleValidationException.class);
    }

    private static Stream<String> unsafeQueries() {
        return Stream.of(
                "show tables",
                "describe demographic",
                "explain select * from demographic",
                "update demographic set last_name='x'",
                "select * from demographic union select * from provider",
                "select * from demographic; select * from provider",
                "select * from demographic -- comment",
                "select * from other_database.demographic",
                "select sleep(1)",
                "select benchmark(1000, md5('x'))",
                "select get_lock('qbe', 1)",
                "select release_lock('qbe')",
                "select is_free_lock('qbe')",
                "select load_file('/etc/passwd')",
                "select * from demographic for update",
                "select * from demographic for share",
                "select demographic_no into @number from demographic");
    }

    private static Properties properties(String databaseName) {
        Properties properties = new Properties();
        properties.setProperty("db_name", databaseName);
        return properties;
    }
}
