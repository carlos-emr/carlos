package io.github.carlos_emr.carlos.billing.CA.ON.web;

import java.math.BigDecimal;
import java.util.List;

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingItemData;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingON3rdPaymentsViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnItemPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnTransactionDao;
import io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

@DisplayName("BillingONPayments2Action")
@Tag("unit")
@Tag("billing")
class BillingONPayments2ActionUnitTest extends CarlosUnitTestBase {

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

    @Test
    void shouldNotDoublePrefixNegativeItemCurrencyValues() {
        BillingItemData item = new BillingItemData();
        item.setId("1");
        item.setService_code("A001A");
        item.setFee("10.00");
        item.setPaid("0.00");
        item.setDiscount("0.00");
        item.setCredit("15.00");

        BillingON3rdPaymentsViewModel model = new BillingONPayments2Action()
                .buildPaymentsViewModel(123, List.of(item), List.of(),
                        new BigDecimal("10.00"), new BigDecimal("-5.00"), List.of());

        assertThat(model.getItems()).hasSize(1);
        assertThat(model.getItems().get(0).realPaidDisplay()).doesNotContain("--");
        assertThat(model.getItems().get(0).balanceDisplay()).doesNotContain("--");
        assertThat(model.getBalanceDisplay()).doesNotContain("--");
    }
}
