/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import jakarta.ws.rs.client.Entity;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
@Tag("unit")
class TicklerPatientDiscoveryEndpointTest extends CarlosRestTestBase {
    @Mock SecurityInfoManager security;
    @Mock TicklerManager ticklers;
    @Mock DemographicDao patients;
    @Override protected Object getServiceBean() {
        var service=new TicklerWebService(); injectDependency(service,"securityInfoManager",security);
        injectDependency(service,"ticklerManager",ticklers); return service;
    }
    @BeforeEach void setupDiscovery() {
        when(security.hasPrivilege(mockLoggedInInfo,"_tickler","r",(String)null)).thenReturn(true);
        when(security.hasPrivilegeReadOnly(mockLoggedInInfo,"_tickler","r","42")).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(mockLoggedInInfo,42)).thenReturn(true);
        registerMock(ProviderDao.class,mock(ProviderDao.class)); registerMock(DemographicDao.class,patients);
        registerMock(TicklerDocsDao.class,mock(TicklerDocsDao.class)); registerMock(ProgramDao.class,mock(ProgramDao.class));
        var patient=new Demographic(); patient.setDemographicNo(42);patient.setFirstName("Retained");patient.setLastName("Patient");
        when(patients.getDemographicById(42)).thenReturn(patient);
        List<Tickler> rows=IntStream.rangeClosed(1,502).mapToObj(id -> {
            var row=new Tickler();row.setId(id);row.setDemographicNo(id<=500?43:42);row.setMessage("retained-"+id);return row;
        }).toList();
        when(ticklers.getTicklers(eq(mockLoggedInInfo),any(),anyInt(),eq(500),anyBoolean(),anyBoolean(),eq(false),eq(false)))
                .thenAnswer(call -> {int offset=call.getArgument(2); return rows.subList(offset,Math.min(rows.size(),offset+500));});
    }
    @Test void shouldSkipDeniedBatches_andReturnAuthorizedSearchRowsAndTotals() {
        try(var response=request().path("/tickler/search").query("startIndex",1).query("limit",1).post(Entity.json("{}"))) {
            assertThat(response.getStatus()).isEqualTo(200);var body=responseJson(response);
            assertThat(body.get("total").asInt()).isEqualTo(2);
            assertThat(body.get("tickler")).hasSize(1);assertThat(body.at("/tickler/0/id").asInt()).isEqualTo(502);
            assertThat(body.at("/tickler/0/message").asText()).isEqualTo("retained-502");
        }
        verify(patients,never()).getDemographicById(43); verify(ticklers,never()).getNumTicklers(any(),any());
    }
    @Test void shouldReturnOnlyAuthorizedMineRows() {
        try(var response=request().path("/tickler/mine").query("limit",1).get()) {
            assertThat(response.getStatus()).isEqualTo(200);var body=responseJson(response);
            assertThat(body.get("total").asInt()).isEqualTo(1);assertThat(body.at("/tickler/0/id").asInt()).isEqualTo(501);
        }
        verify(patients,never()).getDemographicById(43);
    }
    @Test void shouldUseAuthorizedLegacyListOffsetsAndTotals() {
        mockServletRequest.addParameter("count","1"); mockServletRequest.addParameter("page","2");
        try(var response=request().path("/tickler/ticklers").query("count",1).query("page",2).get()) {
            assertThat(response.getStatus()).isEqualTo(200);var body=responseJson(response);
            assertThat(body.get("total").asInt()).isEqualTo(2);assertThat(body.at("/tickler/0/id").asInt()).isEqualTo(502);
        }
        verify(ticklers,never()).getNumTicklers(any(),any());
    }
    @Test void shouldReturnTruthfulEmptyPages_forBlockedCharts() {
        when(security.isAllowedAccessToPatientRecord(mockLoggedInInfo,42)).thenReturn(false);
        try(var response=request().path("/tickler/search").query("limit",1).post(Entity.json("{}"))) {
            assertThat(response.getStatus()).isEqualTo(200);var body=responseJson(response);
            assertThat(body.get("total").asInt()).isZero();assertThat(body.get("tickler")).isEmpty();
        }
        verifyNoInteractions(patients);
    }
    @Test void shouldPropagatePolicyFailure_withoutReturningAnApparentlyEmptySuccess() {
        when(security.hasPrivilegeReadOnly(mockLoggedInInfo,"_tickler","r","42")).thenThrow(new IllegalStateException("Synthetic policy failure"));
        assertThatThrownBy(() -> request().path("/tickler/search").query("limit",1).post(Entity.json("{}")))
                .hasRootCauseInstanceOf(IllegalStateException.class);verifyNoInteractions(patients);
    }
}
