/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.form;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.forms.Growth0To36FormDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Patient privilege and demographic-default regressions for the migrated service. */
@Tag("unit")
@Tag("form")
class Growth0To36FormServiceUnitTest extends CarlosUnitTestBase {
    private final Growth0To36FormDao forms = mock(Growth0To36FormDao.class);
    private final DemographicDao demographics = mock(DemographicDao.class);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final LoggedInInfo actor = mock(LoggedInInfo.class);
    private final Growth0To36FormService service = new Growth0To36FormService(forms, demographics, security);

    @ParameterizedTest
    @ValueSource(strings = {"_form", "_demographic"})
    void shouldRejectReadsBeforeAccessingPatientData_whenAPrivilegeIsMissing(String object) {
        when(security.hasPrivilege(actor, "_form", "r", 123)).thenReturn(!object.equals("_form"));
        when(security.hasPrivilege(actor, "_demographic", "r", 123)).thenReturn(!object.equals("_demographic"));
        assertThatThrownBy(() -> service.load(actor, 123, 42)).isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (" + object + ")");
        verifyNoInteractions(forms, demographics);
    }

    @ParameterizedTest
    @ValueSource(strings = {"_form", "_demographic"})
    void shouldRejectWritesBeforeInsertingAForm_whenAPrivilegeIsMissing(String object) {
        when(security.hasPrivilege(actor, "_form", "w", 123)).thenReturn(!object.equals("_form"));
        when(security.hasPrivilege(actor, "_demographic", "w", 123)).thenReturn(!object.equals("_demographic"));
        Properties values = new Properties();
        values.setProperty("demographic_no", "123");
        assertThatThrownBy(() -> service.saveVersion(actor, values)).isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (" + object + ")");
        verifyNoInteractions(forms, demographics);
    }

    @Test
    void shouldRetainDemographicDefaultsAndDateFormatting_whenOpeningANewForm() {
        when(security.hasPrivilege(actor, "_form", "r", 123)).thenReturn(true);
        when(security.hasPrivilege(actor, "_demographic", "r", 123)).thenReturn(true);
        Demographic patient = new Demographic();
        patient.setFirstName("  Synthetic ");
        patient.setLastName(" Infant  ");
        patient.setSex("F");
        patient.setYearOfBirth("2025");
        patient.setMonthOfBirth("02");
        patient.setDateOfBirth("03");
        when(demographics.getDemographic("123")).thenReturn(patient);
        assertThat(service.load(actor, 123, 0)).containsEntry("demographic_no", "123")
                .containsEntry("patientName", "Synthetic Infant").containsEntry("patientSex", "F")
                .containsEntry("dateOfBirth", "2025/02/03");
        verifyNoInteractions(forms);
    }

    @Test
    void shouldRefuseMissingOrForeignVersions_withoutReadingDemographicDefaults() {
        when(security.hasPrivilege(actor, "_form", "r", 123)).thenReturn(true);
        when(security.hasPrivilege(actor, "_demographic", "r", 123)).thenReturn(true);
        assertThatThrownBy(() -> service.load(actor, 123, 42)).isInstanceOf(SecurityException.class);
        verify(forms).findForPatient(123, 42);
        verifyNoInteractions(demographics);
    }
}
