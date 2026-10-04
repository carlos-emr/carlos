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
package io.github.carlos_emr.carlos.mds.data;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Exercises all three native MDS query layouts through both public result readers. */
@Tag("unit")
class MDSNativeQueryTypesUnitTest extends CarlosUnitTestBase {
    private ProviderLabRoutingDao dao;
    private static final LocalDateTime RECEIVED = LocalDateTime.of(2026, java.time.Month.MARCH, 4, 12, 34, 56);

    @BeforeEach
    void setUp() {
        registerMock(ConsultDocsDao.class, mock(ConsultDocsDao.class));
        registerMock(ConsultResponseDocDao.class, mock(ConsultResponseDocDao.class));
        registerMock(LabPatientPhysicianInfoDao.class, mock(LabPatientPhysicianInfoDao.class));
        registerMock(PatientLabRoutingDao.class, mock(PatientLabRoutingDao.class));
        registerMock(EFormDocsDao.class, mock(EFormDocsDao.class));
        dao = mock(ProviderLabRoutingDao.class);
        registerMock(ProviderLabRoutingDao.class, dao);
    }

    private void rows(boolean patient, boolean selectedLab, boolean modern) {
        var row = new ArrayList<Object>(Arrays.asList(17, "accession-17", modern ? 'A' : "A",
                "Example^Patient", "1234567890", modern ? 'F' : "F", modern ? '1' : "1",
                modern ? RECEIVED : Timestamp.valueOf(RECEIVED), "S", "Doctor^Test",
                modern ? '0' : "0", "MICROBIOLOGY"));
        if (patient && !selectedLab) row.remove(2);
        var result = Collections.singletonList(row.toArray());
        if (selectedLab) when(dao.findMdsResultResultDataByDemographicNoAndLabNo(42, 17)).thenReturn(result);
        else if (patient) when(dao.findMdsResultResultDataByDemoId("42")).thenReturn(result);
        else when(dao.findMdsResultResultDataByManyThings("", "999998", "", "", "")).thenReturn(result);
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void shouldReadLegacyLists_whenNativeOrJdbcScalarsReturned(boolean patient, boolean modern) {
        rows(patient, false, modern);
        var data = new MDSResultsData();
        data.populateMDSResultsData("999998", patient ? "42" : null, "", "", "", "");
        assertThat(data.segmentID).containsExactly("17");
        assertThat(data.sex).containsExactly("F");
        assertThat(data.dateTime).containsExactly("2026-03-04");
        assertThat(data.reportStatus).containsExactly("0");
        assertThat(data.acknowledgedStatus).containsExactly(patient ? "U" : "A");
        assertThat(data.discipline).containsExactly("Microbiology");
    }

    @ParameterizedTest
    @CsvSource({"false,false,false", "false,false,true", "true,false,false", "true,false,true",
            "true,true,false", "true,true,true"})
    void shouldReadLabResults_whenNativeOrJdbcScalarsReturned(boolean patient, boolean selectedLab, boolean modern) {
        rows(patient, selectedLab, modern);
        var results = new MDSResultsData().populateMDSResultsData2("999998", patient ? "42" : null,
                "", "", "", "", selectedLab ? 17 : null);
        assertThat(results).hasSize(1);
        var result = results.getFirst();
        assertThat(result.segmentID).isEqualTo("17");
        assertThat(result.sex).isEqualTo("F");
        assertThat(result.dateTime).isEqualTo("2026-03-04 12:34:56");
        assertThat(result.getDateObj()).isEqualTo(Timestamp.valueOf(RECEIVED));
        assertThat(result.acknowledgedStatus).isEqualTo(patient ? "U" : "A");
        assertThat(result.reportStatus).isEqualTo("0");
        assertThat(result.abn).isTrue();
        assertThat(result.finalRes).isFalse();
        assertThat(result.priority).isEqualTo("Stat/Urgent");
        assertThat(result.discipline).isEqualTo("Microbiology");
    }
}
