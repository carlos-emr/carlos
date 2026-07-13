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

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimItemDto;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnThirdPartyPaymentsViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnItemPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnTransactionDao;
import io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;
import io.github.carlos_emr.carlos.commn.model.BillingOnItemPayment;
import io.github.carlos_emr.carlos.commn.model.BillingOnTransaction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import io.github.carlos_emr.carlos.billings.ca.on.service.BillingPaymentDeletionService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingPaymentSaveService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentCaptor.forClass;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit coverage for {@code BillingOnPayments2Action} dispatch, guards, and JSON response contracts. */
@DisplayName("BillingOnPayments2Action")
@Tag("unit")
@Tag("billing")
class BillingOnPayments2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private SecurityInfoManager mockSecurityInfoManager;
    private LoggedInInfo mockLoggedInInfo;

    @BeforeEach
    void registerActionDependencies() {
        mockSecurityInfoManager = mock(SecurityInfoManager.class);
        mockLoggedInInfo = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(BillingONItemDao.class, mock(BillingONItemDao.class));
        registerMock(BillingONPaymentDao.class, mock(BillingONPaymentDao.class));
        registerMock(BillingPaymentTypeDao.class, mock(BillingPaymentTypeDao.class));
        registerMock(BillingONCHeader1Dao.class, mock(BillingONCHeader1Dao.class));
        registerMock(BillingONExtDao.class, mock(BillingONExtDao.class));
        registerMock(BillingOnItemPaymentDao.class, mock(BillingOnItemPaymentDao.class));
        registerMock(BillingOnTransactionDao.class, mock(BillingOnTransactionDao.class));
        registerMock(BillingPaymentSaveService.class, mock(BillingPaymentSaveService.class));
        registerMock(BillingPaymentDeletionService.class, mock(BillingPaymentDeletionService.class));
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest)
                .thenReturn(new MockHttpServletRequest());
        servletActionContextMock.when(ServletActionContext::getResponse)
                .thenReturn(new MockHttpServletResponse());

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void closeServletActionContextMock() {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    // -- parseStrictAmount: gates the savePayment() rejection-vs-persist
    // -- decision. The legacy flow silently zeroed malformed amounts and
    // -- persisted $0 rows; the strict variant must surface the failure.

    @Test
    void shouldReturnParsedAmount_whenInputIsValid() {
        assertThat(BillingOnPayments2Action.parseStrictAmount("12.34"))
                .isEqualByComparingTo("12.34");
    }

    @Test
    void shouldReturnZero_whenInputIsNull() {
        assertThat(BillingOnPayments2Action.parseStrictAmount(null))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldReturnZero_whenInputIsEmpty() {
        assertThat(BillingOnPayments2Action.parseStrictAmount(""))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldReturnZero_whenInputIsWhitespaceOnly() {
        assertThat(BillingOnPayments2Action.parseStrictAmount("   "))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldThrow_whenInputIsMalformed() {
        assertThatThrownBy(() -> BillingOnPayments2Action.parseStrictAmount("1OO"))
                .isInstanceOf(NumberFormatException.class)
                .hasMessageContaining("1OO");
    }

    @Test
    void shouldThrow_whenInputHasMixedAlphaNumeric() {
        assertThatThrownBy(() -> BillingOnPayments2Action.parseStrictAmount("12abc"))
                .isInstanceOf(NumberFormatException.class)
                .hasMessageContaining("12abc");
    }

    @Test
    void shouldThrow_whenInputIsNegative() {
        assertThatThrownBy(() -> BillingOnPayments2Action.parseStrictAmount("-12.34"))
                .isInstanceOf(NumberFormatException.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    void shouldTrimSurroundingWhitespace_beforeParsing() {
        assertThat(BillingOnPayments2Action.parseStrictAmount("  42.00  "))
                .isEqualByComparingTo("42.00");
    }

    @Test
    void shouldReturn400_whenListPaymentsCalledWithoutBillingNo() throws Exception {
        // Pre-fix: NumberFormatException("Cannot parse null string") leaked
        // out of execute() and crashed the whole request with a 500 (visible
        // when navigating directly to /billing/CA/ON/billingON3rdPayments
        // without a query string). Defensive parse → 400 with a clear message.
        MockHttpServletRequest request = new MockHttpServletRequest();
        // No billingNo parameter set.
        MockHttpServletResponse response = new MockHttpServletResponse();
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        String result = new BillingOnPayments2Action().listPayments();

        assertThat(result).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getErrorMessage()).contains("billingNo");
    }

    @Test
    void shouldReturn404_whenBillingNoIsValidButCheaderNotFound() throws Exception {
        // Round-6 M7 fix: a concurrent delete or otherwise-missing claim
        // would NPE on cheader.getTotal() and produce a 500. The action
        // now null-checks cheader and returns 404 with a clear message.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("billingNo", "999999");
        MockHttpServletResponse response = new MockHttpServletResponse();
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        // Default mocks return null for ch1Dao.find — exactly the scenario.
        String result = new BillingOnPayments2Action().listPayments();

        assertThat(result).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getErrorMessage()).contains("Billing record not found");
    }

    @Test
    void shouldCheckPrivilege_whenListPaymentsCalledDirectly() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> new BillingOnPayments2Action().listPayments())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");
    }

    @Test
    void shouldNotDoublePrefixNegativeItemCurrencyValues_forCurrencyRendering() {
        BillingClaimItemDto item = new BillingClaimItemDto();
        item = item.withId("1");
        item = item.withServiceCode("A001A");
        item = item.withFee("10.00");
        item = item.withPaid("0.00");
        item = item.withDiscount("0.00");
        item = item.withCredit("15.00");

        BillingOnThirdPartyPaymentsViewModel model = new BillingOnPayments2Action()
                .buildPaymentsViewModel(123, List.of(item), List.of(),
                        new BigDecimal("10.00"), new BigDecimal("-5.00"), List.of());

        assertThat(model.getItems()).hasSize(1);
        assertThat(model.getItems().get(0).realPaidDisplay()).doesNotContain("--");
        assertThat(model.getItems().get(0).balanceDisplay()).doesNotContain("--");
        assertThat(model.getBalanceDisplay()).doesNotContain("--");
    }

    @Test
    void shouldFlagAmountUnreadable_whenStoredAmountMalformed() {
        BillingOnPayments2Action.ParsedAmount parsed = BillingOnPayments2Action.parseDec("not-money");

        assertThat(parsed.value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(parsed.unreadable()).isTrue();
    }

    // -- savePayment(): JSON rejection contract.
    //
    // The legacy shape silently zeroed malformed amounts inside per-item
    // catch blocks and persisted $0 rows for every malformed cell — turning
    // a typo into a duplicate-/zero-payment ledger row. The current
    // implementation validates every amount UPFRONT and aborts the entire
    // request before any persist if any single cell fails. The tests below
    // pin that contract end-to-end so a regression that re-introduces an
    // empty per-item catch is caught.

    private MockHttpServletRequest savePaymentRequestWithItem(String paymentValue, String discountValue) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");                     // required by savePayment POST gate
        request.setParameter("size", "1");
        request.setParameter("billingNo", "4242");
        request.setParameter("paymentDate", "2026-04-29");
        request.setParameter("paymentType", "1");
        request.setParameter("status", "");
        request.getSession().setAttribute("user", "999998");
        request.setParameter("itemId0", "11");
        request.setParameter("sel0", "payment");
        request.setParameter("payment0", paymentValue);
        request.setParameter("discount0", discountValue);
        return request;
    }

    @Test
    void shouldReturn405WithAllowHeader_whenSavePaymentInvokedViaGet() throws Exception {
        // Pre-fix: HttpMethodGuardFilter's "save" token didn't match
        // "savePayment", so a forged GET could drive ~10 DAO writes.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setParameter("size", "0");
        request.setParameter("billingNo", "1");
        request.setParameter("paymentDate", "2026-04-29");

        MockHttpServletResponse response = new MockHttpServletResponse();
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        new BillingOnPayments2Action().savePayment();

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
    }

    @Test
    void shouldReturnFailure_whenDeletePaymentInvokedViaGet() throws Exception {
        // Pre-fix: HttpMethodGuardFilter's "delete" token didn't match
        // "deletePayment", so a forged GET could wipe a payment.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setParameter("id", "1");

        MockHttpServletResponse response = new MockHttpServletResponse();
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        String result = new BillingOnPayments2Action().deletePayment();

        assertThat(result).isEqualTo("failure");
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
    }

    private MockHttpServletResponse executeSavePayment(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        // Stub the item lookup so the savePayment loop reaches the parse step.
        BillingONItemDao itemDao = mock(BillingONItemDao.class);
        when(itemDao.find(anyInt())).thenReturn(new BillingONItem());
        registerMock(BillingONItemDao.class, itemDao);

        new BillingOnPayments2Action().savePayment();
        return response;
    }

    @Test
    void shouldWriteJsonRejectionAndSkipPersist_whenSingleItemPaymentMalformed() throws Exception {
        MockHttpServletRequest request = savePaymentRequestWithItem("not-a-number", "0.00");
        BillingONPaymentDao paymentDao = mock(BillingONPaymentDao.class);
        BillingOnItemPaymentDao itemPaymentDao = mock(BillingOnItemPaymentDao.class);
        BillingOnTransactionDao txDao = mock(BillingOnTransactionDao.class);
        registerMock(BillingONPaymentDao.class, paymentDao);
        registerMock(BillingOnItemPaymentDao.class, itemPaymentDao);
        registerMock(BillingOnTransactionDao.class, txDao);

        MockHttpServletResponse response = executeSavePayment(request);

        assertThat(response.getContentAsString()).contains("\"ret\":1");
        assertThat(response.getContentAsString()).contains("Invalid amount on row 0");
        assertThat(response.getContentAsString()).contains("not-a-number");
        assertThat(response.getContentType()).contains("application/json");

        // Critical contract: nothing persists when any single amount is malformed.
        verify(paymentDao, never()).persist(any(BillingONPayment.class));
        verify(itemPaymentDao, never()).persist(any(BillingOnItemPayment.class));
        verify(txDao, never()).persist(any(BillingOnTransaction.class));
    }

    @Test
    void shouldWriteJsonRejection_whenSizeParamMissing() throws Exception {
        // Pre-fix: NumberFormatException on Integer.parseInt(null) leaked
        // out as a 500. Defensive parse → JSON rejection contract.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        // size NOT set
        request.setParameter("billingNo", "4242");
        request.setParameter("paymentDate", "2026-04-29");

        MockHttpServletResponse response = new MockHttpServletResponse();
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        new BillingOnPayments2Action().savePayment();

        assertThat(response.getContentAsString()).contains("\"ret\":1");
        assertThat(response.getContentAsString()).contains("size");
        assertThat(response.getContentType()).contains("application/json");
    }

    @Test
    void shouldWriteJsonRejection_whenBillingNoParamMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setParameter("size", "1");
        // billingNo NOT set
        request.setParameter("paymentDate", "2026-04-29");

        MockHttpServletResponse response = new MockHttpServletResponse();
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        new BillingOnPayments2Action().savePayment();

        assertThat(response.getContentAsString()).contains("\"ret\":1");
        assertThat(response.getContentAsString()).contains("billingNo");
    }

    @Test
    void shouldWriteJsonRejection_whenItemIdMalformed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setParameter("size", "1");
        request.setParameter("billingNo", "4242");
        request.setParameter("paymentDate", "2026-04-29");
        request.setParameter("paymentType", "1");
        request.setParameter("status", "");
        request.getSession().setAttribute("user", "999998");
        request.setParameter("itemId0", "not-an-int");  // malformed
        request.setParameter("sel0", "payment");
        request.setParameter("payment0", "10.00");
        request.setParameter("discount0", "0.00");

        MockHttpServletResponse response = new MockHttpServletResponse();
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        new BillingOnPayments2Action().savePayment();

        assertThat(response.getContentAsString()).contains("\"ret\":1");
        assertThat(response.getContentAsString()).contains("Invalid itemId on row 0");
        assertThat(response.getContentAsString()).contains("not-an-int");
    }

    @Test
    void shouldWriteJsonRejectionAndSkipPersist_whenSingleItemDiscountMalformed() throws Exception {
        MockHttpServletRequest request = savePaymentRequestWithItem("10.00", "abc");
        BillingONPaymentDao paymentDao = mock(BillingONPaymentDao.class);
        registerMock(BillingONPaymentDao.class, paymentDao);

        MockHttpServletResponse response = executeSavePayment(request);

        assertThat(response.getContentAsString()).contains("\"ret\":1");
        assertThat(response.getContentAsString()).contains("Invalid amount on row 0");

        verify(paymentDao, never()).persist(any(BillingONPayment.class));
    }

    @Test
    void shouldRejectFirstMalformedRowAndNotProcessLaterRows_whenMultipleItems() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setParameter("size", "3");
        request.setParameter("billingNo", "4242");
        request.setParameter("paymentDate", "2026-04-29");
        request.setParameter("paymentType", "1");
        request.setParameter("status", "");
        request.getSession().setAttribute("user", "999998");
        // Row 0 valid, row 1 malformed, row 2 valid → must reject on row 1.
        request.setParameter("itemId0", "11"); request.setParameter("sel0", "payment");
        request.setParameter("payment0", "10.00"); request.setParameter("discount0", "0.00");
        request.setParameter("itemId1", "12"); request.setParameter("sel1", "payment");
        request.setParameter("payment1", "BAD");  request.setParameter("discount1", "0.00");
        request.setParameter("itemId2", "13"); request.setParameter("sel2", "payment");
        request.setParameter("payment2", "5.00"); request.setParameter("discount2", "0.00");

        BillingONPaymentDao paymentDao = mock(BillingONPaymentDao.class);
        registerMock(BillingONPaymentDao.class, paymentDao);

        MockHttpServletResponse response = executeSavePayment(request);

        // The rejection should reference row 1 (zero-indexed) — proving
        // validation iterated past row 0 but stopped at row 1.
        assertThat(response.getContentAsString()).contains("Invalid amount on row 1");

        // Critical: even though row 0 was valid, NO persist call happened.
        // This is what makes the upfront-validate-then-persist split safe.
        verify(paymentDao, never()).persist(any(BillingONPayment.class));
    }

    @Test
    void shouldNormalizePaymentTypeAfterSingleParse_whenSavingPayment() throws Exception {
        MockHttpServletRequest request = savePaymentRequestWithItem("10.00", "0.00");
        request.setParameter("paymentType", "007");
        request.setParameter("status", io.github.carlos_emr.carlos.commn.model.BillingONCHeader1.OPEN);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());

        BillingONItemDao itemDao = mock(BillingONItemDao.class);
        when(itemDao.find(11)).thenReturn(new BillingONItem());
        registerMock(BillingONItemDao.class, itemDao);

        BillingONCHeader1Dao headerDao = mock(BillingONCHeader1Dao.class);
        io.github.carlos_emr.carlos.commn.model.BillingONCHeader1 header =
                new io.github.carlos_emr.carlos.commn.model.BillingONCHeader1();
        header.setStatus(io.github.carlos_emr.carlos.commn.model.BillingONCHeader1.OPEN);
        when(headerDao.find(4242)).thenReturn(header);
        registerMock(BillingONCHeader1Dao.class, headerDao);

        BillingPaymentSaveService saveService = mock(BillingPaymentSaveService.class);
        registerMock(BillingPaymentSaveService.class, saveService);

        new BillingOnPayments2Action().savePayment();

        ArgumentCaptor<BillingPaymentSaveService.Command> commandCaptor =
                forClass(BillingPaymentSaveService.Command.class);
        verify(saveService).saveThirdPartyPayment(commandCaptor.capture());
        assertThat(commandCaptor.getValue().paymentTypeId).isEqualTo(7);
        assertThat(commandCaptor.getValue().paymentTypeIdRaw).isEqualTo("7");
    }

    @Test
    void shouldWriteJsonRejection_whenBillingHeaderMissingDuringSave() throws Exception {
        MockHttpServletRequest request = savePaymentRequestWithItem("10.00", "0.00");
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        MockHttpServletResponse response = new MockHttpServletResponse();
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        BillingONItemDao itemDao = mock(BillingONItemDao.class);
        when(itemDao.find(11)).thenReturn(new BillingONItem());
        registerMock(BillingONItemDao.class, itemDao);

        BillingONCHeader1Dao headerDao = mock(BillingONCHeader1Dao.class);
        when(headerDao.find(4242)).thenReturn(null);
        registerMock(BillingONCHeader1Dao.class, headerDao);

        String result = new BillingOnPayments2Action().savePayment();

        assertThat(result).isNull();
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString()).contains("\"ret\":1");
        assertThat(response.getContentAsString()).contains("no longer exists");
    }

    // -- viewPayment_ext: returns "failure" instead of null on bad input
    // -- or missing payment so the JSP renders an explicit error page
    // -- rather than the silent blank page the legacy contract produced.

    @Test
    void shouldReturnFailure_whenBillPaymentIdIsNonNumeric() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setParameter("billPaymentId", "not-an-int");
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());

        String result = new BillingOnPayments2Action().viewPayment_ext();

        // Pre-fix: returned null → JSP rendered an empty page with no error.
        assertThat(result).isEqualTo("failure");
    }

    @Test
    void shouldCheckPrivilege_whenViewPaymentExtCalledDirectly() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> new BillingOnPayments2Action().viewPayment_ext())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");
    }

    @Test
    void shouldCheckPrivilege_whenViewPaymentCalledDirectly() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> new BillingOnPayments2Action().viewPayment())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");
    }

    @Test
    void shouldReturnFailure_whenBillingONPaymentNotFound() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setParameter("billPaymentId", "9999");
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());

        BillingONPaymentDao paymentDao = mock(BillingONPaymentDao.class);
        when(paymentDao.find(9999)).thenReturn(null);
        registerMock(BillingONPaymentDao.class, paymentDao);

        String result = new BillingOnPayments2Action().viewPayment_ext();

        // Same blank-page bug fix — null lookup must surface as "failure".
        assertThat(result).isEqualTo("failure");
    }

    @Test
    void shouldRenderViewPaymentJsp_againstBillingClaimItemRecordProperties() throws Exception {
        String jsp = Files.readString(Path.of(
                "src/main/webapp/WEB-INF/jsp/billing/CA/ON/billingON3rdViewPayment.jsp"));

        assertThat(jsp)
                .contains("itemData.claimHeaderId")
                .contains("itemData.serviceCode")
                .doesNotContain("itemData.ch1_id")
                .doesNotContain("itemData.service_code");
    }
}
