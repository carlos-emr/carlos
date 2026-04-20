<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

    This software was written for the
    Department of Family Medicine
    McMaster University
    Hamilton
    Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%@ page import="java.util.*, io.github.carlos_emr.*, io.github.carlos_emr.carlos.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilDict" %>
<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    if (session.getAttribute("userrole") == null) {
        response.sendRedirect(request.getContextPath() + "/logoutPage");
        return;
    }
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_appointment"
                   rights="r" reverse="<%=true%>">
    <%response.sendRedirect(request.getContextPath() + "/logoutPage");%>
</security:oscarSec>
<%
    // associate each operation with an output action — displaymode
    // Post-migration: all sibling appointment JSPs live behind /WEB-INF/jsp/
    // so every live dispatch target is now either an extensionless Struts
    // action or an internal JSP. The dead "Search" entry (WITHOUT trailing space)
    // was removed — its former target appointmentsearchrecords.jsp does
    // not exist in the repo. The live "Search " operation (WITH trailing
    // space — mind the difference) is still active: addappointment.jsp
    // submits exactly "Search " as the displaymode value, so do not merge
    // the two entries in a later cleanup.
    String[][] opToFile = new String[][]{
            {"Add Appointment", "/appointment/AddRecord"},
            {"Group Appt", "/appointment/appointmentgrouprecords"},
            {"Group Action", "/appointment/appointmentgrouprecords"},
            {"Add Appt & PrintPreview", "/appointment/appointmentaddrecordprint"},
            {"Add Appt & PrintCard", "/appointment/appointmentaddrecordcard"},
            {"PrintCard", "/appointment/appointmentviewrecordcard"},
            {"TicklerSearch", "/tickler/ViewAddTickler"},
            {"Search ", "/demographic/DemographicSearch"},
            {"edit", "/appointment/editappointment"},
            {"Update Appt", "/appointment/UpdateRecord"},
            {"Delete Appt", "/appointment/DeleteRecord"},
            {"Cut", "/appointment/CutRecord"},
            {"Copy", "/appointment/appointmentcopyrecord"}
    };

    // create an operation-to-file dictionary
    UtilDict opToFileDict = new UtilDict();
    opToFileDict.setDef(opToFile);

    // create a request parameter name-to-value dictionary
    UtilDict requestParamDict = new UtilDict();
    requestParamDict.setDef(request);

    // get operation name from request
    String operation = requestParamDict.getDef("displaymode", "");

    // redirect to a file associated with operation
    String target = opToFileDict.getDef(operation, "");
    if (target.isEmpty()) {
        MiscUtils.getLogger().warn("appointmentcontrol.jsp: unrecognized displaymode: {}",
                SafeEncode.forJava(operation));
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                "Unrecognized appointment operation");
        return;
    }
    out.clearBuffer();
    // Dispatch to the operation-specific target.
    //
    // Internal view fragments (.jsp / .jspf / .html) are rendered via include()
    // so their output is composed into the current response.
    //
    // Extensionless Struts action targets are reached via sendRedirect() rather
    // than forward(). A JSP forward into a Struts action produces a nested
    // dispatch chain (Struts action → JSP → JSP forward → Struts action → JSP)
    // that passes twice through CsrfGuardScriptInjectionFilter's
    // CaptureResponseWrapper. Under Tomcat 11 that nested-wrapper/forward
    // combination drops the body of the innermost JSP, producing a blank page
    // (HTTP 200, 0 bytes) — the exact symptom of "Editappointment fails". A
    // 302 hands the browser a fresh URL that is processed on a single dispatch
    // path, so the target action's security gate runs cleanly and the
    // response body reaches the client. Query string is preserved so the
    // target action sees the original appointment_no / demographic_no /
    // provider_no / displaymode / dboperation parameters.
    if (target.endsWith(".jsp") || target.endsWith(".jspf") || target.endsWith(".html")) {
        request.getRequestDispatcher(target).include(request, response);
    } else {
        if (response.isCommitted()) {
            // Defensive: nothing in the preceding scriptlet writes body bytes,
            // so this path should be unreachable in practice. If we do land
            // here, the response has already been flushed — sendError() would
            // fail and throwing would mix a stack trace into the committed
            // body. Log-and-return is the only non-destructive option.
            MiscUtils.getLogger().error("appointmentcontrol.jsp: cannot redirect to {} — response already committed; returning without further output", target);
            return;
        }
        String queryString = request.getQueryString();
        String redirectUrl = request.getContextPath() + target
                + (queryString != null && !queryString.isEmpty() ? "?" + queryString : "");
        response.sendRedirect(redirectUrl);
    }
%>
