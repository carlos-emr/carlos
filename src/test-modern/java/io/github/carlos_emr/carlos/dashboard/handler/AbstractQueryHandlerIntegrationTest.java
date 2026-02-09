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
 * <p>The test is organized into four {@link Nested} inner classes, each covering a
 * distinct aspect of the query handler lifecycle:</p>
 * <ul>
 *   <li>{@link QueryStringFiltering} - SQL comment and special character removal</li>
 *   <li>{@link ParameterSubstitution} - Named placeholder replacement with single/multi values</li>
 *   <li>{@link RangeSubstitution} - Upper and lower limit range placeholder replacement</li>
 *   <li>{@link BuildQuery} - End-to-end query building combining filtering and substitution</li>
 *   <li>{@link QueryExecution} - Native SQL execution via Hibernate, including error handling</li>
 * </ul>
 *
 * @since 2026-02-09
 * @see AbstractQueryHandler
 * @see Parameter
 * @see RangeInterface
 * @see RangeUpperLimit
 * @see RangeLowerLimit
 */
@DisplayName("AbstractQueryHandler Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("dashboard")
@Transactional
public class AbstractQueryHandlerIntegrationTest extends OpenOTestBase {

    /**
     * JPA EntityManager injected from the test persistence unit.
     *
     * <p>Provides JPA-level access to the test database. While the tests in this
     * class primarily exercise Hibernate-native APIs through {@link AbstractQueryHandler},
     * the EntityManager is available for any JPA-based setup or verification that
     * may be needed in the Spring-managed transactional context.</p>
     */
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /**
     * Hibernate {@link SessionFactory} injected by Spring from the test application context.
     *
     * <p>This is the same {@code SessionFactory} used by {@link AbstractQueryHandler}
     * (via its {@code HibernateDaoSupport} superclass) to open sessions and execute
     * native SQL queries. It is injected into the {@link TestQueryHandler} during
     * {@link #setUp()} to wire the handler to the test database.</p>
     */
    @Autowired
    private SessionFactory sessionFactory;

    /**
     * Concrete test subclass of {@link AbstractQueryHandler} used to invoke
     * protected and package-private methods that are otherwise inaccessible
     * from the test class.
     *
     * <p>Initialized in {@link #setUp()} before each test with the Spring-managed
     * {@link #sessionFactory} so that execution tests can open real Hibernate
     * sessions against the test database.</p>
     */
    private TestQueryHandler queryHandler;

    /**
     * Concrete implementation of {@link AbstractQueryHandler} for testing purposes.
     *
     * <p>This inner class exists solely to expose the three {@code protected} methods
     * of {@code AbstractQueryHandler} that need direct testing:</p>
     * <ul>
     *   <li>{@link #testExecute(String)} - delegates to {@link AbstractQueryHandler#execute(String)}</li>
     *   <li>{@link #testBuildQuery(String)} - delegates to {@link AbstractQueryHandler#buildQuery(String)}</li>
     *   <li>{@link #testFilterQueryString(String)} - delegates to {@link AbstractQueryHandler#filterQueryString(String)}</li>
     * </ul>
     *
     * <p>No additional behavior is added beyond delegation. The handler inherits
     * {@code HibernateDaoSupport} from its parent and uses the injected
     * {@link SessionFactory} for database operations.</p>
     */
    static class TestQueryHandler extends AbstractQueryHandler {

        /**
         * Exposes the protected {@link AbstractQueryHandler#execute(String)} method for testing.
         *
         * <p>The underlying method opens its own Hibernate {@code Session} and
         * {@code Transaction}, executes the given native SQL, applies the
         * {@code ALIAS_TO_ENTITY_MAP} result transformer, and returns the results
         * as a list of {@code Map<String, Object>} entries.</p>
         *
         * @param query String the native SQL query to execute
         * @return List the result list from Hibernate, where each row is a
         *         {@code Map<String, Object>} keyed by column alias
         * @throws RuntimeException if the query fails (wraps the underlying exception)
         */
        public List<?> testExecute(String query) {
            return execute(query);
        }

        /**
         * Exposes the protected {@link AbstractQueryHandler#buildQuery(String)} method for testing.
         *
         * <p>The underlying method applies the full query-building pipeline:
         * comment/special-character filtering, column rewriting (if columns are set),
         * parameter placeholder substitution, and range placeholder substitution.</p>
         *
         * @param query String the query template containing {@code ${ placeholder }} tokens
         * @return String the fully built query with all substitutions applied and
         *         comments removed
         */
        public String testBuildQuery(String query) {
            return buildQuery(query);
        }

        /**
         * Exposes the protected {@link AbstractQueryHandler#filterQueryString(String)} method for testing.
         *
         * <p>The underlying method removes block comments ({@code /* ... * /}),
         * line comments ({@code --} and {@code #}), colons, and question marks
         * from the query string. These characters are stripped because they can
         * interfere with Hibernate's native SQL parsing.</p>
         *
         * @param queryString String the raw query string to filter
         * @return String the filtered query string with comments and special characters removed
         */
        public String testFilterQueryString(String queryString) {
            return filterQueryString(queryString);
        }
    }

    /**
     * Initializes the test fixture before each test method.
     *
     * <p>Creates a fresh {@link TestQueryHandler} instance and injects the
     * Spring-managed {@link SessionFactory} into it via the inherited
     * {@code HibernateDaoSupport.setSessionFactory()} method. This ensures
     * each test starts with a clean handler state (no residual parameters,
     * ranges, columns, or cached query results from previous tests).</p>
     */
    @BeforeEach
    void setUp() {
        queryHandler = new TestQueryHandler();
        queryHandler.setSessionFactory(sessionFactory);
    }

    /**
     * Tests for {@link AbstractQueryHandler#filterQueryString(String)}.
     *
     * <p>This group verifies that the filter correctly removes various forms
     * of SQL comments and special characters that could interfere with
     * Hibernate's native SQL parsing:</p>
     * <ul>
     *   <li>Block comments: {@code /* ... * /} (single-line and multi-line)</li>
     *   <li>Line comments: {@code -- ...} (SQL standard)</li>
     *   <li>Hash comments: {@code # ...} (MySQL-specific)</li>
     *   <li>Colons ({@code :}) which Hibernate interprets as named parameter prefixes</li>
     *   <li>Question marks ({@code ?}) which Hibernate interprets as positional parameters</li>
     * </ul>
     */
    @Nested
    @DisplayName("Query String Filtering")
    class QueryStringFiltering {

        /**
         * Verifies that single-line block comments ({@code /* ... * /}) are
         * stripped from the query while preserving the surrounding SQL.
         *
         * <p>Block comments are removed using a regex pattern that matches
         * {@code /\*...\* /} including newlines, ensuring they do not cause
         * parse errors when the query is executed as native SQL.</p>
         */
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

        /**
         * Verifies that SQL-standard line comments ({@code -- ...}) are stripped
         * from the query while preserving other lines.
         *
         * <p>The filter splits the query by newline and discards any line
         * whose trimmed content starts with {@code --}. This prevents
         * single-line SQL comments from reaching Hibernate's SQL parser.</p>
         */
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

        /**
         * Verifies that colons ({@code :}) and question marks ({@code ?}) are
         * removed from the query string.
         *
         * <p>These characters have special meaning in Hibernate's query parser:
         * colons prefix named parameters ({@code :name}) and question marks
         * denote positional parameters. Since {@link AbstractQueryHandler} uses
         * its own {@code ${ placeholder }} syntax for parameter substitution,
         * these characters must be stripped to avoid Hibernate misinterpreting
         * them as parameter markers in the native SQL.</p>
         */
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
            // After removing the colon, ":name" becomes "name" (no space added)
            assertThat(filtered).contains("SELECT * FROM demographic WHERE name = name AND id =");
        }

        /**
         * Verifies that MySQL-specific hash comments ({@code # ...}) are stripped
         * from the query while preserving other lines.
         *
         * <p>MySQL supports {@code #} as a line comment introducer in addition
         * to the SQL-standard {@code --}. The filter handles this by discarding
         * lines that start with {@code #} after trimming whitespace.</p>
         */
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

        /**
         * Verifies that block comments spanning multiple lines are fully removed.
         *
         * <p>The block comment regex uses a non-greedy quantifier
         * ({@code /\*(?:.|[\n\r])*?\* /}) to match the shortest possible
         * comment, ensuring that only the comment content is removed and
         * subsequent SQL lines are preserved.</p>
         */
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

    /**
     * Tests for {@link AbstractQueryHandler#addParameters(List, String)}.
     *
     * <p>This group verifies that the {@code ${ parameterId }} placeholder syntax
     * is correctly replaced with actual parameter values. The handler supports
     * two value formats:</p>
     * <ul>
     *   <li><strong>Single value</strong>: The placeholder is replaced with the
     *       trimmed value directly (e.g., {@code ${ providerNo }} becomes {@code 12345})</li>
     *   <li><strong>Multiple values</strong>: The placeholder is replaced with a
     *       parenthesized, comma-separated, single-quoted list suitable for SQL
     *       {@code IN} clauses (e.g., {@code ('111','222','333')})</li>
     * </ul>
     *
     * @see Parameter
     */
    @Nested
    @DisplayName("Parameter Substitution")
    class ParameterSubstitution {

        /**
         * Verifies that a single-value parameter replaces its corresponding
         * {@code ${ parameterId }} placeholder in the query string.
         *
         * <p>Uses a {@link Parameter} with id {@code "providerNo"} and a single
         * value of {@code "12345"}. After substitution, the placeholder should
         * be gone and the literal value should appear in its place.</p>
         */
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

        /**
         * Verifies that multiple distinct parameters can each replace their
         * own placeholders within the same query string.
         *
         * <p>Uses two {@link Parameter} objects ({@code providerNo} and
         * {@code status}), each with a single value. Both placeholders
         * should be independently resolved in the resulting query.</p>
         */
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

        /**
         * Verifies that a parameter with multiple values is formatted as an
         * SQL {@code IN} clause: a parenthesized, comma-separated list of
         * single-quoted strings.
         *
         * <p>When a {@link Parameter} has more than one value in its
         * {@code String[]} array, {@code AbstractQueryHandler.parseParameterValue()}
         * formats them as {@code ('val1','val2','val3')} for direct use in
         * SQL {@code IN (...)} expressions.</p>
         */
        @Test
        @Tag("read")
        @DisplayName("should format multiple values as SQL IN clause")
        void shouldFormatAsInClause_whenMultipleValuesProvided() {
            // Given
            Parameter param = new Parameter();
            param.setId("providerNo");
            param.setName("Provider Number");
            // Three values trigger the IN-clause formatting logic
            param.setValue(new String[]{"111", "222", "333"});

            String query = "SELECT * FROM demographic WHERE provider_no IN ${ providerNo }";

            // When
            String result = queryHandler.addParameters(Collections.singletonList(param), query);

            // Then
            // Expect parenthesized, comma-separated, single-quoted values
            assertThat(result).contains("('111','222','333')");
            assertThat(result).doesNotContain("${ providerNo }");
        }
    }

    /**
     * Tests for {@link AbstractQueryHandler#addRanges(List, String)}.
     *
     * <p>This group verifies that range placeholders are correctly substituted.
     * Ranges use a prefix-based placeholder syntax that differs from regular
     * parameters:</p>
     * <ul>
     *   <li>{@code ${ upperLimit.rangeId }} - for {@link RangeUpperLimit} instances</li>
     *   <li>{@code ${ lowerLimit.rangeId }} - for {@link RangeLowerLimit} instances</li>
     * </ul>
     *
     * <p>The prefix ({@code upperLimit} or {@code lowerLimit}) is determined by
     * inspecting the runtime class of the {@link RangeInterface} implementation,
     * matching it against the {@link RangeInterface.Limit} enum values.</p>
     *
     * @see RangeInterface
     * @see RangeUpperLimit
     * @see RangeLowerLimit
     */
    @Nested
    @DisplayName("Range Substitution")
    class RangeSubstitution {

        /**
         * Verifies that an upper-limit range placeholder ({@code ${ upperLimit.age }})
         * is replaced with the range's value.
         *
         * <p>Creates a {@link RangeUpperLimit} with id {@code "age"} and value
         * {@code "65"}. The handler determines the prefix from the class name
         * ({@code RangeUpperLimit} maps to {@code upperLimit}) and builds the
         * full placeholder pattern for regex replacement.</p>
         */
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

        /**
         * Verifies that a lower-limit range placeholder ({@code ${ lowerLimit.age }})
         * is replaced with the range's value.
         *
         * <p>Creates a {@link RangeLowerLimit} with id {@code "age"} and value
         * {@code "18"}. The handler determines the prefix from the class name
         * ({@code RangeLowerLimit} maps to {@code lowerLimit}) and builds the
         * full placeholder pattern for regex replacement.</p>
         */
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

    /**
     * Tests for {@link AbstractQueryHandler#buildQuery(String)}.
     *
     * <p>This group verifies the end-to-end query-building pipeline, which
     * applies the following transformations in order:</p>
     * <ol>
     *   <li>Filter the query string (remove comments and special characters)</li>
     *   <li>Rewrite the SELECT clause if columns are configured (not tested here)</li>
     *   <li>Substitute parameter placeholders with values</li>
     *   <li>Substitute range placeholders with values</li>
     * </ol>
     *
     * <p>Note: {@code buildQuery()} is marked as {@code final} and not thread-safe
     * in the source, since it reads from mutable handler state (parameters, ranges,
     * columns). Each test configures the handler state via setters before calling
     * {@code testBuildQuery()}.</p>
     */
    @Nested
    @DisplayName("Build Query")
    class BuildQuery {

        /**
         * Verifies that {@code buildQuery()} applies parameter substitution and
         * query filtering when parameters are set and ranges/columns are null.
         *
         * <p>Sets a single parameter ({@code providerNo = "99901"}) and verifies
         * that the placeholder is replaced and that no {@code ${} tokens remain
         * in the output. Ranges and columns are explicitly set to {@code null}
         * to skip those pipeline stages.</p>
         */
        @Test
        @Tag("read")
        @DisplayName("should build query with parameters and filtering applied")
        void shouldBuildQuery_whenParametersProvided() {
            // Given
            Parameter param = new Parameter();
            param.setId("providerNo");
            param.setName("Provider Number");
            param.setValue(new String[]{"99901"});

            // Configure handler state: only parameters, no ranges or columns
            queryHandler.setParameters(Collections.singletonList(param));
            queryHandler.setRanges(null);
            queryHandler.setColumns(null);

            String template = "SELECT * FROM demographic WHERE provider_no = ${ providerNo }";

            // When
            String builtQuery = queryHandler.testBuildQuery(template);

            // Then
            assertThat(builtQuery).contains("99901");
            // Ensure no unresolved placeholders remain
            assertThat(builtQuery).doesNotContain("${");
        }

        /**
         * Verifies that {@code buildQuery()} removes both block comments and
         * line comments while also performing parameter substitution.
         *
         * <p>The input template includes a block comment ({@code /* indicator query * /}),
         * a line comment ({@code -- filter by demo}), and a parameter placeholder
         * ({@code ${ demoNo }}). All three transformations should be applied:
         * both comment styles removed, and the placeholder replaced with
         * {@code "5001"}.</p>
         */
        @Test
        @Tag("read")
        @DisplayName("should build query with comments removed and parameters replaced")
        void shouldBuildQueryWithCommentsRemoved_whenCommentsAndParametersPresent() {
            // Given
            Parameter param = new Parameter();
            param.setId("demoNo");
            param.setName("Demographic Number");
            param.setValue(new String[]{"5001"});

            // Configure handler state: only parameters, no ranges or columns
            queryHandler.setParameters(Collections.singletonList(param));
            queryHandler.setRanges(null);
            queryHandler.setColumns(null);

            // Template includes a block comment, a line comment, and a parameter placeholder
            String template = "/* indicator query */\nSELECT * FROM demographic\n-- filter by demo\nWHERE demographic_no = ${ demoNo }";

            // When
            String builtQuery = queryHandler.testBuildQuery(template);

            // Then
            assertThat(builtQuery).doesNotContain("/* indicator query */");
            assertThat(builtQuery).doesNotContain("-- filter by demo");
            assertThat(builtQuery).contains("5001");
        }
    }

    /**
     * Tests for {@link AbstractQueryHandler#execute(String)} and
     * {@link AbstractQueryHandler#getQuery()}.
     *
     * <p>This group verifies the native SQL execution path and related accessor
     * behavior. Key considerations for these tests:</p>
     * <ul>
     *   <li>The {@code execute(String)} method opens its own Hibernate
     *       {@code Session} and {@code Transaction}, independent of the
     *       Spring {@code @Transactional} test context. This means test data
     *       inserted via the EntityManager is NOT visible to executed queries.</li>
     *   <li>Tests therefore use self-contained queries (e.g., {@code SELECT 1})
     *       that do not depend on pre-existing database rows.</li>
     *   <li>The result transformer {@code ALIAS_TO_ENTITY_MAP} converts each
     *       result row into a {@code Map<String, Object>} keyed by column alias.</li>
     *   <li>Invalid SQL should trigger a {@code RuntimeException} wrapping the
     *       underlying Hibernate/database exception.</li>
     * </ul>
     */
    @Nested
    @DisplayName("Query Execution")
    class QueryExecution {

        /**
         * Verifies that a simple native SQL query can be executed and returns
         * the expected result structure.
         *
         * <p>Executes {@code SELECT 1 AS result}, which is database-independent
         * and requires no pre-existing data. The test validates that:</p>
         * <ol>
         *   <li>The result list is non-null and contains exactly one row</li>
         *   <li>Each row is a {@code Map} (from the ALIAS_TO_ENTITY_MAP transformer)</li>
         *   <li>The map contains the expected {@code "result"} key matching the column alias</li>
         * </ol>
         */
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

        /**
         * Verifies that {@link AbstractQueryHandler#getQuery()} returns an empty
         * string when no query has been set on the handler.
         *
         * <p>A freshly constructed {@link TestQueryHandler} has a {@code null}
         * internal query field. The {@code getQuery()} method defensively returns
         * an empty string ({@code ""}) instead of {@code null} to prevent
         * {@code NullPointerException} in downstream code that concatenates or
         * compares query strings.</p>
         */
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

        /**
         * Verifies that executing syntactically invalid SQL throws a
         * {@link RuntimeException} with an appropriate error message.
         *
         * <p>The {@code execute(String)} method catches any exception from the
         * Hibernate session, rolls back the transaction, logs the error, and
         * wraps it in a {@code RuntimeException} with the message
         * {@code "Error executing query"}. This test confirms that contract
         * by passing a nonsensical string as SQL.</p>
         */
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
