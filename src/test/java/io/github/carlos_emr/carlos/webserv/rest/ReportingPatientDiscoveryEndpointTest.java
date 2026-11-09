/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.commn.model.DemographicSets;
import io.github.carlos_emr.carlos.managers.*;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import jakarta.ws.rs.client.Entity;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
@Tag("unit")
class ReportingPatientDiscoveryEndpointTest extends CarlosRestTestBase {
    @Mock SecurityInfoManager security;
    @Mock DemographicSetsManager sets;
    @Mock DemographicManager patients;
    @Override protected Object getServiceBean(){var service=new ReportingService();injectDependency(service,"securityInfoManager",security);injectDependency(service,"demographicSetsManager",sets);injectDependency(service,"demographicManager",patients);return service;}
    @BeforeEach void setupRows(){
        when(security.hasPrivilege(mockLoggedInInfo,"_report","r",(String)null)).thenReturn(true);
        when(security.hasPrivilegeReadOnly(mockLoggedInInfo,"_report","r","42")).thenReturn(true);
        when(security.hasPrivilegeReadOnly(mockLoggedInInfo,"_demographic","r","42")).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(mockLoggedInInfo,42)).thenReturn(true);
        when(patients.getDemographicFormattedName(mockLoggedInInfo,42)).thenReturn("Retained patient");
        List<DemographicSets> rows=IntStream.rangeClosed(1,502).mapToObj(id->{var row=new DemographicSets();row.setId(id);row.setDemographicNo(id<=500?43:42);row.setName("fixture");row.setEligibility("retained-"+id);return row;}).toList();
        when(sets.getByNamePage(eq(mockLoggedInInfo),eq("fixture"),anyInt(),eq(500))).thenAnswer(call->{int offset=call.getArgument(2);return rows.subList(offset,Math.min(rows.size(),offset+500));});
    }
    @Test void shouldFilterSetRowsAndTotals_afterBlockedBatches(){
        try(var response=request().path("/reporting/demographicSets/demographicSet/fixture").get()){
            assertThat(response.getStatus()).isEqualTo(200);var body=responseJson(response);assertThat(body.get("total").asInt()).isEqualTo(2);
            assertThat(body.get("content")).hasSize(2);assertThat(body.at("/content/0/eligibility").asText()).isEqualTo("retained-501");
        }
        verify(sets,never()).getByName(any(),any());
    }
    @Test void shouldLoadNamesOnlyForPermittedPatientListRows(){
        try(var response=request().path("/reporting/demographicSets/patientList").post(Entity.json("{\"name\":\"fixture\"}"))){
            assertThat(response.getStatus()).isEqualTo(200);var body=responseJson(response);assertThat(body.get("patients")).hasSize(2);assertThat(body.at("/patients/0/name").asText()).isEqualTo("Retained patient");
        }
        verify(patients,never()).getDemographicFormattedName(mockLoggedInInfo,43);
    }
    @Test void shouldExcludeBlockedCharts_fromBothRowsAndTotals(){
        when(security.isAllowedAccessToPatientRecord(mockLoggedInInfo,42)).thenReturn(false);
        try(var response=request().path("/reporting/demographicSets/demographicSet/fixture").get()){
            assertThat(response.getStatus()).isEqualTo(200);var body=responseJson(response);assertThat(body.get("total").asInt()).isZero();assertThat(body.get("content")).isEmpty();
        }
        verifyNoInteractions(patients);
    }
    @Test void shouldRespectDemographicOverrides_beforeReadingNames(){
        when(security.hasPrivilegeReadOnly(mockLoggedInInfo,"_demographic","r","42")).thenReturn(false);
        try(var response=request().path("/reporting/demographicSets/patientList").post(Entity.json("{\"name\":\"fixture\"}"))){assertThat(response.getStatus()).isEqualTo(200);assertThat(responseJson(response).get("patients")).isEmpty();}
        verifyNoInteractions(patients);
    }
    @Test void shouldPropagatePolicyFailure_withoutReturningSuccessfulPartialResults(){
        when(security.hasPrivilegeReadOnly(mockLoggedInInfo,"_report","r","42")).thenThrow(new IllegalStateException("Synthetic policy failure"));
        assertThatThrownBy(()->request().path("/reporting/demographicSets/demographicSet/fixture").get()).hasRootCauseInstanceOf(IllegalStateException.class);verifyNoInteractions(patients);
    }
}
