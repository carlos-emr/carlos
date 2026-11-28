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
package io.github.carlos_emr.carlos.prevention;

import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Table positions and lexical rules of the scan the prevention classifier falls back to when
 * JSqlParser cannot read a definition (issue #6344). Each statement here that MariaDB accepts was
 * checked against the tables MariaDB 11.8 resolves for it.
 */
@Tag("unit")
class SqlTableReferencesUnitTest {
    /** A join to preventions inside a block comment whose opening each case supplies. */
    private static final String COMMENTED_JOIN =
            "SELECT 1 FROM demographic d %s JOIN preventions p ON p.demographic_no = d.demographic_no */";

    /** A keyword used as a column, qualified in ON, before a comma-joined preventions table. */
    private static final String QUALIFIED_KEYWORD_IN_ON = "SELECT 1 FROM demographic d"
            + " JOIN (SELECT 1 AS `%1$s`) f ON f.%1$s = d.demographic_no, preventions p";

    /** A keyword used as a column, qualified in the select list before a comma and a column named preventions. */
    private static final String QUALIFIED_KEYWORD_IN_SELECT =
            "SELECT d.%1$s, preventions FROM (SELECT 1 AS `%1$s`, 2 AS preventions) d";

    @Test
    void shouldReportEveryTableReference_inFromListsAndJoins() {
        assertThat(SqlTableReferences.tableNames("SELECT d.demographic_no FROM demographic d"
                + " JOIN allergies a ON a.demographic_no = d.demographic_no, clinic.preventions p"
                + " NATURAL LEFT OUTER JOIN measurements m STRAIGHT_JOIN drugs dr"))
                .containsExactly("demographic", "allergies", "preventions", "measurements", "drugs");
    }

    @Test
    void shouldReportTablesOfNestedQueries_inDerivedTablesAndPredicates() {
        assertThat(SqlTableReferences.tableNames("SELECT (SELECT MAX(prevention_date) FROM preventions) AS latest"
                + " FROM (SELECT * FROM demographic) d WHERE d.demographic_no IN (SELECT demographic_no FROM allergies)"
                + " AND EXISTS (SELECT 1 FROM drugs) UNION SELECT 1 FROM measurements"))
                .containsExactly("preventions", "demographic", "allergies", "drugs", "measurements");
    }

    @Test
    void shouldReadParenthesisedFromList_asTableReferences() {
        assertThat(SqlTableReferences.tableNames(
                "SELECT 1 FROM ((demographic d JOIN preventions p ON p.demographic_no = d.demographic_no), allergies a)"))
                .containsExactly("demographic", "preventions", "allergies");
    }

    @Test
    void shouldReportLastNamePart_forQuotedAndQualifiedNames() {
        // The double-quoted pair is read as ANSI_QUOTES names; ".allergies" is ODBC's default database.
        assertThat(SqlTableReferences.tableNames("SELECT 1 FROM `clinic`.`preventions` p,"
                + " clinic . /* schema */ `pre``ventions` q, \"clinic\".\"drugs\" r, .allergies a"))
                .containsExactly("preventions", "pre`ventions", "drugs", "allergies");
    }

    @Test
    void shouldSkipColumnsAndAliases_namedLikeTables() {
        assertThat(SqlTableReferences.tableNames("SELECT STRAIGHT_JOIN preventions.preventions,"
                + " EXTRACT(YEAR FROM preventions.reviewed) FROM chart_summary preventions"
                + " WHERE TRIM(BOTH ' ' FROM preventions.preventions) <> ''"
                + " GROUP BY preventions.preventions, preventions.reviewed ORDER BY preventions LIMIT 1, 2"))
                .containsExactly("chart_summary");
    }

    @Test
    void shouldSkipLiterals_withEscapedQuotes() {
        assertThat(SqlTableReferences.tableNames("SELECT 'FROM preventions', \"FROM preventions\","
                + " 'it''s \\' FROM preventions', \"say \"\"FROM preventions\"\"\" FROM demographic"))
                .containsExactly("demographic");
    }

    @Test
    void shouldSkipComments_followingMariaDbRules() {
        assertThat(SqlTableReferences.tableNames("""
                SELECT 1 FROM demographic d -- , preventions p
                # , preventions p
                /* , preventions p */ WHERE 1 = 1"""))
                .containsExactly("demographic");
    }

    @Test
    void shouldReadCode_thatMariaDbRunsDespiteCommentSyntax() {
        // "--" opens a comment only before a blank; executable comments are run, not skipped.
        assertThat(SqlTableReferences.tableNames("SELECT 1--1 FROM preventions")).containsExactly("preventions");
        assertThat(SqlTableReferences.tableNames("SELECT 1 FROM /*!*/ preventions")).containsExactly("preventions");
        assertThat(SqlTableReferences.tableNames("SELECT 1 FROM demographic d"
                + " /*!50000 JOIN preventions p ON p.demographic_no = d.demographic_no */ /*M!100100 , allergies */"))
                .containsExactly("demographic", "preventions", "allergies");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/*!", "/*!40101", "/*!50699", "/*!100100", "/*!110806", "/*!120000", "/*M!",
            "/*M!50700", "/*M!80000", "/*M!100100", "/*M!110806", "/*M!120000", "/*M!999999"})
    void shouldReadExecutableComment_whenAnySupportedMariaDbRunsIt(String opening) {
        assertThat(SqlTableReferences.tableNames(COMMENTED_JOIN.formatted(opening)))
                .containsExactly("demographic", "preventions");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/*!50700", "/*!80000", "/*!99999", "/*m!100100", "/*+"})
    void shouldSkipComment_whenNoSupportedMariaDbRunsIt(String opening) {
        assertThat(SqlTableReferences.tableNames(COMMENTED_JOIN.formatted(opening))).containsExactly("demographic");
    }

    @Test
    void shouldReadTable_whenOnlyAnIntermediateVersionEnablesIt() {
        assertThat(SqlTableReferences.tableNames("""
                SELECT 1 FROM (SELECT 1 AS preventions) d
                /*M!120000 WHERE 1 IN (1 */
                /*M!110800 , preventions */
                /*M!120000 ) */""")).containsExactly("preventions");
    }

    @Test
    void shouldEndSkippedVersionedComment_afterOneNestedComment() {
        assertThat(SqlTableReferences.tableNames("SELECT 1 FROM demographic d /*!80000 JOIN allergies a"
                + " ON a.demographic_no = d.demographic_no /* nested */ JOIN preventions p ON 1 = 1 */, measurements m"))
                .containsExactly("demographic", "measurements");
    }

    /** Every word the scan gives a keyword role. */
    static Stream<String> scannerKeywords() {
        return Stream.of("select", "with", "from", "join", "straight_join", "for", "where", "group", "having",
                "order", "limit", "window", "union", "except", "intersect", "set", "update", "returning");
    }

    @ParameterizedTest
    @MethodSource("scannerKeywords")
    void shouldKeepFromList_whenKeywordFollowsQualifyingDot(String keyword) {
        // MariaDB reads a word right after a qualifying dot as a name, never as a keyword.
        assertThat(SqlTableReferences.tableNames(QUALIFIED_KEYWORD_IN_ON.formatted(keyword)))
                .containsExactly("demographic", "preventions");
    }

    @ParameterizedTest
    @MethodSource("scannerKeywords")
    void shouldOpenNoTablePosition_whenKeywordFollowsQualifyingDot(String keyword) {
        assertThat(SqlTableReferences.tableNames(QUALIFIED_KEYWORD_IN_SELECT.formatted(keyword))).isEmpty();
    }

    @Test
    void shouldReadQualifiedReservedWords_inSelectOnAndWhere() {
        assertThat(SqlTableReferences.tableNames("SELECT q.select, q.from AS x"
                + " FROM (SELECT 1 AS `select`, 2 AS `from`, 3 AS `where`) q"
                + " JOIN demographic d ON d.demographic_no = q.select, preventions p WHERE q.where = 3"))
                .containsExactly("demographic", "preventions");
    }

    @Test
    void shouldReportSchemaQualifiedTable_afterJoin() {
        assertThat(SqlTableReferences.tableNames("SELECT 1 FROM demographic d"
                + " JOIN clinic.preventions p ON p.demographic_no = d.demographic_no"
                + " JOIN `clinic`.`drugs` dr ON dr.demographic_no = d.demographic_no"))
                .containsExactly("demographic", "preventions", "drugs");
    }

    @Test
    void shouldKeepFromList_acrossIndexHintsAndLocking() {
        assertThat(SqlTableReferences.tableNames("SELECT 1 FROM demographic d IGNORE INDEX FOR ORDER BY (PRIMARY)"
                + " FORCE INDEX FOR JOIN (PRIMARY) JOIN allergies a USING (demographic_no), preventions p FOR UPDATE"))
                .containsExactly("demographic", "allergies", "preventions");
    }

    @Test
    void shouldKeepFromList_whenWindowIsAColumn() {
        assertThat(SqlTableReferences.tableNames("SELECT 1 FROM (SELECT 1 AS `window`) f"
                + " JOIN (SELECT 1) g ON window = 1 /*M!100100 , preventions p */"))
                .containsExactly("preventions");
    }

    @Test
    void shouldEndFromList_whenWindowDefinesNamedWindows() {
        assertThat(SqlTableReferences.tableNames("SELECT ROW_NUMBER() OVER w FROM demographic"
                + " WINDOW w AS (), preventions AS (ORDER BY demographic_no)"))
                .containsExactly("demographic");
    }

    @ParameterizedTest
    @ValueSource(strings = {"OJ", "oj", "`marker`"})
    void shouldReadFirstTable_insideOdbcJoinEscape(String marker) {
        assertThat(SqlTableReferences.tableNames("SELECT 1 /*M!100100 FROM { %s preventions p"
                .formatted(marker) + " LEFT JOIN demographic d ON p.demographic_no = d.demographic_no }, allergies */"))
                .containsExactly("preventions", "demographic", "allergies");
    }

    @Test
    void shouldStartAfresh_afterStatementSeparator() {
        assertThat(SqlTableReferences.tableNames("SELECT 1 FROM demographic; SELECT 2 FROM preventions"))
                .containsExactly("demographic", "preventions");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "SELECT 1",
            "SELECT 1 FROM",
            "SELECT 1 FROM (((",
            "))) SELECT 1",
            "SELECT 'unterminated FROM preventions",
            "SELECT `unterminated FROM preventions",
            "SELECT 1 /* unterminated FROM preventions"})
    void shouldReportNoTables_forTextWithoutTableReference(String sql) {
        assertThat(SqlTableReferences.tableNames(sql)).isEmpty();
    }

    @Test
    void shouldHandleBoundaryTokensAndNonClauseConstructs_withoutFalseTables() {
        assertThat(SqlTableReferences.tableNames("SELECT 1 FROM demographic--")).containsExactly("demographic");
        assertThat(SqlTableReferences.tableNames("SELECT 1 FROM demographic # trailing")).containsExactly("demographic");
        assertThat(SqlTableReferences.tableNames("SELECT 'a\\")).isEmpty();
        assertThat(SqlTableReferences.tableNames("SELECT 'a\\\\', a$1 FROM demographic.")).containsExactly("demographic");
        assertThat(SqlTableReferences.tableNames("SELECT 1 FROM demographic WINDOW w FROM preventions"))
                .containsExactly("demographic", "preventions");
        assertThat(SqlTableReferences.tableNames("SELECT 1 FROM demographic WINDOW + preventions"))
                .containsExactly("demographic");
        assertThat(SqlTableReferences.tableNames("SELECT 1 FROM { 1 }, preventions"))
                .containsExactly("preventions");
        assertThat(SqlTableReferences.tableNames("SELECT 1 FROM demographic FOR SELECT, preventions"))
                .containsExactly("demographic");
        assertThat(SqlTableReferences.tableNames("SELECT 1 FROM demographic + preventions"))
                .containsExactly("demographic");
        assertThat(SqlTableReferences.tableNames("SELECT /*!1234567 */ 1 FROM demographic"))
                .containsExactly("demographic");
    }

    @Test
    void shouldFoldOnlyAsciiLetters_whenComparingNames() {
        assertThat(SqlTableReferences.foldAsciiCase("PREVENTIONS_Ünïcode_İ"))
                .isEqualTo("preventions_Ünïcode_İ");
    }
}
