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
package io.github.carlos_emr.carlos.email.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.commn.model.EmailFooterLogo;
import io.github.carlos_emr.carlos.email.core.EmailFooterLogoService;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Saving the clinic's default email footer (issue #4093, follow-up to #3981).
 *
 * @since 2026-10-07
 */
@DisplayName("SaveClinicEmailFooter2Action")
@Tag("unit")
@Tag("fast")
@Tag("email")
@Tag("security")
class SaveClinicEmailFooter2ActionUnitTest {

    private SecurityInfoManager securityInfoManager;
    private EmailFooterService emailFooterService;
    private EmailFooterLogoService logoService;
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
        logoService = mock(EmailFooterLogoService.class);
        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        request = new MockHttpServletRequest("POST", "/admin/saveClinicEmailFooter");
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

    /** The fingerprint the page sent: it showed "Riverside Clinic". */
    private static final String SHOWN = EmailFooterService.fingerprint("Riverside Clinic");

    private SaveClinicEmailFooter2Action action() {
        return new SaveClinicEmailFooter2Action(securityInfoManager, emailFooterService, logoService);
    }

    private void post(String footer) {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(true);
        request.addParameter(SaveClinicEmailFooter2Action.FOOTER_PARAM, footer);
        request.addParameter(SaveClinicEmailFooter2Action.FINGERPRINT_PARAM, SHOWN);
    }

    @Test
    @DisplayName("should refuse a user without _admin write before reading the footer")
    void shouldThrowSecurityException_whenAdminWriteMissing() {
        request.addParameter(SaveClinicEmailFooter2Action.FOOTER_PARAM, "Riverside Clinic");

        assertThatThrownBy(() -> action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin)");
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_admin", "w", null);
        verifyNoInteractions(emailFooterService);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("should answer GET and HEAD with 405 and save nothing")
    void shouldRejectUnsafeMethod_withoutSaving(String method) throws Exception {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(true);
        request.setMethod(method);
        request.addParameter(SaveClinicEmailFooter2Action.FOOTER_PARAM, "Riverside Clinic");

        assertThat(action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verifyNoInteractions(emailFooterService);
    }

    @Test
    @DisplayName("should save the footer, audit who changed it and how many users were told, and go back to the page")
    void shouldSaveAndRedirect_whenPostedByAdmin() throws Exception {
        post("Riverside Clinic\r\nBook online");
        when(emailFooterService.saveClinicDefault("Riverside Clinic\r\nBook online", SHOWN))
                .thenReturn(new EmailFooterService.ClinicDefaultSaved(EmailFooterService.ClinicDefaultOutcome.CHANGED, 2));

        assertThat(action().execute()).isEqualTo(ActionSupport.NONE);

        verify(emailFooterService).saveClinicDefault("Riverside Clinic\r\nBook online", SHOWN);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/admin/ViewConfigureEmail?clinicFooterSaved=true");
        // The audit holds the count, never the footer text.
        logAction.verify(() -> LogAction.addLog(eq("999998"), eq("update"), eq("emailFooterClinicDefault"), eq(""),
                anyString(), isNull(), eq("noticed=2")));
    }

    @Test
    @DisplayName("should neither audit nor claim a save that left the clinic footer unchanged")
    void shouldSkipAudit_whenClinicFooterUnchanged() throws Exception {
        post("Riverside Clinic");
        when(emailFooterService.saveClinicDefault("Riverside Clinic", SHOWN))
                .thenReturn(new EmailFooterService.ClinicDefaultSaved(EmailFooterService.ClinicDefaultOutcome.UNCHANGED, 0));

        assertThat(action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/admin/ViewConfigureEmail?clinicFooterUnchanged=true");
        logAction.verifyNoInteractions();
    }

    @Test
    @DisplayName("should answer a post without the footer field with 400 and change nothing")
    void shouldRejectPost_whenFooterFieldMissing() throws Exception {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(true);
        request.addParameter(SaveClinicEmailFooter2Action.FINGERPRINT_PARAM, SHOWN);

        assertThat(action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(emailFooterService);
        logAction.verifyNoInteractions();
    }

    @ParameterizedTest(name = "fingerprint \"{0}\"")
    @ValueSource(strings = {"", "abc", "ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ", "missing"})
    @DisplayName("should answer a post without a well-formed fingerprint of the shown footer with 400")
    void shouldRejectPost_whenFingerprintMissingOrMalformed(String fingerprint) throws Exception {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(true);
        request.addParameter(SaveClinicEmailFooter2Action.FOOTER_PARAM, "Riverside Clinic");
        if (!"missing".equals(fingerprint)) {
            request.addParameter(SaveClinicEmailFooter2Action.FINGERPRINT_PARAM, fingerprint);
        }

        assertThat(action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(emailFooterService);
    }

    @Test
    @DisplayName("should show the current footer and keep the admin's text when the footer changed since the page opened")
    void shouldShowCurrentFooter_whenChangedSincePageOpened() throws Exception {
        post("Edited footer");
        when(emailFooterService.saveClinicDefault("Edited footer", SHOWN))
                .thenReturn(new EmailFooterService.ClinicDefaultSaved(
                        EmailFooterService.ClinicDefaultOutcome.CHANGED_SINCE_SHOWN, 0));
        when(emailFooterService.clinicDefault()).thenReturn("Footer saved by someone else");
        when(emailFooterService.ownFootersReplacedOnClinicChange()).thenReturn(true);

        assertThat(action().execute()).isEqualTo(ActionSupport.INPUT);

        assertThat(request.getAttribute("clinicFooterChangedSinceShown")).isEqualTo(true);
        assertThat(request.getAttribute("clinicFooter")).isEqualTo("Edited footer");
        assertThat(request.getAttribute("clinicFooterCurrent")).isEqualTo("Footer saved by someone else");
        // The form now carries the current footer's fingerprint, so saving again goes through.
        assertThat(request.getAttribute("clinicFooterFingerprint"))
                .isEqualTo(EmailFooterService.fingerprint("Footer saved by someone else"));
        assertThat(request.getAttribute("ownFootersReplacedOnClinicChange")).isEqualTo(true);
        assertThat(response.getRedirectedUrl()).isNull();
        logAction.verifyNoInteractions();
    }

    @Test
    @DisplayName("should ask the admin to try again when the save collided with another")
    void shouldAskToRetry_whenSaveCollides() throws Exception {
        post("Edited footer");
        when(emailFooterService.saveClinicDefault("Edited footer", SHOWN))
                .thenThrow(new CannotAcquireLockException("Deadlock found when trying to get lock"));

        assertThat(action().execute()).isEqualTo(ActionSupport.INPUT);

        assertThat(request.getAttribute("clinicFooterSaveConflict")).isEqualTo(true);
        assertThat(request.getAttribute("clinicFooter")).isEqualTo("Edited footer");
        assertThat(request.getAttribute("clinicFooterFingerprint")).isEqualTo(SHOWN);
        assertThat(response.getRedirectedUrl()).isNull();
        logAction.verifyNoInteractions();
    }

    @Test
    @DisplayName("should show a footer over the limit again for editing, with nothing saved or audited")
    void shouldReturnInput_whenFooterTooLong() throws Exception {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(true);
        UserPropertyDAO dao = mock(UserPropertyDAO.class);
        String tooLong = "x".repeat(EmailData.FOOTER_MAX_LENGTH + 1);
        request.addParameter(SaveClinicEmailFooter2Action.FOOTER_PARAM, tooLong);
        request.addParameter(SaveClinicEmailFooter2Action.FINGERPRINT_PARAM, SHOWN);

        EmailFooterLogo logo = new EmailFooterLogo();
        logo.setWidth(320);
        logo.setHeight(80);
        when(logoService.currentLogo()).thenReturn(logo);

        String result = new SaveClinicEmailFooter2Action(securityInfoManager,
                new EmailFooterService(dao, mock(ProviderDao.class)), logoService).execute();

        assertThat(result).isEqualTo(ActionSupport.INPUT);
        assertThat(request.getAttribute("clinicFooter")).isEqualTo(tooLong);
        assertThat(request.getAttribute("clinicFooterTooLong")).isEqualTo(true);
        // Still the footer the page first showed, so the next save checks against it.
        assertThat(request.getAttribute("clinicFooterFingerprint")).isEqualTo(SHOWN);
        assertThat(request.getAttribute("ownFootersReplacedOnClinicChange")).isEqualTo(true);
        // The page renders straight from here: its logo card still shows the clinic's logo.
        assertThat(request.getAttribute("clinicLogoSet")).isEqualTo(true);
        assertThat(request.getAttribute("clinicLogoWidth")).isEqualTo(320);
        assertThat(response.getRedirectedUrl()).isNull();
        verifyNoInteractions(dao);
        logAction.verifyNoInteractions();
    }
}
