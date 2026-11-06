/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form;

import io.github.carlos_emr.carlos.commn.dao.forms.FormGrowth0To36Dao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.form.model.FormGrowth0To36;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("form")
class Growth0To36FormServiceUnitTest extends CarlosUnitTestBase {
    private FormGrowth0To36Dao forms;
    private DemographicManager demographics;
    private SecurityInfoManager security;
    private LoggedInInfo actor;
    private Growth0To36FormService service;

    @BeforeEach
    void prepare() {
        forms = mock(FormGrowth0To36Dao.class);
        demographics = mock(DemographicManager.class);
        security = mock(SecurityInfoManager.class);
        actor = mock(LoggedInInfo.class);
        service = new Growth0To36FormService(forms, demographics, security);
        when(security.hasPrivilege(any(), anyString(), anyString(), anyString())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(actor, 123)).thenReturn(true);
    }

    @Test
    void shouldPrefillCurrentDemographics_whenCreatingANewForm() {
        Demographic patient = new Demographic();
        patient.setFirstName(" Synthetic ");
        patient.setLastName(" Écho ");
        patient.setSex(" F ");
        patient.setYearOfBirth("2024");
        patient.setMonthOfBirth("02");
        patient.setDateOfBirth("29");
        when(demographics.getDemographic(actor, 123)).thenReturn(patient);
        Properties loaded = service.load(actor, 123, 0);
        assertThat(loaded.getProperty("demographic_no")).isEqualTo("123");
        assertThat(loaded.getProperty("patientName")).isEqualTo("Synthetic Écho");
        assertThat(loaded.getProperty("patientSex")).isEqualTo("F");
        assertThat(loaded.getProperty("dateOfBirth")).isEqualTo("2024/02/29");
        assertThat(loaded.getProperty("formCreated")).matches("\\d{4}/\\d{2}/\\d{2}");
        verifyNoInteractions(forms);
    }

    @ParameterizedTest
    @ValueSource(strings = {"_form", "_demographic"})
    void shouldRefuseBeforeReadingOrWriting_whenAPrivilegeIsDenied(String object) {
        when(security.hasPrivilege(eq(actor), eq(object), anyString(), eq("123"))).thenReturn(false);
        assertThatThrownBy(() -> service.load(actor, 123, 44)).isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (" + object + ")");
        assertThatThrownBy(() -> service.saveVersion(actor, 123, 44, new Properties())).isInstanceOf(SecurityException.class);
        verifyNoInteractions(forms, demographics);
    }

    @Test
    void shouldAllowFormWrites_withReadAccessToDemographics() {
        when(actor.getLoggedInProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(actor, "_demographic", "w", "123")).thenReturn(false);
        doAnswer(call -> {
            FormGrowth0To36 saved = call.getArgument(0);
            saved.setId(42);
            assertThat(saved.getDemographicNo()).isEqualTo(123);
            assertThat(saved.getProviderNo()).isEqualTo(999998);
            return null;
        }).when(forms).persist(any());
        assertThat(service.saveVersion(actor, 123, 0, new Properties())).isEqualTo(42);
        verify(security).hasPrivilege(actor, "_form", "w", "123");
        verify(security).hasPrivilege(actor, "_demographic", "r", "123");
    }

    @Test
    void shouldRefuseBeforeReadingOrWriting_whenPatientAccessIsDenied() {
        when(security.isAllowedAccessToPatientRecord(actor, 123)).thenReturn(false);
        assertThatThrownBy(() -> service.load(actor, 123, 44)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.saveVersion(actor, 123, 44, new Properties())).isInstanceOf(SecurityException.class);
        verifyNoInteractions(forms, demographics);
    }

    @Test
    void shouldRejectAbsentActorAndInvalidPatient_beforeAnyPersistence() {
        assertThatThrownBy(() -> service.load(null, 123, 0)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.saveVersion(null, 123, 0, new Properties())).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.load(actor, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.saveVersion(actor, -1, 0, new Properties())).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(forms, demographics);
    }
}
