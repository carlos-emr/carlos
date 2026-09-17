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
package io.github.carlos_emr.carlos.fax.action;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link Fax2Action#cancel()} covering the flush-failure UX fix.
 *
 * <p>Before the fix, a failed {@code faxManager.flush(...)} call only recorded an
 * {@code addActionError} and then redirected away from the preview page — the redirect discarded
 * the action error, so the user landed on the destination page with no indication that the
 * preview cache / temporary file cleanup had failed. The fix skips the redirect on flush failure,
 * returns to the "preview" result, and sets a {@code faxCleanupFailed} request attribute so
 * CoverPage.jsp can render the failure.</p>
 */
@DisplayName("Fax2Action cancel() flush-failure UX unit tests")
@Tag("unit")
@Tag("fast")
class Fax2ActionCancelUnitTest extends CarlosUnitTestBase {

    private static final String APP_TEMP_ROOT =
            java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"), "carlos-temp").toString();

    private FaxManager faxManager;
    private DocumentAttachmentManager documentAttachmentManager;
    private SecurityInfoManager securityInfoManager;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private void setUpCommonMocks() {
        faxManager = mock(FaxManager.class);
        documentAttachmentManager = mock(DocumentAttachmentManager.class);
        securityInfoManager = mock(SecurityInfoManager.class);

        request = new MockHttpServletRequest();
        LoggedInInfo info = mock(LoggedInInfo.class);
        when(info.getLoggedInProviderNo()).thenReturn("999998");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), info);
        response = new MockHttpServletResponse();

        // cancel() gates on _fax read before touching the flush/redirect flow.
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq("r"), isNull()))
                .thenReturn(true);

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);
    }

    @Test
    @DisplayName("should throw SecurityException when the fax read privilege is missing")
    void shouldThrowSecurityException_whenFaxReadPrivilegeMissing() {
        setUpCommonMocks();
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq("r"), isNull()))
                .thenReturn(false);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.setTransactionType("CONSULTATION");
            action.setFaxFilePath(APP_TEMP_ROOT + "/fax.pdf");

            assertThatThrownBy(action::cancel)
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("missing required sec object (_fax)");
            // The privilege gate fires before any flush/deletion side effect.
            verify(faxManager, never()).flush(any(), anyString());
        }
    }

    @Test
    @DisplayName("should return preview with faxCleanupFailed set and skip the redirect when flush fails")
    void shouldReturnPreviewWithCleanupFailedAttribute_whenFlushFails() {
        setUpCommonMocks();
        when(faxManager.flush(any(LoggedInInfo.class), anyString())).thenReturn(false);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.setTransactionType("CONSULTATION");
            action.setFaxFilePath(APP_TEMP_ROOT + "/fax.pdf");
            action.setTransactionId(55);
            action.setDemographicNo(10);
            claim(55, 10, "999998");

            String result = action.cancel();

            assertThat(result).isEqualTo("preview");
            assertThat(request.getAttribute("faxCleanupFailed")).isEqualTo(Boolean.TRUE);
            // The user must never see the CONSULTATION redirect after a failed cleanup: the
            // pre-fix code recorded an addActionError but redirected anyway, silently discarding it.
            assertThat(response.getRedirectedUrl()).isNull();
        }
    }

    @Test
    @DisplayName("should still redirect to the consultation view when flush succeeds")
    void shouldRedirectToConsultationView_whenFlushSucceeds() {
        setUpCommonMocks();
        when(faxManager.flush(any(LoggedInInfo.class), anyString())).thenReturn(true);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.setTransactionType("CONSULTATION");
            action.setFaxFilePath(APP_TEMP_ROOT + "/fax.pdf");
            action.setTransactionId(55);
            action.setDemographicNo(10);
            claim(55, 10, "999998");

            String result = action.cancel();

            assertThat(result).isEqualTo(Fax2Action.NONE);
            assertThat(request.getAttribute("faxCleanupFailed")).isNull();
            assertThat(response.getRedirectedUrl()).contains("/encounter/ViewRequest");
        }
    }

    @Test
    @DisplayName("should still redirect to the eform view when flush succeeds")
    void shouldRedirectToEformView_whenFlushSucceeds() {
        setUpCommonMocks();
        when(faxManager.flush(any(LoggedInInfo.class), anyString())).thenReturn(true);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.setTransactionType("EFORM");
            action.setFaxFilePath(APP_TEMP_ROOT + "/fax.pdf");
            action.setTransactionId(77);
            action.setDemographicNo(10);
            claim(77, 10, "999998");

            String result = action.cancel();

            assertThat(result).isEqualTo(Fax2Action.NONE);
            assertThat(request.getAttribute("faxCleanupFailed")).isNull();
            assertThat(response.getRedirectedUrl()).contains("/eform/efmshowform_data");
        }
    }

    @Test
    @DisplayName("should return preview with faxCleanupFailed set for an EFORM cancel when flush fails")
    void shouldReturnPreviewWithCleanupFailedAttribute_forEformWhenFlushFails() {
        setUpCommonMocks();
        when(faxManager.flush(any(LoggedInInfo.class), anyString())).thenReturn(false);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.setTransactionType("EFORM");
            action.setFaxFilePath(APP_TEMP_ROOT + "/fax.pdf");
            action.setTransactionId(77);
            action.setDemographicNo(10);
            claim(77, 10, "999998");

            String result = action.cancel();

            assertThat(result).isEqualTo("preview");
            assertThat(request.getAttribute("faxCleanupFailed")).isEqualTo(Boolean.TRUE);
            assertThat(response.getRedirectedUrl()).isNull();
        }
    }
    private java.util.Map<String, Fax2Action.FaxPreviewClaim> claim(int eform, int patient, String provider) {
        var claims = new java.util.concurrent.ConcurrentHashMap<String, Fax2Action.FaxPreviewClaim>();
        claims.put(APP_TEMP_ROOT + "/fax.pdf", new Fax2Action.FaxPreviewClaim(eform, patient, provider));
        request.getSession().setAttribute(Fax2Action.CLAIMED_FAX_FILE_PATHS_SESSION_KEY, claims);
        return claims;
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"77,11,999998", "78,10,999998", "77,10,999997"})
    @DisplayName("should reject cancellation when any preview ownership binding differs")
    void shouldRejectCancellation_whenClaimBindingDiffers(int eform, int patient, String provider) {
        setUpCommonMocks();
        var claims = claim(eform, patient, provider);
        try (var context = mockStatic(ServletActionContext.class)) {
            context.when(ServletActionContext::getRequest).thenReturn(request);
            context.when(ServletActionContext::getResponse).thenReturn(response);
            var action = eformCancel();
            assertThat(action.cancel()).isEqualTo(Fax2Action.NONE);
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(claims.get(APP_TEMP_ROOT + "/fax.pdf").cancelled()).isFalse();
            org.mockito.Mockito.verifyNoInteractions(faxManager);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"/unclaimed-preview.pdf", "/var/lib/carlos/document/other-patient.pdf"})
    @DisplayName("should reject unclaimed paths without validating or flushing another document")
    void shouldRejectCancellation_whenPathIsUnclaimed(String path) {
        setUpCommonMocks();
        var claims = claim(77, 10, "999998");
        try (var context = mockStatic(ServletActionContext.class)) {
            context.when(ServletActionContext::getRequest).thenReturn(request);
            context.when(ServletActionContext::getResponse).thenReturn(response);
            var action = eformCancel();
            action.setFaxFilePath(path);
            assertThat(action.cancel()).isEqualTo(Fax2Action.NONE);
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(claims).hasSize(1);
            org.mockito.Mockito.verifyNoInteractions(faxManager);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    @DisplayName("should retain failed cancellation for cleanup retry but never for queueing")
    void shouldAllowCleanupRetryWithoutQueueing_whenFlushFails(boolean throwsFailure) {
        setUpCommonMocks();
        var claims = claim(77, 10, "999998");
        claims.put("other-owned-preview", new Fax2Action.FaxPreviewClaim(88, 11, "999998"));
        if (throwsFailure) {
            when(faxManager.flush(any(), anyString())).thenThrow(new IllegalStateException("PRIVATE_CLINICAL_TEXT")).thenReturn(true);
        } else {
            when(faxManager.flush(any(), anyString())).thenReturn(false, true);
        }
        try (var context = mockStatic(ServletActionContext.class);
             var logs = io.github.carlos_emr.carlos.test.logging.LogCapture.forLogger(Fax2Action.class)) {
            context.when(ServletActionContext::getRequest).thenReturn(request);
            context.when(ServletActionContext::getResponse).thenReturn(response);
            var action = eformCancel();
            assertThat(action.cancel()).isEqualTo("preview");
            assertThat(claims.get(APP_TEMP_ROOT + "/fax.pdf").cancelled()).isTrue();
            assertThat((Object) org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                    action, "consumeClaimedFaxFilePathFromSession")).isNull();
            assertThat(action.cancel()).isEqualTo(Fax2Action.NONE);
            assertThat(claims).containsOnlyKeys("other-owned-preview");
            assertThat(logs.events()).allSatisfy(event -> {
                assertThat(event.getMessage().getFormattedMessage()).doesNotContain("PRIVATE_CLINICAL_TEXT");
                assertThat(event.getThrown()).isNull();
            });
        }
    }

    @Test
    @DisplayName("should leave a queued source untouched when its preview claim was consumed")
    void shouldRejectCancellation_whenQueueAlreadyConsumedClaim() {
        setUpCommonMocks();
        claim(77, 10, "999998");
        try (var context = mockStatic(ServletActionContext.class)) {
            context.when(ServletActionContext::getRequest).thenReturn(request);
            context.when(ServletActionContext::getResponse).thenReturn(response);
            var action = eformCancel();
            assertThat((String) org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                    action, "consumeClaimedFaxFilePathFromSession")).isEqualTo(APP_TEMP_ROOT + "/fax.pdf");
            assertThat(action.cancel()).isEqualTo(Fax2Action.NONE);
            assertThat(response.getStatus()).isEqualTo(403);
            org.mockito.Mockito.verifyNoInteractions(faxManager);
        }
    }

    private Fax2Action eformCancel() {
        var action = new Fax2Action();
        action.setTransactionType("EFORM");
        action.setTransactionId(77);
        action.setDemographicNo(10);
        action.setFaxFilePath(APP_TEMP_ROOT + "/fax.pdf");
        return action;
    }
}
