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
                org.owasp.encoder.Encode.forJava(operation));
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                "Unrecognized appointment operation");
        return;
    }
    out.clearBuffer();
    // Struts actions require FORWARD dispatch — the Struts filter does not
    // intercept INCLUDE dispatches, so extensionless action targets must use
    // forward() instead of include().
    if (!(target.endsWith(".jsp") || target.endsWith(".jspf") || target.endsWith(".html"))) {
        if (response.isCommitted()) {
            // Defensive: nothing in the preceding scriptlet writes body bytes,
            // so this path should be unreachable in practice. If we do land
            // here, the response has already been flushed — sendError() would
            // fail (can't set status on committed response) and throwing would
            // mix a stack trace into the committed body. Log-and-return is the
            // only non-destructive option. This is noted as a defensive
            // no-op, not a silent success — the client sees whatever partial
            // bytes were flushed and the error is visible in server logs.
            MiscUtils.getLogger().error("appointmentcontrol.jsp: cannot forward to {} — response already committed; returning without further output", target);
            return;
        }
        request.getRequestDispatcher(target).forward(request, response);
    } else {
        request.getRequestDispatcher(target).include(request, response);
    }
%>
