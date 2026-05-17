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

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingHistoryDAO;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.billings.ca.service.GstSettingsService;
import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.entities.Billingmaster;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
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
    @Mock private GstSettingsService gstSettingsService;

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
        registerMock(GstSettingsService.class, gstSettingsService);
        when(gstSettingsService.getCurrentPercent()).thenReturn(BigDecimal.ZERO);

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
                "/carlos", List.of("101", "102 303"));

        assertThat(redirectUrl)
                .isEqualTo("/carlos/billing/CA/BC/billingView?billing_no=101&billing_no=102+303&receipt=yes");
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
    @DisplayName("should encode appointment number before direct response body")
    void shouldEncodeAppointmentNumber_beforeDirectResponseBody() {
        assertThat(BillingSaveBilling2Action.formatMalformedAppointmentMessage(
                "Malformed appointment number \"{0}\".", "<script>alert(1)</script>"))
                .isEqualTo("Malformed appointment number \"&lt;script&gt;alert(1)&lt;/script&gt;\".");
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
    @DisplayName("should resolve localized malformed billing request message")
    void shouldResolveLocalizedMalformedBillingRequestMessage_whenLocaleIsFrench() {
        assertThat(BillingSaveBilling2Action.message(Locale.FRENCH,
                "billing.billingSave.malformedBillingRequest", "fallback value."))
                .isEqualTo("Demande de facturation non valide. Veuillez retourner "
                        + "\u00e0 la facturation et r\u00e9essayer.");
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
    @DisplayName("should save bill update appointment and redirect receipt when session is valid")
    void shouldSaveBillUpdateAppointmentAndRedirectReceipt_whenSessionIsValid() throws Exception {
        request.setMethod("POST");
        request.setContextPath("/carlos");
        request.addParameter("dispPrice+00100", "125.50");
        request.addParameter("WCBid", "77");
        request.getSession().setAttribute("user", "100");
        BillingSessionBean bean = populatedBillingSessionBean("123");
        BillingBillingManager.BillingItem item = new BillingBillingManager()
                .new BillingItem("00100", "Office visit", "100.00", "100", 2);
        bean.getBillItem().add(item);
        request.getSession().setAttribute("billingSessionBean", bean);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("100");
        Appointment appointment = new Appointment();
        appointment.setId(123);
        appointment.setStatus("N");
        when(appointmentDao.find(123)).thenReturn(appointment);
        doAnswer(invocation -> {
            Billing billing = invocation.getArgument(0);
            ReflectionTestUtils.setField(billing, "id", 456);
            return null;
        }).when(billingmasterDAO).save(any(Billing.class));
        doAnswer(invocation -> {
            Billingmaster master = invocation.getArgument(0);
            master.setBillingmasterNo(789);
            return null;
        }).when(billingmasterDAO).save(any(Billingmaster.class));
        ArgumentCaptor<Billing> billingCaptor = ArgumentCaptor.forClass(Billing.class);
        ArgumentCaptor<Billingmaster> masterCaptor = ArgumentCaptor.forClass(Billingmaster.class);

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class);
             MockedConstruction<BillingHistoryDAO> archiveConstruction =
                     mockConstruction(BillingHistoryDAO.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                    .thenReturn(true);
            BillingSaveBilling2Action action = new BillingSaveBilling2Action();
            action.setSubmit("Save & Print Receipt");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getRedirectedUrl())
                    .isEqualTo("/carlos/billing/CA/BC/billingView?billing_no=456&receipt=yes");
            verify(appointmentArchiveDao).archiveAppointment(appointment);
            verify(appointmentDao).merge(appointment);
            assertThat(appointment.getLastUpdateUser()).isEqualTo("100");
            verify(billingmasterDAO).save(billingCaptor.capture());
            verify(billingmasterDAO).save(masterCaptor.capture());
            assertThat(archiveConstruction.constructed()).hasSize(1);
            verify(archiveConstruction.constructed().get(0)).createBillingHistoryArchive("789");
        }

        Billing savedBilling = billingCaptor.getValue();
        assertThat(savedBilling.getAppointmentNo()).isEqualTo(123);
        assertThat(savedBilling.getDemographicNo()).isEqualTo(22);
        assertThat(savedBilling.getProviderNo()).isEqualTo("200");
        assertThat(savedBilling.getTotal()).isEqualTo("251.00");

        Billingmaster savedMaster = masterCaptor.getValue();
        assertThat(savedMaster.getBillingNo()).isEqualTo(456);
        assertThat(savedMaster.getBillAmount()).isEqualTo("251.00");
        assertThat(savedMaster.getPaymentMethod()).isEqualTo(3);
        assertThat(savedMaster.getWcbId()).isEqualTo(77);
    }

    @Test
    @DisplayName("should reject malformed payment type before billing writes")
    void shouldRejectMalformedPaymentType_beforeBillingWrites() throws Exception {
        request.setMethod("POST");
        request.getSession().setAttribute("user", "100");
        BillingSessionBean bean = populatedBillingSessionBean("0");
        bean.setPaymentType("bad");
        bean.getBillItem().add(new BillingBillingManager()
                .new BillingItem("00100", "Office visit", "100.00", "100", 1));
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
            assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(response.getContentAsString())
                    .isEqualTo("Malformed billing request. Please return to billing and retry.");
            verifyNoInteractions(billingmasterDAO, appointmentDao, appointmentArchiveDao);
        }
    }

    @Test
    @DisplayName("should reject malformed WCB id before billing writes")
    void shouldRejectMalformedWcbId_beforeBillingWrites() throws Exception {
        request.setMethod("POST");
        request.getSession().setAttribute("user", "100");
        BillingSessionBean bean = populatedBillingSessionBean("0");
        bean.setWcbId("bad");
        bean.getBillItem().add(new BillingBillingManager()
                .new BillingItem("00100", "Office visit", "100.00", "100", 1));
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
            assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(response.getContentAsString())
                    .isEqualTo("Malformed billing request. Please return to billing and retry.");
            verifyNoInteractions(billingmasterDAO, appointmentDao, appointmentArchiveDao);
        }
    }

    @Test
    @DisplayName("should reject malformed price override before billing writes")
    void shouldRejectMalformedPriceOverride_beforeBillingWrites() throws Exception {
        request.setMethod("POST");
        request.addParameter("dispPrice+00100", "not-a-price");
        request.getSession().setAttribute("user", "100");
        BillingSessionBean bean = populatedBillingSessionBean("123");
        bean.getBillItem().add(new BillingBillingManager()
                .new BillingItem("00100", "Office visit", "100.00", "100", 1));
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
            assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(response.getContentAsString())
                    .isEqualTo("Malformed billing request. Please return to billing and retry.");
            verifyNoInteractions(billingmasterDAO, appointmentDao, appointmentArchiveDao);
        }
    }

    @Test
    @DisplayName("should reject malformed final price override before billing writes")
    void shouldRejectMalformedFinalPriceOverride_beforeBillingWrites() throws Exception {
        request.setMethod("POST");
        request.addParameter("dispPrice+00100", "125.50");
        request.addParameter("dispPrice+00101", " ");
        request.addParameter("dispPrice+00102", "not-a-price");
        request.getSession().setAttribute("user", "100");
        BillingSessionBean bean = populatedBillingSessionBean("123");
        BillingBillingManager manager = new BillingBillingManager();
        bean.getBillItem().add(manager.new BillingItem("00100", "Office visit", "100.00", "100", 1));
        bean.getBillItem().add(manager.new BillingItem("00101", "Follow-up", "50.00", "50", 1));
        bean.getBillItem().add(manager.new BillingItem("00102", "Procedure", "75.00", "75", 1));
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
            verifyNoInteractions(billingmasterDAO, appointmentDao, appointmentArchiveDao);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Infinity", "NaN"})
    @DisplayName("should reject non-finite price override before billing writes")
    void shouldRejectNonFinitePriceOverride_beforeBillingWrites(String priceOverride) throws Exception {
        request.setMethod("POST");
        request.addParameter("dispPrice+00100", priceOverride);
        request.getSession().setAttribute("user", "100");
        BillingSessionBean bean = populatedBillingSessionBean("123");
        bean.getBillItem().add(new BillingBillingManager()
                .new BillingItem("00100", "Office visit", "100.00", "100", 1));
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
            verifyNoInteractions(billingmasterDAO, appointmentDao, appointmentArchiveDao);
        }
    }

    @Test
    @DisplayName("should ignore blank price override when saving billing")
    void shouldIgnoreBlankPriceOverride_whenSavingBilling() throws Exception {
        request.setMethod("POST");
        request.addParameter("dispPrice+00100", " ");
        request.getSession().setAttribute("user", "100");
        BillingSessionBean bean = populatedBillingSessionBean("123");
        bean.getBillItem().add(new BillingBillingManager()
                .new BillingItem("00100", "Office visit", "100.00", "100", 1));
        request.getSession().setAttribute("billingSessionBean", bean);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("100");
        doAnswer(invocation -> {
            Billing billing = invocation.getArgument(0);
            ReflectionTestUtils.setField(billing, "id", 456);
            return null;
        }).when(billingmasterDAO).save(any(Billing.class));
        doAnswer(invocation -> {
            Billingmaster master = invocation.getArgument(0);
            master.setBillingmasterNo(789);
            return null;
        }).when(billingmasterDAO).save(any(Billingmaster.class));

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class);
             MockedConstruction<BillingHistoryDAO> ignored = mockConstruction(BillingHistoryDAO.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                    .thenReturn(true);

            String result = new BillingSaveBilling2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            assertThat(response.getStatus()).isEqualTo(200);
            verify(billingmasterDAO).save(any(Billing.class));
            verify(billingmasterDAO).save(any(Billingmaster.class));
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
        bean.setPaymentType("1");
        bean.setBillItem(new java.util.ArrayList<>());
        return bean;
    }

    private BillingSessionBean populatedBillingSessionBean(String apptNo) {
        BillingSessionBean bean = minimalBillingSessionBean(apptNo);
        bean.setEncounter("O");
        bean.setBillingType("WCB");
        bean.setPatientNo("22");
        bean.setPatientName("Test Patient");
        bean.setPatientFirstName("Test");
        bean.setPatientLastName("Patient");
        bean.setPatientDoB("1980-01-02");
        bean.setPatientPHN("1234567890");
        bean.setPatientSex("M");
        bean.setBillingProvider("200");
        bean.setApptProviderNo("201");
        bean.setBillingPracNo("PRAC");
        bean.setBillingGroupNo("GROUP");
        bean.setBillRegion("BC");
        bean.setVisitLocation("00");
        bean.setVisitType("O");
        bean.setServiceDate("2026-05-17");
        bean.setAdmissionDate("2026-05-17");
        bean.setGrandtotal("251.00");
        bean.setPaymentType("3");
        bean.setWcbId("77");
        bean.setCorrespondenceCode("C");
        bean.setDependent("00");
        bean.setAfterHours("0");
        bean.setSubmissionCode("0");
        bean.setService_to_date("");
        bean.setDx1("001");
        bean.setDx2("");
        bean.setDx3("");
        bean.setReferral1("");
        bean.setReferral2("");
        bean.setReferType1("");
        bean.setReferType2("");
        bean.setFacilityNum("");
        bean.setFacilitySubNum("");
        bean.setShortClaimNote("");
        bean.setMessageNotes("");
        bean.setMva_claim_code("");
        bean.setIcbc_claim_no("");
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
