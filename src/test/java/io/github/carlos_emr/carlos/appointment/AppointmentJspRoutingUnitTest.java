/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * Maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("Appointment JSP Routing Unit Tests")
@Tag("unit")
@Tag("appointment")
class AppointmentJspRoutingUnitTest {

    private static final Pattern FORCE_WINDOW_PATHS_PATTERN = Pattern.compile(
            "(?:(?:var|let|const)\\s+)?(?:window\\.)?forceWindowPaths\\s*=\\s*(?:(?:window\\.)?forceWindowPaths\\s*\\|\\|\\s*)?\\[(?<body>[\\s\\S]*?)]\\s*;?");
    private static final Pattern POPUP_FOCUS_PAGE_RETURN_PATTERN = Pattern.compile(
            "function\\s+popupFocusPage\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?return\\s+popup;\\s*}");

    @Test
    void shouldRouteEditAndReceipt_toValidatedTargets() throws IOException {
        String editAppointment = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/editappointment.jsp");
        String globalJs = readJspContent("src/main/webapp/js/global.js");

        assertThat(editAppointment).contains("/demographic/DemographicSearch")
                .contains("/appointment/UpdateRecord")
                .contains("/appointment/DeleteRecord")
                .contains("/appointment/appointmentgrouprecords")
                .contains("/appointment/CutRecord")
                .contains("/appointment/appointmentcopyrecord")
                .contains("/appointment/appointmentviewrecordcard")
                .doesNotContain("/appointment/appointmentcontrol")
                .contains("/appointment/appointmenteditrepeatbooking")
                .doesNotContain("appointmenteditrepeatbooking.jsp");
        String printReceiptButton = extractInputElement(editAppointment, "printReceiptButton");
        assertThat(printReceiptButton)
                .as("the receipt update must use the same validated submit path as a normal update")
                .contains(
                        "formaction=\"<%=request.getContextPath() %>/appointment/UpdateRecord\"",
                        "onclick=\"",
                        "displaymode.value='Update Appt'",
                        "printReceipt.value='1'",
                        "onButUpdate()");
        assertThat(editAppointment)
                .as("the validated submit path must reserve the receipt window and explain a failed handoff")
                .contains(
                        "if (document.EDITAPPT.printReceipt.value === '1')",
                        "reserveAppointmentReceiptWindow()",
                        "popupFocusPage(350, 750, '', 'appointmentReceipt')",
                        "receiptDocument.title = '${carlos:forJavaScript(appointmentReceiptTitle)}'",
                        "receiptDocument.body.textContent = '${carlos:forJavaScript(appointmentReceiptPending)}'");
        assertThat(globalJs)
                .as("popupFocusPage must return the reserved window so callers can manage its lifecycle")
                .containsPattern(POPUP_FOCUS_PAGE_RETURN_PATTERN);
    }

    @Test
    void shouldRouteAddAppointment_toFinalTargets() throws IOException {
        String addAppointment = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/addappointment.jsp");

        assertThat(addAppointment).contains("/appointment/AddRecord")
                .contains("/appointment/appointmentgrouprecords")
                .contains("/demographic/DemographicSearch")
                .doesNotContain("/appointment/appointmentcontrol")
                .contains("/appointment/appointmentrepeatbooking")
                .contains("id=\"addButton\" class=\"btn btn-primary\"")
                .contains("formaction=\"<%=request.getContextPath()%>/appointment/AddRecord\"")
                .doesNotContain("appointmentrepeatbooking.jsp");
    }

    @Test
    void shouldRouteRepeatAndGroup_toFinalTargets() throws IOException {
        String editRepeat = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/appointmenteditrepeatbooking.jsp");
        String repeat = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/appointmentrepeatbooking.jsp");
        String groupRecords = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/appointmentgrouprecords.jsp");

        assertThat(editRepeat).contains("action=\"<%=request.getContextPath() %>/appointment/appointmenteditrepeatbooking\"")
                .doesNotContain("action=\"appointmenteditrepeatbooking.jsp\"");

        assertThat(repeat).contains("<jsp:forward page=\"/WEB-INF/jsp/appointment/appointmenteditrepeatbooking.jsp\"/>")
                .doesNotContain("action=\"appointmentrepeatbooking.jsp\"");

        assertThat(groupRecords).contains("action=\"<%=request.getContextPath() %>/appointment/appointmentgrouprecords\"")
                .doesNotContain("action=\"appointmentgrouprecords.jsp\"");
    }

    @Test
    void shouldRouteReceiptResults_toDedicatedWindow() throws IOException {
        String addRecord = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/appointmentaddarecord.jsp");
        String updateRecord = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/appointmentupdatearecord.jsp");

        assertThat(addRecord).contains("/appointment/printappointment?appointment_no=")
                .contains("pageContext.request.contextPath")
                .contains("carlos:forJavaScript(carlos:forUriComponent(apptId))")
                .doesNotContain("printappointment.jsp?appointment_no=");

        assertThat(updateRecord).contains("/appointment/printappointment?appointment_no=")
                .containsAnyOf("request.getContextPath()", "pageContext.request.contextPath")
                .contains("pageContext.request.contextPath")
                .contains("carlos:forJavaScript(carlos:forUriComponent(appointmentNo))")
                .as("the update result must reuse the dedicated receipt window, not its own attachment window")
                .contains("popupFocusPage(350, 750,", "'appointmentReceipt'")
                .doesNotContain("printappointment.jsp?appointment_no=");
    }

    @Test
    void shouldRouteLiveCallers_toFinalTargets() throws IOException {
        String providerDay = readJspContent("src/main/webapp/WEB-INF/jsp/provider/appointmentprovideradminday.jsp");
        String demographicApptHistory = readJspContent("src/main/webapp/WEB-INF/jsp/demographic/demographicappthistory.jsp");
        String ticklerAdd = readJspContent("src/main/webapp/WEB-INF/jsp/tickler/ticklerAdd.jsp");
        String addAlternateContact = readJspContent("src/main/webapp/WEB-INF/jsp/demographic/AddAlternateContact.jsp");

        assertThat(providerDay).contains("/appointment/addappointment?")
                .contains("/appointment/editappointment?")
                .doesNotContain("/appointment/appointmentcontrol")
                .contains("return ctx + '/appointment/addappointment'")
                .doesNotContain("/appointment/addappointment.jsp");

        assertThat(demographicApptHistory).contains("/appointment/editappointment?demographic_no=")
                .doesNotContain("/appointment/appointmentcontrol");

        assertThat(ticklerAdd).contains("action=\"<%= request.getContextPath() %>/demographic/DemographicSearch\"")
                .contains("name=\"displaymode\" value=\"Search \"")
                .doesNotContain("/appointment/appointmentcontrol");

        assertThat(addAlternateContact).contains("action=\"<%= request.getContextPath() %>/demographic/DemographicSearch\"")
                .contains("name=\"displaymode\"")
                .contains("value=\"Search \"")
                .doesNotContain("/appointment/appointmentcontrol");
    }

    @Test
    void shouldReservePopupRoutes_withoutLegacyDispatcher() throws IOException {
        String oscarJs = readJspContent("src/main/webapp/share/javascript/Oscar.js");
        String schedulingStruts = readJspContent("src/main/webapp/WEB-INF/classes/struts-scheduling.xml");

        Matcher forceWindowPathsMatcher = FORCE_WINDOW_PATHS_PATTERN.matcher(oscarJs);
        assertThat(forceWindowPathsMatcher.find())
                .as("Oscar.js should declare the forceWindowPaths list")
                .isTrue();
        String forceWindowPathsBody = forceWindowPathsMatcher.group("body");
        assertThat(forceWindowPathsBody)
                .as("forceWindowPaths should include both appointment popup routes")
                .contains("'addappointment'", "'editappointment'");
        assertThat(oscarJs).doesNotContain("appointmentcontrol.jsp");

        assertThat(schedulingStruts).doesNotContain("<action name=\"appointment/appointmentcontrol\"");
    }

    /**
     * Regression test for the direct-call refactor that removed the
     * appointmentcontrol dispatcher entirely.
     *
     * <p>The original blank-page bug came from routing the edit popup through
     * appointmentcontrol.jsp, which then nested into other actions/JSPs.
     * Known live callers must now go directly to their final endpoints so the
     * dispatcher route is not part of the flow anymore.</p>
     */
    @Test
    void shouldNotReferenceAppointmentControl_inLiveCallersOrRoutes() throws IOException {
        String editAppointment = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/editappointment.jsp");
        String addAppointment = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/addappointment.jsp");
        String providerDay = readJspContent("src/main/webapp/WEB-INF/jsp/provider/appointmentprovideradminday.jsp");
        String demographicApptHistory = readJspContent("src/main/webapp/WEB-INF/jsp/demographic/demographicappthistory.jsp");
        String ticklerAdd = readJspContent("src/main/webapp/WEB-INF/jsp/tickler/ticklerAdd.jsp");
        String addAlternateContact = readJspContent("src/main/webapp/WEB-INF/jsp/demographic/AddAlternateContact.jsp");
        String schedulingStruts = readJspContent("src/main/webapp/WEB-INF/classes/struts-scheduling.xml");
        String oscarJs = readJspContent("src/main/webapp/share/javascript/Oscar.js");

        assertThat(editAppointment).doesNotContain("/appointment/appointmentcontrol");
        assertThat(addAppointment).doesNotContain("/appointment/appointmentcontrol");
        assertThat(providerDay).doesNotContain("/appointment/appointmentcontrol");
        assertThat(demographicApptHistory).doesNotContain("/appointment/appointmentcontrol");
        assertThat(ticklerAdd).doesNotContain("/appointment/appointmentcontrol");
        assertThat(addAlternateContact).doesNotContain("/appointment/appointmentcontrol");
        assertThat(schedulingStruts).doesNotContain("<action name=\"appointment/appointmentcontrol\"");
        assertThat(oscarJs).doesNotContain("appointmentcontrol.jsp");
    }

    private String readJspContent(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private String extractInputElement(String pageSource, String id) {
        String idAttribute = "id=\"" + id + "\"";
        int idIndex = pageSource.indexOf(idAttribute);
        assertThat(idIndex)
                .as("the page should contain input %s", id)
                .isNotNegative();

        int inputStart = pageSource.lastIndexOf("<input", idIndex);
        int nextInput = pageSource.indexOf("<input", idIndex + idAttribute.length());
        assertThat(inputStart).isNotNegative();
        assertThat(nextInput).isGreaterThan(inputStart);
        return pageSource.substring(inputStart, nextInput);
    }
}
