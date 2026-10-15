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

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.entities.Billingmaster;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.BillRecipientsDao;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.BillingHistoryDao;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
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
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Routing and the POST-only save contract of the BC invoice list / adjust bill route (#4343).
 *
 * <p>Every link reaches {@code billing/CA/BC/reprocessBill} by GET, so GET must open a page and
 * never save: Bill Status without a bill, the adjust bill page with {@code billingmaster_no}.
 * A GET or HEAD that carries a save parameter is refused before anything is read or written.
 * A POST from the adjust bill page saves the fields Struts bound to the action's own setters;
 * this used to read a {@code form} field that nothing filled.</p>
 *
 * @since 2026-10-07
 */
@DisplayName("BillingReProcessBill2Action (BC invoice list and adjust bill)")
@Tag("unit")
@Tag("billing")
@Tag("security")
class BillingReProcessBill2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private BillingDao mockBillingDao;
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
        response = new MockHttpServletResponse();
        request.getSession().setAttribute("user", "999998");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(BillingDao.class, mockBillingDao);
        registerMock(BillingmasterDAO.class, mockBillingmasterDao);
        // The action's MSPReconcile field builds these at construction.
        registerMock(BillRecipientsDao.class, mockBillRecipientsDao);
        registerMock(BillingHistoryDao.class, mockBillingHistoryDao);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    @DisplayName("should open Bill Status from a menu or invoice-list link that names no bill")
    void shouldOpenBillStatus_whenNoBillIsNamed() throws Exception {
        request.setMethod("GET");
        request.addParameter("lastName", "FAKE-BILL");

        assertThat(new BillingReProcessBill2Action().execute()).isEqualTo(BillingReProcessBill2Action.LIST);
        verifyNoInteractions(mockBillingmasterDao, mockBillingDao, mockBillingHistoryDao);
    }

    @Test
    @DisplayName("should open the adjust bill page, without saving, from an Edit link")
    void shouldOpenAdjustBillPage_forEditLinkWithBillingmasterNo() throws Exception {
        request.setMethod("GET");
        request.addParameter("billingmaster_no", "41");

        assertThat(new BillingReProcessBill2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        verifyNoInteractions(mockBillingmasterDao, mockBillingDao, mockBillingHistoryDao);
    }

    @ParameterizedTest(name = "billingmaster_no=[{0}]")
    @ValueSource(strings = {"", " 41", "41x", "../41", "9999999999"})
    @DisplayName("should open Bill Status, not a broken adjust page, for a blank or malformed bill number")
    void shouldOpenBillStatus_forBlankOrMalformedBillingmasterNo(String value) throws Exception {
        request.setMethod("GET");
        request.addParameter("billingmaster_no", value);

        assertThat(new BillingReProcessBill2Action().execute()).isEqualTo(BillingReProcessBill2Action.LIST);
        verifyNoInteractions(mockBillingmasterDao, mockBillingDao, mockBillingHistoryDao);
    }

    @ParameterizedTest(name = "{0} with {1}")
    @CsvSource({"GET, billCheck, 1_41", "HEAD, billCheck, 1_41", "GET, billingmasterNo, 41", "HEAD, billingmasterNo, 41"})
    @DisplayName("should refuse a GET or HEAD that carries a save parameter, before reading or writing a bill")
    void shouldRejectGetOrHead_whenSaveParameterPresent(String method, String parameter, String value) throws Exception {
        request.setMethod(method);
        request.addParameter(parameter, value);

        assertThat(new BillingReProcessBill2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(mockBillingmasterDao, mockBillingDao, mockBillingHistoryDao);
    }

    @ParameterizedTest(name = "{0} with {1}")
    @CsvSource({"POST, billingmasterNo, 41", "GET, billingmaster_no, 41", "GET, lastName, FAKE-BILL"})
    @DisplayName("should require billing write rights before opening or saving anything")
    void shouldThrowSecurityException_whenBillingWritePrivilegeMissing(String method, String parameter, String value) {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(false);
        request.setMethod(method);
        request.addParameter(parameter, value);

        assertThatThrownBy(new BillingReProcessBill2Action()::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_billing)");
        verifyNoInteractions(mockBillingmasterDao, mockBillingDao, mockBillingHistoryDao);
    }

    @Test
    @DisplayName("should save every field the adjust bill page posts, including the providerNo select")
    void shouldCopyEveryBoundField_intoSubmittedForm() throws Exception {
        request.setMethod("POST");
        BillingReProcessBill2Action action = new BillingReProcessBill2Action();
        List<Method> getters = new ArrayList<>();
        for (Method getter : BillingReProcessBill2Form.class.getMethods()) {
            if (getter.getName().startsWith("get") && getter.getParameterCount() == 0
                    && getter.getReturnType() == String.class) {
                getters.add(getter);
            }
        }
        // The save takes the patient and the invoice from the stored bill, not from the page.
        List<String> fromStoredBill = List.of("DemoNo", "BillNumber");
        for (Method getter : getters) {
            String property = getter.getName().substring(3);
            // Each form field needs a setter on the action that Struts 7 may bind (@StrutsParameter).
            Method setter = BillingReProcessBill2Action.class.getMethod("set" + property, String.class);
            assertThat(setter.isAnnotationPresent(StrutsParameter.class)).as(setter.getName()).isTrue();
            setter.invoke(action, "FAKE-" + property);
        }

        BillingReProcessBill2Form submitted = action.submittedForm();

        assertThat(getters).hasSizeGreaterThan(40);
        for (Method getter : getters) {
            String property = getter.getName().substring(3);
            Object expected = fromStoredBill.contains(property) ? null : "FAKE-" + property;
            assertThat(getter.invoke(submitted)).as(getter.getName()).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("should save under the stored bill's own patient and invoice, whatever the page's hidden fields say")
    void shouldUseStoredPatientAndInvoice_whenHiddenFieldsDiffer() {
        Billingmaster stored = new Billingmaster();
        stored.setBillingNo(7);
        Billing bill = new Billing();
        bill.setDemographicNo(1);
        when(mockBillingmasterDao.getBillingMasterByBillingMasterNo("41")).thenReturn(stored);
        when(mockBillingmasterDao.getBilling(7)).thenReturn(bill);
        BillingReProcessBill2Action action = new BillingReProcessBill2Action();
        action.setBillingmasterNo("41");
        action.setDemoNo("2");
        action.setBillNumber("999");

        BillingReProcessBill2Action.StoredBill target = action.storedBill(action.submittedForm().getBillingmasterNo());

        assertThat(target.billingmaster()).isSameAs(stored);
        assertThat(target.demographicNo()).isEqualTo("1");
        assertThat(target.billNumber()).isEqualTo("7");
    }

    @ParameterizedTest(name = "{0}=[{1}]")
    @CsvSource({"billingmasterNo, ''", "billingmasterNo, 41x", "billingmasterNo, 9999999999", "billCheck, 41", "billCheck, 1_x"})
    @DisplayName("should answer a malformed save with 400 before reading or writing a bill")
    void shouldRejectSave_whenBillNumberMalformed(String parameter, String value) throws Exception {
        request.setMethod("POST");
        request.addParameter(parameter, value);

        assertThat(new BillingReProcessBill2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(mockBillingmasterDao, mockBillingDao, mockBillingHistoryDao);
    }

    @Test
    @DisplayName("should answer a save for a bill that does not exist with 404, writing nothing")
    void shouldRejectSave_whenBillDoesNotExist() throws Exception {
        request.setMethod("POST");
        request.addParameter("billingmasterNo", "41");
        BillingReProcessBill2Action action = new BillingReProcessBill2Action();
        action.setBillingmasterNo("41");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        verify(mockBillingmasterDao, never()).update(any(Billingmaster.class));
        verifyNoInteractions(mockBillingDao, mockBillingHistoryDao);
    }

    @Test
    @DisplayName("should treat a submit with no button as no button, not as a missing value")
    void shouldDefaultSubmitToEmpty_whenNoButtonWasPosted() {
        BillingReProcessBill2Action action = new BillingReProcessBill2Action();
        action.setBillingmasterNo("41");

        assertThat(action.submittedForm().getSubmit()).isEmpty();
    }

    @Test
    @DisplayName("should map the open, save and list results to the two BC pages")
    void shouldMapResults_toAdjustBillAndBillStatusPages() throws IOException {
        String struts = Files.readString(projectPath("src/main/webapp/WEB-INF/classes/struts-billing.xml"),
                StandardCharsets.UTF_8);
        Matcher action = Pattern.compile("<action name=\"billing/CA/BC/reprocessBill\"[^>]*>(.*?)</action>",
                Pattern.DOTALL).matcher(struts);

        assertThat(action.find()).isTrue();
        assertThat(action.group(1))
                .contains("<result name=\"success\">/WEB-INF/jsp/billing/CA/BC/adjustBill.jsp</result>")
                .contains("<result name=\"save\">/WEB-INF/jsp/billing/CA/BC/billStatus.jsp</result>")
                .contains("<result name=\"list\">/WEB-INF/jsp/billing/CA/BC/billStatus.jsp</result>");
    }

    @Test
    @DisplayName("should post a fixed, recognised value from every adjust bill button, whatever the language")
    void shouldPostRecognisedSubmitValues_fromEveryAdjustBillButton() throws IOException {
        String jsp = Files.readString(projectPath("src/main/webapp/WEB-INF/jsp/billing/CA/BC/adjustBill.jsp"),
                StandardCharsets.UTF_8);
        List<String> recognised = List.of(BillingReProcessBill2Action.SUBMIT_REPROCESS, BillingReProcessBill2Action.SUBMIT_RESUBMIT,
                BillingReProcessBill2Action.SUBMIT_REPROCESS_AND_RESUBMIT, BillingReProcessBill2Action.SUBMIT_SETTLE,
                BillingReProcessBill2Action.SUBMIT_REVERT_TO_PWE);
        Matcher button = Pattern.compile("<(input|button)\\b[^>]*\\bname=\"submit\"[^>]*>").matcher(jsp);
        List<String> values = new ArrayList<>();
        while (button.find()) {
            assertThat(button.group(1)).as("a translated <input> label would be posted as the value: %s", button.group())
                    .isEqualTo("button");
            Matcher value = Pattern.compile("\\bvalue=\"([^\"]*)\"").matcher(button.group());
            assertThat(value.find()).as(button.group()).isTrue();
            values.add(value.group(1));
        }

        assertThat(values).hasSize(6).allSatisfy(v -> assertThat(recognised).contains(v)).containsAll(recognised);
    }

    private static Path projectPath(String relative) {
        Path current = Path.of(System.getProperty("basedir", System.getProperty("user.dir"))).toAbsolutePath();
        for (int up = 0; current != null && up < 6; up++, current = current.getParent()) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to locate " + relative);
    }
}
