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

import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.prevention.dto.PreventionDatePrecisionException;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import java.io.InputStream;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Element;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Outcomes of the prevention-table classifier and the patient-scoped date guard built on it.
 *
 * <p>The shipped influenza dashboard queries are legal MariaDB that JSqlParser 5.3 cannot parse
 * (issue #6344). MariaDB 11.8 resolves the {@code preventions} table in both, so the classifier must
 * as well; the dashboard itself never consults it, and a population caller passes no patient.</p>
 */
@Tag("unit")
class PreventionReadSafetyServiceUnitTest {
    private static final String FLU_TEMPLATE = "/indicatorXMLTemplates/flu_immunization_bonus.xml";
    private static final String INDICATOR_QUERY = "indicatorQuery";
    private static final String DRILL_DOWN_QUERY = "drillDownQuery";
    private static final Integer PATIENT = 7;

    /**
     * A select list MariaDB accepts and JSqlParser 5.3 rejects: the upper bound of {@code BETWEEN} is a
     * call with several arguments, one of them nesting another call, written as the shipped influenza
     * templates write it. Put before a {@code FROM} clause, it leaves the definition to the
     * table-reference scan alone.
     */
    private static final String LEGACY_SELECT = "SELECT NOW() BETWEEN NOW()"
            + " AND STR_TO_DATE(CONCAT('31,', '12,', (YEAR(NOW()) - 1)), '%d,%m,%Y') AS in_season FROM ";

    /** A patient-scoped eForm AP definition in the same legacy shape. */
    private static final String PATIENT_LEGACY_SQL = "SELECT MAX(p.prevention_date) AS last_flu FROM preventions p"
            + " WHERE p.demographic_no = ${demographic} AND p.prevention_type = 'Flu' AND p.deleted = '0'"
            + " AND DATE(p.prevention_date) BETWEEN DATE_SUB(NOW(), INTERVAL 6 MONTH)"
            + " AND STR_TO_DATE(CONCAT('31,', '12,', (YEAR(NOW()) - 1)), '%d,%m,%Y')";

    @ParameterizedTest
    @ValueSource(strings = {INDICATOR_QUERY, DRILL_DOWN_QUERY})
    void shouldRecognizePreventionsTable_inShippedFluDashboardQuery(String definition) throws Exception {
        assertThat(PreventionReadSafetyService.readsPreventions(shippedFluQuery(definition))).isTrue();
    }

    @Test
    void shouldReadSameBaseTablesAsMariaDb_forShippedFluDashboardQueries() throws Exception {
        // MariaDB 11.8 EXPLAIN EXTENDED of both definitions, with representative template parameters,
        // resolved exactly these base tables (issue #6344).
        assertThat(SqlTableReferences.tableNames(shippedFluQuery(INDICATOR_QUERY)))
                .containsExactlyInAnyOrder("demographic", "preventions");
        assertThat(SqlTableReferences.tableNames(shippedFluQuery(DRILL_DOWN_QUERY)))
                .containsExactlyInAnyOrder("demographic", "preventions", "billing_on_cheader1", "billing_on_item");
    }

    @Test
    void shouldClassifyWithScanAlone_whenParserRejectsLegacyShape() throws Exception {
        // If a JSqlParser upgrade accepts these, the legacy-shape cases below no longer isolate the scan:
        // replace LEGACY_SELECT with a shape the new parser still rejects.
        for (String sql : new String[] {
                shippedFluQuery(INDICATOR_QUERY), shippedFluQuery(DRILL_DOWN_QUERY), LEGACY_SELECT + "preventions",
                PATIENT_LEGACY_SQL}) {
            String inspection = sql.replaceAll("\\$\\{[^}]+}", "?");
            assertThatThrownBy(() -> CCJSqlParserUtil.parse(inspection)).isInstanceOf(JSQLParserException.class);
        }
    }

    @Test
    void shouldRecognizePreventionsTable_whereParserReadsMariaDbCodeAsComment() throws Exception {
        // JSqlParser parses both, taking "--1 FROM preventions" and the executable comment for comments;
        // MariaDB runs that text and reads preventions.
        for (String sql : new String[] {"SELECT 1--1 FROM preventions",
                "SELECT 1 FROM demographic d /*!50000 JOIN preventions p ON p.demographic_no = d.demographic_no */",
                "SELECT 1 FROM demographic d /*M!100100 JOIN preventions p ON p.demographic_no = d.demographic_no */"}) {
            assertThat(new TablesNamesFinder<Void>().getTables(CCJSqlParserUtil.parse(sql)))
                    .doesNotContain("preventions");
            assertThat(PreventionReadSafetyService.readsPreventions(sql)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "preventions",
            "preventions p",
            "preventions AS p",
            "PREVENTIONS p",
            "Preventions AS p",
            "`preventions` p",
            "`clinic`.`preventions` p",
            "clinic.preventions p",
            "clinic . preventions AS p",
            "\"preventions\" p",
            "demographic d, preventions p",
            "demographic d LEFT JOIN clinic.preventions p ON p.demographic_no = d.demographic_no",
            "(demographic d JOIN preventions p ON p.demographic_no = d.demographic_no)",
            "demographic d WHERE EXISTS (SELECT 1 FROM preventions p WHERE p.demographic_no = d.demographic_no)",
            "(SELECT demographic_no FROM `preventions`) recent"})
    void shouldRecognizePreventionsReference_inShapeParserRejects(String fromClause) {
        assertThat(PreventionReadSafetyService.readsPreventions(LEGACY_SELECT + fromClause)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "preventionsExt pe",
            "prevention_ext pe",
            "preventions_archive pa",
            "preventions.preventionsExt pe",
            "demographic preventions",
            "demographic AS preventions",
            "demographic d WHERE d.preventions = 1 ORDER BY preventions",
            "demographic d WHERE d.last_name IN ('preventions', \"preventions\")",
            "demographic d WHERE EXTRACT(YEAR FROM d.preventions) > 0",
            "demographic d -- JOIN preventions p ON 1 = 1\n",
            "demographic d # JOIN preventions p ON 1 = 1\n",
            "demographic d /* JOIN preventions p ON 1 = 1 */"})
    void shouldIgnoreLookalikeNames_inShapeParserRejects(String fromClause) {
        assertThat(PreventionReadSafetyService.readsPreventions(LEGACY_SELECT + fromClause)).isFalse();
    }

    @Test
    void shouldPermitShippedFluDashboards_withoutInspectingPatientCharts() throws Exception {
        PreventionDao dao = mock(PreventionDao.class);
        var safety = new PreventionReadSafetyService(dao);
        for (String definition : new String[] {INDICATOR_QUERY, DRILL_DOWN_QUERY}) {
            String sql = shippedFluQuery(definition);
            assertThatCode(() -> safety.requireSafeSql(sql, null)).doesNotThrowAnyException();
        }
        verifyNoInteractions(dao);
    }

    @Test
    void shouldAdmitPatientScopedLegacyDefinition_whenPatientDatesAreExact() {
        PreventionDao dao = mock(PreventionDao.class);
        when(dao.hasUncertainPreventionDates(PATIENT)).thenReturn(false);
        assertThatCode(() -> new PreventionReadSafetyService(dao).requireSafeSql(PATIENT_LEGACY_SQL, PATIENT))
                .doesNotThrowAnyException();
        verify(dao).hasUncertainPreventionDates(PATIENT);
        verifyNoMoreInteractions(dao);
    }

    @Test
    void shouldRefusePatientScopedLegacyDefinition_whenPatientDatesAreUncertain() {
        // The DAO reports an imported YYYY / YYYY-MM or unknown date for this patient only.
        PreventionDao dao = mock(PreventionDao.class);
        when(dao.hasUncertainPreventionDates(PATIENT)).thenReturn(true);
        assertThatThrownBy(() -> new PreventionReadSafetyService(dao).requireSafeSql(PATIENT_LEGACY_SQL, PATIENT))
                .isExactlyInstanceOf(PreventionDatePrecisionException.class);
        verify(dao).hasUncertainPreventionDates(PATIENT);
        verifyNoMoreInteractions(dao);
    }

    @Test
    void shouldLeaveSqlUnguarded_whenMariaDbSkipsVersionedComment() {
        // MariaDB never runs MySQL 5.7+ versioned comments, so this definition reads demographic only.
        PreventionDao dao = mock(PreventionDao.class);
        String sql = "SELECT d.last_name FROM demographic d"
                + " /*!80000 JOIN preventions p ON p.demographic_no = d.demographic_no */"
                + " WHERE d.demographic_no = ${demographic}";
        assertThat(PreventionReadSafetyService.readsPreventions(sql)).isFalse();
        assertThatCode(() -> new PreventionReadSafetyService(dao).requireSafeSql(sql, PATIENT))
                .doesNotThrowAnyException();
        verifyNoInteractions(dao);
    }

    @Test
    void shouldLeaveUnparseableSqlUnguarded_whenItNamesNoPreventionsTable() {
        PreventionDao dao = mock(PreventionDao.class);
        String sql = LEGACY_SELECT + "demographic d LEFT JOIN preventionsExt pe ON pe.prevention_id = d.demographic_no";
        assertThatCode(() -> new PreventionReadSafetyService(dao).requireSafeSql(sql, PATIENT))
                .doesNotThrowAnyException();
        verifyNoInteractions(dao);
    }

    @Test
    void shouldPermitPopulationAggregation_withoutInspectingUnrelatedPatients() {
        PreventionDao dao = mock(PreventionDao.class);
        var safety = new PreventionReadSafetyService(dao);
        assertThatCode(() -> safety.requireSafeSql(
                "select max(p.prevention_date) from preventions p where p.prevention_date > '2019-05-01'", null))
                .doesNotThrowAnyException();
        verifyNoInteractions(dao);
    }

    @Test
    void shouldKeepPatientScope_whenSqlReadsPreventionHistory() {
        PreventionDao dao = mock(PreventionDao.class);
        var safety = new PreventionReadSafetyService(dao);
        assertThatCode(() -> safety.requireSafeSql("select p.* from preventions p where p.demographic_no = ?", 7))
                .doesNotThrowAnyException();
        verify(dao).hasUncertainPreventionDates(7);
        verify(dao, never()).hasUncertainPreventionDates(null);
    }

    @Test
    void shouldRecognizeNestedAndQualifiedReferences_withQuotedTableNames() {
        assertThat(PreventionReadSafetyService.readsPreventions(
                "select * from demographic d where exists (select 1 from `clinic`.`preventions` p where p.demographic_no=d.demographic_no)"))
                .isTrue();
        assertThat(PreventionReadSafetyService.readsPreventions("select 'preventions' from demographic")).isFalse();
    }

    @Test
    void shouldLeaveUnrelatedQueriesUnchanged_whenTheyDoNotReadPreventions() {
        PreventionDao dao = mock(PreventionDao.class);
        new PreventionReadSafetyService(dao).requireSafeSql("select * from allergies", null);
        verifyNoInteractions(dao);
    }

    /**
     * Reads a shipped influenza definition from the packaged template, as the dashboard does: the DOM
     * text content, which drops the XML comments and unescapes {@code &lt;}.
     */
    private static String shippedFluQuery(String definition) throws Exception {
        try (InputStream source = PreventionReadSafetyServiceUnitTest.class.getResourceAsStream(FLU_TEMPLATE)) {
            assertThat(source).as(FLU_TEMPLATE).isNotNull();
            Element section = (Element) XmlUtils.toDocument(source).getElementsByTagName(definition).item(0);
            return section.getElementsByTagName("query").item(0).getTextContent();
        }
    }
}
