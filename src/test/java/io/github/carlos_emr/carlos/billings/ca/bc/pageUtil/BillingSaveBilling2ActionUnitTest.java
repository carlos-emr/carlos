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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import java.util.List;
import java.util.Locale;

import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.apache.logging.log4j.Level;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for BC billing-save request guards and direct-response failure contracts.
 *
 * <p>The action writes billing records, appointment updates, and archive rows, so these tests pin
 * the pre-write rejection paths that keep malformed or unauthenticated requests from mutating
 * billing state.</p>
 */
@DisplayName("BillingSaveBilling2Action")
@Tag("unit")
@Tag("billing")
class BillingSaveBilling2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private AutoCloseable mockitoCloseable;

    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private AppointmentArchiveDao appointmentArchiveDao;
    @Mock private OscarAppointmentDao appointmentDao;
    @Mock private BillingmasterDAO billingmasterDAO;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(AppointmentArchiveDao.class, appointmentArchiveDao);
        registerMock(OscarAppointmentDao.class, appointmentDao);
        registerMock(BillingmasterDAO.class, billingmasterDAO);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    @DisplayName("should include context path when building receipt redirect")
    void shouldIncludeContextPath_whenBuildingReceiptRedirect() {
        String redirectUrl = BillingSaveBilling2Action.receiptRedirectUrl(
                "/carlos", List.of("101", "102"));

        assertThat(redirectUrl)
                .isEqualTo("/carlos/billing/CA/BC/billingView?billing_no=101&billing_no=102&receipt=yes");
    }

    @Test
    @DisplayName("should use root-relative billing route when context path is empty")
    void shouldUseRootRelativeBillingRoute_whenContextPathIsEmpty() {
        String redirectUrl = BillingSaveBilling2Action.receiptRedirectUrl("", List.of("101"));

        assertThat(redirectUrl)
                .isEqualTo("/billing/CA/BC/billingView?billing_no=101&receipt=yes");
    }

    @Test
    @DisplayName("should reject GET before billing writes")
    void shouldRejectGetBeforeBillingWrites_whenSavingBilling() throws Exception {
        request.setMethod("GET");

        String result = new BillingSaveBilling2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(securityInfoManager, billingmasterDAO, appointmentDao, appointmentArchiveDao);
    }

    @Test
    @DisplayName("should reject HEAD before billing writes")
    void shouldRejectHeadBeforeBillingWrites_whenSavingBilling() throws Exception {
        request.setMethod("HEAD");

        String result = new BillingSaveBilling2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(securityInfoManager, billingmasterDAO, appointmentDao, appointmentArchiveDao);
    }

    @Test
    @DisplayName("should redirect unauthenticated request when session user attribute is missing")
    void shouldRedirectUnauthenticatedRequest_whenSessionUserAttributeIsMissing() throws Exception {
        request.setMethod("POST");

        String result = new BillingSaveBilling2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("/logoutPage");
        verifyNoInteractions(securityInfoManager, billingmasterDAO, appointmentDao, appointmentArchiveDao);
    }

    @Test
    @DisplayName("should send bad request when billing session bean is missing")
    void shouldSendBadRequest_whenBillingSessionBeanIsMissing() throws Exception {
        request.setMethod("POST");
        request.getSession().setAttribute("user", "100");
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                    .thenReturn(true);

            String result = new BillingSaveBilling2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getContentType()).isEqualTo("text/plain;charset=UTF-8");
            assertThat(response.getContentAsString()).isEqualTo("Billing session expired.");
            verifyNoInteractions(billingmasterDAO, appointmentDao, appointmentArchiveDao);
        }
    }

    @Test
    @DisplayName("should localize expired billing session when request locale is French")
    void shouldLocalizeExpiredBillingSession_whenRequestLocaleIsFrench() throws Exception {
        request.setMethod("POST");
        request.addPreferredLocale(Locale.FRENCH);
        request.getSession().setAttribute("user", "100");
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                    .thenReturn(true);

            String result = new BillingSaveBilling2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getContentType()).isEqualTo("text/plain;charset=UTF-8");
            assertThat(response.getContentAsString()).isEqualTo("La session de facturation a expir\u00e9.");
            verifyNoInteractions(billingmasterDAO, appointmentDao, appointmentArchiveDao);
        }
    }

    @Test
    @DisplayName("should propagate IOException when expired session response body write fails")
    void shouldPropagateIOException_whenExpiredSessionResponseBodyWriteFails() throws Exception {
        request.setMethod("POST");
        request.getSession().setAttribute("user", "100");
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        IOException writeFailure = new IOException("client disconnected");
        HttpServletResponse throwingResponse = mock(HttpServletResponse.class);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(throwingResponse);
        when(throwingResponse.getWriter()).thenThrow(new IllegalStateException("writer already used"));
        when(throwingResponse.getOutputStream()).thenReturn(failingOutputStream(writeFailure));

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                    .thenReturn(true);

            assertThatThrownBy(() -> new BillingSaveBilling2Action().execute())
                    .isSameAs(writeFailure);
            verifyNoInteractions(billingmasterDAO, appointmentDao, appointmentArchiveDao);
        }
    }

    @Test
    @DisplayName("should write expired session body after output stream obtained")
    void shouldWriteExpiredSessionBody_whenOutputStreamWasAlreadyObtained() throws Exception {
        request.setMethod("POST");
        request.getSession().setAttribute("user", "100");
        response.getOutputStream();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class);
             LogCapture capture = LogCapture.forLogger(BillingSaveBilling2Action.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                    .thenReturn(true);

            String result = new BillingSaveBilling2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getContentType()).isEqualTo("text/plain;charset=UTF-8");
            assertThat(response.getContentAsString()).isEqualTo("Billing session expired.");
            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("Response writer unavailable")
                        .contains("falling back to output stream");
            });
            verifyNoInteractions(billingmasterDAO, appointmentDao, appointmentArchiveDao);
        }
    }

    @Test
    @DisplayName("should reject malformed appointment number before billing writes")
    void shouldRejectMalformedAppointmentNumber_beforeBillingWrites() throws Exception {
        request.setMethod("POST");
        request.getSession().setAttribute("user", "100");
        BillingSessionBean bean = minimalBillingSessionBean("bad\r\nvalue");
        bean.getBillItem().add(mock(BillingBillingManager.BillingItem.class));
        request.getSession().setAttribute("billingSessionBean", bean);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("100");

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                    .thenReturn(true);

            String result = new BillingSaveBilling2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getContentType()).isEqualTo("text/plain;charset=UTF-8");
            assertThat(response.getContentAsString()).isEqualTo("Malformed appointment number \"badvalue\". "
                    + "Please return to billing and re-select the appointment.");
            assertThat(bean.getApptNo()).isEqualTo("bad\r\nvalue");
            verifyNoInteractions(appointmentDao, appointmentArchiveDao, billingmasterDAO);
        }
    }

    @Test
    @DisplayName("should reject malformed appointment number after output stream obtained")
    void shouldRejectMalformedAppointmentNumber_whenOutputStreamWasAlreadyObtained() throws Exception {
        request.setMethod("POST");
        request.getSession().setAttribute("user", "100");
        BillingSessionBean bean = minimalBillingSessionBean("bad-value");
        bean.getBillItem().add(mock(BillingBillingManager.BillingItem.class));
        request.getSession().setAttribute("billingSessionBean", bean);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("100");
        response.getOutputStream();

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                    .thenReturn(true);

            String result;
            try (LogCapture capture = LogCapture.forLogger(BillingSaveBilling2Action.class)) {
                result = new BillingSaveBilling2Action().execute();

                assertThat(capture.events()).anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                    assertThat(event.getMessage().getFormattedMessage())
                            .contains("Response writer unavailable")
                            .contains("falling back to output stream")
                            .contains("uri=");
                });
            }

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getContentType()).isEqualTo("text/plain;charset=UTF-8");
            assertThat(response.getContentAsString()).isEqualTo("Malformed appointment number \"bad-value\". "
                    + "Please return to billing and re-select the appointment.");
            verifyNoInteractions(appointmentDao, appointmentArchiveDao, billingmasterDAO);
        }
    }

    @Test
    @DisplayName("should localize malformed appointment number when request locale is French")
    void shouldLocalizeMalformedAppointmentNumber_whenRequestLocaleIsFrench() throws Exception {
        request.setMethod("POST");
        request.addPreferredLocale(Locale.FRENCH);
        request.getSession().setAttribute("user", "100");
        BillingSessionBean bean = minimalBillingSessionBean("not-a-number");
        bean.getBillItem().add(mock(BillingBillingManager.BillingItem.class));
        request.getSession().setAttribute("billingSessionBean", bean);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("100");

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                    .thenReturn(true);

            String result = new BillingSaveBilling2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getContentType()).isEqualTo("text/plain;charset=UTF-8");
            assertThat(response.getContentAsString()).isEqualTo("Num\u00e9ro de rendez-vous non valide "
                    + "\"not-a-number\". Veuillez retourner \u00e0 la facturation et s\u00e9lectionner "
                    + "de nouveau le rendez-vous.");
            verifyNoInteractions(appointmentDao, appointmentArchiveDao, billingmasterDAO);
        }
    }

    @Test
    @DisplayName("should substitute appointment number when translated pattern has apostrophe")
    void shouldSubstituteAppointmentNumber_whenTranslatedPatternHasApostrophe() {
        assertThat(BillingSaveBilling2Action.formatMalformedAppointmentMessage(
                "L'identifiant de rendez-vous \"{0}\" est invalide.", "abc123"))
                .isEqualTo("L'identifiant de rendez-vous \"abc123\" est invalide.");
    }

    @Test
    @DisplayName("should preserve already escaped apostrophe in translated pattern")
    void shouldPreserveAlreadyEscapedApostrophe_whenTranslatedPatternIsValid() {
        assertThat(BillingSaveBilling2Action.formatMalformedAppointmentMessage(
                "L''identifiant de rendez-vous \"{0}\" est invalide.", "abc123"))
                .isEqualTo("L'identifiant de rendez-vous \"abc123\" est invalide.");
    }

    @Test
    @DisplayName("should not warn when appointment number contains placeholder text")
    void shouldNotWarn_whenAppointmentNumberContainsPlaceholderText() {
        try (LogCapture capture = LogCapture.forLogger(BillingSaveBilling2Action.class)) {
            assertThat(BillingSaveBilling2Action.formatMalformedAppointmentMessage(
                    "Malformed appointment number \"{0}\".", "bad-{0}"))
                    .isEqualTo("Malformed appointment number \"bad-{0}\".");

            assertThat(capture.events()).noneSatisfy(event -> assertThat(event.getMessage()
                    .getFormattedMessage()).contains("placeholder unsubstituted"));
        }
    }

    @Test
    @DisplayName("should warn when message pattern leaves placeholder unsubstituted")
    void shouldWarn_whenMessagePatternLeavesPlaceholderUnsubstituted() {
        try (LogCapture capture = LogCapture.forLogger(BillingSaveBilling2Action.class)) {
            assertThat(BillingSaveBilling2Action.formatMalformedAppointmentMessage(
                    "Malformed appointment number '{0}'.", "abc123"))
                    .isEqualTo("Malformed appointment number 'abc123'.");

            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("retrying with escaped single quotes");
            });
        }
    }

    @Test
    @DisplayName("should warn and use fallback when message pattern is invalid")
    void shouldWarnAndUseFallback_whenMessagePatternIsInvalid() {
        try (LogCapture capture = LogCapture.forLogger(BillingSaveBilling2Action.class)) {
            assertThat(BillingSaveBilling2Action.formatMalformedAppointmentMessage(
                    "Malformed appointment number \"{O}\".", "abc123"))
                    .isEqualTo("Malformed appointment number \"abc123\". "
                            + "Please return to billing and re-select the appointment.");

            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("message pattern is invalid");
            });
        }
    }

    @Test
    @DisplayName("should not retry quote recovery when invalid pattern fallback contains placeholder text")
    void shouldNotRetryQuoteRecovery_whenInvalidPatternFallbackContainsPlaceholderText() {
        try (LogCapture capture = LogCapture.forLogger(BillingSaveBilling2Action.class)) {
            assertThat(BillingSaveBilling2Action.formatMalformedAppointmentMessage(
                    "Malformed appointment number \"{O}\".", "bad-{0}"))
                    .isEqualTo("Malformed appointment number \"bad-{0}\". "
                            + "Please return to billing and re-select the appointment.");

            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("message pattern is invalid");
            });
            assertThat(capture.events()).noneSatisfy(event -> assertThat(event.getMessage()
                    .getFormattedMessage()).contains("retrying with escaped single quotes"));
        }
    }

    @Test
    @DisplayName("should treat null literal appointment number as unlinked billing")
    void shouldTreatNullLiteralAppointmentNumber_asUnlinkedBilling() throws Exception {
        request.setMethod("POST");
        request.getSession().setAttribute("user", "100");
        request.getSession().setAttribute("billingSessionBean", minimalBillingSessionBean("NuLl"));
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("100");

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                    .thenReturn(true);

            String result = new BillingSaveBilling2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            verifyNoInteractions(appointmentDao, appointmentArchiveDao, billingmasterDAO);
        }
    }

    @Test
    @DisplayName("should use English fallback when billing message key is missing")
    void shouldUseEnglishFallback_whenBillingMessageKeyIsMissing() {
        try (LogCapture capture = LogCapture.forLogger(BillingSaveBilling2Action.class)) {
            assertThat(BillingSaveBilling2Action.message(Locale.ENGLISH,
                    "billing.billingSave.missingRoundSevenKey", "fallback value."))
                    .isEqualTo("fallback value.");
            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getMessage().getFormattedMessage())
                        .contains("Missing billing save resource bundle key")
                        .contains("billing.billingSave.missingRoundSevenKey");
            });
        }
    }

    @Test
    @DisplayName("should use English locale when billing message locale is null")
    void shouldUseEnglishLocale_whenBillingMessageLocaleIsNull() {
        assertThat(BillingSaveBilling2Action.message(null,
                "billing.billingSave.sessionExpired", "fallback value."))
                .isEqualTo("Billing session expired.");
    }

    @Test
    @DisplayName("should skip appointment archive when numeric appointment is not found")
    void shouldSkipAppointmentArchive_whenNumericAppointmentIsNotFound() throws Exception {
        request.setMethod("POST");
        request.getSession().setAttribute("user", "100");
        request.getSession().setAttribute("billingSessionBean", minimalBillingSessionBean("123"));
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("100");
        when(appointmentDao.find(123)).thenReturn(null);

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                    .thenReturn(true);

            String result = new BillingSaveBilling2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            verify(appointmentDao).find(123);
            verify(appointmentArchiveDao, never()).archiveAppointment(any());
            verify(appointmentDao, never()).merge(any());
            verifyNoInteractions(billingmasterDAO);
        }
    }

    @Test
    @DisplayName("should throw security exception when user lacks billing write privilege")
    void shouldThrowSecurityException_whenUserLacksBillingWritePrivilege() {
        request.setMethod("POST");
        request.getSession().setAttribute("user", "100");
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                    .thenReturn(false);

            assertThatThrownBy(() -> new BillingSaveBilling2Action().execute())
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_billing)");
            verifyNoInteractions(billingmasterDAO, appointmentDao, appointmentArchiveDao);
        }
    }

    private BillingSessionBean minimalBillingSessionBean(String apptNo) {
        BillingSessionBean bean = new BillingSessionBean();
        bean.setApptNo(apptNo);
        bean.setEncounter("E");
        bean.setBillingType("MSP");
        bean.setBillItem(new java.util.ArrayList<>());
        return bean;
    }

    private static ServletOutputStream failingOutputStream(IOException failure) {
        return new ServletOutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw failure;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
                // Not used by synchronous unit tests.
            }
        };
    }
}
