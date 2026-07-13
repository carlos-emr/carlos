/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link QueryAppender}.
 *
 * <p>Tests the fluent query builder API for constructing HQL/SQL WHERE clauses
 * with AND, OR, sub-clauses, and ORDER BY support.</p>
 *
 * @since 2026-03-07
 */
@Tag("unit")
@DisplayName("QueryAppender")
class QueryAppenderUnitTest {

    @Nested
    @DisplayName("basic query building")
    class BasicQueryBuilding {

        @Test
        @DisplayName("should return empty string when no clauses added")
        void shouldReturnEmpty_whenNoClausesAdded() {
            QueryAppender qa = new QueryAppender();
            assertThat(qa.toString()).isEmpty();
        }

        @Test
        @DisplayName("should build query with base query only")
        void shouldBuildQuery_withBaseQueryOnly() {
            QueryAppender qa = new QueryAppender("SELECT t FROM Tickler t");
            assertThat(qa.toString()).isEqualTo("SELECT t FROM Tickler t");
        }

        @Test
        @DisplayName("should append WHERE clause")
        void shouldAppendWhereClause() {
            QueryAppender qa = new QueryAppender("SELECT t FROM Tickler t");
            qa.addWhere("t.status = 'A'");
            assertThat(qa.toString()).contains("WHERE");
            assertThat(qa.toString()).contains("t.status = 'A'");
        }

        @Test
        @DisplayName("should append AND clause after WHERE")
        void shouldAppendAndClause_afterWhere() {
            QueryAppender qa = new QueryAppender("SELECT t FROM Tickler t");
            qa.addWhere("t.status = 'A'");
            qa.and("t.demographicNo = 1");
            String result = qa.toString();
            assertThat(result).contains("WHERE");
            assertThat(result).contains("AND");
            assertThat(result).contains("t.demographicNo = 1");
        }

        @Test
        @DisplayName("should append OR clause")
        void shouldAppendOrClause() {
            QueryAppender qa = new QueryAppender("SELECT t FROM Tickler t");
            qa.addWhere("t.status = 'A'");
            qa.or("t.status = 'C'");
            String result = qa.toString();
            assertThat(result).contains("OR");
            assertThat(result).contains("t.status = 'C'");
        }

        @Test
        @DisplayName("should append ORDER BY clause")
        void shouldAppendOrderBy() {
            QueryAppender qa = new QueryAppender("SELECT t FROM Tickler t");
            qa.addWhere("t.status = 'A'");
            qa.addOrder("t.serviceDate DESC");
            String result = qa.toString();
            assertThat(result).contains("ORDER BY");
            assertThat(result).contains("t.serviceDate DESC");
        }
    }

    @Nested
    @DisplayName("complex query construction")
    class ComplexQueryConstruction {

        @Test
        @DisplayName("should build full query with WHERE, AND, OR, ORDER BY")
        void shouldBuildFullQuery_withAllClauses() {
            QueryAppender qa = new QueryAppender("SELECT t FROM Tickler t");
            qa.addWhere("t.status = 'A'");
            qa.and("t.demographicNo = 1");
            qa.or("t.demographicNo = 2");
            qa.addOrder("t.serviceDate DESC");

            String result = qa.toString();
            assertThat(result).startsWith("SELECT t FROM Tickler t");
            assertThat(result).contains("WHERE");
            assertThat(result).contains("AND");
            assertThat(result).contains("OR");
            assertThat(result).contains("ORDER BY");
        }

        @Test
        @DisplayName("should track whether clauses have been appended")
        void shouldTrackAppendedState() {
            QueryAppender qa = new QueryAppender();
            assertThat(qa.isAppended()).isFalse();
            qa.addWhere("x = 1");
            assertThat(qa.isAppended()).isTrue();
        }
    }

    @Nested
    @DisplayName("sub-clause support")
    class SubClauseSupport {

        @Test
        @DisplayName("should support nested sub-clauses with AND")
        void shouldSupportNestedSubClauses_withAnd() {
            QueryAppender qa = new QueryAppender("SELECT t FROM Tickler t");
            qa.addWhere("t.status = 'A'");

            QueryAppender sub = new QueryAppender();
            sub.addWhere("t.demographicNo = 1");
            sub.or("t.demographicNo = 2");

            qa.and(sub);
            String result = qa.toString();
            assertThat(result).contains("(");
            assertThat(result).contains(")");
        }

        @Test
        @DisplayName("should support nested sub-clauses with OR")
        void shouldSupportNestedSubClauses_withOr() {
            QueryAppender qa = new QueryAppender("SELECT t FROM Tickler t");
            qa.addWhere("t.status = 'A'");

            QueryAppender sub = new QueryAppender();
            sub.addWhere("t.creator = '1'");
            sub.and("t.taskAssignedTo = '1'");

            qa.or(sub);
            String result = qa.toString();
            assertThat(result).contains("OR");
            assertThat(result).contains("(");
        }
    }

    @Nested
    @DisplayName("constructor variants")
    class ConstructorVariants {

        @Test
        @DisplayName("should accept base query in constructor")
        void shouldAcceptBaseQuery_inConstructor() {
            QueryAppender qa = new QueryAppender("FROM Entity e");
            assertThat(qa.getBaseQuery()).isEqualTo("FROM Entity e");
        }

        @Test
        @DisplayName("should allow setting base query after construction")
        void shouldAllowSettingBaseQuery_afterConstruction() {
            QueryAppender qa = new QueryAppender();
            qa.setBaseQuery("FROM Entity e");
            assertThat(qa.getBaseQuery()).isEqualTo("FROM Entity e");
        }
    }
}
