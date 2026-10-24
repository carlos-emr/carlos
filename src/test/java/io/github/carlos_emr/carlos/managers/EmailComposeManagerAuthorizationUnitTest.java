/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.managers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.dao.EmailLogDaoImpl;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("security")
class EmailComposeManagerAuthorizationUnitTest extends CarlosUnitTestBase {
    private final LoggedInInfo loggedInInfo = new LoggedInInfo();
    private EmailComposeManager manager;
    private EmailLogDaoImpl dao;
    private SecurityInfoManager security;

    @BeforeEach
    void setUp() {
        manager = new EmailComposeManager();
        dao = mock(EmailLogDaoImpl.class);
        security = mock(SecurityInfoManager.class);
        injectDependency(manager, "emailLogDao", dao);
        injectDependency(manager, "securityInfoManager", security);
    }

    @Test
    void shouldRejectBeforeLookup_whenEmailReadIsDenied() {
        assertThatThrownBy(() -> manager.prepareEmailForResend(loggedInInfo, 42))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(dao);
    }

    @Test
    void shouldRejectCrossChartLookup_whenDemographicReadIsDenied() {
        patientEmail();
        assertThatThrownBy(() -> manager.prepareEmailForResend(loggedInInfo, 42))
                .isInstanceOf(SecurityException.class);
        verify(security).hasPrivilege(loggedInInfo, "_demographic", SecurityInfoManager.READ, "123");
    }

    @Test
    void shouldRejectPatientSpecificEmailDenial_despiteGeneralEmailRead() {
        patientEmail();
        when(security.hasPrivilege(loggedInInfo, "_demographic", SecurityInfoManager.READ, "123"))
                .thenReturn(true);
        assertThatThrownBy(() -> manager.prepareEmailForResend(loggedInInfo, 42))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldRejectRestrictedChart_despiteEmailAndDemographicRead() {
        patientEmail();
        grantPatientPrivileges();
        assertThatThrownBy(() -> manager.prepareEmailForResend(loggedInInfo, 42))
                .isInstanceOf(SecurityException.class);
        verify(security).isAllowedAccessToPatientRecord(loggedInInfo, 123);
    }

    @Test
    void shouldReturnEmail_whenPatientAccessIsAuthorized() {
        EmailLog log = patientEmail();
        grantPatientPrivileges();
        when(security.isAllowedAccessToPatientRecord(loggedInInfo, 123)).thenReturn(true);
        assertThat(manager.prepareEmailForResend(loggedInInfo, 42)).isSameAs(log);
    }

    @Test
    void shouldReturnNoContent_whenPatientOrLogIsMissing() {
        when(security.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null)).thenReturn(true);
        assertThat(manager.prepareEmailForResend(loggedInInfo, 42)).isNull();
        EmailLog orphan = new EmailLog();
        when(dao.find((Object) 42)).thenReturn(orphan);
        assertThat(manager.prepareEmailForResend(loggedInInfo, 42)).isNull();
        orphan.setDemographic(new Demographic());
        assertThat(manager.prepareEmailForResend(loggedInInfo, 42)).isNull();
    }

    private EmailLog patientEmail() {
        when(security.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null)).thenReturn(true);
        EmailLog log = new EmailLog();
        Demographic patient = new Demographic();
        patient.setDemographicNo(123);
        log.setDemographic(patient);
        when(dao.find((Object) 42)).thenReturn(log);
        return log;
    }

    private void grantPatientPrivileges() {
        when(security.hasPrivilege(loggedInInfo, "_demographic", SecurityInfoManager.READ, "123"))
                .thenReturn(true);
        when(security.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, "123"))
                .thenReturn(true);
    }
}
