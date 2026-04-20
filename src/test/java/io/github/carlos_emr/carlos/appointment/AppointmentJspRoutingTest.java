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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("Appointment JSP Routing Unit Tests")
@Tag("unit")
@Tag("appointment")
class AppointmentJspRoutingTest {

    @Test
    void shouldUseExtensionlessRoutes_forAllAppointmentWorkflows() throws IOException {
        String editAppointment = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/editappointment.jsp");
        String addAppointment = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/addappointment.jsp");
        String editRepeat = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/appointmenteditrepeatbooking.jsp");
        String repeat = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/appointmentrepeatbooking.jsp");
        String groupRecords = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/appointmentgrouprecords.jsp");
        String addRecord = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/appointmentaddarecord.jsp");
        String updateRecord = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/appointmentupdatearecord.jsp");
        String providerDay = readJspContent("src/main/webapp/WEB-INF/jsp/provider/appointmentprovideradminday.jsp");

        assertThat(editAppointment).contains("/appointment/appointmentcontrol");
        assertThat(editAppointment).contains("/appointment/appointmenteditrepeatbooking");
        assertThat(editAppointment).doesNotContain("ACTION=\"appointmentcontrol.jsp\"");
        assertThat(editAppointment).doesNotContain("window.location='appointmentcontrol.jsp");
        assertThat(editAppointment).doesNotContain("appointmenteditrepeatbooking.jsp");

        assertThat(addAppointment).contains("/appointment/appointmentrepeatbooking");
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
        assertThat(providerDay).contains("return ctx + '/appointment/addappointment'");
        assertThat(providerDay).doesNotContain("/appointment/addappointment.jsp");
    }

    /**
     * Regression test for the "Editappointment fails" blank-page bug.
     *
     * <p>{@code appointmentcontrol.jsp} is the dispatcher that routes each
     * {@code displaymode} (edit / Add Appointment / Copy / ...) to its
     * per-operation target. For extensionless Struts action targets the
     * dispatch method depends on the incoming HTTP method:</p>
     *
     * <ul>
     *   <li><b>GET/HEAD</b> (popup links from the schedule grid, demographic
     *   appt history, etc. — {@code displaymode=edit}, {@code PrintCard}):
     *   use {@code response.sendRedirect()}. A JSP forward into a Struts
     *   action produces a nested dispatch chain that is captured twice by
     *   {@code CsrfGuardScriptInjectionFilter}'s response wrapper; under
     *   Tomcat 11 the nested-wrapper/forward combination drops the
     *   innermost JSP's body and the popup opens blank (HTTP 200, 0 bytes).
     *   A 302 sidesteps the nested-forward case entirely.</li>
     *
     *   <li><b>POST</b> (EDITAPPT/ADDAPPT form submissions carrying
     *   {@code displaymode=Update Appt / Delete Appt / Cut / Copy /
     *   Add Appointment / Group Action / Search }): must still use
     *   {@code forward()}. A 302 turns the POST into a GET and drops the
     *   form body; the mutator targets ({@code UpdateRecord},
     *   {@code DeleteRecord}, {@code CutRecord}, {@code AddRecord}) are
     *   blocked by {@code HttpMethodGuardFilter} on GET and also rely on
     *   POST parameters that a redirect would discard.</li>
     * </ul>
     *
     * <p>Internal JSP/JSPF/HTML fragments must still be composed via
     * {@code include()}.</p>
     */
    @Test
    void shouldUseSendRedirect_forExtensionlessActionTargetsInAppointmentControl() throws IOException {
        String appointmentControl = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/appointmentcontrol.jsp");

        // Edit dispatch must still target the extensionless Struts action
        assertThat(appointmentControl).contains("\"edit\"");
        assertThat(appointmentControl).contains("/appointment/editappointment");

        // GET/HEAD path: dispatch-specific redirect call (not unrelated
        // security/logout redirects elsewhere in the file)
        assertThat(appointmentControl).contains("response.sendRedirect(redirectUrl");

        // POST path: must still forward() so mutator targets (UpdateRecord,
        // DeleteRecord, CutRecord, AddRecord) receive the form body. A 302
        // would turn the POST into a GET and HttpMethodGuardFilter would
        // block the request with 405.
        assertThat(appointmentControl).contains("\"POST\".equalsIgnoreCase(request.getMethod())");
        assertThat(appointmentControl).contains("request.getRequestDispatcher(target).forward(request, response)");

        // Query string must be preserved on the redirect path so the target
        // action sees appointment_no / demographic_no / provider_no /
        // displaymode / dboperation
        assertThat(appointmentControl).contains("request.getQueryString()");

        // include() must remain available for internal JSP/JSPF/HTML fragments
        assertThat(appointmentControl).contains("request.getRequestDispatcher(target).include(");

        // Structural ordering: the .jsp/.jspf/.html include branch must come
        // first, then the POST forward() branch, then the GET/HEAD
        // sendRedirect() fallback. If the order regresses (e.g. redirect
        // before the POST check) a POST would be 302-redirected and lose its
        // form body.
        int includeIdx = appointmentControl.indexOf("request.getRequestDispatcher(target).include(");
        int postIdx = appointmentControl.indexOf("\"POST\".equalsIgnoreCase(request.getMethod())");
        int forwardIdx = appointmentControl.indexOf("request.getRequestDispatcher(target).forward(request, response)");
        int redirectIdx = appointmentControl.indexOf("response.sendRedirect(redirectUrl");
        assertThat(includeIdx).isLessThan(postIdx);
        assertThat(postIdx).isLessThan(forwardIdx);
        assertThat(forwardIdx).isLessThan(redirectIdx);
    }

    private String readJspContent(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
