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
package io.github.carlos_emr.carlos.report.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Regression tests for the "Patient List by Appointment Time" export
 * (issue #3346).
 *
 * <p>Two independent defects are pinned here:</p>
 * <ul>
 *   <li>Routing — {@code /patientlistbyappt} is a plain servlet declared in
 *       {@code web.xml}. Struts' global {@code struts.action.excludePattern}
 *       has to let it through, otherwise the Struts filter claims the request
 *       and answers 404 ("no Action mapped for namespace [/]").</li>
 *   <li>Null appointment type — {@code appointment.appointment_type} is
 *       optional, so the export must not dereference it.</li>
 *   <li>Authorization — the servlet is directly addressable and must enforce
 *       the same {@code _report} or {@code _admin.reporting} read policy as
 *       the report form.</li>
 * </ul>
 *
 * @since 2026-08-06
 */
@DisplayName("Patient List by Appointment Time export")
@Tag("unit")
@Tag("report")
class PatientListByApptExportUnitTest extends CarlosUnitTestBase {

    private static final Path STRUTS_XML =
            Path.of("src/main/webapp/WEB-INF/classes/struts.xml");
    private static final Path WEB_XML = Path.of("src/main/webapp/WEB-INF/web.xml");
    private static final Path PATIENT_LIST_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/oscarReport/patientlist.jsp");
    private static final Pattern STRUTS_ACTION_EXCLUDE_PATTERN = Pattern.compile(
            "<constant name=\"struts\\.action\\.excludePattern\" value=\"([^\"]+)\"\\s*/>");
    private static final Pattern PATIENT_LIST_SERVLET = Pattern.compile(
            "<servlet>\\s*<servlet-name>PatientListByAppt</servlet-name>\\s*"
                    + "<servlet-class>io\\.github\\.carlos_emr\\.carlos\\.report\\.data\\."
                    + "PatientListByAppt</servlet-class>\\s*</servlet>",
            Pattern.DOTALL);
    private static final Pattern PATIENT_LIST_SERVLET_MAPPING = Pattern.compile(
            "<servlet-mapping>\\s*<servlet-name>PatientListByAppt</servlet-name>\\s*"
                    + "<url-pattern>/patientlistbyappt</url-pattern>\\s*</servlet-mapping>",
            Pattern.DOTALL);

    private final OscarAppointmentDao appointmentDao = mock(OscarAppointmentDao.class);
    private final SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
    private final LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);

    private PatientListByAppt servlet;

    @BeforeEach
    void setUpServlet() {
        registerMock(OscarAppointmentDao.class, appointmentDao);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        when(securityInfoManager.hasPrivilege(
                loggedInInfo, "_report,_admin.reporting", "r", null))
                .thenReturn(true);
        servlet = new PatientListByAppt();
    }

    @Test
    @DisplayName("struts global config should let the patient list servlet route reach its web.xml mapping")
    void shouldExcludePatientListByApptRoute_whenReadingStrutsGlobalConfig() throws IOException {
        String globalStruts = Files.readString(STRUTS_XML, StandardCharsets.UTF_8);
        Matcher matcher = STRUTS_ACTION_EXCLUDE_PATTERN.matcher(globalStruts);

        assertThat(matcher.find()).isTrue();

        Pattern excludePattern = Pattern.compile(matcher.group(1));
        assertThat(excludePattern.matcher("/patientlistbyappt").matches()).isTrue();
        assertThat(excludePattern.matcher("/carlos/patientlistbyappt").matches()).isTrue();
        // The exclusion must stay anchored to this exact route, not swallow
        // sibling Struts actions that merely share the prefix.
        assertThat(excludePattern.matcher("/patientlistbyapptExtra").matches()).isFalse();
    }

    @Test
    @DisplayName("web.xml should map the patient list route to the export servlet")
    void shouldMapPatientListRouteToExportServlet_whenReadingWebXml() throws IOException {
        String webXml = Files.readString(WEB_XML, StandardCharsets.UTF_8);

        assertThat(PATIENT_LIST_SERVLET.matcher(webXml).find()).isTrue();
        assertThat(PATIENT_LIST_SERVLET_MAPPING.matcher(webXml).find()).isTrue();
    }

    @Test
    @DisplayName("visible export form should use POST so CSRF tokens do not enter the URL")
    void shouldSubmitVisibleFormWithPost_whenReadingPatientListJsp() throws IOException {
        String patientListJsp = Files.readString(PATIENT_LIST_JSP, StandardCharsets.UTF_8);

        assertThat(patientListJsp).contains(
                "<form id=\"plForm\" method=\"post\" "
                        + "action=\"<%=request.getContextPath() %>/patientlistbyappt\"");
    }

    @Test
    @DisplayName("export should emit an empty type field when the appointment has no type")
    void shouldEmitEmptyTypeField_whenAppointmentTypeIsNull() throws Exception {
        Date appointmentDate = ConversionUtils.fromDateString("2026-08-07");
        Date startTime = ConversionUtils.fromTimestampString("2026-08-07 09:00:00");
        stubAppointments(row(
                demographic("Aaron", "Bell", "555-0100", "555-0101"),
                appointment(appointmentDate, startTime, null, "Main Office"),
                provider("Doris", "Doctor")));

        MockHttpServletResponse response = export("all", "2026-08-07", "2026-08-10");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Content-disposition"))
                .isEqualTo("attachment; filename=patientlist.txt");
        assertThat(response.getContentAsString().lines().findFirst()).hasValue(
                "Bell,Aaron,555-0100,555-0101,09:00:00,2026-08-07,,Doris Doctor,Main Office");
    }

    @Test
    @DisplayName("export should keep the documented column order for a populated appointment type")
    void shouldKeepDocumentedColumnOrder_whenAppointmentTypeIsPresent() throws Exception {
        Date appointmentDate = ConversionUtils.fromDateString("2026-08-08");
        Date startTime = ConversionUtils.fromTimestampString("2026-08-08 10:00:00");
        stubAppointments(row(
                demographic("Cara", "Dunn", "555-0200", null),
                appointment(appointmentDate, startTime, "Follow Up", "Annex"),
                provider("Ravi", "Singh")));

        MockHttpServletResponse response = export("999998", "2026-08-07", "2026-08-10");

        assertThat(response.getContentAsString().lines().findFirst()).hasValue(
                "Dunn,Cara,555-0200,,10:00:00,2026-08-08,Follow Up,Ravi Singh,Annex");
        org.mockito.Mockito.verify(appointmentDao).findPatientAppointments(
                eq("999998"),
                eq(ConversionUtils.fromDateString("2026-08-07")),
                eq(ConversionUtils.fromDateString("2026-08-10")));
    }

    @Test
    @DisplayName("export should preserve CSV escaping and spreadsheet formula protection")
    void shouldEscapeCsvAndProtectSpreadsheetFormulas_whenFieldsNeedEscaping() throws Exception {
        Date appointmentDate = ConversionUtils.fromDateString("2026-08-09");
        Date startTime = ConversionUtils.fromTimestampString("2026-08-09 10:30:00");
        stubAppointments(row(
                demographic("Comma, \"Cara\"", "=Dunn", "+15550200", null),
                appointment(appointmentDate, startTime, "Follow, Up", "@Main"),
                provider("Ravi", "Singh")));

        MockHttpServletResponse response = export("999998", "2026-08-07", "2026-08-10");

        assertThat(response.getContentAsString().lines().findFirst()).hasValue(
                "'=Dunn,\"Comma, \"\"Cara\"\"\",'+15550200,,10:30:00,2026-08-09,"
                        + "\"Follow, Up\",Ravi Singh,'@Main");
    }

    @Test
    @DisplayName("export should emit empty fields for null optional phone, type, and location values")
    void shouldEmitEmptyFields_whenOptionalValuesAreNull() throws Exception {
        Date appointmentDate = ConversionUtils.fromDateString("2026-08-09");
        Date startTime = ConversionUtils.fromTimestampString("2026-08-09 14:00:00");
        stubAppointments(row(
                demographic("Aaron", "Bell", null, null),
                appointment(appointmentDate, startTime, null, null),
                provider("Doris", "Doctor")));

        MockHttpServletResponse response = export("all", "2026-08-07", "2026-08-10");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString().lines().findFirst()).hasValue(
                "Bell,Aaron,,,14:00:00,2026-08-09,,Doris Doctor,");
        assertThat(response.getContentAsString()).doesNotContain("null");
    }

    @Test
    @DisplayName("export should keep one row per appointment when the type contains line breaks")
    void shouldKeepOneRowPerAppointment_whenTypeContainsLineBreaks() throws Exception {
        Date appointmentDate = ConversionUtils.fromDateString("2026-08-10");
        Date startTime = ConversionUtils.fromTimestampString("2026-08-10 11:00:00");
        // A lone LF (not a CRLF pair) is what free-text appointment types actually
        // carry; the legacy replaceAll("\r\n", "") left it in place, and escapeCsv
        // then quoted the field, splitting one appointment across two output lines.
        stubAppointments(row(
                demographic("Erin", "Fox", "555-0300", "555-0301"),
                appointment(appointmentDate, startTime, "Lab\nreview\r\nvisit", "Annex"),
                provider("Ravi", "Singh")));

        MockHttpServletResponse response = export("999998", "2026-08-07", "2026-08-10");

        assertThat(response.getContentAsString().lines().findFirst()).hasValue(
                "Fox,Erin,555-0300,555-0301,11:00:00,2026-08-10,Labreviewvisit,Ravi Singh,Annex");
    }

    @Test
    @DisplayName("export should return an empty attachment when the date range matches no appointments")
    void shouldReturnEmptyAttachment_whenNoAppointmentsMatch() throws Exception {
        stubAppointments();

        MockHttpServletResponse response = export("all", "2026-09-01", "2026-09-02");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Content-disposition"))
                .isEqualTo("attachment; filename=patientlist.txt");
        assertThat(response.getContentAsString()).isBlank();
    }

    @Test
    @DisplayName("export should drop the provider filter when All Doctors is selected")
    void shouldDropProviderFilter_whenAllDoctorsSelected() throws Exception {
        stubAppointments();

        export("all", "2026-08-07", "2026-08-10");

        org.mockito.Mockito.verify(appointmentDao).findPatientAppointments(
                eq(null),
                eq(ConversionUtils.fromDateString("2026-08-07")),
                eq(ConversionUtils.fromDateString("2026-08-10")));
    }

    @Test
    @DisplayName("export should reject an unauthenticated direct servlet request")
    void shouldReturnUnauthorizedAndSkipQuery_whenSessionIsMissing() throws Exception {
        MockHttpServletRequest request = exportRequest("all", "2026-08-07", "2026-08-10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        servlet.processRequest(request, response);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(appointmentDao);
    }

    @Test
    @DisplayName("export should require report read privilege at the servlet boundary")
    void shouldReturnForbiddenAndSkipQuery_whenReportPrivilegeIsDenied() throws Exception {
        when(securityInfoManager.hasPrivilege(
                loggedInInfo, "_report,_admin.reporting", "r", null))
                .thenReturn(false);

        MockHttpServletResponse response = export("all", "2026-08-07", "2026-08-10");

        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(appointmentDao);
    }

    @Test
    @DisplayName("export should reject missing or invalid dates instead of running an unbounded query")
    void shouldReturnBadRequestAndSkipQuery_whenDatesAreMissingInvalidOrReversed() throws Exception {
        List<MockHttpServletResponse> responses = List.of(
                export("all", null, "2026-08-10"),
                export("all", "2026-02-30", "2026-08-10"),
                export("all", "2026-08-11", "2026-08-10"));

        assertThat(responses).allSatisfy(response ->
                assertThat(response.getStatus()).isEqualTo(400));
        verifyNoInteractions(appointmentDao);
    }

    @Test
    @DisplayName("export should reject a missing provider instead of widening to all doctors")
    void shouldReturnBadRequestAndSkipQuery_whenProviderIsMissing() throws Exception {
        MockHttpServletResponse response = export(null, "2026-08-07", "2026-08-10");

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(appointmentDao);
    }

    private MockHttpServletResponse export(String providerNo, String dateFrom, String dateTo)
            throws IOException {
        MockHttpServletRequest request = exportRequest(providerNo, dateFrom, dateTo);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);

        MockHttpServletResponse response = new MockHttpServletResponse();
        servlet.processRequest(request, response);
        return response;
    }

    private static MockHttpServletRequest exportRequest(
            String providerNo, String dateFrom, String dateTo) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/patientlistbyappt");
        if (providerNo != null) {
            request.setParameter("provider_no", providerNo);
        }
        if (dateFrom != null) {
            request.setParameter("date_from", dateFrom);
        }
        if (dateTo != null) {
            request.setParameter("date_to", dateTo);
        }
        return request;
    }

    private void stubAppointments(Object[]... rows) {
        List<Object[]> results = new ArrayList<>(List.of(rows));
        when(appointmentDao.findPatientAppointments(any(), any(), any())).thenReturn(results);
    }

    private static Object[] row(Demographic demographic, Appointment appointment, Provider provider) {
        return new Object[] { demographic, appointment, provider };
    }

    private static Demographic demographic(String firstName, String lastName, String phone, String phone2) {
        Demographic demographic = new Demographic();
        demographic.setFirstName(firstName);
        demographic.setLastName(lastName);
        demographic.setPhone(phone);
        demographic.setPhone2(phone2);
        return demographic;
    }

    private static Appointment appointment(Date appointmentDate, Date startTime, String type, String location) {
        Appointment appointment = new Appointment();
        appointment.setAppointmentDate(appointmentDate);
        appointment.setStartTime(startTime);
        appointment.setType(type);
        appointment.setLocation(location);
        return appointment;
    }

    private static Provider provider(String firstName, String lastName) {
        Provider provider = new Provider();
        provider.setFirstName(firstName);
        provider.setLastName(lastName);
        return provider;
    }
}
