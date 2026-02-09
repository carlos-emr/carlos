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
import io.github.carlos_emr.carlos.dashboard.query.RangeInterface;
import io.github.carlos_emr.carlos.dashboard.query.RangeLowerLimit;
import io.github.carlos_emr.carlos.dashboard.query.RangeUpperLimit;
import io.github.carlos_emr.carlos.test.base.OpenOTestBase;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link AbstractQueryHandler}.
 *
 * <p>Since {@code AbstractQueryHandler} is abstract, these tests use a concrete
 * inner subclass ({@link TestQueryHandler}) that exposes protected methods for
 * verification. Tests cover query building, parameter substitution, range
 * replacement, query string filtering, and native SQL execution via Hibernate.</p>
 *
 * <p>The {@code execute()} method opens its own Hibernate session and transaction,
 * so it does not participate in the Spring {@code @Transactional} test transaction.
 * Execution tests therefore use self-contained queries (e.g., {@code SELECT 1 AS result})
 * that do not depend on pre-existing test data.</p>
 *
 * @since 2026-02-09
 * @see AbstractQueryHandler
 */
@DisplayName("AbstractQueryHandler Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("dashboard")
@Transactional
public class AbstractQueryHandlerIntegrationTest extends OpenOTestBase {

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @Autowired
    private SessionFactory sessionFactory;

    private TestQueryHandler queryHandler;

    /**
     * Concrete implementation of AbstractQueryHandler for testing purposes.
     * Exposes protected methods so they can be called from the test class.
     */
    static class TestQueryHandler extends AbstractQueryHandler {

        /**
         * Exposes the protected {@code execute(String)} method for testing.
         *
         * @param query the native SQL query to execute
         * @return the result list from Hibernate
         */
        public List<?> testExecute(String query) {
            return execute(query);
        }

        /**
         * Exposes the protected {@code buildQuery(String)} method for testing.
         *
         * @param query the query template with placeholders
         * @return the fully built query with substitutions applied
         */
        public String testBuildQuery(String query) {
            return buildQuery(query);
        }

        /**
         * Exposes the protected {@code filterQueryString(String)} method for testing.
         *
         * @param queryString the raw query string to filter
         * @return the filtered query string
         */
        public String testFilterQueryString(String queryString) {
            return filterQueryString(queryString);
        }
    }

    @BeforeEach
    void setUp() {
        queryHandler = new TestQueryHandler();
        queryHandler.setSessionFactory(sessionFactory);
    }

    @Nested
    @DisplayName("Query String Filtering")
    class QueryStringFiltering {

        @Test
        @Tag("read")
        @DisplayName("should remove block comments from query string")
        void shouldRemoveBlockComments_whenCommentsPresent() {
            // Given
            String queryWithComments = "SELECT * /* this is a comment */ FROM demographic";

            // When
            String filtered = queryHandler.testFilterQueryString(queryWithComments);

            // Then
            assertThat(filtered).doesNotContain("/* this is a comment */");
            assertThat(filtered).contains("SELECT *");
            assertThat(filtered).contains("FROM demographic");
        }

        @Test
        @Tag("read")
        @DisplayName("should remove line comments from query string")
        void shouldRemoveLineComments_whenLineCommentsPresent() {
            // Given
            String queryWithLineComments = "SELECT *\n-- this is a line comment\nFROM demographic";

            // When
            String filtered = queryHandler.testFilterQueryString(queryWithLineComments);

            // Then
            assertThat(filtered).doesNotContain("-- this is a line comment");
            assertThat(filtered).contains("SELECT *");
            assertThat(filtered).contains("FROM demographic");
        }

        @Test
        @Tag("read")
        @DisplayName("should remove colons and question marks from query string")
        void shouldRemoveColonsAndQuestionMarks_whenPresent() {
            // Given
            String queryWithSpecialChars = "SELECT * FROM demographic WHERE name = :name AND id = ?";

            // When
            String filtered = queryHandler.testFilterQueryString(queryWithSpecialChars);

            // Then
            assertThat(filtered).doesNotContain(":");
            assertThat(filtered).doesNotContain("?");
            assertThat(filtered).contains("SELECT * FROM demographic WHERE name = name AND id =");
        }

        @Test
        @Tag("read")
        @DisplayName("should remove hash line comments from query string")
        void shouldRemoveHashComments_whenHashCommentsPresent() {
            // Given
            String queryWithHash = "SELECT *\n# hash comment\nFROM demographic";

            // When
            String filtered = queryHandler.testFilterQueryString(queryWithHash);

            // Then
            assertThat(filtered).doesNotContain("# hash comment");
            assertThat(filtered).contains("SELECT *");
            assertThat(filtered).contains("FROM demographic");
        }

        @Test
        @Tag("read")
        @DisplayName("should remove multiline block comments spanning multiple lines")
        void shouldRemoveMultilineBlockComments_whenSpanningMultipleLines() {
            // Given
            String query = "SELECT *\n/* multi\nline\ncomment */\nFROM demographic";

            // When
            String filtered = queryHandler.testFilterQueryString(query);

            // Then
            assertThat(filtered).doesNotContain("multi");
            assertThat(filtered).doesNotContain("comment");
            assertThat(filtered).contains("SELECT *");
            assertThat(filtered).contains("FROM demographic");
        }
    }

    @Nested
    @DisplayName("Parameter Substitution")
    class ParameterSubstitution {

        @Test
        @Tag("read")
        @DisplayName("should replace parameter placeholder with single value")
        void shouldReplaceParameterPlaceholder_whenSingleValueProvided() {
            // Given
            Parameter param = new Parameter();
            param.setId("providerNo");
            param.setName("Provider Number");
            param.setValue(new String[]{"12345"});

            String query = "SELECT * FROM demographic WHERE provider_no = ${ providerNo }";

            // When
            String result = queryHandler.addParameters(Collections.singletonList(param), query);

            // Then
            assertThat(result).contains("12345");
            assertThat(result).doesNotContain("${ providerNo }");
        }

        @Test
        @Tag("read")
        @DisplayName("should replace multiple parameter placeholders in single query")
        void shouldReplaceMultiplePlaceholders_whenMultipleParametersProvided() {
            // Given
            Parameter providerParam = new Parameter();
            providerParam.setId("providerNo");
            providerParam.setName("Provider Number");
            providerParam.setValue(new String[]{"12345"});

            Parameter statusParam = new Parameter();
            statusParam.setId("status");
            statusParam.setName("Status");
            statusParam.setValue(new String[]{"AC"});

            String query = "SELECT * FROM demographic WHERE provider_no = ${ providerNo } AND status = ${ status }";
            List<Parameter> params = Arrays.asList(providerParam, statusParam);

            // When
            String result = queryHandler.addParameters(params, query);

            // Then
            assertThat(result).contains("12345");
            assertThat(result).contains("AC");
            assertThat(result).doesNotContain("${ providerNo }");
            assertThat(result).doesNotContain("${ status }");
        }

        @Test
        @Tag("read")
        @DisplayName("should format multiple values as SQL IN clause")
        void shouldFormatAsInClause_whenMultipleValuesProvided() {
            // Given
            Parameter param = new Parameter();
            param.setId("providerNo");
            param.setName("Provider Number");
            param.setValue(new String[]{"111", "222", "333"});

            String query = "SELECT * FROM demographic WHERE provider_no IN ${ providerNo }";

            // When
            String result = queryHandler.addParameters(Collections.singletonList(param), query);

            // Then
            assertThat(result).contains("('111','222','333')");
            assertThat(result).doesNotContain("${ providerNo }");
        }
    }

    @Nested
    @DisplayName("Range Substitution")
    class RangeSubstitution {

        @Test
        @Tag("read")
        @DisplayName("should replace upper limit range placeholder with value")
        void shouldReplaceUpperLimitPlaceholder_whenUpperLimitRangeProvided() {
            // Given
            RangeUpperLimit range = new RangeUpperLimit();
            range.setId("age");
            range.setLabel("Age Upper");
            range.setName("ageUpper");
            range.setValue("65");

            String query = "SELECT * FROM demographic WHERE age <= ${ upperLimit.age }";

            // When
            String result = queryHandler.addRanges(Collections.singletonList(range), query);

            // Then
            assertThat(result).contains("65");
            assertThat(result).doesNotContain("${ upperLimit.age }");
        }

        @Test
        @Tag("read")
        @DisplayName("should replace lower limit range placeholder with value")
        void shouldReplaceLowerLimitPlaceholder_whenLowerLimitRangeProvided() {
            // Given
            RangeLowerLimit range = new RangeLowerLimit();
            range.setId("age");
            range.setLabel("Age Lower");
            range.setName("ageLower");
            range.setValue("18");

            String query = "SELECT * FROM demographic WHERE age >= ${ lowerLimit.age }";

            // When
            String result = queryHandler.addRanges(Collections.singletonList(range), query);

            // Then
            assertThat(result).contains("18");
            assertThat(result).doesNotContain("${ lowerLimit.age }");
        }
    }

    @Nested
    @DisplayName("Build Query")
    class BuildQuery {

        @Test
        @Tag("read")
        @DisplayName("should build query with parameters and filtering applied")
        void shouldBuildQuery_whenParametersProvided() {
            // Given
            Parameter param = new Parameter();
            param.setId("providerNo");
            param.setName("Provider Number");
            param.setValue(new String[]{"99901"});

            queryHandler.setParameters(Collections.singletonList(param));
            queryHandler.setRanges(null);
            queryHandler.setColumns(null);

            String template = "SELECT * FROM demographic WHERE provider_no = ${ providerNo }";

            // When
            String builtQuery = queryHandler.testBuildQuery(template);

            // Then
            assertThat(builtQuery).contains("99901");
            assertThat(builtQuery).doesNotContain("${");
        }

        @Test
        @Tag("read")
        @DisplayName("should build query with comments removed and parameters replaced")
        void shouldBuildQueryWithCommentsRemoved_whenCommentsAndParametersPresent() {
            // Given
            Parameter param = new Parameter();
            param.setId("demoNo");
            param.setName("Demographic Number");
            param.setValue(new String[]{"5001"});

            queryHandler.setParameters(Collections.singletonList(param));
            queryHandler.setRanges(null);
            queryHandler.setColumns(null);

            String template = "/* indicator query */\nSELECT * FROM demographic\n-- filter by demo\nWHERE demographic_no = ${ demoNo }";

            // When
            String builtQuery = queryHandler.testBuildQuery(template);

            // Then
            assertThat(builtQuery).doesNotContain("/* indicator query */");
            assertThat(builtQuery).doesNotContain("-- filter by demo");
            assertThat(builtQuery).contains("5001");
        }
    }

    @Nested
    @DisplayName("Query Execution")
    class QueryExecution {

        @Test
        @Tag("read")
        @DisplayName("should execute native query and return result when simple select provided")
        void shouldExecuteNativeQuery_whenSimpleSelectProvided() {
            // Given
            // Using a self-contained query that does not require pre-existing test data,
            // since execute() opens its own session/transaction independent of @Transactional.
            String query = "SELECT 1 AS result";

            // When
            List<?> results = queryHandler.testExecute(query);

            // Then
            assertThat(results).isNotNull();
            assertThat(results).hasSize(1);

            // The result transformer ALIAS_TO_ENTITY_MAP returns Map objects
            Object row = results.get(0);
            assertThat(row).isInstanceOf(Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) row;
            assertThat(resultMap).containsKey("result");
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty string from getQuery when query is null")
        void shouldReturnEmptyString_whenQueryIsNull() {
            // Given
            // queryHandler is freshly created with no query set (defaults to null)

            // When
            String query = queryHandler.getQuery();

            // Then
            assertThat(query).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should throw RuntimeException when invalid SQL is executed")
        void shouldThrowRuntimeException_whenInvalidSqlExecuted() {
            // Given
            String invalidQuery = "THIS IS NOT VALID SQL";

            // When / Then
            assertThatThrownBy(() -> queryHandler.testExecute(invalidQuery))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Error executing query");
        }
    }
}
