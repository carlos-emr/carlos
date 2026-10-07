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

import java.util.List;
import java.util.Optional;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
        when(dao.findClinicProperty(EmailFooterService.CLINIC_DEFAULT))
                .thenReturn(property(null, EmailFooterService.CLINIC_DEFAULT, text));
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
        String crlfFooter = "a\r\n".repeat(EmailData.FOOTER_MAX_LENGTH / 2);

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
        assertThatThrownBy(() -> service.saveClinicDefault(tooLong))
                .isInstanceOf(EmailFooterService.FooterTooLongException.class);
        verifyNoInteractions(dao);
    }

    @Test
    @DisplayName("should change nothing when the clinic footer is saved unchanged, or empty when none is set")
    void shouldChangeNothing_whenClinicFooterUnchanged() {
        clinicDefault("Riverside Clinic\nBook online");

        assertThat(service.saveClinicDefault("Riverside Clinic\r\nBook online")).isZero();

        verify(dao, never()).saveProp(any(UserProperty.class));
        verify(dao, never()).findProviderProperties(anyString());

        UserPropertyDAO emptyDao = mock(UserPropertyDAO.class);
        assertThat(new EmailFooterService(emptyDao, true).saveClinicDefault("")).isZero();
        verify(emptyDao, never()).saveProp(any(UserProperty.class));
        verify(emptyDao, never()).findProviderProperties(anyString());
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
        when(dao.findProviderProperties(EmailFooterService.USER_FOOTER))
                .thenReturn(List.of(custom, wasOldDefault, isNewDefault, customWithNotice, noFooter));
        ownRows("104", EmailFooterService.CLINIC_CHANGE_NOTICE,
                property("104", EmailFooterService.CLINIC_CHANGE_NOTICE, "Dr D first footer"));

        int noticed = service.saveClinicDefault("New clinic footer");

        assertThat(noticed).isEqualTo(3);
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
    @DisplayName("should keep users' own footers but still tell them when the replace rule is off")
    void shouldKeepOwnFooters_whenReplaceRuleIsOff() {
        EmailFooterService keeping = new EmailFooterService(dao, false);
        clinicDefault("Old clinic footer");
        UserProperty custom = property("101", EmailFooterService.USER_FOOTER, "Dr A footer");
        UserProperty wasOldDefault = property("102", EmailFooterService.USER_FOOTER, "Old clinic footer");
        when(dao.findProviderProperties(EmailFooterService.USER_FOOTER)).thenReturn(List.of(custom, wasOldDefault));

        assertThat(keeping.saveClinicDefault("New clinic footer")).isEqualTo(2);

        verify(dao, never()).delete(custom);
        verify(dao, never()).delete(wasOldDefault);
        verify(dao).saveProp("101", EmailFooterService.CLINIC_CHANGE_NOTICE, "Dr A footer");
        verify(dao).saveProp("102", EmailFooterService.CLINIC_CHANGE_NOTICE, "Old clinic footer");
        assertThat(keeping.ownFootersReplacedOnClinicChange()).isFalse();
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
        service.dismissClinicChangeNotice("101");

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
