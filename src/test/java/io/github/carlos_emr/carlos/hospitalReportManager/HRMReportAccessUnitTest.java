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
package io.github.carlos_emr.carlos.hospitalReportManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * The per-patient decision every HRM route asks before it lists, shows, prints, downloads or
 * changes a report: a report filed to a patient is refused exactly when that patient's chart is.
 */
@Tag("unit")
@Tag("hrm")
@Tag("security")
@DisplayName("HRM report patient access decision")
class HRMReportAccessUnitTest {

    private static final int PATIENT = 4242;
    private static final int OTHER_PATIENT = 4343;

    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final HRMDocumentToDemographicDao links = mock(HRMDocumentToDemographicDao.class);
    private final LoggedInInfo user = mock(LoggedInInfo.class);

    /** Everything the decision asks about a patient answers yes. */
    private void permit(int patient) {
        when(security.isAllowedAccessToPatientRecord(user, patient)).thenReturn(true);
        when(security.hasPrivilege(user, "_demographic", "r", Integer.toString(patient))).thenReturn(true);
        when(security.hasPrivilege(user, "_hrm", "r", Integer.toString(patient))).thenReturn(true);
        when(security.hasPrivilegeReadOnly(user, "_demographic", "r", Integer.toString(patient))).thenReturn(true);
        when(security.hasPrivilegeReadOnly(user, "_hrm", "r", Integer.toString(patient))).thenReturn(true);
    }

    private static HRMDocumentToDemographic link(Integer patient) {
        HRMDocumentToDemographic link = new HRMDocumentToDemographic();
        link.setDemographicNo(patient);
        link.setHrmDocumentId(7);
        return link;
    }

    @Test
    @DisplayName("should permit a report filed to a patient the user may read")
    void shouldPermitReport_whenPatientIsReadable() {
        permit(PATIENT);
        when(links.findByHrmDocumentId(7)).thenReturn(List.of(link(PATIENT)));

        assertThat(HRMReportAccess.mayReadReport(security, links, user, 7)).isTrue();
        HRMReportAccess.requireReport(security, links, user, 7);
    }

    @Test
    @DisplayName("should refuse a report filed to a patient the user is locked out of, before any privilege is evaluated")
    void shouldRefuseReport_whenPatientIsLocked() {
        permit(PATIENT);
        when(security.isAllowedAccessToPatientRecord(user, PATIENT)).thenReturn(false);
        when(links.findByHrmDocumentId(7)).thenReturn(List.of(link(PATIENT)));

        assertThat(HRMReportAccess.mayReadReport(security, links, user, 7)).isFalse();
        assertThatThrownBy(() -> HRMReportAccess.requireReport(security, links, user, 7))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required patient read access");
        // The lock answers first: the stateful privilege check, which marks the session, is not reached.
        verify(security, never()).hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"_demographic", "_hrm"})
    @DisplayName("should refuse a report when the user lacks a right for that patient")
    void shouldRefuseReport_whenPatientScopedRightIsMissing(String object) {
        permit(PATIENT);
        when(security.hasPrivilege(user, object, "r", Integer.toString(PATIENT))).thenReturn(false);

        assertThat(HRMReportAccess.mayReadReport(security, user, List.of(link(PATIENT)))).isFalse();
        assertThat(HRMReportAccess.mayReadPatient(security, user, PATIENT)).isFalse();
    }

    @Test
    @DisplayName("should permit a report filed to no patient without asking about any patient")
    void shouldPermitReport_whenFiledToNoPatient() {
        when(links.findByHrmDocumentId(7)).thenReturn(List.of());

        assertThat(HRMReportAccess.mayReadReport(security, links, user, 7)).isTrue();
        assertThat(HRMReportAccess.mayReadReport(security, user, List.of(link(null), link(0)))).isTrue();
        assertThat(HRMReportAccess.mayReadReport(security, user, null)).isTrue();
        verifyNoInteractions(security);
    }

    @Test
    @DisplayName("should refuse a report filed to two patients when one of them is locked")
    void shouldRefuseReport_whenAnyLinkedPatientIsLocked() {
        permit(PATIENT);

        assertThat(HRMReportAccess.mayReadReport(security, user, List.of(link(PATIENT), link(OTHER_PATIENT)))).isFalse();
        permit(OTHER_PATIENT);
        assertThat(HRMReportAccess.mayReadReport(security, user, List.of(link(PATIENT), link(OTHER_PATIENT)))).isTrue();
    }

    @Test
    @DisplayName("should refuse everything without a logged-in user, even a report filed to no patient")
    void shouldRefuse_whenThereIsNoUser() {
        assertThat(HRMReportAccess.mayReadReport(security, links, null, 7)).isFalse();
        assertThat(HRMReportAccess.mayReadReport(security, null, List.of())).isFalse();
        assertThat(HRMReportAccess.mayReadPatient(security, null, PATIENT)).isFalse();
        verifyNoInteractions(links);
    }

    @Test
    @DisplayName("should not decide for a request that names no report")
    void shouldNotDecide_whenNoReportIsNamed() {
        assertThat(HRMReportAccess.mayReadReport(security, links, user, null)).isTrue();
        verifyNoInteractions(links, security);
    }

    @Test
    @DisplayName("should refuse a patient number that is not a patient")
    void shouldRefusePatient_whenNumberIsNotPositive() {
        assertThat(HRMReportAccess.mayReadPatient(security, user, 0)).isFalse();
        assertThat(HRMReportAccess.mayReadPatient(security, user, -1)).isFalse();
    }

    @Test
    @DisplayName("should filter a list read-only, once per patient, without the stateful check")
    void shouldFilterListReadOnly_oncePerPatient() {
        permit(PATIENT);
        Predicate<Collection<HRMDocumentToDemographic>> listable = HRMReportAccess.listFilter(security, user, true);

        assertThat(listable.test(List.of(link(PATIENT)))).isTrue();
        assertThat(listable.test(List.of(link(PATIENT)))).isTrue();
        assertThat(listable.test(List.of(link(OTHER_PATIENT)))).isFalse();
        assertThat(listable.test(List.of(link(OTHER_PATIENT)))).isFalse();
        assertThat(listable.test(List.of())).isTrue();

        verify(security, times(1)).isAllowedAccessToPatientRecord(user, PATIENT);
        verify(security, times(1)).isAllowedAccessToPatientRecord(user, OTHER_PATIENT);
        verify(security, never()).hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), anyString());
        verify(security, never()).hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), anyInt());
        verify(security, never()).hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), isNull(String.class));
    }

    @Test
    @DisplayName("should leave the module right out of an inbox list but keep the patient's own rules")
    void shouldLeaveModuleRightOut_forInboxList() {
        permit(PATIENT);
        when(security.hasPrivilegeReadOnly(user, "_hrm", "r", Integer.toString(PATIENT))).thenReturn(false);

        assertThat(HRMReportAccess.listFilter(security, user, false).test(List.of(link(PATIENT)))).isTrue();
        assertThat(HRMReportAccess.listFilter(security, user, true).test(List.of(link(PATIENT)))).isFalse();

        when(security.hasPrivilegeReadOnly(user, "_demographic", "r", Integer.toString(PATIENT))).thenReturn(false);
        assertThat(HRMReportAccess.listFilter(security, user, false).test(List.of(link(PATIENT)))).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"7,7", "' 7 ',7", "+7,7", "007,7", "-3,-3", "0,0"})
    @DisplayName("should read a number the way the HRM pages and DAOs read it")
    void shouldReadNumber_asIntegerParseIntDoes(String value, int expected) {
        assertThat(HRMReportAccess.number(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "abc", "7a", "1.0", "99999999999"})
    @DisplayName("should read nothing from a value that is not an integer")
    void shouldReadNoNumber_whenValueIsNotAnInteger(String value) {
        assertThat(HRMReportAccess.number(value)).isNull();
    }
}
