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
package io.github.carlos_emr.carlos.sms.admin;

import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.assembler.SmsConfigViewModelAssembler;
import io.github.carlos_emr.carlos.sms.dao.SmsConfigDao;
import io.github.carlos_emr.carlos.sms.dto.SmsConfigUpdateDto;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsConfig;
import io.github.carlos_emr.carlos.sms.service.SmsConfigAuditRecorder;
import io.github.carlos_emr.carlos.sms.service.SmsConfigConflictException;
import io.github.carlos_emr.carlos.sms.service.SmsConfigService;
import io.github.carlos_emr.carlos.sms.service.SmsProviderClient;
import io.github.carlos_emr.carlos.sms.service.SmsProviderClientResolver;
import io.github.carlos_emr.carlos.sms.service.SmsSendService;
import io.github.carlos_emr.carlos.sms.service.StubSmsProviderClient;
import io.github.carlos_emr.carlos.sms.validator.SmsConfigValidator;
import io.github.carlos_emr.carlos.sms.viewmodel.SmsConfigViewModel;
import io.github.carlos_emr.carlos.test.util.EncryptionKeyTestSupport;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("security")
class ConfigureSms2ActionUnitTest {
    // Plain placeholders kept in constants so secret scanners do not read a literal as a password.
    private static final String FIELD_INPUT = "entered";
    private static final String TYPED_CREDENTIAL = "typed-for-other-provider";

    private final SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
    private final SmsConfigService configService = mock(SmsConfigService.class);
    private final SmsConfigValidator validator = mock(SmsConfigValidator.class);
    private final SmsConfigViewModelAssembler assembler = mock(SmsConfigViewModelAssembler.class);
    private final SmsSendService sendService = mock(SmsSendService.class);
    private final LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servletActionContext;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setContextPath("/carlos");
        response = new MockHttpServletResponse();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        Security security = mock(Security.class);
        when(security.getSecurityNo()).thenReturn(1001);
        when(loggedInInfo.getLoggedInSecurity()).thenReturn(security);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() {
        servletActionContext.close();
    }

    @Test
    @DisplayName("the page needs _admin.sms read")
    void shouldDenyPage_withoutAdminSmsRead() {
        request.setMethod("GET");

        assertThatThrownBy(() -> action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin.sms)");
        verifyNoInteractions(assembler, configService);
    }

    @Test
    @DisplayName("the page shows the settings and the result of the last action")
    void shouldShowSettings_whenUserMayRead() throws Exception {
        request.setMethod("GET");
        request.setParameter("result", "saved");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.sms", "r", null)).thenReturn(true);
        SmsConfigViewModel model = mock(SmsConfigViewModel.class);
        when(assembler.assemble("saved", List.of())).thenReturn(model);

        String result = action().execute();

        assertThat(result).isEqualTo("success");
        assertThat(request.getAttribute("smsConfig")).isSameAs(model);
    }

    @Test
    @DisplayName("saving rejects GET with 405 before any privilege check or write")
    void shouldRejectSave_whenGet() throws Exception {
        request.setMethod("GET");
        request.setParameter("method", "configure");

        String result = action().execute();

        assertThat(result).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(securityInfoManager, configService, sendService);
    }

    @Test
    @DisplayName("the system test needs _admin.sms write")
    void shouldDenySystemTest_withoutAdminSmsWrite() {
        request.setMethod("POST");
        request.setParameter("method", "sendSystemTest");
        request.setParameter("testNumber", "416-555-1212");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.sms", "w", null)).thenReturn(false);

        assertThatThrownBy(() -> action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin.sms)");
        verifyNoInteractions(sendService);
    }

    @Test
    @DisplayName("saving needs _admin.sms write")
    void shouldDenySave_withoutAdminSmsWrite() {
        request.setMethod("POST");
        request.setParameter("method", "configure");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.sms", "w", null)).thenReturn(false);

        assertThatThrownBy(() -> action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin.sms)");
        verify(configService, never()).save(any(), any());
    }

    @Test
    @DisplayName("saving stores the submitted settings and redirects back with a saved message")
    void shouldSaveSettingsAndRedirect_whenSettingsAreValid() throws Exception {
        allowWrite();
        request.setParameter("method", "configure");
        request.setParameter("providerType", "STUB");
        request.setParameter("enabled", "true");
        request.setParameter("senderNumber", "416-555-1212");
        request.setParameter("webhookSecret", "webhook-value");
        request.setParameter("credentialsProvider", "STUB");
        request.setParameter("credential.field_two", FIELD_INPUT);
        when(configService.credentialFields(SmsProviderType.STUB)).thenReturn(List.of("field_two"));
        when(validator.validate(any(), any())).thenReturn(List.of());

        String result = action().execute();

        assertThat(result).isEqualTo("none");
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/admin/ConfigureSms?result=saved");
        ArgumentCaptor<SmsConfigUpdateDto> update = ArgumentCaptor.forClass(SmsConfigUpdateDto.class);
        verify(configService).save(update.capture(), eq("999998"));
        assertThat(update.getValue())
                .extracting(SmsConfigUpdateDto::providerType, SmsConfigUpdateDto::enabled,
                        SmsConfigUpdateDto::schedulerEnabled, SmsConfigUpdateDto::senderNumber,
                        SmsConfigUpdateDto::webhookSecret, SmsConfigUpdateDto::clearWebhookSecret,
                        SmsConfigUpdateDto::credentials, SmsConfigUpdateDto::clearCredentials)
                .containsExactly(SmsProviderType.STUB, true, false, "416-555-1212", "webhook-value", false,
                        Map.of("field_two", FIELD_INPUT), false);
    }

    @Test
    @DisplayName("saving stores typed credentials when the page showed the fields of the provider being saved")
    void shouldSaveTypedCredentials_whenShownProviderIsSaved() throws Exception {
        allowWrite();
        request.setParameter("method", "configure");
        request.setParameter("providerType", "VOIPMS");
        request.setParameter("credentialsProvider", "VOIPMS");
        request.setParameter("credential.field_one", FIELD_INPUT);
        request.setParameter("clearCredentials", "true");
        when(configService.credentialFields(SmsProviderType.VOIPMS)).thenReturn(List.of("field_one"));
        when(validator.validate(any(), any())).thenReturn(List.of());

        action().execute();

        SmsConfigUpdateDto update = savedUpdate();
        assertThat(update.credentials()).containsExactly(Map.entry("field_one", FIELD_INPUT));
        assertThat(update.clearCredentials()).isTrue();
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/admin/ConfigureSms?result=saved");
    }

    @Test
    @DisplayName("saving after switching provider ignores the typed credentials, saves the rest, and says so")
    void shouldIgnoreTypedCredentials_whenProviderWasSwitched() throws Exception {
        allowWrite();
        request.setParameter("method", "configure");
        request.setParameter("providerType", "CLOUDLI");
        request.setParameter("credentialsProvider", "VOIPMS");
        request.setParameter("enabled", "true");
        request.setParameter("schedulerEnabled", "true");
        request.setParameter("senderNumber", "416-555-1212");
        request.setParameter("webhookSecret", "webhook-value");
        // Same field name as the new provider declares, so only the provider check keeps it out.
        request.setParameter("credential.field_one", FIELD_INPUT);
        request.setParameter("clearCredentials", "true");
        when(configService.credentialFields(SmsProviderType.CLOUDLI)).thenReturn(List.of("field_one"));
        when(validator.validate(any(), any())).thenReturn(List.of());

        action().execute();

        assertThat(savedUpdate())
                .extracting(SmsConfigUpdateDto::providerType, SmsConfigUpdateDto::enabled,
                        SmsConfigUpdateDto::schedulerEnabled, SmsConfigUpdateDto::senderNumber,
                        SmsConfigUpdateDto::webhookSecret, SmsConfigUpdateDto::credentials,
                        SmsConfigUpdateDto::clearCredentials)
                .containsExactly(SmsProviderType.CLOUDLI, true, true, "416-555-1212", "webhook-value", Map.of(),
                        false);
        assertThat(response.getRedirectedUrl())
                .isEqualTo("/carlos/admin/ConfigureSms?result=savedWithoutCredentials");
    }

    @Test
    @DisplayName("saving without the field that names the shown provider ignores typed credentials rather than guessing")
    void shouldIgnoreTypedCredentials_whenShownProviderIsMissing() throws Exception {
        allowWrite();
        request.setParameter("method", "configure");
        request.setParameter("providerType", "VOIPMS");
        request.setParameter("credential.field_one", FIELD_INPUT);
        when(configService.credentialFields(SmsProviderType.VOIPMS)).thenReturn(List.of("field_one"));
        when(validator.validate(any(), any())).thenReturn(List.of());

        action().execute();

        SmsConfigUpdateDto update = savedUpdate();
        assertThat(update.providerType()).isEqualTo(SmsProviderType.VOIPMS);
        assertThat(update.credentials()).isEmpty();
        assertThat(response.getRedirectedUrl())
                .isEqualTo("/carlos/admin/ConfigureSms?result=savedWithoutCredentials");
    }

    @Test
    @DisplayName("saving after switching provider ignores the remove-credentials checkbox, and says so")
    void shouldIgnoreClearCredentials_whenProviderWasSwitched() throws Exception {
        allowWrite();
        request.setParameter("method", "configure");
        request.setParameter("providerType", "CLOUDLI");
        request.setParameter("credentialsProvider", "VOIPMS");
        request.setParameter("clearCredentials", "true");
        when(validator.validate(any(), any())).thenReturn(List.of());

        action().execute();

        assertThat(savedUpdate().clearCredentials()).isFalse();
        assertThat(response.getRedirectedUrl())
                .isEqualTo("/carlos/admin/ConfigureSms?result=savedWithoutCredentials");
    }

    @Test
    @DisplayName("saving after switching provider with no credential typed is an ordinary save")
    void shouldReportSaved_whenProviderWasSwitchedWithNothingTyped() throws Exception {
        allowWrite();
        request.setParameter("method", "configure");
        request.setParameter("providerType", "CLOUDLI");
        request.setParameter("credentialsProvider", "VOIPMS");
        request.setParameter("credential.field_one", " ");
        when(validator.validate(any(), any())).thenReturn(List.of());

        action().execute();

        assertThat(savedUpdate().providerType()).isEqualTo(SmsProviderType.CLOUDLI);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/admin/ConfigureSms?result=saved");
    }

    @Test
    @DisplayName("a switched-provider save stores no typed credential, and none reaches the redirect or the audit")
    void shouldKeepTypedCredentialOutOfStoreAndAudit_whenProviderWasSwitched() throws Exception {
        String originalKey = EncryptionKeyTestSupport.seedFreshKey();
        try {
            SmsConfigDao dao = mock(SmsConfigDao.class);
            SmsConfigAuditRecorder auditRecorder = mock(SmsConfigAuditRecorder.class);
            SmsConfig stored = new SmsConfig();
            stored.setProviderType(SmsProviderType.VOIPMS);
            when(dao.findCurrent()).thenReturn(Optional.of(stored));
            SmsConfigService realService = new SmsConfigService(dao, installed(),
                    mock(ApplicationEventPublisher.class), auditRecorder);
            allowWrite();
            request.setParameter("method", "configure");
            request.setParameter("providerType", "CLOUDLI");
            request.setParameter("credentialsProvider", "VOIPMS");
            request.setParameter("credential.field_one", TYPED_CREDENTIAL);

            new ConfigureSms2Action(securityInfoManager, realService, new SmsConfigValidator(), assembler, sendService)
                    .execute();

            assertThat(stored.getProviderType()).isEqualTo(SmsProviderType.CLOUDLI);
            assertThat(stored.storedCredentials()).isNull();
            assertThat(response.getRedirectedUrl()).doesNotContain(TYPED_CREDENTIAL);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> changed = ArgumentCaptor.forClass(List.class);
            verify(auditRecorder).recordSaved(same(stored), eq("999998"), changed.capture());
            assertThat(changed.getValue()).containsExactly("providerType");
            assertThat(String.join(",", changed.getValue())).doesNotContain(TYPED_CREDENTIAL);
        } finally {
            EncryptionKeyTestSupport.restoreKey(originalKey);
        }
    }

    @Test
    @DisplayName("saving invalid settings re-displays the page (200) with the errors and stores nothing")
    void shouldShowErrors_whenSettingsAreInvalid() throws Exception {
        allowWrite();
        request.setParameter("method", "configure");
        request.setParameter("providerType", "VOIPMS");
        when(validator.validate(any(), any())).thenReturn(List.of("sms.config.error.providerNotInstalled"));
        SmsConfigViewModel model = mock(SmsConfigViewModel.class);
        when(assembler.assembleRejected(any(SmsConfigUpdateDto.class), eq(List.of("sms.config.error.providerNotInstalled"))))
                .thenReturn(model);

        String result = action().execute();

        assertThat(result).isEqualTo("success");
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(request.getAttribute("smsConfig")).isSameAs(model);
        verify(configService, never()).save(any(), any());
    }

    @Test
    @DisplayName("saving that races another admin's save re-displays the page (200) with a conflict message")
    void shouldShowConflict_whenAnotherSaveWonTheRace() throws Exception {
        allowWrite();
        request.setParameter("method", "configure");
        request.setParameter("providerType", "STUB");
        when(validator.validate(any(), any())).thenReturn(List.of());
        doThrow(new SmsConfigConflictException(new IllegalStateException("stale")))
                .when(configService).save(any(), any());
        SmsConfigViewModel model = mock(SmsConfigViewModel.class);
        when(assembler.assemble(null, List.of("sms.config.error.concurrentSave"))).thenReturn(model);

        String result = action().execute();

        assertThat(result).isEqualTo("success");
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(request.getAttribute("smsConfig")).isSameAs(model);
    }

    @Test
    @DisplayName("the system test rejects GET with 405 and sends nothing")
    void shouldRejectSystemTest_whenGet() throws Exception {
        request.setMethod("GET");
        request.setParameter("method", "sendSystemTest");
        request.setParameter("testNumber", "416-555-1212");

        String result = action().execute();

        assertThat(result).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verifyNoInteractions(sendService);
    }

    @Test
    @DisplayName("the system test sends to the typed number as the admin and reports it was sent")
    void shouldReportTestSent_whenSystemTestSucceeds() throws Exception {
        allowWrite();
        request.setParameter("method", "sendSystemTest");
        request.setParameter("testNumber", "416-555-1212");
        when(sendService.sendSystemTest("416-555-1212", "999998", 1001))
                .thenReturn(new SmsSendResultDto(true, SmsStatus.SENT, "stub-1", List.of()));

        action().execute();

        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/admin/ConfigureSms?result=testSent");
    }

    @Test
    @DisplayName("the system test reports it was queued, not sent, when the send rate limit held it back")
    void shouldReportTestQueued_whenSystemTestIsRateLimited() throws Exception {
        allowWrite();
        request.setParameter("method", "sendSystemTest");
        request.setParameter("testNumber", "416-555-1212");
        when(sendService.sendSystemTest("416-555-1212", "999998", 1001)).thenReturn(SmsSendResultDto.queued());

        action().execute();

        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/admin/ConfigureSms?result=testQueued");
    }

    @Test
    @DisplayName("the system test reports when system tests are switched off")
    void shouldReportBlocked_whenSystemTestsAreOff() throws Exception {
        allowWrite();
        request.setParameter("method", "sendSystemTest");
        request.setParameter("testNumber", "416-555-1212");
        when(sendService.sendSystemTest(anyString(), anyString(), any())).thenReturn(SmsSendResultDto.consentBlocked(
                SmsConsentDecisionDto.blocked(SmsStatus.CONSENT_BLOCKED, "SMS_SYSTEM_TEST_DISABLED", "off")));

        action().execute();

        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/admin/ConfigureSms?result=testBlocked");
    }

    @Test
    @DisplayName("the system test reports an invalid number")
    void shouldReportInvalid_whenNumberIsRejected() throws Exception {
        allowWrite();
        request.setParameter("method", "sendSystemTest");
        request.setParameter("testNumber", "nope");
        when(sendService.sendSystemTest(anyString(), anyString(), any()))
                .thenReturn(SmsSendResultDto.validationFailed(List.of("A valid recipient phone number is required.")));

        action().execute();

        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/admin/ConfigureSms?result=testInvalid");
        verify(configService, never()).save(any(), any());
        verify(validator, never()).validate(any(), any());
    }

    private SmsConfigUpdateDto savedUpdate() {
        ArgumentCaptor<SmsConfigUpdateDto> update = ArgumentCaptor.forClass(SmsConfigUpdateDto.class);
        verify(configService).save(update.capture(), eq("999998"));
        return update.getValue();
    }

    /** STUB plus VOIPMS and CLOUDLI clients that both declare {@code field_one}. */
    private static SmsProviderClientResolver installed() {
        List<SmsProviderClient> clients = new ArrayList<>();
        clients.add(new StubSmsProviderClient());
        for (SmsProviderType providerType : List.of(SmsProviderType.VOIPMS, SmsProviderType.CLOUDLI)) {
            clients.add(new StubSmsProviderClient() {
                @Override
                public SmsProviderType providerType() {
                    return providerType;
                }

                @Override
                public List<String> credentialFields() {
                    return List.of("field_one");
                }
            });
        }
        return new SmsProviderClientResolver(clients);
    }

    private void allowWrite() {
        request.setMethod("POST");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.sms", "w", null)).thenReturn(true);
    }

    private ConfigureSms2Action action() {
        return new ConfigureSms2Action(securityInfoManager, configService, validator, assembler, sendService);
    }
}
