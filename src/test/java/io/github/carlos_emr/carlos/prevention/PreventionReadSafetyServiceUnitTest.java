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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class PreventionReadSafetyServiceUnitTest {
    @Test
    void shouldPermitInstalledFluBonusQueries_whenTheirLegacyDialectIsUnsupported() throws Exception {
        try (var source = getClass().getResourceAsStream("/indicatorXMLTemplates/flu_immunization_bonus.xml")) {
            assertThat(source).isNotNull();
            var document = javax.xml.parsers.DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder().parse(source);
            int checked = 0;
            for (String element : new String[] {"query", "patientQuery"}) {
                var queries = document.getElementsByTagName(element);
                for (int i = 0; i < queries.getLength(); i++) {
                    String sql = queries.item(i).getTextContent();
                    assertThatCode(() -> PreventionReadSafetyService.readsPreventions(sql)).doesNotThrowAnyException();
                    checked++;
                }
            }
            assertThat(checked).isEqualTo(2);
        }
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
    void shouldPreserveLegacySqlExecution_whenClassifierDoesNotSupportTheDialect() {
        assertThat(PreventionReadSafetyService.readsPreventions("select !!! from preventions")).isFalse();
    }

    @Test
    void shouldLeaveUnrelatedQueriesUnchanged_whenTheyDoNotReadPreventions() {
        PreventionDao dao = mock(PreventionDao.class);
        new PreventionReadSafetyService(dao).requireSafeSql("select * from allergies", null);
        verifyNoInteractions(dao);
    }
}
