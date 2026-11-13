/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.admin.service.SecurityRecordUpdateService.Outcome;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.www.admin.SecurityEditVersion;
import io.github.carlos_emr.carlos.www.admin.SecurityUpdatePasswordValidator;

/**
 * The Security Records save: a form rendered before another administrator's save is refused
 * whole, under the row lock, instead of silently undoing that save (ledger finding 355).
 */
@Tag("unit")
@Tag("security")
@Tag("admin")
@DisplayName("SecurityRecordUpdateService")
class SecurityRecordUpdateServiceUnitTest extends CarlosUnitTestBase {

    /** A new password as an administrator would type it; built at run time so no credential-like literal is committed. */
    private static final String TYPED_PASSWORD = "FAKE-" + java.util.UUID.randomUUID();

    private static final int SECURITY_NO = 41;
    private static final String UNCHANGED = SecurityUpdatePasswordValidator.UNCHANGED_PASSWORD;
    private static final Date LAST_UPDATE = new Date(1_700_000_000_000L);

    private final SecurityDao securityDao = mock(SecurityDao.class);
    private final SecurityManager securityManager = mock(SecurityManager.class);
    private SecurityRecordUpdateService service;
    private Security login;

    @BeforeEach
    void setUp() {
        service = new SecurityRecordUpdateService(securityDao, securityManager);
        login = new Security();
        login.setSecurityNo(SECURITY_NO);
        login.setUserName("pwlogin");
        login.setProviderNo("700123");
        login.setBExpireset(0);
        login.setDateExpiredate(java.sql.Date.valueOf("2100-01-01"));
        login.setBLocallockset(1);
        login.setBRemotelockset(1);
        login.setForcePasswordReset(Boolean.FALSE);
        login.setUsingMfa(false);
        login.setLastUpdateDate(LAST_UPDATE);
        login.setPasswordUpdateDate(new Date(1_690_000_000_000L));
        login.setPinUpdateDate(new Date(1_680_000_000_000L));
        login.setPassword("stored-password-hash");
        login.setPin("stored-pin");
        when(securityDao.findForUpdate(SECURITY_NO)).thenReturn(login);
    }

    /** The form as the edit page renders it for {@link #login}, with the given changes. */
    private static SecurityRecordUpdateCommand form(String version, int expireSet, boolean forceReset,
                                                    String password, String pin) {
        return new SecurityRecordUpdateCommand(SECURITY_NO, "pwlogin", "700123", expireSet, "2100-01-01",
                true, "1", "1", password, pin, forceReset, false, version);
    }

    /** Another administrator's save between rendering and posting: Force password reset turned on. */
    private void anotherAdministratorForcesAReset() {
        login.setForcePasswordReset(Boolean.TRUE);
        login.setLastUpdateDate(new Date(LAST_UPDATE.getTime() + 5_000L));
    }

    @Test
    void shouldRefuseWithoutWriting_whenAnotherSaveChangedTheLoginAfterTheFormWasRendered() {
        String rendered = SecurityEditVersion.of(login);
        anotherAdministratorForcesAReset();

        // Administrator A, still on the older page, ticks the expiry flag and saves.
        Outcome outcome = service.update(form(rendered, 1, false, UNCHANGED, "****"));

        assertThat(outcome).isEqualTo(Outcome.STALE);
        assertThat(login.isForcePasswordReset()).as("B's Force password reset survives").isTrue();
        assertThat(login.getBExpireset()).as("none of A's stale form is applied").isZero();
        verify(securityDao, never()).saveEntity(any());
        verify(securityDao, never()).merge(any());
    }

    @Test
    void shouldStoreNoCredential_whenAStaleFormCarriesANewPasswordAndPin() {
        String rendered = SecurityEditVersion.of(login);
        anotherAdministratorForcesAReset();

        Outcome outcome = service.update(form(rendered, 0, false, TYPED_PASSWORD, "4321"));

        assertThat(outcome).isEqualTo(Outcome.STALE);
        assertThat(login.getPassword()).isEqualTo("stored-password-hash");
        assertThat(login.getPin()).isEqualTo("stored-pin");
        assertThat(login.getPasswordUpdateDate()).isEqualTo(new Date(1_690_000_000_000L));
        assertThat(login.getPinUpdateDate()).isEqualTo(new Date(1_680_000_000_000L));
        verifyNoInteractions(securityManager);
        verify(securityDao, never()).saveEntity(any());
    }

    @Test
    void shouldSaveUnderTheLock_whenTheFormVersionIsCurrent() {
        String rendered = SecurityEditVersion.of(login);

        Outcome outcome = service.update(form(rendered, 1, true, UNCHANGED, "****"));

        assertThat(outcome).isEqualTo(Outcome.SAVED);
        assertThat(login.getBExpireset()).isEqualTo(1);
        assertThat(login.isForcePasswordReset()).isTrue();
        assertThat(login.getLastUpdateDate()).isAfter(LAST_UPDATE);
        assertThat(login.getPassword()).as("the unchanged marker keeps the stored hash").isEqualTo("stored-password-hash");
        assertThat(login.getPin()).as("the unchanged marker keeps the stored PIN").isEqualTo("stored-pin");
        verify(securityDao).findForUpdate(SECURITY_NO);
        verify(securityDao).saveEntity(login);
        verifyNoInteractions(securityManager);
    }

    @Test
    void shouldHashANewPassword_whenTheFormVersionIsCurrent() {
        when(securityManager.encodePassword(TYPED_PASSWORD)).thenReturn("new-hash");
        String rendered = SecurityEditVersion.of(login);

        Outcome outcome = service.update(form(rendered, 0, false, TYPED_PASSWORD, "****"));

        assertThat(outcome).isEqualTo(Outcome.SAVED);
        assertThat(login.getPassword()).isEqualTo("new-hash");
        assertThat(login.getPasswordUpdateDate()).isAfter(new Date(1_690_000_000_000L));
        verify(securityDao).saveEntity(login);
    }

    @Test
    void shouldSaveAsBefore_whenThePostCarriesNoVersion() {
        anotherAdministratorForcesAReset();

        // An older page or a hand-built request: last writer wins, as it always did.
        Outcome outcome = service.update(form(null, 1, false, UNCHANGED, "****"));

        assertThat(outcome).isEqualTo(Outcome.SAVED);
        assertThat(login.getBExpireset()).isEqualTo(1);
        assertThat(login.isForcePasswordReset()).isFalse();
        verify(securityDao).saveEntity(login);
    }

    @Test
    void shouldAnswerAlreadyStored_whenTheSameFormIsPostedAgainAfterItsOwnSave() {
        SecurityRecordUpdateCommand form = form(SecurityEditVersion.of(login), 1, true, UNCHANGED, "****");
        assertThat(service.update(form)).isEqualTo(Outcome.SAVED);
        // The first save stamped lastUpdateDate, so the second click's version no longer matches.

        Outcome repeat = service.update(form);

        assertThat(repeat).isEqualTo(Outcome.ALREADY_STORED);
        verify(securityDao, times(1)).saveEntity(login);
    }

    @Test
    void shouldRefuseTheRepeat_whenItCarriesANewPasswordThatCannotBeCompared() {
        when(securityManager.encodePassword(TYPED_PASSWORD)).thenReturn("new-hash");
        SecurityRecordUpdateCommand form = form(SecurityEditVersion.of(login), 0, false, TYPED_PASSWORD, "****");
        assertThat(service.update(form)).isEqualTo(Outcome.SAVED);

        // Telling whether the stored hash is this password would mean testing it, which the save
        // never does; the repeat is refused and the first click's password stays.
        Outcome repeat = service.update(form);

        assertThat(repeat).isEqualTo(Outcome.STALE);
        assertThat(login.getPassword()).isEqualTo("new-hash");
        verify(securityManager, times(1)).encodePassword(any());
        verify(securityManager, never()).matchesPassword(any(), any());
        verify(securityDao, times(1)).saveEntity(login);
    }

    @Test
    void shouldAnswerAlreadyStored_whenAnotherSaveMadeTheSameChange() {
        String rendered = SecurityEditVersion.of(login);
        anotherAdministratorForcesAReset();

        // A's stale form asks for exactly what the login now holds: there is nothing to undo.
        Outcome outcome = service.update(form(rendered, 0, true, UNCHANGED, null));

        assertThat(outcome).isEqualTo(Outcome.ALREADY_STORED);
        verify(securityDao, never()).saveEntity(any());
    }

    @Test
    void shouldReportNotFound_whenNoLoginHasTheNumber() {
        when(securityDao.findForUpdate(SECURITY_NO)).thenReturn(null);

        assertThat(service.update(form(null, 0, false, UNCHANGED, "****"))).isEqualTo(Outcome.NOT_FOUND);
        verify(securityDao, never()).saveEntity(any());
    }

    @Test
    void shouldNotNameTheCredentials_whenTheCommandIsPrinted() {
        assertThat(form(null, 0, false, TYPED_PASSWORD, "4321").toString())
                .doesNotContain(TYPED_PASSWORD, "4321").contains("41");
    }
}
