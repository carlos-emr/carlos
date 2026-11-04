/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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

import io.github.carlos_emr.carlos.billing.CA.BC.dao.BillRecipientsDao;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.BillingHistoryDao;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.BillingNoteDao;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.entities.Billingmaster;
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

import java.util.List;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** POST-only guard coverage for the BC billing update action. */
@DisplayName("BillingUpdateBilling2Action")
@Tag("unit")
@Tag("billing")
class BillingUpdateBilling2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private LoggedInInfo loggedInInfo;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        registerMock(SecurityInfoManager.class, securityInfoManager);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldRejectGetBeforeBillingWrites_whenUpdateSubmittedWithoutPost() throws Exception {
        request.setMethod("GET");

        String result = new BillingUpdateBilling2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verify(securityInfoManager, never()).hasPrivilege(any(LoggedInInfo.class), eq("_billing"),
                eq("w"), isNull());
    }

    /**
     * The receipt's Update Invoice posted an empty billingNo, which ended in a NumberFormatException
     * and an HTTP 500 (issue #4717). A missing, non-numeric or over-long number is now refused before
     * any read or write.
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "12a", "0000000000123"})
    void shouldRefuseWithBadRequest_whenBillingNoIsNotABillNumber(String billingNo) throws Exception {
        request.setMethod("POST");
        BillingmasterDAO billingmasterDao = mock(BillingmasterDAO.class);
        registerBillingDaos(billingmasterDao);

        BillingUpdateBilling2Action action = new BillingUpdateBilling2Action();
        action.setBillingNo(billingNo);
        String result = action.execute();

        // Refused before any write: a number the receipt view refuses would otherwise be updated
        // and then shown as an error.
        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(billingmasterDao);
    }

    /**
     * The receipt posts no billing provider. Updating the recipient or the note must not write that
     * missing value over the bill lines' payee (issue #4717).
     */
    @Test
    void shouldLeavePayeeAlone_whenReceiptPostsNoProvider() throws Exception {
        request.setMethod("POST");
        request.getSession().setAttribute("user", "999998");
        Billingmaster line = new Billingmaster();
        line.setPayeeNo("111111");
        BillingmasterDAO billingmasterDao = mock(BillingmasterDAO.class);
        when(billingmasterDao.getBillingMasterByBillingNo("123")).thenReturn(List.of(line));
        registerBillingDaos(billingmasterDao);

        BillingUpdateBilling2Action action = new BillingUpdateBilling2Action();
        action.setBillingNo("123");
        action.setMessageNotes("FAKE note");
        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(line.getPayeeNo()).isEqualTo("111111");
        verify(billingmasterDao, never()).update(any(Billingmaster.class));
    }

    @Test
    void shouldSetPayee_whenProviderIsSent() throws Exception {
        request.setMethod("POST");
        request.setParameter("billingProvider", "999998");
        request.getSession().setAttribute("user", "999998");
        Billingmaster line = new Billingmaster();
        line.setPayeeNo("111111");
        BillingmasterDAO billingmasterDao = mock(BillingmasterDAO.class);
        when(billingmasterDao.getBillingMasterByBillingNo("123")).thenReturn(List.of(line));
        registerBillingDaos(billingmasterDao);

        BillingUpdateBilling2Action action = new BillingUpdateBilling2Action();
        action.setBillingNo("123");
        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verify(billingmasterDao).update(line);
        assertThat(line.getPayeeNo()).isEqualTo("999998");
    }

    /**
     * Issue #4673: the receipt shown after Update Invoice is the view of the receipt the update
     * came from. A private bill is saved one row per service line and its receipt combines them,
     * so the view names every one of them, the updated bill first.
     */
    @Test
    void shouldShowTheWholeReceiptAgain_whenItCombinesSeveralBills() throws Exception {
        request.setMethod("POST");
        request.getSession().setAttribute("user", "999998");
        registerBillingDaos(mock(BillingmasterDAO.class));

        BillingUpdateBilling2Action action = new BillingUpdateBilling2Action();
        action.setBillingNo("101");
        action.setReceiptBillingNo(new String[]{"101", "102"});
        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.getReceiptView())
                .isEqualTo("/billing/CA/BC/billingView?billing_no=101&billing_no=102&receipt=yes");
    }

    @Test
    void shouldShowTheUpdatedBillAlone_whenTheReceiptNamesNoOtherBill() throws Exception {
        request.setMethod("POST");
        request.getSession().setAttribute("user", "999998");
        registerBillingDaos(mock(BillingmasterDAO.class));

        BillingUpdateBilling2Action action = new BillingUpdateBilling2Action();
        action.setBillingNo("123");
        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.getReceiptView()).isEqualTo("/billing/CA/BC/billingView?billing_no=123&receipt=yes");
    }

    /** The receipt's bill numbers end up in the redirect: anything else is refused before any write. */
    @Test
    void shouldRefuseBeforeAnyWrite_whenAReceiptBillIsNotABillNumber() throws Exception {
        request.setMethod("POST");
        request.getSession().setAttribute("user", "999998");
        BillingmasterDAO billingmasterDao = mock(BillingmasterDAO.class);
        registerBillingDaos(billingmasterDao);

        BillingUpdateBilling2Action action = new BillingUpdateBilling2Action();
        action.setBillingNo("101");
        action.setReceiptBillingNo(new String[]{"101", "102&receipt=no"});
        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(action.getReceiptView()).isNull();
        verifyNoInteractions(billingmasterDao);
    }

    /** The DAOs the update reaches through MSPReconcile, BillingViewBean and BillingNote. */
    private void registerBillingDaos(BillingmasterDAO billingmasterDao) {
        registerMock(BillingmasterDAO.class, billingmasterDao);
        registerMock(BillRecipientsDao.class, mock(BillRecipientsDao.class));
        registerMock(BillingHistoryDao.class, mock(BillingHistoryDao.class));
        registerMock(BillingDao.class, mock(BillingDao.class));
        registerMock(BillingNoteDao.class, mock(BillingNoteDao.class));
    }
}
