/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.EmailConfigDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.DeviceCodeEmailScope;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Whether the device-code job can email a patient, and why not (clinic code v1, mycarlos#141). */
@Tag("unit")
class PortalMyCarlosDeviceCodeSyncServiceUnitTest {
    private final EmailManager emails = mock(EmailManager.class);
    private final EmailConfigDao configs = mock(EmailConfigDao.class);
    private final DemographicDao demographics = mock(DemographicDao.class);
    private final ProviderDao providers = mock(ProviderDao.class);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final Demographic chart = new Demographic();
    private String sender = "FAKE.clinic@example.com";
    private PortalMyCarlosDeviceCodeSyncService.ChartMail mail;

    @BeforeEach
    void setUp() {
        mail = new PortalMyCarlosDeviceCodeSyncService.ChartMail(emails, configs, demographics, mock(ClinicDAO.class),
                providers, security, () -> sender);
        when(security.isAllowedAccessToPatientRecord(any(), eq(7))).thenReturn(true);
        when(configs.findActiveEmailConfig("FAKE.clinic@example.com")).thenReturn(mock(EmailConfig.class));
        var system = new Provider();
        system.setProviderNo(Provider.SYSTEM_PROVIDER_NO);
        when(providers.getProvider(Provider.SYSTEM_PROVIDER_NO)).thenReturn(system);
        chart.setEmail(" fake.patient@example.com ");
        when(demographics.getDemographicById(7)).thenReturn(chart);
    }

    @Test
    void shouldReachChartEmail_whenSenderFooterEmailAndConsentAllowIt() {
        var reach = mail.reach(7);
        assertEquals("fake.patient@example.com", reach.address());
        assertNull(reach.fallback());
        verify(emails).consentBlockMessage(argThat(login -> Provider.SYSTEM_PROVIDER_NO.equals(login.getLoggedInProviderNo())),
                argThat(probe -> probe.getDemographicNo() == 7));
    }

    @Test
    void shouldLeaveForStaff_whenClinicFooterIsRequiredButMissing() {
        when(emails.clinicFooterMissing()).thenReturn(true);
        assertEquals(PortalMyCarlosStaffApprovalService.FOOTER_MISSING, mail.reach(7).fallback());
        assertEquals("clinic footer missing", PortalMyCarlosStaffApprovalService.FOOTER_MISSING);
        // Nothing about the patient is looked at for a send that can't go out.
        verifyNoInteractions(demographics);
        verify(emails, never()).consentBlockMessage(any(), any());
    }

    @Test
    void shouldLeaveForStaff_whenSenderAccountIsNotActive() {
        sender = null;
        assertEquals(PortalMyCarlosStaffApprovalService.SENDER_INACTIVE, mail.reach(7).fallback());
        sender = "FAKE.other@example.com";
        assertEquals(PortalMyCarlosStaffApprovalService.SENDER_INACTIVE, mail.reach(7).fallback());
    }

    @Test
    void shouldLeaveForStaff_whenChartEmailIsNotUsable() {
        // The .invalid top-level domain is refused, as any address a mail server can't deliver to.
        for (String email : new String[] {null, "", "not an address", "fake.patient@example.invalid"}) {
            chart.setEmail(email);
            assertEquals(PortalMyCarlosStaffApprovalService.NO_USABLE_EMAIL, mail.reach(7).fallback(), String.valueOf(email));
        }
    }

    @Test
    void shouldLeaveForStaff_whenEmailConsentBlocksSending() {
        when(emails.consentBlockMessage(any(), any())).thenReturn("FAKE consent refusal");
        assertEquals(PortalMyCarlosStaffApprovalService.NO_CONSENT, mail.reach(7).fallback());
    }

    @Test
    void shouldLeaveForStaff_whenPatientRecordIsPrivacyLocked() {
        // The archive would refuse every send for this patient, so no code is made.
        when(security.isAllowedAccessToPatientRecord(any(), eq(7))).thenReturn(false);
        assertEquals(PortalMyCarlosStaffApprovalService.RECORD_RESTRICTED, mail.reach(7).fallback());
        assertEquals("record access restricted", PortalMyCarlosStaffApprovalService.RECORD_RESTRICTED);
        verify(emails, never()).consentBlockMessage(any(), any());
    }

    @Test
    void shouldWaitQuietly_untilCarlosHasStarted() {
        // The first run can come before Spring has finished starting: no portal yet, nothing done.
        var approvals = mock(PortalMyCarlosStaffApprovalService.class);
        var job = new PortalMyCarlosDeviceCodeSyncService(approvals, () -> true, () -> null,
                () -> PortalMyCarlosApprovalPolicy.CODE, mail);
        assertDoesNotThrow(job::runOnce);
        verifyNoInteractions(approvals);
    }

    @Test
    void shouldRefuseToRun_whileADeviceCodeEmailScopeIsOpenOnItsThread() {
        var approvals = mock(PortalMyCarlosStaffApprovalService.class);
        var job = new PortalMyCarlosDeviceCodeSyncService(approvals, () -> true, () -> null,
                () -> PortalMyCarlosApprovalPolicy.CODE, mail);
        try (var leaked = DeviceCodeEmailScope.open(7)) {
            assertThrows(IllegalStateException.class, job::runOnce);
        }
        verifyNoInteractions(approvals);
        assertDoesNotThrow(job::runOnce);
    }

    @Test
    void shouldDoNothing_whenDeviceApprovalIsOff() {
        var approvals = mock(PortalMyCarlosStaffApprovalService.class);
        var job = new PortalMyCarlosDeviceCodeSyncService(approvals, () -> false, () -> null,
                () -> PortalMyCarlosApprovalPolicy.CODE, mail);
        job.runOnce();
        verifyNoInteractions(approvals);
    }
}
