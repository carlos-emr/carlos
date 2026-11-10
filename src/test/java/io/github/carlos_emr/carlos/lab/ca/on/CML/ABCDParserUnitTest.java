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
package io.github.carlos_emr.carlos.lab.ca.on.CML;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.io.BufferedReader;
import java.io.StringReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Rejects empty CML reports and propagates lookup failures instead of silently routing unmatched.
 * @since 2026-09-25
 */
@Tag("unit")
@Tag("lab")
class ABCDParserUnitTest extends CarlosUnitTestBase {
    private PatientLabRoutingDao routing;

    @BeforeEach
    void setUpParser() {
        routing = mock(PatientLabRoutingDao.class);
        registerMock(PatientLabRoutingDao.class, routing);
        registerMock(LabReportInformationDao.class, mock(LabReportInformationDao.class));
        registerMock(LabPatientPhysicianInfoDao.class, mock(LabPatientPhysicianInfoDao.class));
        registerMock(LabTestResultsDao.class, mock(LabTestResultsDao.class));
    }

    @Test
    void shouldRejectEmptyUpload_whenNoReportsPresent() {
        assertThatThrownBy(() -> new ABCDParser().parse(new BufferedReader(new StringReader(""))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("CML upload contains no patient reports");
    }

    @Test
    void shouldRejectHeaderOnlyUpload_whenNoPatientsPresent() {
        assertThatThrownBy(() -> new ABCDParser().parse(new BufferedReader(
                new StringReader("A^1^20260925^12:00^0^0^0^"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("CML upload contains no patient reports");
    }

    @Test
    void shouldPropagateProviderLookupFailure_whenDatabaseUnavailable() throws Exception {
        LabRoutingLookupDao lookup = mock(LabRoutingLookupDao.class);
        registerMock(LabRoutingLookupDao.class, lookup);
        when(lookup.findCmlProviderIdentifiers()).thenThrow(new jakarta.persistence.PersistenceException("synthetic failure"));
        assertThatThrownBy(() -> new ABCDParser().save()).isInstanceOf(jakarta.persistence.PersistenceException.class);
        verifyNoInteractions(routing);
    }

    @Test
    void shouldAvoidUnmatchedPatientRouting_whenPatientLookupFails() throws Exception {
        LabRoutingLookupDao lookup = mock(LabRoutingLookupDao.class);
        registerMock(LabRoutingLookupDao.class, lookup);
        when(lookup.findCmlPatients(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenThrow(new jakarta.persistence.PersistenceException("synthetic failure"));
        assertThatThrownBy(() -> new ABCDParser().patientRouteReport("1", "Synthetic", "Test", "F", "20000101", "1234567890"))
                .isInstanceOf(jakarta.persistence.PersistenceException.class);
        verifyNoInteractions(routing);
    }

    @Test
    void shouldLeaveMissingHinUnmatched_withoutCandidateLookup() {
        LabRoutingLookupDao lookup = mock(LabRoutingLookupDao.class);
        registerMock(LabRoutingLookupDao.class, lookup);
        for (String hin : new String[] {null, "", "  ", "UNKNOWN", "unknown"}) {
            new ABCDParser().patientRouteReport("1", "Synthetic", "Test", "F", "20000101", hin);
        }
        verifyNoInteractions(lookup);
        verify(routing, times(5)).persist(argThat(row -> row instanceof io.github.carlos_emr.carlos.commn.model.PatientLabRouting route && route.getDemographicNo() == 0));
    }

    @Test
    void shouldResolveShortDoctorIdentifiers_withoutDroppingExactMatches() {
        java.util.HashMap<String, String> providers = new java.util.HashMap<>();
        providers.put("A", "123");
        providers.put("1234", "456");
        for (String doctor : new String[] {null, "", "B", "UNKNOWN"}) {
            assertThat(ABCDParser.resolveProvider(doctor, providers)).isEqualTo("0");
        }
        assertThat(ABCDParser.resolveProvider("A", providers)).isEqualTo("123");
        assertThat(ABCDParser.resolveProvider("1234", providers)).isEqualTo("456");
        assertThat(ABCDParser.resolveProvider("1234X", providers)).isEqualTo("456");
    }

    @Test
    void shouldRejectMalformedPatientRecord_beforeAnyPersistence() {
        assertThatThrownBy(() -> new ABCDParser().parse(new BufferedReader(
                new StringReader("A^1^20260925^12:00^1^0^0^\nB^truncated^"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CML B record must contain exactly 22 fields");
        verifyNoInteractions(routing);
    }
}
