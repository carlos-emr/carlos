/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.encounter.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.AppointmentManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.web.JspRenderServer;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Renders the three classic-encounter fragments that no page includes any more,
 * {@code encounter-head.jspf}, {@code encounter-header-bar.jspf} and
 * {@code encounter-row-three.jspf}, and pins what fe28ab77bc (#6300) changed in them: each now
 * declares the tag libraries it uses ({@code oscar} in all three, {@code security} in row three),
 * so each translates on its own and its {@code oscar:} and {@code security:} tags run instead of
 * reaching the browser as template text.
 *
 * <p>Since the classic encounter page {@code Index2.jsp} stopped including them (5a85f66af4), no
 * production page supplies those directives, and the {@code jspc} build ({@code jsp-compile})
 * skips every {@code .jspf} file outside its short list of dispatched fragments. Before #6336,
 * then, nothing translated these fragments with Jasper: the directive audit
 * ({@code scripts/ci/test_jsp_directive_prefixes.py}) only reads their text. Each fragment is
 * rendered here through its own test-only wrapper page that declares no tag library. Jasper scopes
 * a taglib directive to its translation unit, so a wrapper that declared {@code oscar}, or one
 * that included the fragments one after another, would make the tags render whatever a fragment
 * declares. A wrapper supplies only what {@code Index2.jsp} supplied before the include: the
 * scriptlet variables {@code roleName$}, {@code bPrincipalControl} and {@code bPrincipalDisplay},
 * the {@code bean} and {@code ctx} request attributes, and the attributes
 * {@code EctIncomingEncounter2Action.setEncounterAttributes} sets (synthetic FAKE values).</p>
 *
 * <p>The tags these renders reach look their collaborators up per call:
 * {@code DemographicNameAgeTag} builds a new {@code DemographicData}, whose instance fields read
 * {@code DemographicCustDao} and {@code DemographicManager}; {@code NextApptTag} reads
 * {@code AppointmentManager}; {@code SecurityTag} reads {@code SecObjPrivilegeDao} through
 * {@code OscarRoleObjectPrivilege}; {@code CarlosPropertiesCheck} reads the shared
 * {@code CarlosProperties}, whose {@code billregion} this class sets per render and puts back
 * afterwards. None of the classes these pages load keeps a {@code SpringUtils} bean in a static
 * field.</p>
 */
@Tag("integration")
@Tag("encounter")
class EncounterIncludeFragmentsJspIntegrationTest {
    private static final String WRAPPERS = "/WEB-INF/jsp/encounter-fragment-wrappers/";
    private static final String HEAD_PAGE = WRAPPERS + "encounter-head.jsp";
    private static final String HEADER_BAR_PAGE = WRAPPERS + "encounter-header-bar.jsp";
    private static final String ROW_THREE_PAGE = WRAPPERS + "encounter-row-three.jsp";
    private static final String PAGE_DIRECTIVE =
            "<%@ page contentType=\"text/html; charset=UTF-8\" pageEncoding=\"UTF-8\" %>";
    private static final String BODY_PAGE_START =
            "<!DOCTYPE html>\n<html><head><title>wrapper</title></head>\n<body>\n";

    /** The retired Index2.jsp around encounter-head.jspf: the fragment is the page's head. */
    private static final String HEAD_WRAPPER = PAGE_DIRECTIVE + "<!DOCTYPE html>\n<html>\n"
            + "<%@ include file=\"/WEB-INF/jsp/encounter/includes/encounter-head.jspf\" %>\n"
            + "<body></body>\n</html>\n";
    /** The retired Index2.jsp around encounter-header-bar.jspf: the bar opens the page body. */
    private static final String HEADER_BAR_WRAPPER = PAGE_DIRECTIVE + BODY_PAGE_START
            + "<%@ include file=\"/WEB-INF/jsp/encounter/includes/encounter-header-bar.jspf\" %>\n"
            + "</body>\n</html>\n";
    /**
     * The retired Index2.jsp around encounter-row-three.jspf: its scriptlet variables (the chart
     * role list and the principal-only save flags, false unless a principal rule applies) and the
     * encForm the row's buttons submit.
     */
    private static final String ROW_THREE_WRAPPER = PAGE_DIRECTIVE + "<%\n"
            + "    String roleName$ = (String) session.getAttribute(\"userrole\") + \",\"\n"
            + "            + (String) session.getAttribute(\"user\");\n"
            + "    boolean bPrincipalControl = false;\n"
            + "    boolean bPrincipalDisplay = false;\n"
            + "%>" + BODY_PAGE_START
            + "<form name=\"encForm\" action=\"<%= request.getContextPath() %>/encounter/SaveEncounter2\""
            + " method=\"POST\">\n"
            + "<%@ include file=\"/WEB-INF/jsp/encounter/includes/encounter-row-three.jspf\" %>\n"
            + "</form>\n</body>\n</html>\n";

    private static final String PATIENT = "1001";
    private static final Integer PATIENT_ID = 1001;
    private static final String PROVIDER = "999998";
    private static final String LAST_NAME = "FAKE-Lastname";
    private static final String FIRST_NAME = "FAKE-Firstname";
    private static final int BIRTH_YEAR = 1980;
    private static final int BIRTH_MONTH = 4;
    private static final int BIRTH_DAY = 7;
    private static final String NEXT_APPOINTMENT = "2026-12-01";
    private static final String BILL_REGION = "billregion";
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("oscarResources", Locale.ENGLISH);

    @TempDir
    static Path directory;
    private static JspRenderServer server;
    private static boolean billRegionSaved;
    /** The raw billregion value before this class, null when the key was absent. */
    private static Object billRegionBefore;

    @BeforeAll
    static void startServer() throws Exception {
        for (String wrapper : List.of(HEAD_WRAPPER, HEADER_BAR_WRAPPER, ROW_THREE_WRAPPER)) {
            // A wrapper that declared a library would supply the prefixes these renders are about.
            assertThat(wrapper).doesNotContain("taglib");
        }
        billRegionBefore = CarlosProperties.getInstance().get(BILL_REGION);
        billRegionSaved = true;

        Demographic patient = new Demographic();
        patient.setDemographicNo(PATIENT_ID);
        patient.setLastName(LAST_NAME);
        patient.setFirstName(FIRST_NAME);
        patient.setSex("F");
        // The schema's zero-padded text columns, as DemographicData reads them.
        patient.setYearOfBirth(String.valueOf(BIRTH_YEAR));
        patient.setMonthOfBirth(String.format("%02d", BIRTH_MONTH));
        patient.setDateOfBirth(String.format("%02d", BIRTH_DAY));
        DemographicManager demographics = mock(DemographicManager.class);
        when(demographics.getDemographic(any(LoggedInInfo.class), eq(PATIENT_ID))).thenReturn(patient);
        AppointmentManager appointments = mock(AppointmentManager.class);
        when(appointments.getNextAppointmentDate(eq(PATIENT_ID))).thenReturn(NEXT_APPOINTMENT);

        server = JspRenderServer.builder(directory)
                .file(HEAD_PAGE, HEAD_WRAPPER)
                .file(HEADER_BAR_PAGE, HEADER_BAR_WRAPPER)
                .file(ROW_THREE_PAGE, ROW_THREE_WRAPPER)
                .bean(SecObjPrivilegeDao.class, JspRenderServer.privilegeDaoGranting("doctor", "x"))
                .bean(DemographicManager.class, demographics)
                .bean(DemographicCustDao.class, mock(DemographicCustDao.class))
                .bean(AppointmentManager.class, appointments)
                .start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        try {
            if (server != null) {
                server.close();
            }
        } finally {
            if (billRegionSaved) {
                CarlosProperties properties = CarlosProperties.getInstance();
                if (billRegionBefore == null) {
                    properties.remove(BILL_REGION);
                } else {
                    properties.put(BILL_REGION, billRegionBefore);
                }
            }
        }
    }

    @Test
    void shouldTitleTheEncounterWithThePatientsNameAndAge_throughTheHeadFragmentsOwnOscarTaglib() throws Exception {
        JspRenderServer.RenderedPage page =
                server.render(HEAD_PAGE, request -> openEncounter(request, "doctor")).assertOk();

        // DemographicNameAgeString prints "last, first sex age"; an adult's age is whole years.
        int years = Period.between(LocalDate.of(BIRTH_YEAR, BIRTH_MONTH, BIRTH_DAY), LocalDate.now())
                .getYears();
        Document html = page.document();
        assertThat(html.title()).isEqualTo(BUNDLE.getString("encounter.Index.title") + " - "
                + LAST_NAME + ", " + FIRST_NAME + " F " + years + " " + BUNDLE.getString("global.years"));
        assertThat(page.body()).doesNotContain("<oscar:");
        // The rest of the head ran too: the configuration script encounter.js reads.
        Element configuration = html.select("script").stream()
                .filter(script -> script.data().contains("var encounterConfig"))
                .findFirst().orElse(null);
        assertThat(configuration).isNotNull();
        assertThat(configuration.data()).contains("demographicNo: \"" + PATIENT + "\"");
    }

    @Test
    void shouldShowTheNextAppointment_throughTheHeaderBarsOwnOscarTaglib() throws Exception {
        JspRenderServer.RenderedPage page =
                server.render(HEADER_BAR_PAGE, request -> openEncounter(request, "doctor")).assertOk();

        Element nextAppointment = page.document().selectFirst("a[title='Click to see appointment history'] > span");
        assertThat(nextAppointment).isNotNull();
        assertThat(nextAppointment.text())
                .isEqualTo(BUNDLE.getString("encounter.headerBar.nextAppt") + " " + NEXT_APPOINTMENT);
        assertThat(page.body()).doesNotContain("<oscar:");
    }

    @Test
    void shouldOfferSaveSignAndBill_whenTheRoleMayWriteTheChartAndTheRegionBillsInBc() throws Exception {
        List<String> buttons = rowThreeButtons("doctor", "BC");

        assertThat(buttons).containsExactly(text("global.btnPrint"), text("encounter.Index.btnSave"),
                text("encounter.Index.btnSignSave"), text("encounter.Index.btnSignSaveBill"),
                text("encounter.Index.btnSign"), text("global.btnExit"));
    }

    @Test
    void shouldOfferSaveAndSignWithoutBilling_whenTheRegionIsOntario() throws Exception {
        List<String> buttons = rowThreeButtons("doctor", "ON");

        assertThat(buttons).containsExactly(text("global.btnPrint"), text("encounter.Index.btnSave"),
                text("encounter.Index.btnSignSave"), text("encounter.Index.btnSign"), text("global.btnExit"));
    }

    @Test
    void shouldOfferOnlyPrintAndExit_whenTheRoleMayNotWriteTheChart() throws Exception {
        List<String> buttons = rowThreeButtons("receptionist", "BC");

        assertThat(buttons).containsExactly(text("global.btnPrint"), text("global.btnExit"));
    }

    /**
     * Renders row three for a session in {@code role} with {@code billregion} set to {@code region}
     * and returns the values of its buttons, in page order. The save buttons sit inside
     * {@code <security:oscarSec objectName="_eChart" rights="w">}, the bill button also inside
     * {@code <oscar:oscarPropertiesCheck property="billregion" value="BC">}; the Print and Exit
     * buttons are outside both.
     */
    private static List<String> rowThreeButtons(String role, String region) throws Exception {
        CarlosProperties.getInstance().setProperty(BILL_REGION, region);
        JspRenderServer.RenderedPage page =
                server.render(ROW_THREE_PAGE, request -> openEncounter(request, role)).assertOk();

        assertThat(page.body()).doesNotContain("<oscar:").doesNotContain("<security:");
        // The row translated whole: its encounter text area holds the chart's text.
        Element note = page.document().selectFirst("#rowThree textarea[name=enTextarea]");
        assertThat(note).isNotNull();
        assertThat(note.text()).isEqualTo("FAKE encounter text");
        return page.document().select("input[type=button]").eachAttr("value");
    }

    private static String text(String key) {
        return BUNDLE.getString(key);
    }

    /**
     * What a signed-in chart request carried into Index2.jsp: the session's role, provider and
     * LoggedInInfo, the chart's EctSessionBean as {@code bean}, the context path as {@code ctx},
     * and the attributes EctIncomingEncounter2Action.setEncounterAttributes sets.
     */
    private static void openEncounter(HttpServletRequest request, String role) {
        request.getSession().setAttribute("userrole", role);
        request.getSession().setAttribute("user", PROVIDER);
        LoggedInInfo login = new LoggedInInfo();
        login.setLoggedInProvider(new Provider(PROVIDER));
        login.setLocale(Locale.ENGLISH);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);

        EctSessionBean bean = new EctSessionBean();
        bean.demographicNo = PATIENT;
        bean.providerNo = PROVIDER;
        bean.appointmentNo = "2002";
        bean.eChartId = "3003";
        bean.reason = "FAKE reason";
        bean.patientLastName = LAST_NAME;
        bean.patientFirstName = FIRST_NAME;
        bean.patientSex = "F";
        bean.patientAge = "46";
        request.setAttribute("bean", bean);
        request.setAttribute("ctx", request.getContextPath());

        request.setAttribute("pAge", "46");
        request.setAttribute("popUrl", "");
        request.setAttribute("templateNames", List.of());
        request.setAttribute("famDocName", "FAKE-Doctor");
        request.setAttribute("famDocSurname", "FAKE-Family");
        request.setAttribute("rowOneSize", "60");
        request.setAttribute("rowTwoSize", "60");
        request.setAttribute("rowThreeSize", "378");
        request.setAttribute("presBoxSize", "30");
        request.setAttribute("hasSplitChart", Boolean.FALSE);
        request.setAttribute("consumption", 1);
        request.setAttribute("consumptionColor", "green");
        request.setAttribute("bSplit", Boolean.FALSE);
        request.setAttribute("encounterText", "FAKE encounter text");
    }
}
