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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingCorrectionRecordService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingCorrectionRecordService.UnbillOutcome;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Security regression for issue #4538: Billing History ▸ Unbill must decide from the claim's
 * stored status, never from the {@code billCode} the browser posts.
 *
 * <p>The history page only ever offers Unbill for a claim that is not billed (B) or settled (S),
 * and it posts that claim's status as {@code billCode}. A hand-built POST can post any code, so a
 * {@code _billing} writer could mark a claim already submitted to OHIP as deleted by posting
 * {@code billCode=O}. These tests pin that the posted code has no say on either storage path.</p>
 */
@DisplayName("BillingDeleteNoAppt2Action (Ontario unbill)")
@Tag("unit")
@Tag("billing")
class BillingDeleteNoAppt2ActionUnitTest extends CarlosUnitTestBase {

    private static final String PROVIDER_NO = "999998";

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;
    private String previousIsNewOnBilling;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private BillingDao mockBillingDao;
    @Mock private BillingCorrectionRecordService mockCorrectionRecordService;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        mockRequest = new MockHttpServletRequest();
        mockRequest.setMethod("POST");
        mockResponse = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);

        previousIsNewOnBilling = CarlosProperties.getInstance().getProperty("isNewONbilling");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (previousIsNewOnBilling == null) {
            CarlosProperties.getInstance().remove("isNewONbilling");
        } else {
            CarlosProperties.getInstance().setProperty("isNewONbilling", previousIsNewOnBilling);
        }
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    private BillingDeleteNoAppt2Action newAction() {
        return new BillingDeleteNoAppt2Action(
                mockSecurityInfoManager, mockBillingDao, mockCorrectionRecordService);
    }

    private static void useNewOntarioBilling(boolean enabled) {
        CarlosProperties.getInstance().setProperty("isNewONbilling", String.valueOf(enabled));
    }

    @Test
    void shouldThrowSecurityException_whenBillingWritePrivilegeMissing() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(false);
        mockRequest.setParameter("billing_no", "42");

        BillingDeleteNoAppt2Action action = newAction();

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_billing)");
        verifyNoInteractions(mockBillingDao, mockCorrectionRecordService);
    }

    @Nested
    @DisplayName("claims in billing_on_cheader1 (isNewONbilling=true, the packaged default)")
    class ClaimHeaderPath {

        @BeforeEach
        void useClaimHeaders() {
            useNewOntarioBilling(true);
        }

        @ParameterizedTest
        @ValueSource(strings = {"O", "A", "D", "W", "B", "S", " "})
        void shouldRefuseWhateverBillCodeIsPosted_whenServiceReportsBilledOrSettled(String postedCode) {
            when(mockCorrectionRecordService.unbillClaim(42, PROVIDER_NO))
                    .thenReturn(UnbillOutcome.REFUSED_BILLED_OR_SETTLED);
            mockRequest.setParameter("billing_no", "42");
            mockRequest.setParameter("billCode", postedCode);

            String result = newAction().execute();

            assertThat(result).isEqualTo("cannotDelete");
            assertThat(mockRequest.getAttribute("cannotDelete")).isEqualTo(Boolean.TRUE);
        }

        @ParameterizedTest
        @ValueSource(strings = {"O", "B", "S", "not-a-status"})
        void shouldDeleteWhateverBillCodeIsPosted_whenServiceAcceptsTheClaim(String postedCode) {
            when(mockCorrectionRecordService.unbillClaim(42, PROVIDER_NO)).thenReturn(UnbillOutcome.DELETED);
            mockRequest.setParameter("billing_no", "42");
            mockRequest.setParameter("billCode", postedCode);

            assertThat(newAction().execute()).isEqualTo(ActionSupport.SUCCESS);
            verify(mockCorrectionRecordService).unbillClaim(42, PROVIDER_NO);
        }

        @Test
        void shouldNotRequireBillCode_whenUnbillingAnUnlockedClaim() {
            when(mockCorrectionRecordService.unbillClaim(42, PROVIDER_NO)).thenReturn(UnbillOutcome.DELETED);
            mockRequest.setParameter("billing_no", "42");

            assertThat(newAction().execute()).isEqualTo(ActionSupport.SUCCESS);
        }

        @Test
        void shouldReturnError_whenNoClaimHasTheId() {
            when(mockCorrectionRecordService.unbillClaim(42, PROVIDER_NO)).thenReturn(UnbillOutcome.NOT_FOUND);
            mockRequest.setParameter("billing_no", "42");

            assertThat(newAction().execute()).isEqualTo(ActionSupport.ERROR);
            assertThat(mockRequest.getAttribute("cannotDelete")).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "abc", "4 2", "42; DROP TABLE billing_on_cheader1"})
        void shouldReturnErrorWithoutTouchingAnyClaim_whenBillingNoIsNotANumber(String billingNo) {
            mockRequest.setParameter("billing_no", billingNo);
            mockRequest.setParameter("billCode", "O");

            assertThat(newAction().execute()).isEqualTo(ActionSupport.ERROR);
            verifyNoInteractions(mockBillingDao, mockCorrectionRecordService);
        }

        @Test
        void shouldReturnErrorWithoutTouchingAnyClaim_whenBillingNoIsMissing() {
            mockRequest.setParameter("billCode", "O");

            assertThat(newAction().execute()).isEqualTo(ActionSupport.ERROR);
            verifyNoInteractions(mockBillingDao, mockCorrectionRecordService);
        }

        @Test
        void shouldLogNoUnsanitizedInput_whenBillingNoCarriesALineBreak() {
            // NumberFormatException's message repeats the raw input; attaching it to the log event
            // would write that input unsanitized (a forged log line).
            mockRequest.setParameter("billing_no", "42\r\nFORGED ENTRY");

            try (LogCapture logCapture = LogCapture.forLogger(BillingDeleteNoAppt2Action.class)) {
                assertThat(newAction().execute()).isEqualTo(ActionSupport.ERROR);

                assertThat(logCapture.events()).isNotEmpty()
                        .allSatisfy(event -> assertThat(event.getThrown()).isNull());
                assertThat(logCapture.messages())
                        .allSatisfy(message -> assertThat(message).doesNotContain("\r").doesNotContain("\n"));
            }
        }

        @Test
        void shouldNeverUseTheLegacyBillingTable_whenClaimHeadersAreInUse() {
            when(mockCorrectionRecordService.unbillClaim(anyInt(), anyString())).thenReturn(UnbillOutcome.DELETED);
            mockRequest.setParameter("billing_no", "42");

            newAction().execute();

            verifyNoInteractions(mockBillingDao);
        }
    }

    @Nested
    @DisplayName("legacy billing table (isNewONbilling=false)")
    class LegacyBillingPath {

        @BeforeEach
        void useLegacyBilling() {
            useNewOntarioBilling(false);
        }

        private Billing storedBilling(String status) {
            Billing billing = new Billing();
            billing.setStatus(status);
            when(mockBillingDao.find(42)).thenReturn(billing);
            return billing;
        }

        @ParameterizedTest
        @ValueSource(strings = {"B", "S"})
        void shouldRefuseWhateverBillCodeIsPosted_whenStoredStatusIsBilledOrSettled(String storedStatus) {
            Billing billing = storedBilling(storedStatus);
            mockRequest.setParameter("billing_no", "42");
            mockRequest.setParameter("billCode", "O");

            String result = newAction().execute();

            assertThat(result).isEqualTo("cannotDelete");
            assertThat(mockRequest.getAttribute("cannotDelete")).isEqualTo(Boolean.TRUE);
            assertThat(billing.getStatus()).isEqualTo(storedStatus);
            verify(mockBillingDao, never()).merge(any(Billing.class));
        }

        @ParameterizedTest
        @ValueSource(strings = {"A", "O", "D"})
        void shouldSoftDeleteWhateverBillCodeIsPosted_whenStoredStatusIsNotBilledOrSettled(String storedStatus) {
            Billing billing = storedBilling(storedStatus);
            mockRequest.setParameter("billing_no", "42");
            mockRequest.setParameter("billCode", "B");

            assertThat(newAction().execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(billing.getStatus()).isEqualTo("D");
            verify(mockBillingDao).merge(billing);
        }

        @Test
        void shouldReturnError_whenNoBillingHasTheId() {
            when(mockBillingDao.find(42)).thenReturn(null);
            mockRequest.setParameter("billing_no", "42");

            assertThat(newAction().execute()).isEqualTo(ActionSupport.ERROR);
            verify(mockBillingDao, never()).merge(any(Billing.class));
        }

        @Test
        void shouldReturnError_whenBillingNoIsNotANumber() {
            mockRequest.setParameter("billing_no", "abc");

            assertThat(newAction().execute()).isEqualTo(ActionSupport.ERROR);
            verify(mockBillingDao, never()).merge(any(Billing.class));
        }

        @Test
        void shouldLogNoUnsanitizedInput_whenBillingNoCarriesALineBreak() {
            mockRequest.setParameter("billing_no", "42\r\nFORGED ENTRY");

            try (LogCapture logCapture = LogCapture.forLogger(BillingDeleteNoAppt2Action.class)) {
                assertThat(newAction().execute()).isEqualTo(ActionSupport.ERROR);

                assertThat(logCapture.events()).isNotEmpty()
                        .allSatisfy(event -> assertThat(event.getThrown()).isNull());
                assertThat(logCapture.messages())
                        .allSatisfy(message -> assertThat(message).doesNotContain("\r").doesNotContain("\n"));
            }
        }

        @Test
        void shouldNeverUseTheClaimHeaderService_whenLegacyBillingIsInUse() {
            storedBilling("A");
            mockRequest.setParameter("billing_no", "42");

            newAction().execute();

            verifyNoInteractions(mockCorrectionRecordService);
        }
    }

    @Test
    void shouldReturn405WithoutReadingAnything_whenMethodIsGet() {
        mockRequest.setMethod("GET");
        mockRequest.setParameter("billing_no", "42");

        assertThat(newAction().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verifyNoInteractions(mockBillingDao, mockCorrectionRecordService);
    }
}
