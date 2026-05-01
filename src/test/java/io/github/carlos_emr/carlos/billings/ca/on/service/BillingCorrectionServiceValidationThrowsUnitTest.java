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
package io.github.carlos_emr.carlos.billings.ca.on.service;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONRepoDao;
import io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingPaymentType;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BillingCorrectionService} input-validation paths.
 *
 * <p>The validation logic was extracted from the legacy
 * {@code BillingCorrection2Action} (753 lines, conflated gate + business
 * logic) so it could be tested without {@code MockedStatic<LoggedInInfo>} +
 * {@code MockedStatic<ServletActionContext>} ceremony. The service takes
 * {@link LoggedInInfo} and {@link jakarta.servlet.http.HttpServletRequest}
 * as method parameters, so each test constructs a plain mock and calls the
 * method directly.</p>
 *
 * <p>Regression armor for the audit-trail integrity guarantees: the
 * service MUST throw {@link BillingValidationException} on validated-input
 * failures rather than returning a silent {@code "closeReload"} /
 * {@code "failure"} result that masks write rejection as a successful
 * page-reload. A future refactor that re-introduces silent returns must
 * fail these tests loudly.</p>
 *
 * <p><strong>Round-5 throw paths cross-referenced upstream:</strong></p>
 * <ul>
 *   <li>{@code updateInvoice} throws {@link BillingValidationException}
 *       when {@code BillingONCHeader1.recomputeTotalFromItems()} returns
 *       {@code Optional.empty} — the Optional-empty contract is pinned by
 *       {@code BillingONCHeader1UnitTest.recomputeTotalFromItems#shouldReturnEmpty_whenAnyActiveItemFeeIsNull}
 *       and the unparseable-fee variant. A regression that re-introduces
 *       {@code return "failure"} would commit dirty-flushed item edits
 *       without rolling back; the upstream pinning ensures the precondition
 *       still triggers the throw.</li>
 *   <li>{@code updateInvoice} throws when {@code updateBillingONCHeader1}
 *       returns false — currently unreachable (the helper only returns
 *       true), the throw is forward-looking guard code preserved by the
 *       validation-throws fix in case future changes introduce a false return.</li>
 * </ul>
 *
 * @since 2026-04-25
 */
@DisplayName("BillingCorrectionService validation throws")
@Tag("unit")
@Tag("billing")
class BillingCorrectionServiceValidationThrowsUnitTest {

    private BillingONCHeader1Dao bCh1Dao;
    private BillingONPaymentDao bPaymentDao;
    private BillingPaymentTypeDao billingPaymentTypeDao;
    private BillingONExtDao billExtDao;
    private BillingONRepoDao billRepoDao;
    private ProviderDao providerDao;
    private BillingServiceDao billingServiceDao;

    private MockHttpServletRequest mockRequest;
    private LoggedInInfo loggedInInfo;

    /** Build the service under test with the test mocks. */
    private BillingCorrectionService newService() {
        return new BillingCorrectionService(
                bPaymentDao, bCh1Dao, billExtDao, billingPaymentTypeDao,
                billRepoDao, providerDao, billingServiceDao);
    }

    @BeforeEach
    void setUp() {
        mockRequest = new MockHttpServletRequest();
        bCh1Dao = Mockito.mock(BillingONCHeader1Dao.class);
        bPaymentDao = Mockito.mock(BillingONPaymentDao.class);
        billingPaymentTypeDao = Mockito.mock(BillingPaymentTypeDao.class);
        billExtDao = Mockito.mock(BillingONExtDao.class);
        billRepoDao = Mockito.mock(BillingONRepoDao.class);
        providerDao = Mockito.mock(ProviderDao.class);
        billingServiceDao = Mockito.mock(BillingServiceDao.class);

        loggedInInfo = Mockito.mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
    }

    // -------- updateInvoice --------

    @Test
    void shouldThrowBillingValidationException_whenUpdateInvoiceBillingNoIsNonNumeric() {
        BillingCorrectionService service = newService();
        // Non-empty, non-null but unparseable: indicates tampering / browser
        // auto-fill regression — throw rather than silent "closeReload".
        mockRequest.setParameter("xml_billing_no", "not-a-number");

        assertThatThrownBy(() -> service.updateInvoice(loggedInInfo, mockRequest))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("invalid bill identifier");

        // Audit-trail integrity: the bill record was never fetched.
        verify(bCh1Dao, never()).find(Mockito.anyInt());
    }

    @Test
    void shouldThrowBillingValidationException_whenUpdateInvoiceBillNotFound() {
        BillingCorrectionService service = newService();
        mockRequest.setParameter("xml_billing_no", "999999");
        when(bCh1Dao.find(Integer.valueOf(999999))).thenReturn(null);

        assertThatThrownBy(() -> service.updateInvoice(loggedInInfo, mockRequest))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("not found");
    }

    /**
     * The service funnels every non-add3rdPartyPayment request through
     * updateInvoice, including the GET-load path that opens the correction
     * page (which posts no xml_billing_no). That path returns the dedicated
     * "loadOnly" result, distinct from the post-save "closeReload" path so
     * the result vocabulary stays semantically honest. Both map to the same
     * JSP, so the user-facing render is identical.
     */
    @Test
    void shouldReturnLoadOnly_whenUpdateInvoiceXmlBillingNoIsAbsent() {
        BillingCorrectionService service = newService();
        // No xml_billing_no parameter at all (the GET-load case).

        String result = service.updateInvoice(loggedInInfo, mockRequest);

        assertThat(result).isEqualTo("loadOnly");
        verify(bCh1Dao, never()).find(Mockito.anyInt());
    }

    @Test
    void shouldReturnLoadOnly_whenUpdateInvoiceXmlBillingNoIsEmpty() {
        BillingCorrectionService service = newService();
        mockRequest.setParameter("xml_billing_no", "");

        String result = service.updateInvoice(loggedInInfo, mockRequest);

        assertThat(result).isEqualTo("loadOnly");
        verify(bCh1Dao, never()).find(Mockito.anyInt());
    }

    @Test
    void shouldThrowBillingValidationException_whenStatusParamMissing() {
        BillingCorrectionService service = newService();
        mockRequest.setParameter("xml_billing_no", "42");
        when(bCh1Dao.findWithItems(Integer.valueOf(42))).thenReturn(new BillingONCHeader1());

        assertThatThrownBy(() -> service.updateInvoice(loggedInInfo, mockRequest))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("missing required field [status]");

        verify(bCh1Dao, never()).merge(Mockito.any(BillingONCHeader1.class));
    }

    // -------- addThirdPartyPayment --------

    @Test
    void shouldThrowBillingValidationException_whenAddThirdPartyPaymentBillingNoIsNonNumeric() {
        BillingCorrectionService service = newService();
        mockRequest.setParameter("billing_no", "not-numeric");

        assertThatThrownBy(() -> service.addThirdPartyPayment(loggedInInfo, mockRequest))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("invalid billing_no");

        verify(bPaymentDao, never()).createPayment(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldThrowBillingValidationException_whenAddThirdPartyPaymentBillNotFound() {
        BillingCorrectionService service = newService();
        mockRequest.setParameter("billing_no", "12345");
        when(bCh1Dao.find(Integer.valueOf(12345))).thenReturn(null);

        assertThatThrownBy(() -> service.addThirdPartyPayment(loggedInInfo, mockRequest))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("bill not found");
    }

    @Test
    void shouldThrowBillingValidationException_whenAddThirdPartyPaymentAmtPaidIsNonNumeric() {
        BillingCorrectionService service = newService();
        mockRequest.setParameter("billing_no", "12345");
        mockRequest.setParameter("amtPaid", "not-a-number");
        BillingONCHeader1 bCh1 = new BillingONCHeader1();
        when(bCh1Dao.find(Integer.valueOf(12345))).thenReturn(bCh1);

        assertThatThrownBy(() -> service.addThirdPartyPayment(loggedInInfo, mockRequest))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("not a valid number");

        verify(bPaymentDao, never()).createPayment(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldThrowBillingValidationException_whenAddThirdPartyPaymentAmtPaidIsMissing() {
        // Missing 'amtPaid' would previously hit `new BigDecimal(null)` →
        // NullPointerException → uncaught 500 (the catch only covered
        // NumberFormatException). Verify the explicit null/empty pre-check
        // routes the failure to the validation page instead.
        BillingCorrectionService service = newService();
        mockRequest.setParameter("billing_no", "12345");
        // intentionally do not set 'amtPaid'
        BillingONCHeader1 bCh1 = new BillingONCHeader1();
        when(bCh1Dao.find(Integer.valueOf(12345))).thenReturn(bCh1);

        assertThatThrownBy(() -> service.addThirdPartyPayment(loggedInInfo, mockRequest))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("amount is missing");

        verify(bPaymentDao, never()).createPayment(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldThrowBillingValidationException_whenAddThirdPartyPaymentPayMethodNotConfigured() {
        BillingCorrectionService service = newService();
        mockRequest.setParameter("billing_no", "12345");
        mockRequest.setParameter("amtPaid", "100.00");
        mockRequest.setParameter("payMethod", "UNKNOWN");
        BillingONCHeader1 bCh1 = new BillingONCHeader1();
        when(bCh1Dao.find(Integer.valueOf(12345))).thenReturn(bCh1);
        when(billingPaymentTypeDao.find("UNKNOWN")).thenReturn(null);

        assertThatThrownBy(() -> service.addThirdPartyPayment(loggedInInfo, mockRequest))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("not configured");

        verify(bPaymentDao, never()).createPayment(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldThrowBillingValidationException_whenAddThirdPartyPaymentPayTypeIsNotPorR() {
        BillingCorrectionService service = newService();
        mockRequest.setParameter("billing_no", "12345");
        mockRequest.setParameter("amtPaid", "100.00");
        mockRequest.setParameter("payMethod", "CASH");
        mockRequest.setParameter("payType", "X");
        BillingONCHeader1 bCh1 = new BillingONCHeader1();
        when(bCh1Dao.find(Integer.valueOf(12345))).thenReturn(bCh1);
        when(billingPaymentTypeDao.find("CASH")).thenReturn(Mockito.mock(BillingPaymentType.class));

        assertThatThrownBy(() -> service.addThirdPartyPayment(loggedInInfo, mockRequest))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("must be P (payment) or R (refund)");

        verify(bPaymentDao, never()).createPayment(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldMergeHeaderAfterCreatingThirdPartyPayment() {
        BillingCorrectionService service = newService();
        mockRequest.setParameter("billing_no", "12345");
        mockRequest.setParameter("amtPaid", "100.00");
        mockRequest.setParameter("payMethod", "CASH");
        mockRequest.setParameter("payType", "P");
        BillingONCHeader1 bCh1 = new BillingONCHeader1();
        when(bCh1Dao.find(Integer.valueOf(12345))).thenReturn(bCh1);
        when(billingPaymentTypeDao.find("CASH")).thenReturn(Mockito.mock(BillingPaymentType.class));

        String result = service.addThirdPartyPayment(loggedInInfo, mockRequest);

        assertThat(result).isEqualTo("success");
        InOrder inOrder = Mockito.inOrder(bPaymentDao, bCh1Dao);
        inOrder.verify(bPaymentDao).createPayment(
                Mockito.same(bCh1),
                Mockito.eq(mockRequest.getLocale()),
                Mockito.eq("P"),
                Mockito.eq(new BigDecimal("100.00")),
                Mockito.eq("CASH"),
                Mockito.eq("999998"));
        inOrder.verify(bCh1Dao).merge(Mockito.same(bCh1));
    }
}
