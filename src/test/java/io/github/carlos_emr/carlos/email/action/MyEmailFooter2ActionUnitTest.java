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
package io.github.carlos_emr.carlos.email.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailFooterService;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The logged-in user's own email footer page and its save action (follow-up to #3981).
 *
 * @since 2026-10-07
 */
@DisplayName("My email footer view and save actions")
@Tag("unit")
@Tag("fast")
@Tag("email")
@Tag("security")
class MyEmailFooter2ActionUnitTest {

    private static final String PROVIDER = "101";

    private SecurityInfoManager securityInfoManager;
    private EmailFooterService emailFooterService;
    private LoggedInInfo loggedInInfo;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servletActionContext;
    private MockedStatic<LoggedInInfo> loggedInInfoStatic;
    private MockedStatic<LogAction> logAction;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        emailFooterService = mock(EmailFooterService.class);
        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER);
        request = new MockHttpServletRequest("POST", "/email/saveMyEmailFooter");
        request.setContextPath("/carlos");
        response = new MockHttpServletResponse();
        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoStatic = mockStatic(LoggedInInfo.class);
        loggedInInfoStatic.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
        logAction = mockStatic(LogAction.class);
    }

    @AfterEach
    void tearDown() {
        logAction.close();
        loggedInInfoStatic.close();
        servletActionContext.close();
    }

    private SaveMyEmailFooter2Action saveAction() {
        return new SaveMyEmailFooter2Action(securityInfoManager, emailFooterService);
    }

    /** The page showed this clinic footer. */
    private static final String SHOWN = EmailFooterService.fingerprint("Riverside Clinic");

    private void allowEmailWrite() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", "w", null)).thenReturn(true);
    }

    @Test
    @DisplayName("should show the clinic default as the footer of a user who has none of their own")
    void shouldExposeClinicDefault_whenUserFollowsIt() {
        allowEmailWrite();
        when(emailFooterService.settingsFor(PROVIDER)).thenReturn(
                new EmailFooterService.UserFooterSettings(null, "Riverside Clinic", "Dr A footer", false));

        String result = new ViewMyEmailFooter2Action(securityInfoManager, emailFooterService).execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("followsClinicDefault")).isEqualTo(true);
        assertThat(request.getAttribute("myFooter")).isEqualTo("Riverside Clinic");
        assertThat(request.getAttribute("clinicFooter")).isEqualTo("Riverside Clinic");
        assertThat(request.getAttribute("clinicChangeNotice")).isEqualTo("Dr A footer");
        assertThat(request.getAttribute("clinicFooterShownFingerprint")).isEqualTo(EmailFooterService.fingerprint("Riverside Clinic"));
        assertThat(request.getAttribute("clinicChangeKeptOwnFooter")).isEqualTo(false);
    }

    @Test
    @DisplayName("should tell the page when a clinic change kept the user's own footer")
    void shouldExposeKeptOwnFooter_whenReplaceRuleIsOff() {
        allowEmailWrite();
        when(emailFooterService.settingsFor(PROVIDER)).thenReturn(
                new EmailFooterService.UserFooterSettings("Dr A footer", "Riverside Clinic", "Dr A footer", true));

        new ViewMyEmailFooter2Action(securityInfoManager, emailFooterService).execute();

        assertThat(request.getAttribute("clinicChangeKeptOwnFooter")).isEqualTo(true);
        assertThat(request.getAttribute("followsClinicDefault")).isEqualTo(false);
        assertThat(request.getAttribute("myFooter")).isEqualTo("Dr A footer");
    }

    @Test
    @DisplayName("should refuse the footer page to a user who can read email but not send it")
    void shouldThrowSecurityException_whenEmailWriteMissingOnPage() {
        // Read alone would show Save buttons that all end in an error.
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", "r", null)).thenReturn(true);

        assertThatThrownBy(() -> new ViewMyEmailFooter2Action(securityInfoManager, emailFooterService).execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_email)");
        verifyNoInteractions(emailFooterService);
    }

    @Test
    @DisplayName("should refuse a save from a user without _email write")
    void shouldThrowSecurityException_whenEmailWriteMissing() {
        request.addParameter(SaveMyEmailFooter2Action.ACTION_PARAM, "save");

        assertThatThrownBy(() -> saveAction().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_email)");
        verifyNoInteractions(emailFooterService);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("should answer GET and HEAD with 405 and save nothing")
    void shouldRejectUnsafeMethod_withoutSaving(String method) throws Exception {
        allowEmailWrite();
        request.setMethod(method);
        request.addParameter(SaveMyEmailFooter2Action.ACTION_PARAM, "save");
        request.addParameter(SaveMyEmailFooter2Action.FOOTER_PARAM, "Dr A footer");

        assertThat(saveAction().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verifyNoInteractions(emailFooterService);
    }

    @Test
    @DisplayName("should save the session user's own footer, audit it without the text, and redirect")
    void shouldSaveOwnFooter_forSessionUser() throws Exception {
        allowEmailWrite();
        request.addParameter(SaveMyEmailFooter2Action.ACTION_PARAM, "save");
        request.addParameter(SaveMyEmailFooter2Action.FOOTER_PARAM, "Dr A footer");
        request.addParameter(SaveMyEmailFooter2Action.CLINIC_SHOWN_PARAM, SHOWN);
        // A provider number in the request is never used.
        request.addParameter("providerNo", "999");
        when(emailFooterService.saveOwnFooter(PROVIDER, "Dr A footer", SHOWN)).thenReturn(true);

        assertThat(saveAction().execute()).isEqualTo(ActionSupport.NONE);

        verify(emailFooterService).saveOwnFooter(PROVIDER, "Dr A footer", SHOWN);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/email/myEmailFooter?saved=true");
        logAction.verify(() -> LogAction.addLog(eq(PROVIDER), eq("update"), eq("emailFooterOwn"), eq(""), anyString()));
    }

    @Test
    @DisplayName("should run each button on the session user's footer")
    void shouldDispatchEachFooterAction_forSessionUser() throws Exception {
        allowEmailWrite();
        when(emailFooterService.restorePreviousFooter(PROVIDER)).thenReturn(true);
        when(emailFooterService.dismissClinicChangeNotice(PROVIDER)).thenReturn(true);
        for (String footerAction : new String[] {"useClinicDefault", "restorePrevious", "keepCurrent"}) {
            request = new MockHttpServletRequest("POST", "/email/saveMyEmailFooter");
            request.setContextPath("/carlos");
            request.addParameter(SaveMyEmailFooter2Action.ACTION_PARAM, footerAction);
            response = new MockHttpServletResponse();
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            assertThat(saveAction().execute()).as(footerAction).isEqualTo(ActionSupport.NONE);
            assertThat(response.getRedirectedUrl()).as(footerAction).isEqualTo("/carlos/email/myEmailFooter?saved=true");
        }

        verify(emailFooterService).useClinicDefault(PROVIDER);
        verify(emailFooterService).restorePreviousFooter(PROVIDER);
        verify(emailFooterService).dismissClinicChangeNotice(PROVIDER);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"restorePrevious", "keepCurrent"})
    @DisplayName("should not claim a save when there is no clinic-change notice to act on")
    void shouldRedirectWithoutSaved_whenNoNotice(String footerAction) throws Exception {
        allowEmailWrite();
        request.addParameter(SaveMyEmailFooter2Action.ACTION_PARAM, footerAction);

        assertThat(saveAction().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/email/myEmailFooter");
        logAction.verifyNoInteractions();
    }

    @Test
    @DisplayName("should answer a save without the footer field with 400 and save nothing")
    void shouldRejectSave_whenFooterFieldMissing() throws Exception {
        allowEmailWrite();
        request.addParameter(SaveMyEmailFooter2Action.ACTION_PARAM, "save");

        assertThat(saveAction().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(emailFooterService);
    }

    @Test
    @DisplayName("should show a footer over the limit again for editing, with nothing saved")
    void shouldReturnInput_whenFooterTooLong() throws Exception {
        allowEmailWrite();
        UserPropertyDAO dao = mock(UserPropertyDAO.class);
        String tooLong = "x".repeat(EmailData.FOOTER_MAX_LENGTH + 1);
        request.addParameter(SaveMyEmailFooter2Action.ACTION_PARAM, "save");
        request.addParameter(SaveMyEmailFooter2Action.FOOTER_PARAM, tooLong);
        request.addParameter(SaveMyEmailFooter2Action.CLINIC_SHOWN_PARAM, SHOWN);

        String result = new SaveMyEmailFooter2Action(securityInfoManager,
                new EmailFooterService(dao, mock(ProviderDao.class))).execute();

        assertThat(result).isEqualTo(ActionSupport.INPUT);
        assertThat(request.getAttribute("myFooter")).isEqualTo(tooLong);
        assertThat(request.getAttribute("myFooterTooLong")).isEqualTo(true);
        assertThat(request.getAttribute("followsClinicDefault")).isEqualTo(false);
        assertThat(response.getRedirectedUrl()).isNull();
        verify(dao, never()).saveProp(anyString(), anyString(), anyString());
        logAction.verifyNoInteractions();
    }

    @Test
    @DisplayName("should save nothing and show the notice when the clinic changed its footer after the page opened")
    void shouldShowNotice_whenClinicChangedSincePageOpened() throws Exception {
        allowEmailWrite();
        request.addParameter(SaveMyEmailFooter2Action.ACTION_PARAM, "save");
        request.addParameter(SaveMyEmailFooter2Action.FOOTER_PARAM, "Dr A, call 555-0100");
        request.addParameter(SaveMyEmailFooter2Action.CLINIC_SHOWN_PARAM, SHOWN);
        when(emailFooterService.saveOwnFooter(PROVIDER, "Dr A, call 555-0100", SHOWN))
                .thenReturn(false);
        when(emailFooterService.settingsFor(PROVIDER)).thenReturn(
                new EmailFooterService.UserFooterSettings(null, "Call 555-0199", "Call 555-0100", false));

        assertThat(saveAction().execute()).isEqualTo(ActionSupport.INPUT);

        assertThat(request.getAttribute("myFooterChangedSinceShown")).isEqualTo(true);
        assertThat(request.getAttribute("clinicChangeNotice")).isEqualTo("Call 555-0100");
        // The page now carries the notice it shows, so the next save goes through.
        assertThat(request.getAttribute("clinicFooterShownFingerprint")).isEqualTo(EmailFooterService.fingerprint("Call 555-0199"));
        // The status line still says which footer is in use now: the clinic's.
        assertThat(request.getAttribute("followsClinicDefault")).isEqualTo(true);
        assertThat(request.getAttribute("myFooter")).isEqualTo("Dr A, call 555-0100");
        assertThat(response.getRedirectedUrl()).isNull();
        logAction.verifyNoInteractions();
    }

    @ParameterizedTest(name = "clinicFooterShown={0}")
    @NullAndEmptySource
    @ValueSource(strings = {"none", "abc", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdeg"})
    @DisplayName("should refuse a save without a well-formed fingerprint of the clinic footer the page showed")
    void shouldRejectSave_whenClinicFingerprintMissingOrMalformed(String token) throws Exception {
        allowEmailWrite();
        request.addParameter(SaveMyEmailFooter2Action.ACTION_PARAM, "save");
        request.addParameter(SaveMyEmailFooter2Action.FOOTER_PARAM, "Dr A footer");
        if (token != null) {
            request.addParameter(SaveMyEmailFooter2Action.CLINIC_SHOWN_PARAM, token);
        }

        assertThat(saveAction().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(400);
        verify(emailFooterService, never()).saveOwnFooter(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("should keep the typed footer and ask to try again when the save collided with another")
    void shouldAskToRetry_whenSaveCollides() throws Exception {
        allowEmailWrite();
        request.addParameter(SaveMyEmailFooter2Action.ACTION_PARAM, "save");
        request.addParameter(SaveMyEmailFooter2Action.FOOTER_PARAM, "Dr A new footer");
        request.addParameter(SaveMyEmailFooter2Action.CLINIC_SHOWN_PARAM, SHOWN);
        doThrow(new ObjectOptimisticLockingFailureException("UserProperty", 7))
                .when(emailFooterService).saveOwnFooter(PROVIDER, "Dr A new footer", SHOWN);
        when(emailFooterService.settingsFor(PROVIDER)).thenReturn(
                new EmailFooterService.UserFooterSettings("Dr A footer", "Riverside Clinic", null, false));

        assertThat(saveAction().execute()).isEqualTo(ActionSupport.INPUT);

        assertThat(request.getAttribute("myFooterSaveConflict")).isEqualTo(true);
        assertThat(request.getAttribute("myFooter")).isEqualTo("Dr A new footer");
        assertThat(request.getAttribute("followsClinicDefault")).isEqualTo(false);
        assertThat(response.getRedirectedUrl()).isNull();
        logAction.verifyNoInteractions();
    }

    @Test
    @DisplayName("should show the page as stored and ask to try again when a button's change collided")
    void shouldShowStoredFooter_whenButtonChangeCollides() throws Exception {
        allowEmailWrite();
        request.addParameter(SaveMyEmailFooter2Action.ACTION_PARAM, "useClinicDefault");
        doThrow(new ObjectOptimisticLockingFailureException("UserProperty", 7))
                .when(emailFooterService).useClinicDefault(PROVIDER);
        when(emailFooterService.settingsFor(PROVIDER)).thenReturn(
                new EmailFooterService.UserFooterSettings("Dr A footer", "Riverside Clinic", null, false));

        assertThat(saveAction().execute()).isEqualTo(ActionSupport.INPUT);

        assertThat(request.getAttribute("myFooterSaveConflict")).isEqualTo(true);
        assertThat(request.getAttribute("myFooter")).isEqualTo("Dr A footer");
        logAction.verifyNoInteractions();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"", "deleteAll"})
    @DisplayName("should answer an unknown button with 400 and change nothing")
    void shouldRejectUnknownFooterAction_withBadRequest(String footerAction) throws Exception {
        allowEmailWrite();
        request.addParameter(SaveMyEmailFooter2Action.ACTION_PARAM, footerAction);

        assertThat(saveAction().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(emailFooterService);
    }

    @Test
    @DisplayName("should answer a missing button with 400 and change nothing")
    void shouldRejectMissingFooterAction_withBadRequest() throws Exception {
        allowEmailWrite();

        assertThat(saveAction().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(emailFooterService);
    }
}
