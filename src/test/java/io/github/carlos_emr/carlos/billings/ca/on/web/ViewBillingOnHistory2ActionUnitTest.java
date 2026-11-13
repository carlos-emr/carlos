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

import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnHistorySpecialistViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnHistoryViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnHistoryViewModel;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The audit row of a patient's Ontario Billing History (Master Record &gt; Billing History): the
 * page lists every invoice of the patient, so opening it is a read of the patient's billing record.
 *
 * @since 2026-11-12
 */
@Tag("unit")
@Tag("billing")
@Tag("security")
@DisplayName("Ontario Billing History audit")
class ViewBillingOnHistory2ActionUnitTest extends CarlosUnitTestBase {

    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final LoggedInInfo login = mock(LoggedInInfo.class);
    private final BillingOnHistoryViewModelAssembler assembler = mock(BillingOnHistoryViewModelAssembler.class);
    private final BillingOnHistorySpecialistViewModelAssembler specialistAssembler =
            mock(BillingOnHistorySpecialistViewModelAssembler.class);

    private MockHttpServletRequest request;
    private MockedStatic<ServletActionContext> servlet;

    @BeforeEach
    void setUpRequest() {
        request = new MockHttpServletRequest();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
        when(security.hasPrivilege(login, "_billing", "r", null)).thenReturn(true);
        when(assembler.assemble(any(), any())).thenReturn(mock(BillingOnHistoryViewModel.class));
    }

    @AfterEach
    void tearDownRequest() {
        servlet.close();
    }

    @Test
    void shouldAuditTheReadAgainstThePatient_whenTheBillingHistoryIsOpened() throws Exception {
        request.setParameter("demographic_no", "42");

        String result = new ViewBillingOnHistory2Action(security, assembler).execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        // One row for the page, keyed by the patient like the chart's own read rows; no per-invoice rows.
        logActionMock.verify(() -> LogAction.addLog(login, LogConst.READ, LogConst.CON_BILLING_HISTORY, "42", "42", null));
        logActionMock.verifyNoMoreInteractions();
    }

    @Test
    void shouldAuditTheReadAgainstThePatient_whenTheSpecialistBillingHistoryIsOpened() throws Exception {
        request.setParameter("demographic_no", "42");
        // The specialist page resolves the patient's display name through the demographic manager.
        registerMock(io.github.carlos_emr.carlos.managers.DemographicManager.class,
                mock(io.github.carlos_emr.carlos.managers.DemographicManager.class));

        String result = new ViewBillingOnHistorySpecialist2Action(security, specialistAssembler).execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        logActionMock.verify(() -> LogAction.addLog(login, LogConst.READ, LogConst.CON_BILLING_HISTORY, "42", "42", null));
        logActionMock.verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"042", "+42", " 42 "})
    void shouldAuditUnderThePatientThePageLists_whenTheNumberIsWrittenAnotherWay(String demographicNo) {
        // The page reads the parameter as an integer, so each of these lists patient 42's invoices.
        ViewBillingOnHistory2Action.auditHistoryRead(login, demographicNo);

        logActionMock.verify(() -> LogAction.addLog(login, LogConst.READ, LogConst.CON_BILLING_HISTORY, "42", "42", null));
        logActionMock.verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "42 OR 1=1", "-5", "0"})
    void shouldNotAuditAPatientRead_whenNoPatientIsNamed(String demographicNo) throws Exception {
        request.setParameter("demographic_no", demographicNo);

        new ViewBillingOnHistory2Action(security, assembler).execute();

        // The page lists nothing for such a value; there is no patient to file a read under.
        logActionMock.verifyNoInteractions();
    }

    @Test
    void shouldNotAudit_whenTheCallerMayNotReadBilling() {
        request.setParameter("demographic_no", "42");
        when(security.hasPrivilege(login, "_billing", "r", null)).thenReturn(false);

        assertThatThrownBy(() -> new ViewBillingOnHistory2Action(security, assembler).execute())
                .isInstanceOf(SecurityException.class);

        logActionMock.verifyNoInteractions();
    }
}
