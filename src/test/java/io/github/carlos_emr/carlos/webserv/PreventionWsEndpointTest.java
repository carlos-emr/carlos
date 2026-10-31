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
 * Modifications made by CARLOS Contributors in 2026: enforce CDS maintenance and disclosure compatibility.
 */
package io.github.carlos_emr.carlos.webserv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.prevention.PreventionDateService;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.test.base.CarlosSoapTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.transfer_objects.PreventionTransfer;

/**
 * SOAP-level endpoint tests for {@link PreventionWs} using CXF local transport.
 *
 * <p>These tests verify the full CXF JAX-WS pipeline for prevention/immunization
 * operations: SOAP envelope marshalling/unmarshalling and response serialization
 * of {@link PreventionTransfer} arrays.</p>
 *
 * @since 2026-03-31
 * @see CarlosSoapTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("soap")
@DisplayName("PreventionWs SOAP endpoint tests")
class PreventionWsEndpointTest extends CarlosSoapTestBase {

    @Mock
    private PreventionManager preventionManager;
    @Mock
    private PartialDateDao partialDateDao;

    @Mock
    private io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLegacyProjectionService projections;

    private PreventionWs ws;

    @Override
    protected Object getServiceBean() {
        ws = new PreventionWs(preventionManager, new PreventionDateService(partialDateDao), projections);
        return ws;
    }

    @Test
    void shouldFaultInsteadOfReturningEmptySubset_whenStructuredSourceCannotBeProjected() {
        when(preventionManager.getByDemographicIdUpdatedAfterDate(any(LoggedInInfo.class),eq(123),any(java.util.Date.class))).thenReturn(java.util.List.of());
        org.mockito.Mockito.doThrow(new org.apache.cxf.binding.soap.SoapFault("Structured projection requires review",
                org.apache.cxf.binding.soap.SoapFault.FAULT_CODE_CLIENT))
                .when(projections).requireSoapSupported(123,java.util.Set.of("Immunizations"));
        org.assertj.core.api.Assertions.assertThatThrownBy(()->createClient(PreventionPort.class).getPreventionsByDemographicIdAfter(java.util.Calendar.getInstance(),123))
                .hasMessageContaining("Structured projection requires review");
    }

    @Override
    protected Class<?> getServiceInterface() {
        return PreventionWs.class;
    }

    @BeforeEach
    void setUpMocks() {
        registerMock(PreventionManager.class, preventionManager);
    }

    @Test
    void shouldKeepLegacyAnchorPayload_forNativeYearOnlyPrevention() {
        Prevention record = new Prevention();
        record.setDemographicId(123);
        org.springframework.test.util.ReflectionTestUtils.setField(record, "id", 41);
        record.setPreventionType("Td");
        record.setPreventionDate(java.sql.Date.valueOf("2019-01-01"));
        when(partialDateDao.getFormat(4, 41, 7)).thenReturn("YYYY");
        when(preventionManager.getPrevention(any(LoggedInInfo.class), eq(41))).thenReturn(record);
        var result = createClient(PreventionPort.class).getPrevention(41);
        assertThat(result.getPreventionDate()).isEqualTo(java.sql.Date.valueOf("2019-01-01"));
        assertThat(result.getPreventionType()).isEqualTo("Td");
        assertThat(result.getDemographicId()).isEqualTo(123);
    }

    @Test
    void shouldRefuseDateOnlyResponse_whenHistoryHasPartialTiming() {
        Prevention record = new Prevention();
        markImported(record);
            record.setDemographicId(123);
        org.springframework.test.util.ReflectionTestUtils.setField(record, "id", 41);
        record.setPreventionDate(java.sql.Date.valueOf("2019-01-01"));
        when(partialDateDao.getFormat(4, 41, 7)).thenReturn("YYYY");
        when(preventionManager.getPrevention(any(LoggedInInfo.class), eq(41))).thenReturn(record);
        assertThatThrownBy(() -> createClient(PreventionPort.class).getPrevention(41))
                .hasMessageContaining("precision-aware");
    }

    @Test
    void shouldRefuseListOperation_whenHistoryContainsUndatedRecords() {
        Prevention record = new Prevention();
        markImported(record);
        when(preventionManager.getUpdatedAfterDate(any(LoggedInInfo.class), any(Date.class), anyInt()))
                .thenReturn(List.of(record));
        assertThatThrownBy(() -> createClient(PreventionPort.class).getPreventionsUpdatedAfterDate(new Date(), 10))
                .hasMessageContaining("precision-aware");
    }

    private static void markImported(Prevention prevention) {
        var provenance = new io.github.carlos_emr.carlos.commn.model.PreventionExt();
        provenance.setKeyval("cdsDatePrecision");
        prevention.addPreventionExt(provenance);
    }

    /** Tests for the getPrevention SOAP operation. */
    @Nested
    @DisplayName("getPrevention operation")
    class GetPrevention {

        @Test
        @DisplayName("should return prevention transfer when found")
        void shouldReturnPreventionTransfer_whenFound() {
            Prevention prevention = new Prevention();
            prevention.setDemographicId(123);
            prevention.setPreventionType("Flu");
            prevention.setPreventionDate(java.sql.Date.valueOf("2019-01-01"));
            when(preventionManager.getPrevention(any(LoggedInInfo.class), eq(7))).thenReturn(prevention);
            when(preventionManager.getPreventionExtByPrevention(any(LoggedInInfo.class), eq(7)))
                .thenReturn(Collections.emptyList());

            PreventionPort proxy = createClient(PreventionPort.class);
            PreventionTransfer result = proxy.getPrevention(7);

            assertThat(result).isNotNull();
            assertThat(result.getPreventionType()).isEqualTo("Flu");
        }

        @Test
        @DisplayName("should return null when prevention not found")
        void shouldReturnNull_whenPreventionNotFound() {
            when(preventionManager.getPrevention(any(LoggedInInfo.class), eq(999))).thenReturn(null);

            PreventionPort proxy = createClient(PreventionPort.class);
            PreventionTransfer result = proxy.getPrevention(999);

            assertThat(result).isNull();
        }
    }

    /** Tests for the getPreventionsUpdatedAfterDate SOAP operation. */
    @Nested
    @DisplayName("getPreventionsUpdatedAfterDate operation")
    class GetPreventionsUpdatedAfterDate {

        @Test

        @DisplayName("should return prevention array when results exist")
        void shouldReturnPreventionArray_whenResultsExist() {
            List<Prevention> preventions = new ArrayList<>();
            Prevention p = new Prevention();
            p.setDemographicId(123);
            p.setPreventionType("Flu");
            p.setPreventionDate(java.sql.Date.valueOf("2019-01-01"));
            preventions.add(p);
            when(preventionManager.getUpdatedAfterDate(any(LoggedInInfo.class), any(Date.class), anyInt()))
                .thenReturn(preventions);

            PreventionPort proxy = createClient(PreventionPort.class);
            PreventionTransfer[] result = proxy.getPreventionsUpdatedAfterDate(new Date(), 10);

            assertThat(result).hasSize(1);
            assertThat(result[0].getPreventionType()).isEqualTo("Flu");
        }

        @Test

        @DisplayName("should contain no SOAP entries when no results")
        void shouldContainNoSoapEntries_whenNoResults() {
            when(preventionManager.getUpdatedAfterDate(any(LoggedInInfo.class), any(Date.class), anyInt()))
                .thenReturn(new ArrayList<>());

            PreventionPort proxy = createClient(PreventionPort.class);
            PreventionTransfer[] result = proxy.getPreventionsUpdatedAfterDate(new Date(), 10);

            // SOAP omits an empty return array, which JAXB may deserialize as null.
            assertThat(result).isNullOrEmpty();
        }
    }

    /** Tests for the getPreventionsByDemographicIdAfter SOAP operation. */
    @Nested
    @DisplayName("getPreventionsByDemographicIdAfter operation")
    class GetPreventionsByDemographicIdAfter {

        @Test

        @DisplayName("should return preventions for demographic after date")
        void shouldReturnPreventions_forDemographicAfterDate() {
            List<Prevention> preventions = new ArrayList<>();
            Prevention p = new Prevention();
            p.setDemographicId(123);
            p.setPreventionType("Flu");
            p.setPreventionDate(java.sql.Date.valueOf("2019-01-01"));
            preventions.add(p);
            when(preventionManager.getByDemographicIdUpdatedAfterDate(any(LoggedInInfo.class), eq(200), any(Date.class)))
                .thenReturn(preventions);

            PreventionPort proxy = createClient(PreventionPort.class);
            Calendar cal = Calendar.getInstance();
            PreventionTransfer[] result = proxy.getPreventionsByDemographicIdAfter(cal, 200);

            assertThat(result).hasSize(1);
            assertThat(result[0].getPreventionType()).isEqualTo("Flu");
        }
    }
    /** JAX-WS clients use a service interface; constructing a concrete CGLIB proxy would
     * require a fake no-argument constructor on the constructor-injected production service. */
    @jakarta.jws.WebService(name="PreventionWs",targetNamespace="http://ws.oscarehr.org/")
    public interface PreventionPort {
        PreventionTransfer getPrevention(Integer arg0);
        PreventionTransfer[] getPreventionsUpdatedAfterDate(Date arg0,int arg1);
        PreventionTransfer[] getPreventionsByProgramProviderDemographicDate(Integer arg0,String arg1,Integer arg2,java.util.Calendar arg3,int arg4);
        PreventionTransfer[] getPreventionsByDemographicIdAfter(
                @jakarta.jws.WebParam(name="lastUpdate") java.util.Calendar lastUpdate,
                @jakarta.jws.WebParam(name="demographicId") Integer demographicId);
    }
}
