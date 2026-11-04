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

import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.entities.Billingmaster;
import io.github.carlos_emr.carlos.commn.model.BillingPaymentType;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * BC Receive Payment (#4551): the form must reach receivePayment.jsp through the
 * {@code receivePaymentActionForm} request attribute, and a payment is recorded only by a
 * well-formed POST.
 *
 * @since 2026-10-15
 */
@DisplayName("ReceivePayment2Action / ViewReceivePayment2Action (BC Receive Payment)")
@Tag("unit")
@Tag("billing")
@Tag("security")
class ReceivePayment2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private BillingPaymentTypeDao mockPaymentTypeDao;
    @Mock private BillingDao mockBillingDao;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    /** Records the save instead of reaching BillingHistoryDAO and MSPReconcile. */
    private static class RecordingAction extends ReceivePayment2Action {
        String savedMaster;
        double savedAmount;
        String savedType;
        int saves;

        @Override
        public void receivePayment(String billingMasterNo, double amount, String paymentType) {
            saves++;
            savedMaster = billingMasterNo;
            savedAmount = amount;
            savedType = paymentType;
        }
    }

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(BillingPaymentTypeDao.class, mockPaymentTypeDao);
        registerMock(BillingDao.class, mockBillingDao);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), any(), isNull()))
                .thenReturn(true);
        givenBill(77, 55, "Pri");
        when(mockPaymentTypeDao.findAll()).thenReturn(List.of(
                paymentType(1, "CASH"), paymentType(2, "ELECTRONIC"), paymentType(3, "CHEQUE"),
                paymentType(4, "ELECTRONIC")));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    private void givenBill(int line, int invoice, String type) {
        Billingmaster master = new Billingmaster();
        master.setBillingmasterNo(line);
        master.setBillingNo(invoice);
        Billing bill = new Billing();
        bill.setBillingtype(type);
        when(mockBillingDao.findByBillingMasterNo(line)).thenReturn(List.<Object[]>of(new Object[]{master, bill}));
    }

    private static BillingPaymentType paymentType(int id, String name) {
        BillingPaymentType type = new BillingPaymentType();
        type.setId(id);
        type.setPaymentType(name);
        return type;
    }

    private RecordingAction postAction(String master, String bill, String amount, String method) {
        request.setMethod("POST");
        RecordingAction action = new RecordingAction();
        action.setBillingmasterNo(master);
        action.setBillNo(bill);
        action.setAmountReceived(amount);
        action.setPaymentMethod(method);
        return action;
    }

    @Test
    @DisplayName("should publish the form with the bill numbers and manual payment methods on the view route")
    void shouldPublishFormAttribute_whenViewRouteOpens() {
        request.setMethod("GET");
        request.setParameter("lineNo", "77");
        request.setParameter("billNo", "55");

        ViewReceivePayment2Action action = new ViewReceivePayment2Action();
        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("receivePaymentActionForm")).isSameAs(action);
        assertThat(action.getBillingmasterNo()).isEqualTo("77");
        assertThat(action.getBillNo()).isEqualTo("55");
        // Both ELECTRONIC rows go, including the one that follows another removed row.
        assertThat(action.getPaymentMethodList()).extracting("paymentType")
                .containsExactly("CASH", "CHEQUE");
    }

    @Test
    @DisplayName("should record the payment and mark it received when a valid POST arrives")
    void shouldRecordPayment_whenValidPost() throws Exception {
        RecordingAction action = postAction("77", "55", "25.50", "3");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.saves).isEqualTo(1);
        assertThat(action.savedMaster).isEqualTo("77");
        assertThat(action.savedAmount).isEqualTo(25.50);
        assertThat(action.savedType).isEqualTo("3");
        assertThat(action.isPaymentReceived()).isTrue();
        assertThat(request.getAttribute("receivePaymentActionForm")).isSameAs(action);
    }

    @Test
    @DisplayName("should record a negative amount when the refund flag is set")
    void shouldRecordNegativeAmount_whenRefund() throws Exception {
        RecordingAction action = postAction("77", "55", "10", "1");
        action.setIsRefund("true");

        action.execute();

        assertThat(action.savedAmount).isEqualTo(-10.0);
        assertThat(action.getAmountReceived()).isEqualTo("-10.0");
    }

    @ParameterizedTest(name = "amount \"{0}\"")
    @ValueSource(strings = {"", " ", "abc", "-5", "1e3", "12.345", "1234567", "12345", "25 ", "0", "0.00"})
    @DisplayName("should re-render the form and save nothing when the amount is not a plain money value")
    void shouldNotSave_whenAmountMalformed(String amount) throws Exception {
        RecordingAction action = postAction("77", "55", amount, "3");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.saves).isZero();
        assertThat(action.isPaymentReceived()).isFalse();
        assertThat(action.hasActionErrors()).isTrue();
        assertThat(action.getPaymentMethodList()).isNotEmpty();
    }

    @Test
    @DisplayName("should save nothing when the invoice does not own the billing line")
    void shouldNotSave_whenInvoiceDoesNotOwnLine() throws Exception {
        RecordingAction action = postAction("77", "56", "10", "3");

        action.execute();

        assertThat(action.saves).isZero();
        assertThat(action.hasActionErrors()).isTrue();
    }

    @Test
    @DisplayName("should save nothing when the billing line does not exist")
    void shouldNotSave_whenLineMissing() throws Exception {
        RecordingAction action = postAction("78", "55", "10", "3");

        action.execute();

        assertThat(action.saves).isZero();
        assertThat(action.isPaymentReceived()).isFalse();
        assertThat(action.hasActionErrors()).isTrue();
    }

    @Test
    @DisplayName("should save nothing when the bill is not a private bill")
    void shouldNotSave_whenBillIsNotPrivate() throws Exception {
        givenBill(77, 55, "MSP");
        RecordingAction action = postAction("77", "55", "10", "3");

        action.execute();

        assertThat(action.saves).isZero();
        assertThat(action.hasActionErrors()).isTrue();
    }

    @Test
    @DisplayName("should save nothing when the method is one the form does not offer")
    void shouldNotSave_whenMethodNotOffered() throws Exception {
        // 2 is ELECTRONIC, 99 does not exist.
        for (String method : List.of("2", "99")) {
            RecordingAction action = postAction("77", "55", "10", method);
            action.execute();
            assertThat(action.saves).isZero();
        }
    }

    @Test
    @DisplayName("should save nothing when a refund would overflow the history amount column")
    void shouldNotSave_whenRefundOverflowsColumn() throws Exception {
        RecordingAction action = postAction("77", "55", "9999.99", "3");
        action.setIsRefund("true");

        action.execute();

        assertThat(action.saves).isZero();
    }

    @Test
    @DisplayName("should record the largest payment the column holds")
    void shouldRecordPayment_whenAmountIsColumnMaximum() throws Exception {
        RecordingAction action = postAction("77", "55", "9999.99", "3");

        action.execute();

        assertThat(action.saves).isEqualTo(1);
        assertThat(action.savedAmount).isEqualTo(9999.99);
    }

    @Test
    @DisplayName("should keep the form showing when a forged paymentReceived accompanies a bad amount")
    void shouldNotShowConfirmation_whenPaymentReceivedForged() throws Exception {
        RecordingAction action = postAction("77", "55", "abc", "3");
        action.setPaymentReceived(true);

        action.execute();

        assertThat(action.saves).isZero();
        assertThat(action.isPaymentReceived()).isFalse();
    }

    @Test
    @DisplayName("should save nothing when the bill line, invoice or method is missing or not numeric")
    void shouldNotSave_whenIdentifiersMalformed() throws Exception {
        for (RecordingAction action : List.of(
                postAction(null, "55", "5", "3"),
                postAction("77", "x", "5", "3"),
                postAction("77", "55", "5", null),
                postAction("7 OR 1=1", "55", "5", "3"))) {
            action.execute();
            assertThat(action.saves).isZero();
        }
    }

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {"GET", "HEAD", "PUT", "post"})
    @DisplayName("should answer 405 and save nothing for any method other than POST")
    void shouldRefuse_whenMethodIsNotPost(String method) throws Exception {
        RecordingAction action = postAction("77", "55", "25", "3");
        request.setMethod(method);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        assertThat(action.saves).isZero();
    }

    @Test
    @DisplayName("should throw SecurityException when the user lacks _billing write")
    void shouldThrowSecurityException_whenNotPrivileged() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(false);
        RecordingAction action = postAction("77", "55", "25", "3");

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_billing)");
        assertThat(action.saves).isZero();
    }
}
