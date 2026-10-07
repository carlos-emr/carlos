/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
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
 */
package io.github.carlos_emr.carlos.email.core;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLTransactionRollbackException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.OptimisticLockException;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.LockAcquisitionException;
import org.mockito.ArgumentCaptor;
import org.owasp.encoder.Encode;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Clinic default and per-user email footers (follow-up to #3981).
 *
 * @since 2026-10-07
 */
@DisplayName("EmailFooterService")
@Tag("unit")
@Tag("fast")
@Tag("email")
class EmailFooterServiceUnitTest {

    private static int nextId = 1;

    private UserPropertyDAO dao;
    private EmailFooterService service;

    @BeforeEach
    void setUp() {
        dao = mock(UserPropertyDAO.class);
        service = new EmailFooterService(dao, true);
    }

    private void ownRows(String providerNo, String name, UserProperty... rows) {
        when(dao.getAllProperties(name, List.of(providerNo))).thenReturn(List.of(rows));
    }

    private void clinicDefault(String text) {
        clinicRows(property(null, EmailFooterService.CLINIC_DEFAULT, text));
    }

    /** The clinic rows as a page reads them and as a save, which locks them, reads them. */
    private void clinicRows(UserProperty... rows) {
        when(dao.findClinicProperties(EmailFooterService.CLINIC_DEFAULT)).thenReturn(List.of(rows));
        when(dao.lockClinicProperties(EmailFooterService.CLINIC_DEFAULT)).thenReturn(List.of(rows));
    }

    /** Saves as the Configure Email page does when it showed the footer stored now. */
    private static EmailFooterService.ClinicDefaultSaved saveClinic(EmailFooterService service, String footer) {
        return service.saveClinicDefault(footer, EmailFooterService.fingerprint(service.clinicDefault()));
    }

    @Test
    @DisplayName("should fill in the user's own footer, even an empty one, ahead of the clinic default")
    void shouldUseOwnFooter_beforeClinicDefault() {
        ownRows("101", EmailFooterService.USER_FOOTER, property("101", EmailFooterService.USER_FOOTER, "Dr A\nBook online"));
        ownRows("102", EmailFooterService.USER_FOOTER, property("102", EmailFooterService.USER_FOOTER, ""));
        clinicDefault("Riverside Clinic");

        assertThat(service.composeFooter("101")).contains("Dr A\nBook online");
        assertThat(service.composeFooter("102")).contains("");
        assertThat(service.composeFooter("103")).contains("Riverside Clinic");
        assertThat(service.composeFooter(null)).contains("Riverside Clinic");
    }

    @Test
    @DisplayName("should fill in nothing when the user has no footer and no clinic default is set")
    void shouldReturnEmpty_whenNeitherFooterIsSet() {
        assertThat(service.composeFooter("101")).isEqualTo(Optional.empty());
        assertThat(service.clinicDefault()).isEmpty();
    }

    @Test
    @DisplayName("should read and keep the oldest row when a double submit left two")
    void shouldUseOldestRowAndRemoveDuplicates_whenRowsAreDoubled() {
        UserProperty older = property("101", EmailFooterService.USER_FOOTER, "first");
        UserProperty newer = property("101", EmailFooterService.USER_FOOTER, "second");
        ownRows("101", EmailFooterService.USER_FOOTER, newer, older);

        assertThat(service.composeFooter("101")).contains("first");
        service.saveOwnFooter("101", "Dr A footer");

        assertThat(older.getValue()).isEqualTo("Dr A footer");
        verify(dao).saveProp(older);
        verify(dao).delete(newer);
    }

    @Test
    @DisplayName("should store line breaks as one character each and accept exactly the limit")
    void shouldSaveNormalisedFooter_whenWithinLimit() {
        // 2,000 characters once each CRLF counts as one, ending in text (surrounding whitespace is dropped).
        String crlfFooter = "a\r\n".repeat(EmailData.FOOTER_MAX_LENGTH / 2 - 1) + "ab";

        service.saveOwnFooter("101", crlfFooter);

        ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
        verify(dao).saveProp(eq("101"), eq(EmailFooterService.USER_FOOTER), saved.capture());
        assertThat(saved.getValue()).hasSize(EmailData.FOOTER_MAX_LENGTH).doesNotContain("\r");
    }

    @Test
    @DisplayName("should refuse a footer over the limit and save nothing")
    void shouldRefuseFooter_whenOverLimit() {
        String tooLong = "x".repeat(EmailData.FOOTER_MAX_LENGTH + 1);

        assertThatThrownBy(() -> service.saveOwnFooter("101", tooLong))
                .isInstanceOf(EmailFooterService.FooterTooLongException.class);
        assertThatThrownBy(() -> service.saveClinicDefault(tooLong, EmailFooterService.fingerprint("")))
                .isInstanceOf(EmailFooterService.FooterTooLongException.class);
        verifyNoInteractions(dao);
    }

    @Test
    @DisplayName("should change nothing when the clinic footer is saved unchanged, or empty when none is set")
    void shouldChangeNothing_whenClinicFooterUnchanged() {
        clinicDefault("Riverside Clinic\nBook online");

        assertThat(saveClinic(service, "Riverside Clinic\r\nBook online").outcome())
                .isEqualTo(EmailFooterService.ClinicDefaultOutcome.UNCHANGED);
        // A browser drops a textarea's first line break; the same text with blank lines around it is unchanged.
        assertThat(saveClinic(service, "\nRiverside Clinic\nBook online\n").changed()).isFalse();

        verify(dao, never()).saveProp(any(UserProperty.class));
        verify(dao, never()).lockProviderProperties(anyString());

        UserPropertyDAO emptyDao = mock(UserPropertyDAO.class);
        assertThat(saveClinic(new EmailFooterService(emptyDao, true), "").changed()).isFalse();
        verify(emptyDao, never()).saveProp(any(UserProperty.class));
        verify(emptyDao, never()).lockProviderProperties(anyString());
    }

    @Test
    @DisplayName("should replace users' own footers on a clinic change and tell only those who lose a choice")
    void shouldReplaceOwnFooters_whenClinicDefaultChanges() {
        clinicDefault("Old clinic footer");
        UserProperty custom = property("101", EmailFooterService.USER_FOOTER, "Dr A footer");
        UserProperty wasOldDefault = property("102", EmailFooterService.USER_FOOTER, "Old clinic footer");
        UserProperty isNewDefault = property("103", EmailFooterService.USER_FOOTER, "New clinic footer");
        UserProperty customWithNotice = property("104", EmailFooterService.USER_FOOTER, "Dr D second footer");
        UserProperty noFooter = property("105", EmailFooterService.USER_FOOTER, "");
        when(dao.lockProviderProperties(EmailFooterService.USER_FOOTER))
                .thenReturn(List.of(custom, wasOldDefault, isNewDefault, customWithNotice, noFooter));
        ownRows("104", EmailFooterService.CLINIC_CHANGE_NOTICE,
                property("104", EmailFooterService.CLINIC_CHANGE_NOTICE, "Dr D first footer"));

        EmailFooterService.ClinicDefaultSaved saved = saveClinic(service, "New clinic footer");

        assertThat(saved.outcome()).isEqualTo(EmailFooterService.ClinicDefaultOutcome.CHANGED);
        assertThat(saved.noticed()).isEqualTo(3);
        verify(dao).delete(custom);
        verify(dao).delete(wasOldDefault);
        verify(dao).delete(isNewDefault);
        verify(dao).delete(customWithNotice);
        verify(dao).delete(noFooter);
        verify(dao).saveProp("101", EmailFooterService.CLINIC_CHANGE_NOTICE, "Dr A footer");
        // "No footer" was a choice of its own, so its user is told too.
        verify(dao).saveProp("105", EmailFooterService.CLINIC_CHANGE_NOTICE, "");
        // A notice from an earlier change keeps the text the user lost first.
        verify(dao, never()).saveProp("104", EmailFooterService.CLINIC_CHANGE_NOTICE, "Dr D second footer");
        verify(dao, never()).saveProp(eq("102"), anyString(), anyString());
        verify(dao, never()).saveProp(eq("103"), anyString(), anyString());
        ArgumentCaptor<UserProperty> clinic = ArgumentCaptor.forClass(UserProperty.class);
        verify(dao).saveProp(clinic.capture());
        assertThat(clinic.getValue().getValue()).isEqualTo("New clinic footer");
        assertThat(clinic.getValue().getProviderNo()).isNull();
    }

    @Test
    @DisplayName("should tell a user who chose no footer when the first clinic footer is set")
    void shouldNoticeEmptyOwnFooter_whenNoOldDefault() {
        UserProperty noFooter = property("105", EmailFooterService.USER_FOOTER, "");
        when(dao.lockProviderProperties(EmailFooterService.USER_FOOTER)).thenReturn(List.of(noFooter));

        assertThat(saveClinic(service, "Riverside Clinic").noticed()).isEqualTo(1);

        verify(dao).delete(noFooter);
        verify(dao).saveProp("105", EmailFooterService.CLINIC_CHANGE_NOTICE, "");
    }

    @Test
    @DisplayName("should keep a user's no-footer choice when the clinic footer is cleared")
    void shouldKeepEmptyOwnFooter_whenClinicFooterCleared() {
        clinicDefault("Riverside Clinic");
        UserProperty noFooter = property("105", EmailFooterService.USER_FOOTER, "");
        when(dao.lockProviderProperties(EmailFooterService.USER_FOOTER)).thenReturn(List.of(noFooter));

        EmailFooterService.ClinicDefaultSaved saved = saveClinic(service, "");

        assertThat(saved.changed()).isTrue();
        assertThat(saved.noticed()).isZero();
        verify(dao, never()).delete(noFooter);
        verify(dao, never()).saveProp(eq("105"), anyString(), anyString());
    }

    @Test
    @DisplayName("should keep users' own footers but still tell them when the replace rule is off")
    void shouldKeepOwnFooters_whenReplaceRuleIsOff() {
        EmailFooterService keeping = new EmailFooterService(dao, false);
        clinicDefault("Old clinic footer");
        UserProperty custom = property("101", EmailFooterService.USER_FOOTER, "Dr A footer");
        UserProperty wasOldDefault = property("102", EmailFooterService.USER_FOOTER, "Old clinic footer");
        when(dao.lockProviderProperties(EmailFooterService.USER_FOOTER)).thenReturn(List.of(custom, wasOldDefault));

        assertThat(saveClinic(keeping, "New clinic footer").noticed()).isEqualTo(2);

        verify(dao, never()).delete(custom);
        verify(dao, never()).delete(wasOldDefault);
        verify(dao).saveProp("101", EmailFooterService.CLINIC_CHANGE_NOTICE, "Dr A footer");
        verify(dao).saveProp("102", EmailFooterService.CLINIC_CHANGE_NOTICE, "Old clinic footer");
        assertThat(keeping.ownFootersReplacedOnClinicChange()).isFalse();
    }

    @Test
    @DisplayName("should change nothing when the page is saved unedited, even after someone else changed the footer")
    void shouldChangeNothing_whenPageSavedUneditedAfterAnotherChange() {
        // Administrator A opened the page showing "Old"; administrator B then saved "New".
        clinicDefault("New clinic footer");
        UserProperty custom = property("101", EmailFooterService.USER_FOOTER, "Dr A footer");
        when(dao.lockProviderProperties(EmailFooterService.USER_FOOTER)).thenReturn(List.of(custom));

        EmailFooterService.ClinicDefaultSaved saved =
                service.saveClinicDefault("Old clinic footer\r\n", EmailFooterService.fingerprint("Old clinic footer"));

        // A's unedited save neither undoes B's footer nor replaces users' footers a second time.
        assertThat(saved.outcome()).isEqualTo(EmailFooterService.ClinicDefaultOutcome.UNCHANGED);
        verify(dao, never()).saveProp(any(UserProperty.class));
        verify(dao, never()).delete(any(UserProperty.class));
        verify(dao, never()).lockProviderProperties(anyString());
    }

    @Test
    @DisplayName("should save nothing when the footer changed after the page was opened and the text was edited")
    void shouldSaveNothing_whenFooterChangedSincePageOpened() {
        clinicDefault("New clinic footer");

        EmailFooterService.ClinicDefaultSaved saved =
                service.saveClinicDefault("Edited footer", EmailFooterService.fingerprint("Old clinic footer"));

        assertThat(saved.outcome()).isEqualTo(EmailFooterService.ClinicDefaultOutcome.CHANGED_SINCE_SHOWN);
        assertThat(saved.changed()).isFalse();
        verify(dao, never()).saveProp(any(UserProperty.class));
        verify(dao, never()).delete(any(UserProperty.class));
        verify(dao, never()).lockProviderProperties(anyString());
    }

    @Test
    @DisplayName("should change nothing when a stale page is edited to exactly the footer someone else saved")
    void shouldChangeNothing_whenStalePageEditedToStoredFooter() {
        clinicDefault("New clinic footer");

        EmailFooterService.ClinicDefaultSaved saved =
                service.saveClinicDefault("New clinic footer", EmailFooterService.fingerprint("Old clinic footer"));

        assertThat(saved.outcome()).isEqualTo(EmailFooterService.ClinicDefaultOutcome.UNCHANGED);
        verify(dao, never()).saveProp(any(UserProperty.class));
        verify(dao, never()).lockProviderProperties(anyString());
    }

    @Test
    @DisplayName("should treat characters the page shows as spaces as spaces, so an unedited save stays unedited")
    void shouldTreatControlCharactersAsShown_whenSavedBackUnedited() {
        // Pasted from a word processor: a vertical tab and a C1 control, which the page shows as spaces.
        clinicDefault("Riverside\u000BClinic\u0090Book online");

        // The page showed the stored footer and posts back what its box held: spaces.
        EmailFooterService.ClinicDefaultSaved saved = saveClinic(service, "Riverside Clinic Book online");

        assertThat(saved.changed()).isFalse();
        verify(dao, never()).saveProp(any(UserProperty.class));
        verify(dao, never()).lockProviderProperties(anyString());
        // Tabs and line breaks are kept, as the page shows them.
        assertThat(EmailFooterService.fingerprint("a\tb\nc")).isNotEqualTo(EmailFooterService.fingerprint("a b c"));
    }

    @Test
    @DisplayName("should store characters the page shows as spaces as spaces in a user's own footer")
    void shouldStoreControlCharactersAsSpaces_inOwnFooter() {
        service.saveOwnFooter("101", "Dr A\u000BBook online\u0000");

        verify(dao).saveProp("101", EmailFooterService.USER_FOOTER, "Dr A Book online");
    }

    @Test
    @DisplayName("should show as a space exactly the characters the page's encoder shows as a space")
    void shouldMatchEncoderSpaces_forEveryCodePoint() {
        List<String> mismatches = new ArrayList<>();
        for (int cp = 0; cp <= Character.MAX_CODE_POINT; cp++) {
            if (cp == '\r' || (cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE)) {
                // A carriage return is a line break here; surrogates only come in pairs or alone (below).
                continue;
            }
            String text = "a" + new String(Character.toChars(cp)) + "b";
            boolean encoderSpace = Encode.forHtmlContent(text).equals("a b");
            if (EmailFooterService.normalise(text).equals("a b") != encoderSpace) {
                mismatches.add(String.format("U+%04X", cp));
            }
        }
        assertThat(mismatches).isEmpty();
        for (String lone : new String[] {"a\uD800b", "a\uDC00b", "a\uDBFF\uD83D\uDE00b"}) {
            assertThat(EmailFooterService.normalise(lone))
                    .isEqualTo(Encode.forHtmlContent(lone).replace("&#x1f600;", "\uD83D\uDE00"));
        }
    }

    @Test
    @DisplayName("should report a deadlock partway through a clinic save as a lock failure, so the page asks to retry")
    void shouldThrowCannotAcquireLock_whenClinicSaveDeadlocks() {
        clinicDefault("Old clinic footer");
        // What Hibernate raises for MariaDB error 1213 during a flush.
        when(dao.lockProviderProperties(EmailFooterService.USER_FOOTER)).thenThrow(new LockAcquisitionException(
                "Deadlock found", new SQLTransactionRollbackException("Deadlock found", "40001", 1213), "select"));

        assertThatThrownBy(() -> saveClinic(service, "New clinic footer"))
                .isInstanceOf(CannotAcquireLockException.class);
    }

    @Test
    @DisplayName("should not turn a constraint violation into a retry: it is a real error, not a collision")
    void shouldThrowDataIntegrityViolation_whenConstraintFails() {
        UserProperty own = property("101", EmailFooterService.USER_FOOTER, "Dr A footer");
        ownRows("101", EmailFooterService.USER_FOOTER, own);
        SQLException duplicate = new SQLIntegrityConstraintViolationException("Duplicate entry", "23000", 1062);
        doThrow(new ConstraintViolationException("Duplicate entry", duplicate, "PRIMARY"))
                .when(dao).saveProp(own);

        assertThatThrownBy(() -> service.saveOwnFooter("101", "Dr A new footer"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(ConcurrencyFailureException.class);
    }

    @Test
    @DisplayName("should report a row another save removed partway through as a concurrency failure")
    void shouldThrowConcurrencyFailure_whenRowRemovedByAnotherSave() {
        UserProperty own = property("101", EmailFooterService.USER_FOOTER, "Dr A footer");
        ownRows("101", EmailFooterService.USER_FOOTER, own);
        // What Hibernate raises when a flush partway through deletes a row that is already gone.
        doThrow(new OptimisticLockException("Row was already deleted")).when(dao).delete(own);

        assertThatThrownBy(() -> service.useClinicDefault("101")).isInstanceOf(ConcurrencyFailureException.class);
    }

    @Test
    @DisplayName("should save into the oldest clinic row and remove a second one left by two first saves")
    void shouldKeepOneClinicRow_whenTwoFirstSavesLeftTwo() {
        UserProperty older = property(null, EmailFooterService.CLINIC_DEFAULT, "First");
        UserProperty newer = property("", EmailFooterService.CLINIC_DEFAULT, "Second");
        clinicRows(older, newer);

        assertThat(service.clinicDefault()).isEqualTo("First");
        assertThat(saveClinic(service, "Third").changed()).isTrue();

        assertThat(older.getValue()).isEqualTo("Third");
        verify(dao).saveProp(older);
        verify(dao).delete(newer);
    }

    @Test
    @DisplayName("should give the same fingerprint to footers that save the same, and a different one otherwise")
    void shouldFingerprintNormalisedFooter_forStaleCheck() {
        String fingerprint = EmailFooterService.fingerprint("Riverside Clinic\nBook online");

        assertThat(fingerprint).matches("[0-9a-f]{64}");
        assertThat(EmailFooterService.fingerprint("\nRiverside Clinic\r\nBook online \n")).isEqualTo(fingerprint);
        assertThat(EmailFooterService.fingerprint("Riverside Clinic")).isNotEqualTo(fingerprint);
        assertThat(EmailFooterService.fingerprint(null)).isEqualTo(EmailFooterService.fingerprint(""));
    }

    @Test
    @DisplayName("should replace users' own footers on a clinic change in the application, as decided on 6 Oct")
    void shouldReplaceOwnFooters_withProductionSetting() {
        assertThat(EmailFooterService.REPLACE_OWN_FOOTERS_ON_CLINIC_CHANGE).isTrue();
        assertThat(new EmailFooterService(dao).ownFootersReplacedOnClinicChange()).isTrue();
    }

    @Test
    @DisplayName("should put the replaced footer back from the notice, and do nothing without one")
    void shouldRestorePreviousFooter_fromNotice() {
        UserProperty notice = property("101", EmailFooterService.CLINIC_CHANGE_NOTICE, "Dr A footer");
        ownRows("101", EmailFooterService.CLINIC_CHANGE_NOTICE, notice);

        assertThat(service.restorePreviousFooter("101")).isTrue();
        assertThat(service.restorePreviousFooter("102")).isFalse();

        verify(dao).saveProp("101", EmailFooterService.USER_FOOTER, "Dr A footer");
        verify(dao).delete(notice);
        verify(dao, never()).saveProp(eq("102"), anyString(), anyString());
    }

    @Test
    @DisplayName("should clear the notice when the user saves, follows the clinic default or dismisses it")
    void shouldClearNotice_whenUserActs() {
        UserProperty own = property("101", EmailFooterService.USER_FOOTER, "Dr A footer");
        UserProperty notice = property("101", EmailFooterService.CLINIC_CHANGE_NOTICE, "Dr A footer");
        ownRows("101", EmailFooterService.USER_FOOTER, own);
        ownRows("101", EmailFooterService.CLINIC_CHANGE_NOTICE, notice);

        service.saveOwnFooter("101", "Dr A new footer");
        service.useClinicDefault("101");
        assertThat(service.dismissClinicChangeNotice("101")).isTrue();
        assertThat(service.dismissClinicChangeNotice("102")).isFalse();

        verify(dao).delete(own);
        verify(dao, times(3)).delete(notice);
    }

    @Test
    @DisplayName("should describe the user's page: own footer or clinic default, and any notice")
    void shouldReportSettings_forFooterPage() {
        clinicDefault("Riverside Clinic");
        ownRows("101", EmailFooterService.CLINIC_CHANGE_NOTICE, property("101", EmailFooterService.CLINIC_CHANGE_NOTICE, ""));

        EmailFooterService.UserFooterSettings following = service.settingsFor("101");

        assertThat(following.ownFooter()).isNull();
        assertThat(following.clinicDefault()).isEqualTo("Riverside Clinic");
        assertThat(following.clinicChangeNotice()).isEmpty();
        assertThat(following.ownFootersReplaced()).isTrue();
        assertThat(service.settingsFor("102").clinicChangeNotice()).isNull();
        verify(dao, never()).saveProp(any(UserProperty.class));
    }

    private static UserProperty property(String providerNo, String name, String value) {
        UserProperty property = new UserProperty();
        // AbstractModel.equals compares ids, so each row needs its own, as a saved row has.
        property.setId(nextId++);
        property.setProviderNo(providerNo);
        property.setName(name);
        property.setValue(value);
        return property;
    }
}
