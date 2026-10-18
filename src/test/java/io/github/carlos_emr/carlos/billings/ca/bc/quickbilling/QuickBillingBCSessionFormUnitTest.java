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
package io.github.carlos_emr.carlos.billings.ca.bc.quickbilling;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingFormData;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingFormData.BillingVisit;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingHistoryDAO;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingNote;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingBillingManager;
import io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingBillingManager.BillingItem;
import io.github.carlos_emr.carlos.billings.ca.service.GstSettingsService;
import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.ProviderData;
import io.github.carlos_emr.carlos.entities.Billingmaster;
import io.github.carlos_emr.carlos.managers.DemographicManager;
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
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Issue #4772: the BC MSP Quick Billing page keeps its header and the bills added so far in one
 * session form, {@code quickBillingBC}, from the first open until the save. The Struts 2 actions
 * had lost that form and answered every request with HTTP 500. These tests drive the page's
 * requests (open, add, remove, save) through both actions on one session.
 *
 * @since 2026-10-16
 */
@Tag("unit")
@Tag("billing")
@DisplayName("BC Quick Billing session form (#4772)")
class QuickBillingBCSessionFormUnitTest extends CarlosUnitTestBase {

    private static final String PROVIDER_NO = "999998";

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedConstruction<BillingFormData> billingFormDataConstruction;
    private MockedConstruction<BillingBillingManager> billingManagerConstruction;
    private MockedConstruction<BillingHistoryDAO> billingHistoryConstruction;
    private MockedConstruction<BillingNote> billingNoteConstruction;

    private final MockHttpSession session = new MockHttpSession();
    private MockHttpServletRequest request;
    private MockHttpServletResponse response = new MockHttpServletResponse();
    private ProviderDataDao providerDao;
    private BillingmasterDAO billingmasterDao;
    private DemographicManager demographicManager;
    private final List<ProviderData> activeProviders = new ArrayList<>();
    private final List<BillingVisit> visitTypes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        SecurityInfoManager securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull())).thenReturn(true);
        providerDao = createAndRegisterMock(ProviderDataDao.class);
        billingmasterDao = createAndRegisterMock(BillingmasterDAO.class);
        demographicManager = createAndRegisterMock(DemographicManager.class);
        registerMock(GstSettingsService.class, mock(GstSettingsService.class));

        ProviderData provider = new ProviderData();
        provider.set(PROVIDER_NO);
        provider.setLastName("FAKE-Doctor");
        provider.setFirstName("Quick");
        provider.setBillingNo("12345");
        provider.setOhipNo("99999");
        activeProviders.add(provider);
        when(providerDao.findAll(false)).thenReturn(activeProviders);
        when(providerDao.findByProviderNo(PROVIDER_NO)).thenReturn(provider);
        when(demographicManager.getDemographic(any(LoggedInInfo.class), anyString()))
                .thenAnswer(invocation -> patient(invocation.getArgument(1)));

        // A real manager only to build a bill line; the handler's own managers are mocked below.
        BillingItem line = new BillingBillingManager().new BillingItem("00100", "1");
        billingFormDataConstruction = mockConstruction(BillingFormData.class,
                (formData, context) -> when(formData.getVisitType(QuickBillingBCHandler.BILLING_PROV)).thenReturn(visitTypes));
        billingManagerConstruction = mockConstruction(BillingBillingManager.class, (manager, context) -> {
            when(manager.getDups2(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenAnswer(invocation -> new ArrayList<>(List.of(line)));
            when(manager.getGrandTotal(any())).thenReturn("33.70");
        });
        billingHistoryConstruction = mockConstruction(BillingHistoryDAO.class);
        billingNoteConstruction = mockConstruction(BillingNote.class);

        session.setAttribute("user", PROVIDER_NO);
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenAnswer(invocation -> request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenAnswer(invocation -> response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mock(LoggedInInfo.class));
    }

    @AfterEach
    void tearDown() {
        loggedInInfoMock.close();
        servletActionContextMock.close();
        billingNoteConstruction.close();
        billingHistoryConstruction.close();
        billingManagerConstruction.close();
        billingFormDataConstruction.close();
    }

    private static Demographic patient(String demographicNo) {
        Demographic demographic = new Demographic();
        demographic.setDemographicNo(Integer.valueOf(demographicNo));
        demographic.setLastName("FAKE-Patient" + demographicNo);
        demographic.setFirstName("Quick");
        demographic.setYearOfBirth("1980");
        demographic.setMonthOfBirth("01");
        demographic.setDateOfBirth("02");
        demographic.setSex("F");
        demographic.setHin("9876543210");
        demographic.setHcType("BC");
        return demographic;
    }

    /** One request of the page, on the shared session, through the quick billing action. */
    private String openOrPost(String name, String value) throws Exception {
        return send("POST", name, value);
    }

    private String send(String method, String name, String value) throws Exception {
        request = new MockHttpServletRequest(method, "/quickBillingBC");
        request.setSession(session);
        response = new MockHttpServletResponse();
        if (name != null) {
            request.setParameter(name, value);
        }
        return new QuickBillingBC2Action().execute();
    }

    private Object save() throws Exception {
        // Kept local: a save started from inside another save replaces the shared request field.
        MockHttpServletRequest saveRequest = new MockHttpServletRequest("POST", "/saveQuickBillingBC");
        saveRequest.setSession(session);
        request = saveRequest;
        response = new MockHttpServletResponse();
        assertThat(new QuickBillingBCSave2Action().execute()).isEqualTo("saved");
        return saveRequest.getAttribute("saved");
    }

    private String add(String demographicNo, String providerNo) throws Exception {
        return openOrPost("data", "{\"visitLocation\":\"A\",\"visitDate\":\"16-10-2026\",\"provider\":\"" + providerNo
                + "\",\"ptNumber\":\"" + demographicNo + "\",\"billingCode\":\"00100\",\"dxCode1\":\"780\",\"halfBilling\":\"1\"}");
    }

    private QuickBillingBCFormBean form() {
        return (QuickBillingBCFormBean) session.getAttribute(QuickBillingBCFormBean.SESSION_ATTRIBUTE);
    }

    @Test
    @DisplayName("should store an empty form with the provider and visit lists in the session when the page opens")
    void shouldStoreEmptyForm_whenPageOpens() throws Exception {
        assertThat(openOrPost(null, null)).isEqualTo(ActionSupport.SUCCESS);

        QuickBillingBCFormBean form = form();
        assertThat(form).isNotNull();
        assertThat(form.getIsHeaderSet()).isFalse();
        assertThat(form.getBillingData()).isEmpty();
        assertThat(form.getProviderList()).isSameAs(activeProviders);
        assertThat(form.getBillingVisitTypes()).isSameAs(visitTypes);
    }

    @Test
    @DisplayName("should keep the first add's header and every added bill across requests")
    void shouldKeepHeaderAndBills_acrossAddRequests() throws Exception {
        openOrPost(null, null);
        assertThat(add("1", PROVIDER_NO)).isEqualTo(ActionSupport.SUCCESS);
        // A later add cannot change the header: the page locks it after the first bill.
        assertThat(add("2", "other")).isEqualTo(ActionSupport.SUCCESS);

        QuickBillingBCFormBean form = form();
        assertThat(form.getIsHeaderSet()).isTrue();
        assertThat(form.getBillingProviderNo()).isEqualTo(PROVIDER_NO);
        assertThat(form.getServiceDate()).isEqualTo("16-10-2026");
        assertThat(form.getCreator()).isEqualTo(PROVIDER_NO);
        assertThat(form.getBillingData()).extracting("patientNo").containsExactly("1", "2");
        assertThat(form.getBillingData()).extracting("grandtotal").containsExactly("33.70", "33.70");
    }

    /**
     * Finding 351: an add whose patient number is empty, or matches no patient (removed since the name
     * search, or typed by hand), used to fail with a NullPointerException. It now adds nothing and the page
     * says so; a known number still adds.
     */
    @Test
    @DisplayName("should add no bill and flag the page when the patient number is empty or unknown, and still add a known one")
    void shouldRefuseAdd_whenPatientNumberEmptyOrUnknown() throws Exception {
        when(demographicManager.getDemographic(any(LoggedInInfo.class), eq("424242"))).thenReturn(null);
        openOrPost(null, null);

        assertThat(add("", PROVIDER_NO)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute(QuickBillingBC2Action.PATIENT_NOT_FOUND)).isEqualTo(Boolean.TRUE);
        assertThat(form().getBillingData()).isEmpty();

        assertThat(add("424242", PROVIDER_NO)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute(QuickBillingBC2Action.PATIENT_NOT_FOUND)).isEqualTo(Boolean.TRUE);
        assertThat(form().getBillingData()).isEmpty();

        assertThat(add("1", PROVIDER_NO)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute(QuickBillingBC2Action.PATIENT_NOT_FOUND)).isNull();
        assertThat(form().getBillingData()).extracting("patientNo").containsExactly("1");
    }

    @Test
    @DisplayName("should show the page's patient-not-found message, encoded, only when the add flagged it")
    void shouldShowPatientNotFoundMessage_onlyWhenFlagged() throws IOException {
        String page = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/quickBillingBC.jsp"),
                StandardCharsets.UTF_8);

        assertThat(page).containsSubsequence(
                "<c:if test=\"${requestScope." + QuickBillingBC2Action.PATIENT_NOT_FOUND + "}\">",
                "<fmt:message key=\"billing.bc.quickBilling.msgPatientNotFound\" var=\"patientNotFoundMessage\"/>",
                "${carlos:forHtml(patientNotFoundMessage)}",
                "</c:if>");
    }

    @Test
    @DisplayName("should add to a new session form when a bill arrives before the page was opened")
    void shouldAddBill_whenSessionHasNoFormYet() throws Exception {
        assertThat(add("1", PROVIDER_NO)).isEqualTo(ActionSupport.SUCCESS);

        assertThat(form().getBillingData()).extracting("patientNo").containsExactly("1");
    }

    @Test
    @DisplayName("should remove the chosen bill and ignore a stale or malformed row number")
    void shouldRemoveBill_andIgnoreStaleOrMalformedRow() throws Exception {
        openOrPost(null, null);
        add("1", PROVIDER_NO);
        add("2", PROVIDER_NO);

        assertThat(openOrPost("remove", "1")).isEqualTo(ActionSupport.SUCCESS);
        assertThat(form().getBillingData()).extracting("patientNo").containsExactly("1");

        // A second click on the same Remove, and a value that is not a row number.
        assertThat(openOrPost("remove", "1")).isEqualTo(ActionSupport.SUCCESS);
        assertThat(openOrPost("remove", "x")).isEqualTo(ActionSupport.SUCCESS);
        assertThat(form().getBillingData()).extracting("patientNo").containsExactly("1");
    }

    @Test
    @DisplayName("should save the session's bills, then clear the form")
    void shouldSaveSessionBills_andClearForm() throws Exception {
        openOrPost(null, null);
        add("1", PROVIDER_NO);
        doAnswer(invocation -> {
            injectDependency(invocation.getArgument(0), "id", 501);
            return null;
        }).when(billingmasterDao).save(any(Billing.class));

        request = new MockHttpServletRequest("POST", "/saveQuickBillingBC");
        request.setSession(session);
        assertThat(new QuickBillingBCSave2Action().execute()).isEqualTo("saved");

        assertThat(request.getAttribute("saved")).isEqualTo(1);
        var order = inOrder(billingmasterDao);
        order.verify(billingmasterDao).save(any(Billing.class));
        order.verify(billingmasterDao).save(any(Billingmaster.class));
        QuickBillingBCFormBean form = form();
        assertThat(form.getBillingData()).isEmpty();
        assertThat(form.getIsHeaderSet()).isFalse();
    }

    @Test
    @DisplayName("should read every add field under the name the page's form gives it")
    void shouldReadAddFields_byThePagesFieldNames() throws IOException {
        // The page posts its form serialized as JSON, keyed by each control's name. A key the handler
        // reads that no control carries is null, and the add fails (a 2025 rename did that to "provider").
        Set<String> pageNames = matches(Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/quickBillingBC.jsp"),
                "\\bname=\"([A-Za-z0-9]+)\"");
        Set<String> handlerKeys = matches(Path.of(
                "src/main/java/io/github/carlos_emr/carlos/billings/ca/bc/quickbilling/QuickBillingBCHandler.java"),
                "billingEntry\\.get\\(\"([A-Za-z0-9]+)\"\\)");
        // QuickBillingBC2Action adds "creator" (the signed-in user) itself.
        handlerKeys.remove("creator");
        assertThat(handlerKeys).contains("provider", "visitLocation", "visitDate", "ptNumber", "billingCode");
        assertThat(pageNames).containsAll(handlerKeys);
    }

    private static Set<String> matches(Path file, String regex) throws IOException {
        Set<String> found = new TreeSet<>();
        Matcher matcher = Pattern.compile(regex).matcher(Files.readString(file, StandardCharsets.UTF_8));
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    @Test
    @DisplayName("should save each bill once when a second Submit arrives while the first is saving")
    void shouldSaveEachBillOnce_whenSecondSubmitArrivesMidSave() throws Exception {
        openOrPost(null, null);
        add("1", PROVIDER_NO);
        // A double click: the second save request reaches the server while the first is still writing.
        Object[] secondSaved = new Object[1];
        boolean[] secondSent = new boolean[1];
        doAnswer(invocation -> {
            injectDependency(invocation.getArgument(0), "id", 502);
            if (!secondSent[0]) {
                secondSent[0] = true;
                secondSaved[0] = save();
            }
            return null;
        }).when(billingmasterDao).save(any(Billing.class));

        assertThat(save()).isEqualTo(1);

        assertThat(secondSaved[0]).isEqualTo(0);
        verify(billingmasterDao, times(1)).save(any(Billing.class));
        verify(billingmasterDao, times(1)).save(any(Billingmaster.class));
    }

    @Test
    @DisplayName("should answer 405 and leave the form unchanged when add or remove is not a POST")
    void shouldRefuseAddAndRemove_whenNotPost() throws Exception {
        openOrPost(null, null);
        add("1", PROVIDER_NO);
        String addData = "{\"visitLocation\":\"B\",\"visitDate\":\"17-10-2026\",\"provider\":\"" + PROVIDER_NO
                + "\",\"ptNumber\":\"2\",\"billingCode\":\"00100\",\"dxCode1\":\"780\",\"halfBilling\":\"1\"}";

        for (String method : List.of("GET", "HEAD")) {
            assertThat(send(method, "data", addData)).as(method + " add").isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).as(method + " add").isEqualTo(405);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            assertThat(send(method, "remove", "0")).as(method + " remove").isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).as(method + " remove").isEqualTo(405);
        }

        QuickBillingBCFormBean form = form();
        assertThat(form.getBillingData()).extracting("patientNo").containsExactly("1");
        assertThat(form.getServiceDate()).isEqualTo("16-10-2026");
        assertThat(form.getVisitLocation()).isEqualTo("A");
    }

    @Test
    @DisplayName("should still open the page with a plain GET")
    void shouldOpenPage_whenPlainGet() throws Exception {
        assertThat(send("GET", null, null)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(form()).isNotNull();
    }

    @Test
    @DisplayName("should keep the header and bills when the session is saved and restored")
    void shouldKeepHeaderAndBills_acrossSessionRestore() throws Exception {
        openOrPost(null, null);
        add("1", PROVIDER_NO);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(form());
        }
        QuickBillingBCFormBean restored;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (QuickBillingBCFormBean) in.readObject();
        }

        assertThat(restored.getIsHeaderSet()).isTrue();
        assertThat(restored.getServiceDate()).isEqualTo("16-10-2026");
        assertThat(restored.getBillingProviderNo()).isEqualTo(PROVIDER_NO);
        assertThat(restored.getBillingData()).extracting("patientNo").containsExactly("1");
        assertThat(restored.getBillingData().get(0).getBillItem()).extracting("serviceCode").containsExactly("00100");
        // The restored form brings its own lock, so its bill list still changes normally.
        assertThat(restored.removeBill(0)).isTrue();
        assertThat(restored.getBillingData()).isEmpty();
    }

    @Test
    @DisplayName("should report nothing saved when the session has no bills")
    void shouldSaveNothing_whenSessionHasNoBills() throws Exception {
        request = new MockHttpServletRequest("POST", "/saveQuickBillingBC");
        request.setSession(session);

        assertThat(new QuickBillingBCSave2Action().execute()).isEqualTo("saved");
        assertThat(request.getAttribute("saved")).isEqualTo(0);
    }
}
