/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.data;

import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class EctFormDataRegistryBoundaryUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldDelegateRegistryValidationOnce_whenLoadingPatientFormHistory() {
        EncounterFormDao dao = createAndRegisterMock(EncounterFormDao.class);
        assertThat(EctFormData.getPatientFormsAsArrayList("123", "Form", "formBPMH")).isEmpty();
        verify(dao).findPatientHistory("formBPMH", "123", false);
        verify(dao, never()).findByFormTable(anyString());
    }

    @Test
    void shouldRejectInvalidIdentifiersBeforeDatabaseAccess_whenLoadingHistory() {
        EncounterFormDao dao = createAndRegisterMock(EncounterFormDao.class);
        assertThat(EctFormData.getPatientFormsAsArrayList("123", "Form", "form;DROP TABLE demographic")).isEmpty();
        verifyNoInteractions(dao);
    }

    @Test
    void shouldReturnEmptyHistoryAndNoShortcut_whenTheDaoRejectsAnUnknownTable() {
        EncounterFormDao dao = createAndRegisterMock(EncounterFormDao.class);
        when(dao.findPatientHistory(eq("formUnregistered"), eq("123"), anyBoolean()))
                .thenThrow(new io.github.carlos_emr.carlos.commn.dao.UnknownEncounterFormTableException());
        assertThat(EctFormData.getPatientFormsAsArrayList("123", "Form", "formUnregistered")).isEmpty();
        assertThat(EctFormData.getGroupedPatientFormsAsArrayList("123", "Form", "formUnregistered", "form.jsp")).isEmpty();
        assertThat(EctFormData.getShortcutLatestPatientForm("123", "formUnregistered")).isNull();
        verify(dao, never()).findByFormTable(anyString());
    }

    @Test
    void shouldPropagateOtherFailures_whenHistoryHasAnUnrelatedInvalidArgument() {
        EncounterFormDao dao = createAndRegisterMock(EncounterFormDao.class);
        when(dao.findPatientHistory(anyString(), anyString(), anyBoolean()))
                .thenThrow(new IllegalArgumentException("unrelated failure"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                EctFormData.getPatientFormsAsArrayList("123", "Form", "formBPMH"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("unrelated failure");
    }
}
