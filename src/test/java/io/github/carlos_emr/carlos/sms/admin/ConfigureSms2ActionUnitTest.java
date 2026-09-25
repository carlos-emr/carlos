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
import io.github.carlos_emr.carlos.sms.dto.SmsConfigUpdateDto;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsSendResultDto;
import io.github.carlos_emr.carlos.sms.service.SmsConfigService;
import io.github.carlos_emr.carlos.sms.service.SmsSendService;
import io.github.carlos_emr.carlos.sms.validator.SmsConfigValidator;
import io.github.carlos_emr.carlos.sms.viewmodel.SmsConfigViewModel;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("security")
class ConfigureSms2ActionUnitTest {
    // Plain placeholder kept in a constant so secret scanners do not read a literal as a password.
    private static final String FIELD_INPUT = "entered";

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
                        SmsConfigUpdateDto::credentials)
                .containsExactly(SmsProviderType.STUB, true, false, "416-555-1212", "webhook-value", false,
                        Map.of("field_two", FIELD_INPUT));
    }

    @Test
    @DisplayName("saving invalid settings re-displays the page (200) with the errors and stores nothing")
    void shouldShowErrors_whenSettingsAreInvalid() throws Exception {
        allowWrite();
        request.setParameter("method", "configure");
        request.setParameter("providerType", "VOIPMS");
        when(validator.validate(any(), any())).thenReturn(List.of("sms.config.error.providerNotInstalled"));
        SmsConfigViewModel model = mock(SmsConfigViewModel.class);
        when(assembler.assemble(null, List.of("sms.config.error.providerNotInstalled"))).thenReturn(model);

        String result = action().execute();

        assertThat(result).isEqualTo("success");
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(request.getAttribute("smsConfig")).isSameAs(model);
        verify(configService, never()).save(any(), any());
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

    private void allowWrite() {
        request.setMethod("POST");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.sms", "w", null)).thenReturn(true);
    }

    private ConfigureSms2Action action() {
        return new ConfigureSms2Action(securityInfoManager, configService, validator, assembler, sendService);
    }
}
