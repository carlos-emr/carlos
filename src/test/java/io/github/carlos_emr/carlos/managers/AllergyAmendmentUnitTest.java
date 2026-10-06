/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.commn.model.PartialDate;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class AllergyAmendmentUnitTest {
    private AllergyDao allergies;
    private PartialDateDao dates;
    private SecurityInfoManager security;
    private LoggedInInfo login;
    private AllergyManagerImpl manager;
    private Allergy replacement;

    @BeforeEach
    void setUp() {
        allergies = mock(AllergyDao.class);
        dates = mock(PartialDateDao.class);
        security = mock(SecurityInfoManager.class);
        login = mock(LoggedInInfo.class);
        manager = new AllergyManagerImpl();
        ReflectionTestUtils.setField(manager, "allergyDao", allergies);
        ReflectionTestUtils.setField(manager, "partialDateDao", dates);
        ReflectionTestUtils.setField(manager, "securityInfoManager", security);
        when(security.hasPrivilege(login, "_allergy", "w", 123)).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(login, 123)).thenReturn(true);
        replacement = new Allergy();
        replacement.setDemographicNo(123);
        replacement.setStartDateFormat(PartialDate.YEARONLY);
    }

    @Test
    void shouldRejectArchivedOriginal_beforeCreatingReplacement() {
        Allergy original = new Allergy();
        original.setDemographicNo(123);
        original.setArchived(true);
        when(allergies.findForUpdate(7)).thenReturn(original);
        assertThat(manager.amendAllergy(login, 7, replacement)).isFalse();
        verify(allergies, never()).persist(any());
        verify(allergies, never()).merge(any());
        verifyNoInteractions(dates);
    }

    @Test
    void shouldRejectMissingOriginal_withoutWriting() {
        assertThat(manager.amendAllergy(login, 7, replacement)).isFalse();
        verify(allergies, never()).persist(any());
        verifyNoInteractions(dates);
    }

    @Test
    void shouldRejectForeignOriginal_beforeWritingEitherPatient() {
        Allergy original = new Allergy();
        original.setDemographicNo(456);
        when(allergies.findForUpdate(7)).thenReturn(original);
        assertThatThrownBy(() -> manager.amendAllergy(login, 7, replacement)).isInstanceOf(SecurityException.class);
        assertThat(original.getArchived()).isFalse();
        verify(allergies, never()).persist(any());
        verify(allergies, never()).merge(any());
        verifyNoInteractions(dates);
    }

    @Test
    void shouldRejectDeniedPatientAccess_beforeLocking() {
        when(security.isAllowedAccessToPatientRecord(login, 123)).thenReturn(false);
        assertThatThrownBy(() -> manager.amendAllergy(login, 7, replacement)).isInstanceOf(SecurityException.class);
        verifyNoInteractions(allergies, dates);
    }

    @Test
    void shouldRejectDeniedWritePrivilege_beforeLocking() {
        when(security.hasPrivilege(login, "_allergy", "w", 123)).thenReturn(false);
        assertThatThrownBy(() -> manager.amendAllergy(login, 7, replacement)).isInstanceOf(SecurityException.class);
        verifyNoInteractions(allergies, dates);
    }

    @Test
    void shouldArchiveAndPersistReplacementWithItsPartialDate() {
        Allergy original = new Allergy();
        original.setDemographicNo(123);
        when(allergies.findForUpdate(7)).thenReturn(original);
        doAnswer(call -> { replacement.setId(8); return null; }).when(allergies).persist(replacement);
        assertThat(manager.amendAllergy(login, 7, replacement)).isTrue();
        assertThat(original.getArchived()).isTrue();
        assertThat(replacement.getArchived()).isFalse();
        assertThat(replacement.getEntryDate()).isNotNull();
        verify(allergies).merge(original);
        verify(allergies).persist(replacement);
        verify(dates).setPartialDate(PartialDate.ALLERGIES, 8, PartialDate.ALLERGIES_STARTDATE, PartialDate.YEARONLY);
    }
}
