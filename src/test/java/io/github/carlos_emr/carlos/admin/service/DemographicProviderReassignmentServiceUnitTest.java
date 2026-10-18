/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.admin.service;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.*;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit") @Tag("security")
class DemographicProviderReassignmentServiceUnitTest {
    DemographicManager demographics; ProviderManager2 providers; SecurityInfoManager security;
    LoggedInInfo user; DemographicProviderReassignmentService service;Provider old;
    @BeforeEach void prepare() {
        demographics=mock(DemographicManager.class);providers=mock(ProviderManager2.class);
        security=mock(SecurityInfoManager.class);user=mock(LoggedInInfo.class);old=new Provider();
        service=new DemographicProviderReassignmentService(demographics,providers,security);
        when(security.hasPrivilege(user,"_admin.misc","w",null)).thenReturn(true);
        when(providers.getProvider(user,"999998")).thenReturn(old);
        when(providers.getProvider(user,"999997")).thenReturn(new Provider());
    }
    void allowPatient(int id) {
        when(security.hasPrivilege(user,"_demographic","u",String.valueOf(id))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(user,id)).thenReturn(true);
    }
    @Test void shouldRefuseReadOnlyAdmin_beforeSelectingPatients() {
        when(security.hasPrivilege(user,"_admin.misc","w",null)).thenReturn(false);
        assertThatThrownBy(()->service.reassign(user,"UpdateMrp","999998","999997","A","Z")).isInstanceOf(SecurityException.class);
        verifyNoInteractions(demographics,providers);
    }
    @Test void shouldRefuseWholeBatchBeforeChanges_whenAnyPatientIsLocked() {
        Demographic first=new Demographic();first.setDemographicNo(101);first.setProviderNo("999998");
        Demographic locked=new Demographic();locked.setDemographicNo(102);locked.setProviderNo("999998");
        when(demographics.getDemographicsNameRangeByProvider(user,old,"^[A-Z]")).thenReturn(List.of(first,locked));allowPatient(101);
        assertThatThrownBy(()->service.reassign(user,"UpdateMrp","999998","999997","A","Z")).isInstanceOf(SecurityException.class);
        assertThat(first.getProviderNo()).isEqualTo("999998");assertThat(locked.getProviderNo()).isEqualTo("999998");
        verify(demographics,never()).updateDemographic(any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"UpdateResident","UpdateNurse","UpdateMidwife"})
    void shouldReassignAuthorizedExtensions(String operation) {
        allowPatient(101);var extension=new DemographicExt();extension.setDemographicNo(101);extension.setValue("999998");
        when(demographics.getDemographicNumbersByResidentNumberAndDemographicLastNameRegex(user,"999998","^[A-Z]")).thenReturn(List.of(101));
        when(demographics.getDemographicNumbersByNurseNumberAndDemographicLastNameRegex(user,"999998","^[A-Z]")).thenReturn(List.of(101));
        when(demographics.getDemographicNumbersByMidwifeNumberAndDemographicLastNameRegex(user,"999998","^[A-Z]")).thenReturn(List.of(101));
        when(demographics.getMultipleResidentForDemographicNumbersByProviderNumber(user,List.of(101),"999998")).thenReturn(List.of(extension));
        when(demographics.getMultipleNurseForDemographicNumbersByProviderNumber(user,List.of(101),"999998")).thenReturn(List.of(extension));
        when(demographics.getMultipleMidwifeForDemographicNumbersByProviderNumber(user,List.of(101),"999998")).thenReturn(List.of(extension));
        assertThat(service.reassign(user,operation,"999998","999997","a","z")).isEqualTo(1);
        assertThat(extension.getValue()).isEqualTo("999998");
        verify(demographics).updateExtension(eq(user),argThat(saved -> saved != extension && saved.getValue().equals("999997")));
    }
    @Test void shouldReassignAuthorizedMrp() {
        allowPatient(101);Demographic patient=new Demographic();patient.setDemographicNo(101);
        when(demographics.getDemographicsNameRangeByProvider(user,old,"^[A-Z]")).thenReturn(List.of(patient));
        assertThat(service.reassign(user,"UpdateMrp","999998","999997","A","Z")).isEqualTo(1);
        verify(demographics).updateDemographic(eq(user),argThat(saved -> saved != patient && saved.getProviderNo().equals("999997")));
    }
    @Test void shouldRejectReversedRange_beforeQueries() {
        assertThatThrownBy(()->service.reassign(user,"UpdateMrp","999998","999997","Z","A")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(demographics);
    }
    @ParameterizedTest
    @ValueSource(strings = {"İ", "ı", "ß", "é", "K"})
    void shouldRejectUnicodeRangeEndpoints_beforeCaseMappingOrQueries(String endpoint) {
        assertThatThrownBy(() -> service.reassign(user, "UpdateMrp", "999998", "999997", endpoint, "Z"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.reassign(user, "UpdateMrp", "999998", "999997", "A", endpoint))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(demographics, providers);
    }
    @Test void shouldAllowNoProviderReplacement_whenClearingMrp() {
        allowPatient(101);Demographic patient=new Demographic();patient.setDemographicNo(101);patient.setProviderNo("999998");
        when(demographics.getDemographicsNameRangeByProvider(user,old,"^[A-Z]")).thenReturn(List.of(patient));
        assertThat(service.reassign(user,"UpdateMrp","999998","","A","Z")).isEqualTo(1);
        assertThat(patient.getProviderNo()).isEqualTo("999998");
        verify(demographics).updateDemographic(eq(user),argThat(saved -> saved != patient && saved.getProviderNo().isEmpty()));
    }
    @Test void shouldAllowNoProviderReplacement_whenClearingResident() {
        allowPatient(101);var extension=new DemographicExt();extension.setDemographicNo(101);extension.setValue("999998");
        when(demographics.getDemographicNumbersByResidentNumberAndDemographicLastNameRegex(user,"999998","^[A-Z]")).thenReturn(List.of(101));
        when(demographics.getMultipleResidentForDemographicNumbersByProviderNumber(user,List.of(101),"999998")).thenReturn(List.of(extension));
        assertThat(service.reassign(user,"UpdateResident","999998","","A","Z")).isEqualTo(1);
        assertThat(extension.getValue()).isEqualTo("999998");
        verify(demographics).updateExtension(eq(user),argThat(saved -> saved != extension && saved.getValue().isEmpty()));
    }

}
