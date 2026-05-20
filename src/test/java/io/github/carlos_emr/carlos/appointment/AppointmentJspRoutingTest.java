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
class AppointmentJspRoutingTest {

    private static final Pattern FORCE_WINDOW_PATHS_PATTERN = Pattern.compile(
            "(?:window\\.)?forceWindowPaths\\s*=\\s*(?:window\\.forceWindowPaths\\s*\\|\\|\\s*)?\\[(?<body>[\\s\\S]*?)]\\s*;?");

    @Test
    void shouldRouteLiveAppointmentCallers_directlyToFinalTargets() throws IOException {
        String editAppointment = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/editappointment.jsp");
        String addAppointment = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/addappointment.jsp");
        String editRepeat = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/appointmenteditrepeatbooking.jsp");
        String repeat = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/appointmentrepeatbooking.jsp");
        String groupRecords = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/appointmentgrouprecords.jsp");
        String addRecord = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/appointmentaddarecord.jsp");
        String updateRecord = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/appointmentupdatearecord.jsp");
        String providerDay = readJspContent("src/main/webapp/WEB-INF/jsp/provider/appointmentprovideradminday.jsp");
        String demographicApptHistory = readJspContent("src/main/webapp/WEB-INF/jsp/demographic/demographicappthistory.jsp");
        String ticklerAdd = readJspContent("src/main/webapp/WEB-INF/jsp/tickler/ticklerAdd.jsp");
        String addAlternateContact = readJspContent("src/main/webapp/WEB-INF/jsp/demographic/AddAlternateContact.jsp");
        String oscarJs = readJspContent("src/main/webapp/share/javascript/Oscar.js");
        String schedulingStruts = readJspContent("src/main/webapp/WEB-INF/classes/struts-scheduling.xml");

        assertThat(editAppointment).contains("/demographic/DemographicSearch");
        assertThat(editAppointment).contains("/appointment/UpdateRecord");
        assertThat(editAppointment).contains("/appointment/DeleteRecord");
        assertThat(editAppointment).contains("/appointment/appointmentgrouprecords");
        assertThat(editAppointment).contains("/appointment/CutRecord");
        assertThat(editAppointment).contains("/appointment/appointmentcopyrecord");
        assertThat(editAppointment).contains("/appointment/appointmentviewrecordcard");
        assertThat(editAppointment).doesNotContain("/appointment/appointmentcontrol");
        assertThat(editAppointment).contains("/appointment/appointmenteditrepeatbooking");
        assertThat(editAppointment).doesNotContain("appointmenteditrepeatbooking.jsp");

        assertThat(addAppointment).contains("/appointment/AddRecord");
        assertThat(addAppointment).contains("/appointment/appointmentgrouprecords");
        assertThat(addAppointment).contains("/demographic/DemographicSearch");
        assertThat(addAppointment).doesNotContain("/appointment/appointmentcontrol");
        assertThat(addAppointment).contains("/appointment/appointmentrepeatbooking");
        assertThat(addAppointment).contains("id=\"addButton\" class=\"btn btn-primary\"");
        assertThat(addAppointment).contains("formaction=\"<%=request.getContextPath()%>/appointment/AddRecord\"");
        assertThat(addAppointment).doesNotContain("appointmentrepeatbooking.jsp");

        assertThat(editRepeat).contains("action=\"<%=request.getContextPath() %>/appointment/appointmenteditrepeatbooking\"");
        assertThat(editRepeat).doesNotContain("action=\"appointmenteditrepeatbooking.jsp\"");

        assertThat(repeat).contains("action=\"<%=request.getContextPath() %>/appointment/appointmentrepeatbooking\"");
        assertThat(repeat).doesNotContain("action=\"appointmentrepeatbooking.jsp\"");

        assertThat(groupRecords).contains("action=\"<%=request.getContextPath() %>/appointment/appointmentgrouprecords\"");
        assertThat(groupRecords).doesNotContain("action=\"appointmentgrouprecords.jsp\"");

        assertThat(addRecord).contains("/appointment/printappointment?appointment_no=");
        assertThat(addRecord).contains("pageContext.request.contextPath");
        assertThat(addRecord).contains("carlos:forJavaScript(carlos:forUriComponent(apptId))");
        assertThat(addRecord).doesNotContain("printappointment.jsp?appointment_no=");

        assertThat(updateRecord).contains("/appointment/printappointment?appointment_no=");
        assertThat(updateRecord).containsAnyOf("request.getContextPath()", "pageContext.request.contextPath");
        assertThat(updateRecord).contains("pageContext.request.contextPath");
        assertThat(updateRecord).contains("carlos:forJavaScript(carlos:forUriComponent(appointmentNo))");
        assertThat(updateRecord).doesNotContain("printappointment.jsp?appointment_no=");

        assertThat(providerDay).contains("/appointment/addappointment?");
        assertThat(providerDay).contains("/appointment/editappointment?");
        assertThat(providerDay).doesNotContain("/appointment/appointmentcontrol");
        assertThat(providerDay).contains("return ctx + '/appointment/addappointment'");
        assertThat(providerDay).doesNotContain("/appointment/addappointment.jsp");

        assertThat(demographicApptHistory).contains("/appointment/editappointment?demographic_no=");
        assertThat(demographicApptHistory).doesNotContain("/appointment/appointmentcontrol");

        assertThat(ticklerAdd).contains("action=\"<%= request.getContextPath() %>/demographic/DemographicSearch\"");
        assertThat(ticklerAdd).contains("name=\"displaymode\" value=\"Search \"");
        assertThat(ticklerAdd).doesNotContain("/appointment/appointmentcontrol");

        assertThat(addAlternateContact).contains("action=\"<%= request.getContextPath() %>/demographic/DemographicSearch\"");
        assertThat(addAlternateContact).contains("name=\"displaymode\"");
        assertThat(addAlternateContact).contains("value=\"Search \"");
        assertThat(addAlternateContact).doesNotContain("/appointment/appointmentcontrol");

        Matcher forceWindowPathsMatcher = FORCE_WINDOW_PATHS_PATTERN.matcher(oscarJs);
        assertThat(forceWindowPathsMatcher.find())
                .as("Oscar.js should declare the forceWindowPaths list")
                .isTrue();
        String forceWindowPathsBody = forceWindowPathsMatcher.group("body");
        assertThat(forceWindowPathsBody)
                .as("forceWindowPaths should include the 'addappointment' route")
                .contains("'addappointment'");
        assertThat(forceWindowPathsBody)
                .as("forceWindowPaths should include the 'editappointment' route")
                .contains("'editappointment'");
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
}
