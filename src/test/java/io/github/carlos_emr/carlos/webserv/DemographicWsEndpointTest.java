/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
package io.github.carlos_emr.carlos.webserv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.PatientConsentManager;
import io.github.carlos_emr.carlos.test.base.CarlosSoapTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.transfer_objects.DemographicTransfer;

/**
 * SOAP-level endpoint tests for {@link DemographicWs} using CXF local transport.
 *
 * <p>These tests verify the full CXF JAX-WS pipeline for demographic operations:
 * SOAP envelope marshalling/unmarshalling, WSDL processing, and response
 * serialization of {@link DemographicTransfer} arrays.</p>
 *
 * @since 2026-03-31
 * @see CarlosSoapTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("soap")
@DisplayName("DemographicWs SOAP endpoint tests")
class DemographicWsEndpointTest extends CarlosSoapTestBase {

    @Mock
    private DemographicManager demographicManager;

    @Mock
    private PatientConsentManager patientConsentManager;

    @Mock private io.github.carlos_emr.carlos.managers.SecurityInfoManager visibility;
    private DemographicWs ws;

    @Override
    protected Object getServiceBean() {
        ws = new DemographicWs();
        return ws;
    }

    @Override
    protected Class<?> getServiceInterface() {
        return DemographicWs.class;
    }

    @BeforeEach
    void setUpMocks() {
        registerMock(DemographicManager.class, demographicManager);
        registerMock(PatientConsentManager.class, patientConsentManager);
        registerMock(io.github.carlos_emr.carlos.managers.SecurityInfoManager.class, visibility);
        injectDependency(ws, "demographicManager", demographicManager);
        injectDependency(ws, "patientConsentManager", patientConsentManager);
    }

    @Test
    void shouldReturnNoConsentedPatients_whenTheProviderHasNoConsentType() {
        when(patientConsentManager.getProviderSpecificConsent(any(LoggedInInfo.class))).thenReturn(null);
        DemographicWs proxy = createClient(DemographicWs.class);
        assertThat(proxy.getConsentedDemographicIdsAfter(new java.util.GregorianCalendar(2026, 0, 2)))
                .isNullOrEmpty();
        org.mockito.Mockito.verify(patientConsentManager, org.mockito.Mockito.never())
                .getConsentsByTypeAndEditDate(any(), any(), any());
    }

    @Test
    void shouldPreserveConsentedPatientSelection_whenTheProviderHasAConsentType() {
        var type = new io.github.carlos_emr.carlos.commn.model.ConsentType();
        type.setId(91);
        when(patientConsentManager.getProviderSpecificConsent(any(LoggedInInfo.class))).thenReturn(type);
        var first = new io.github.carlos_emr.carlos.commn.model.Consent();
        first.setDemographicNo(101);
        var second = new io.github.carlos_emr.carlos.commn.model.Consent();
        second.setDemographicNo(202);
        when(patientConsentManager.getConsentsByTypeAndEditDate(any(), eq(type), any()))
                .thenReturn(List.of(first, first, second));
        DemographicWs proxy = createClient(DemographicWs.class);
        assertThat(proxy.getConsentedDemographicIdsAfter(new java.util.GregorianCalendar(2026, 0, 2)))
                .containsExactly(101, 202);
    }

    /** Tests for the getDemographic SOAP operation. */
    @Nested
    @DisplayName("getDemographic operation")
    class GetDemographic {

        @Test
        @DisplayName("should return demographic transfer when valid ID provided")
        void shouldReturnDemographicTransfer_whenValidIdProvided() {
            Demographic demographic = new Demographic();
            demographic.setDemographicNo(42);
            demographic.setFirstName("Jane");
            demographic.setLastName("Doe");
            when(demographicManager.getDemographicWithExt(any(LoggedInInfo.class), eq(42)))
                .thenReturn(demographic);

            DemographicWs proxy = createClient(DemographicWs.class);
            DemographicTransfer result = proxy.getDemographic(42);

            assertThat(result).isNotNull();
            assertThat(result.getDemographicNo()).isEqualTo(42);
            assertThat(result.getLastName()).isEqualTo("Doe");
        }

        @Test
        @DisplayName("should return null when demographic not found")
        void shouldReturnNull_whenDemographicNotFound() {
            when(demographicManager.getDemographicWithExt(any(LoggedInInfo.class), eq(999)))
                .thenReturn(null);

            DemographicWs proxy = createClient(DemographicWs.class);
            DemographicTransfer result = proxy.getDemographic(999);

            assertThat(result).isNull();
        }
    }

    /** Tests for the searchDemographicByName SOAP operation. */
    @Nested
    @DisplayName("searchDemographicByName operation")
    class SearchDemographicByName {

        @Test
        @DisplayName("should return demographics matching search string")
        void shouldReturnDemographics_whenMatchingNameFound() {
            List<Demographic> demographics = new ArrayList<>();
            Demographic d = new Demographic();
            d.setDemographicNo(1);
            d.setFirstName("John");
            d.setLastName("Smith");
            demographics.add(d);
            when(demographicManager.searchDemographicByName(any(LoggedInInfo.class), eq("Smith"), eq(0), eq(10)))
                .thenReturn(demographics);

            DemographicWs proxy = createClient(DemographicWs.class);
            DemographicTransfer[] result = proxy.searchDemographicByName("Smith", 0, 10);

            assertThat(result).hasSize(1);
            assertThat(result[0].getLastName()).isEqualTo("Smith");
        }

        @Test
        @DisplayName("should return null or empty array when no matches found (JAXB empty array serialization)")
        void shouldReturnEmptyArray_whenNoMatchesFound() {
            when(demographicManager.searchDemographicByName(any(LoggedInInfo.class), anyString(), anyInt(), anyInt()))
                .thenReturn(new ArrayList<>());

            DemographicWs proxy = createClient(DemographicWs.class);
            DemographicTransfer[] result = proxy.searchDemographicByName("NonExistent", 0, 10);

            assertThat(result).isNullOrEmpty();
        }
    }

    /** Tests for the getAdmittedDemographicIdsByProgramProvider SOAP operation. */
    @Nested
    @DisplayName("getAdmittedDemographicIdsByProgramProvider operation")
    class GetAdmittedDemographicIds {

        @Test
        @DisplayName("should return demographic IDs for program and provider")
        void shouldReturnDemographicIds_forProgramAndProvider() {
            List<Integer> ids = new ArrayList<>();
            ids.add(100);
            ids.add(200);
            when(demographicManager.getAdmittedDemographicIdsByProgramAndProvider(
                any(LoggedInInfo.class), eq(1), eq("999")))
                .thenReturn(ids);

            DemographicWs proxy = createClient(DemographicWs.class);
            Integer[] result = proxy.getAdmittedDemographicIdsByProgramProvider(1, "999");

            assertThat(result).isNotNull().containsExactly(100, 200);
        }

        @Test
        @DisplayName("should return null or empty array when no admitted demographics (JAXB empty array serialization)")
        void shouldReturnEmptyArray_whenNoAdmittedDemographics() {
            when(demographicManager.getAdmittedDemographicIdsByProgramAndProvider(
                any(LoggedInInfo.class), any(), anyString()))
                .thenReturn(new ArrayList<>());

            DemographicWs proxy = createClient(DemographicWs.class);
            Integer[] result = proxy.getAdmittedDemographicIdsByProgramProvider(1, "999");

            assertThat(result).isNullOrEmpty();
        }
    }
    @Test
    void shouldFilterAndDeduplicateConsentedIds_throughBoundedReadonlyPatientPolicy() {
        when(visibility.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("r"),
                org.mockito.ArgumentMatchers.isNull())).thenReturn(true);
        var type = new io.github.carlos_emr.carlos.commn.model.ConsentType();
        when(patientConsentManager.getProviderSpecificConsent(any(LoggedInInfo.class))).thenReturn(type);
        List<io.github.carlos_emr.carlos.commn.model.Consent> consents = new ArrayList<>();
        for (int id = 1; id <= 502; id++) {
            var consent = new io.github.carlos_emr.carlos.commn.model.Consent(); consent.setDemographicNo(id);
            consents.add(consent); consents.add(consent);
        }
        when(patientConsentManager.getConsentsByTypeAndEditDate(any(LoggedInInfo.class), eq(type), any(java.util.Date.class)))
                .thenReturn(consents);
        when(visibility.getReadableDemographicIds(any(LoggedInInfo.class), org.mockito.ArgumentMatchers.anyCollection()))
                .thenAnswer(call -> {
                    var ids = new java.util.LinkedHashSet<>(call.<java.util.Collection<Integer>>getArgument(1));
                    ids.retainAll(List.of(501, 502)); return ids;
                });
        var proxy = createClient(DemographicWs.class);
        assertThat(proxy.getConsentedDemographicIdsAfter(java.util.Calendar.getInstance())).containsExactly(501, 502);
        org.mockito.Mockito.verify(visibility).getReadableDemographicIds(any(LoggedInInfo.class),
                eq(java.util.stream.IntStream.rangeClosed(1, 500).boxed().toList()));
        org.mockito.Mockito.verify(visibility).getReadableDemographicIds(any(LoggedInInfo.class), eq(List.of(501, 502)));
    }

    @Test
    void shouldRefuseUnauthorizedConsentDiscovery_beforeReadingConsentRecords() {
        var proxy = createClient(DemographicWs.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> proxy.getConsentedDemographicIdsAfter(java.util.Calendar.getInstance()))
                .isInstanceOf(jakarta.xml.ws.soap.SOAPFaultException.class);
        org.mockito.Mockito.verifyNoInteractions(patientConsentManager);
    }

}
