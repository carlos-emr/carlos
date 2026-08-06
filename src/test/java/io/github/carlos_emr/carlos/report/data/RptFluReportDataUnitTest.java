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

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("fast")
@DisplayName("Flu billing report demographic projection mapping")
class RptFluReportDataUnitTest {

    @Test
    @DisplayName("should map every demographic query column to its patient detail field")
    void shouldMapEveryQueryColumnToItsPatientDetailField() {
        DemographicDao demographicDao = mock(DemographicDao.class);
        Object[] queryRow = {714, "Patient,Flu", "416-555-0714", "RO", "AC", "1940-06-15", 85};
        when(demographicDao.findDemographicsForFluReport("-1"))
            .thenReturn(Arrays.<Object[]>asList(queryRow));

        try (MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class)) {
            springUtils.when(() -> SpringUtils.getBean(DemographicDao.class)).thenReturn(demographicDao);

            RptFluReportData reportData = new RptFluReportData();
            reportData.fluReportGenerate("-1", "2026");

            assertThat(reportData.years).isEqualTo("2026");
            assertThat(reportData.demoList).singleElement().satisfies(patient -> {
                assertThat(patient.demoNo).isEqualTo("714");
                assertThat(patient.demoName).isEqualTo("Patient,Flu");
                assertThat(patient.demoPhone).isEqualTo("416-555-0714");
                assertThat(patient.demoRosterStatus).isEqualTo("RO");
                assertThat(patient.demoPatientStatus).isEqualTo("AC");
                assertThat(patient.demoDOB).isEqualTo("1940-06-15");
                assertThat(patient.demoAge).isEqualTo("85");
            });
            verify(demographicDao).findDemographicsForFluReport("-1");
        }
    }

    @Test
    @DisplayName("should render empty patient details instead of literal null values")
    void shouldRenderEmptyStringsForNullQueryColumns() {
        DemographicDao demographicDao = mock(DemographicDao.class);
        Object[] queryRow = {null, null, null, null, null, null, null};
        when(demographicDao.findDemographicsForFluReport("999998"))
            .thenReturn(Arrays.<Object[]>asList(queryRow));

        try (MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class)) {
            springUtils.when(() -> SpringUtils.getBean(DemographicDao.class)).thenReturn(demographicDao);

            RptFluReportData reportData = new RptFluReportData();
            reportData.fluReportGenerate("999998", "2026");

            assertThat(reportData.demoList).singleElement().satisfies(patient -> {
                assertThat(patient.demoNo).isEmpty();
                assertThat(patient.demoName).isEmpty();
                assertThat(patient.demoPhone).isEmpty();
                assertThat(patient.demoRosterStatus).isEmpty();
                assertThat(patient.demoPatientStatus).isEmpty();
                assertThat(patient.demoDOB).isEmpty();
                assertThat(patient.demoAge).isEmpty();
            });
        }
    }
}
