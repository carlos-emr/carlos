/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.*;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
@Tag("unit")
class ProgramPatientDiscoveryEndpointTest extends CarlosRestTestBase {
    @Mock SecurityInfoManager security;
    @Mock AdmissionManager admissions;
    @Mock ProgramManager2 programs;
    @Mock DemographicManager patients;
    @Override protected Object getServiceBean(){return new ProgramService(programs,admissions,security);}
    @BeforeEach void setupRows(){
        when(security.hasPrivilege(mockLoggedInInfo,"_pmm_management","r",(String)null)).thenReturn(true);
        when(security.hasPrivilegeReadOnly(mockLoggedInInfo,"_pmm_management","r","42")).thenReturn(true);
        when(security.hasPrivilegeReadOnly(mockLoggedInInfo,"_demographic","r","42")).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(mockLoggedInInfo,42)).thenReturn(true);
        registerMock(DemographicManager.class,patients);
        var patient=new Demographic();patient.setDemographicNo(42);patient.setFirstName("Retained");patient.setLastName("Patient");
        patient.setYearOfBirth("2001");patient.setMonthOfBirth("09");patient.setDateOfBirth("09");
        when(patients.getDemographic(mockLoggedInInfo,42)).thenReturn(patient);
        List<Admission> rows=IntStream.rangeClosed(1,502).mapToObj(id->{var row=new Admission();row.setId((long)id);row.setClientId(id<=500?43:42);row.setProgramId(7);row.setProviderNo("P"+id);return row;}).toList();
        when(admissions.findAdmissionsByProgramAndDate(eq(mockLoggedInInfo),eq(7),any(),anyInt(),eq(500))).thenAnswer(call->{int offset=call.getArgument(3);return rows.subList(offset,Math.min(rows.size(),offset+500));});
    }
    private org.apache.cxf.jaxrs.client.WebClient list(int offset,int count){return request().path("/program/patientList").query("programNo",7).query("day","2026-11-09").query("startIndex",offset).query("numToReturn",count);}
    @Test void shouldPageAndCountPermittedAdmissions_afterBlockedNativeSizedBatches(){
        try(var response=list(1,1).get()){
            assertThat(response.getStatus()).isEqualTo(200);var body=responseJson(response);
            assertThat(body.get("total").asInt()).isEqualTo(2);assertThat(body.get("content")).hasSize(1);
            assertThat(body.at("/content/0/id").isNull()).isTrue();assertThat(body.at("/content/0/providerNo").asText()).isEqualTo("P502");assertThat(body.at("/content/0/clientId").asInt()).isEqualTo(42);
        }
        verify(patients,never()).getDemographic(mockLoggedInInfo,43);verify(admissions,never()).findAdmissionsByProgramAndDateAsCount(any(),any(),any());
    }
    @Test void shouldExcludeDemographicOverrides_beforeLoadingPatientDetails(){
        when(security.hasPrivilegeReadOnly(mockLoggedInInfo,"_demographic","r","42")).thenReturn(false);
        try(var response=list(0,1).get()){assertThat(response.getStatus()).isEqualTo(200);var body=responseJson(response);assertThat(body.get("total").asInt()).isZero();assertThat(body.get("content")).isEmpty();}
        verifyNoInteractions(patients);
    }
    @Test void shouldExcludeBlockedCharts_fromRowsAndTotals(){
        when(security.isAllowedAccessToPatientRecord(mockLoggedInInfo,42)).thenReturn(false);
        try(var response=list(0,1).get()){assertThat(response.getStatus()).isEqualTo(200);assertThat(responseJson(response).get("total").asInt()).isZero();}
        verifyNoInteractions(patients);
    }
    @Test void shouldRetainZeroSizeResponses_withAuthorizedTotals(){
        try(var response=list(0,0).get()){assertThat(response.getStatus()).isEqualTo(200);var body=responseJson(response);assertThat(body.get("total").asInt()).isEqualTo(2);assertThat(body.get("content")).isEmpty();}
        verifyNoInteractions(patients);
    }
    @Test void shouldDenyGlobalAccess_beforeQueryingAdmissions(){
        when(security.hasPrivilege(mockLoggedInInfo,"_pmm_management","r",(String)null)).thenReturn(false);
        assertThatThrownBy(()->list(0,1).get()).hasRootCauseInstanceOf(io.github.carlos_emr.carlos.commn.exception.AccessDeniedException.class);
        verifyNoInteractions(admissions,patients);
    }
    @Test void shouldPropagatePolicyFailure_withoutReturningFalseEmptySuccess(){
        when(security.hasPrivilegeReadOnly(mockLoggedInInfo,"_pmm_management","r","42")).thenThrow(new IllegalStateException("Synthetic policy failure"));
        assertThatThrownBy(()->list(0,1).get()).hasRootCauseInstanceOf(IllegalStateException.class);verifyNoInteractions(patients);
    }
}
