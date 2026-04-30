package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.math.BigDecimal;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BillingOnPayments2Action")
@Tag("unit")
@Tag("billing")
class BillingOnPayments2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;

    @BeforeEach
    void registerActionDependencies() {
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        registerMock(BillingONItemDao.class, mock(BillingONItemDao.class));
        registerMock(BillingONPaymentDao.class, mock(BillingONPaymentDao.class));
        registerMock(BillingPaymentTypeDao.class, mock(BillingPaymentTypeDao.class));
        registerMock(BillingONCHeader1Dao.class, mock(BillingONCHeader1Dao.class));
        registerMock(BillingONExtDao.class, mock(BillingONExtDao.class));
        registerMock(BillingOnItemPaymentDao.class, mock(BillingOnItemPaymentDao.class));
        registerMock(BillingOnTransactionDao.class, mock(BillingOnTransactionDao.class));
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest)
                .thenReturn(new MockHttpServletRequest());
        servletActionContextMock.when(ServletActionContext::getResponse)
                .thenReturn(new MockHttpServletResponse());
    }

    @AfterEach
    void closeServletActionContextMock() {
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
    void shouldNotDoublePrefixNegativeItemCurrencyValues() {
        BillingClaimItemDto item = new BillingClaimItemDto();
        item.setId("1");
        item.setService_code("A001A");
        item.setFee("10.00");
        item.setPaid("0.00");
        item.setDiscount("0.00");
        item.setCredit("15.00");

        BillingOnThirdPartyPaymentsViewModel model = new BillingOnPayments2Action()
                .buildPaymentsViewModel(123, List.of(item), List.of(),
                        new BigDecimal("10.00"), new BigDecimal("-5.00"), List.of());

        assertThat(model.getItems()).hasSize(1);
        assertThat(model.getItems().get(0).realPaidDisplay()).doesNotContain("--");
        assertThat(model.getItems().get(0).balanceDisplay()).doesNotContain("--");
        assertThat(model.getBalanceDisplay()).doesNotContain("--");
    }

    // -- savePayment(): JSON rejection contract.
    //
    // The legacy shape silently zeroed malformed amounts inside per-item
    // catch blocks and persisted $0 rows for every malformed cell — turning
    // a typo into a duplicate-/zero-payment ledger row. The fix in commit
    // c79814781b validates every amount UPFRONT and aborts the entire
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
    void shouldWriteJsonRejection_andSkipPersist_whenSingleItemPaymentMalformed() throws Exception {
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
    void shouldWriteJsonRejection_andSkipPersist_whenSingleItemDiscountMalformed() throws Exception {
        MockHttpServletRequest request = savePaymentRequestWithItem("10.00", "abc");
        BillingONPaymentDao paymentDao = mock(BillingONPaymentDao.class);
        registerMock(BillingONPaymentDao.class, paymentDao);

        MockHttpServletResponse response = executeSavePayment(request);

        assertThat(response.getContentAsString()).contains("\"ret\":1");
        assertThat(response.getContentAsString()).contains("Invalid amount on row 0");

        verify(paymentDao, never()).persist(any(BillingONPayment.class));
    }

    @Test
    void shouldRejectFirstMalformedRow_andNotProcessLaterRows_whenMultipleItems() throws Exception {
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

    // -- viewPayment_ext: round-4 fix returns "failure" instead of null on
    // -- bad input or missing payment, replacing the silent-blank-page behavior.

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
}
