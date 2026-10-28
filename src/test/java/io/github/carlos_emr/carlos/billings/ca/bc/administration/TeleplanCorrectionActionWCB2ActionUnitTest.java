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
package io.github.carlos_emr.carlos.billings.ca.bc.administration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import io.github.carlos_emr.carlos.billing.CA.BC.dao.BillRecipientsDao;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.BillingHistoryDao;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.WcbDao;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingHistoryDAO;
import io.github.carlos_emr.carlos.providers.data.ProviderData;
import io.github.carlos_emr.carlos.entities.Billingmaster;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockedConstruction;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The BC WCB claim correction route (#4439 D11): the simulation's claim links open the correction page
 * by GET, and the page's Submit and Settle Bill buttons post the save. Settle Bill settles the bill, and
 * every save comes back to the bill's correction page.
 *
 * @since 2026-10-18
 */
@DisplayName("TeleplanCorrectionActionWCB2Action (BC WCB claim correction)")
@Tag("unit")
@Tag("billing")
@Tag("security")
class TeleplanCorrectionActionWCB2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;
    private MockedConstruction<BillingHistoryDAO> billingHistoryConstruction;
    private MockedConstruction<ProviderData> providerConstruction;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private BillingDao mockBillingDao;
    @Mock private WcbDao mockWcbDao;
    @Mock private DemographicManager mockDemographicManager;
    @Mock private BillingmasterDAO mockBillingmasterDao;
    @Mock private BillRecipientsDao mockBillRecipientsDao;
    @Mock private BillingHistoryDao mockBillingHistoryDao;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        request = new MockHttpServletRequest();
        request.setContextPath("/carlos");
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(BillingDao.class, mockBillingDao);
        registerMock(WcbDao.class, mockWcbDao);
        registerMock(DemographicManager.class, mockDemographicManager);
        // The save's MSPReconcile and BillingHistoryDAO look these up when they are built.
        registerMock(BillingmasterDAO.class, mockBillingmasterDao);
        registerMock(BillRecipientsDao.class, mockBillRecipientsDao);
        registerMock(BillingHistoryDao.class, mockBillingHistoryDao);
        createAndRegisterMock(BillingServiceDao.class);
        billingHistoryConstruction = org.mockito.Mockito.mockConstruction(BillingHistoryDAO.class);
        providerConstruction = org.mockito.Mockito.mockConstruction(ProviderData.class);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (providerConstruction != null) providerConstruction.close();
        if (billingHistoryConstruction != null) billingHistoryConstruction.close();
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    private TeleplanCorrectionActionWCB2Action postedSave(String submit) {
        request.setMethod("POST");
        request.addParameter("id", "41");
        request.addParameter("billingNo", "40");
        request.addParameter("submit", submit);
        TeleplanCorrectionActionWCB2Action action = new TeleplanCorrectionActionWCB2Action();
        // Struts binds the posted fields to the action's setters.
        action.setId("41");
        action.setBillingNo("40");
        return action;
    }

    @Test
    @DisplayName("should open the correction page, without saving, from a simulation claim link")
    void shouldOpenCorrectionPage_forClaimLinkWithBillingNo() throws Exception {
        request.setMethod("GET");
        request.addParameter("billing_no", "41");

        assertThat(new TeleplanCorrectionActionWCB2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        verifyNoInteractions(mockBillingDao, mockWcbDao, mockBillingmasterDao, mockBillingHistoryDao, mockDemographicManager);
    }

    @ParameterizedTest(name = "billing_no=[{0}]")
    @ValueSource(strings = {"", " 41", "41x", "../41", "9999999999"})
    @DisplayName("should answer 400, not a broken page, for a blank or malformed bill number")
    void shouldRefuseToOpen_forBlankOrMalformedBillingNo(String value) throws Exception {
        request.setMethod("GET");
        request.addParameter("billing_no", value);

        assertThat(new TeleplanCorrectionActionWCB2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(mockBillingDao, mockWcbDao, mockBillingmasterDao, mockBillingHistoryDao, mockDemographicManager);
    }

    @Test
    @DisplayName("should answer 400 when no bill is named")
    void shouldRefuseToOpen_whenNoBillIsNamed() throws Exception {
        request.setMethod("GET");

        assertThat(new TeleplanCorrectionActionWCB2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(mockBillingDao, mockWcbDao, mockBillingmasterDao, mockBillingHistoryDao, mockDemographicManager);
    }

    @ParameterizedTest(name = "{0} with {1}")
    @CsvSource({"GET, id, 41", "HEAD, id, 41", "GET, billingNo, 40", "HEAD, billingNo, 40",
            "GET, submit, Submit", "GET, submit, Settle Bill"})
    @DisplayName("should refuse a GET or HEAD that carries a save parameter, before reading or writing a bill")
    void shouldRejectGetOrHead_whenSaveParameterPresent(String method, String parameter, String value) throws Exception {
        request.setMethod(method);
        request.addParameter("billing_no", "41");
        request.addParameter(parameter, value);

        assertThat(new TeleplanCorrectionActionWCB2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(mockBillingDao, mockWcbDao, mockBillingmasterDao, mockBillingHistoryDao, mockDemographicManager);
    }

    @ParameterizedTest(name = "{0} with {1}")
    @CsvSource({"POST, id, 41", "GET, billing_no, 41"})
    @DisplayName("should require billing administration write rights before opening or saving anything")
    void shouldThrowSecurityException_whenBillingAdminWritePrivilegeMissing(String method, String parameter, String value) {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(false);
        request.setMethod(method);
        request.addParameter(parameter, value);

        assertThatThrownBy(new TeleplanCorrectionActionWCB2Action()::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin.billing)");
        verifyNoInteractions(mockBillingDao, mockWcbDao, mockBillingmasterDao, mockBillingHistoryDao, mockDemographicManager);
    }

    @ParameterizedTest(name = "id=[{0}] billingNo=[{1}]")
    @CsvSource({"41x, 40", "'', 40", "41, ../40", "41, ''", "9999999999, 40"})
    @DisplayName("should answer 400 to a save whose bill numbers are malformed, before reading or writing a bill")
    void shouldRejectSave_whenBillNumbersMalformed(String id, String billingNo) throws Exception {
        request.setMethod("POST");
        request.addParameter("id", id);
        request.addParameter("billingNo", billingNo);
        request.addParameter("submit", "Submit");

        assertThat(new TeleplanCorrectionActionWCB2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(mockBillingDao, mockWcbDao, mockBillingmasterDao, mockBillingHistoryDao, mockDemographicManager);
    }

    @Test
    @DisplayName("should settle the bill when Settle Bill is posted, then reopen its correction page")
    void shouldSettleBill_whenSettleBillButtonPosted() throws Exception {
        Billingmaster billingmaster = new Billingmaster();
        billingmaster.setBillingstatus("O");
        when(mockBillingmasterDao.getBillingmaster(41)).thenReturn(billingmaster);

        assertThat(postedSave(TeleplanCorrectionActionWCB2Action.SUBMIT_SETTLE).execute()).isEqualTo(ActionSupport.NONE);

        verify(mockBillingmasterDao).update(argThat((Billingmaster b) -> "S".equals(b.getBillingstatus())));
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/billing/CA/BC/billingTeleplanCorrectionWCB?billing_no=41");
    }


    @ParameterizedTest
    @ValueSource(strings = {"id", "billingNo"})
    void shouldRejectDuplicateIdentifiers_beforeAnySave(String parameter) throws Exception {
        TeleplanCorrectionActionWCB2Action action = postedSave("Submit");
        request.addParameter(parameter, "999");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(mockBillingDao, mockWcbDao, mockBillingmasterDao, mockBillingHistoryDao);
    }

    @Test
    void shouldUseCanonicalPostedIdentifiers_whenBoundValuesDiffer() throws Exception {
        TeleplanCorrectionActionWCB2Action action = postedSave("Submit");
        action.setId("999");
        action.setBillingNo("998");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        verify(mockBillingDao).find(40);
        verify(mockBillingmasterDao).updateBillingUnitForBillingNumber("", 40);
        assertThat(action.getId()).isEqualTo("41");
        assertThat(action.getBillingNo()).isEqualTo("40");
        assertThat(response.getRedirectedUrl()).endsWith("?billing_no=41");
    }

    @Test
    void shouldSettleHeaderAndClaim_whenPreviouslyNotSubmitted() throws Exception {
        TeleplanCorrectionActionWCB2Action action = postedSave(TeleplanCorrectionActionWCB2Action.SUBMIT_SETTLE);
        action.setStatus(io.github.carlos_emr.carlos.billings.ca.bc.MSP.MSPReconcile.NOTSUBMITTED);
        Billingmaster master = new Billingmaster();
        when(mockBillingmasterDao.getBillingmaster(41)).thenReturn(master);
        Billing header = new Billing();
        when(mockBillingDao.find(40)).thenReturn(header);

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(master.getBillingstatus()).isEqualTo("S");
        assertThat(header.getStatus()).isEqualTo("S");
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FOUND);
    }

    @Test
    void shouldReportSaveFailure_whenPersistenceThrows() throws Exception {
        TeleplanCorrectionActionWCB2Action action = postedSave("Submit");
        org.mockito.Mockito.doThrow(new IllegalStateException("Database unavailable"))
                .when(mockBillingmasterDao).updateBillingUnitForBillingNumber("", 40);

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertThat(response.getRedirectedUrl()).isNull();
    }

    @Test
    void shouldPreserveHeaderStatus_whenSubmitCarriesNoStatus() throws Exception {
        Billing header = new Billing();
        header.setStatus("O");
        when(mockBillingDao.find(40)).thenReturn(header);

        assertThat(postedSave("Submit").execute()).isEqualTo(ActionSupport.NONE);
        assertThat(header.getStatus()).isEqualTo("O");
        verify(mockBillingDao, never()).merge(any(Billing.class));
    }

    @Test
    @DisplayName("should post one id field and the Settle Bill value the action reads, from the correction page")
    void shouldPostOneIdAndTheSettleValue_fromCorrectionPage() throws IOException {
        String page = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/billingTeleplanCorrectionWCB.jsp"),
                StandardCharsets.UTF_8);

        // Struts joins a repeated field ("41, 41"), so a second id made every save fail to read its bill.
        assertThat(Pattern.compile("name=\"id\"").matcher(page).results().count()).isEqualTo(1);
        assertThat(page).contains("name=\"submit\" value=\"" + TeleplanCorrectionActionWCB2Action.SUBMIT_SETTLE + "\"");
    }

    @Test
    @DisplayName("should keep the bill's status when Submit is posted without one, then reopen its correction page")
    void shouldKeepStatus_whenSubmitPostedWithoutStatus() throws Exception {
        assertThat(postedSave("Submit").execute()).isEqualTo(ActionSupport.NONE);

        verify(mockBillingmasterDao, never()).update(any(Billingmaster.class));
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/billing/CA/BC/billingTeleplanCorrectionWCB?billing_no=41");
    }
}
