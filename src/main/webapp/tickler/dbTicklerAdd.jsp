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

<%@ taglib uri="/WEB-INF/rewrite-tag.tld" prefix="rewrite" %>

<%@ page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@ page import="org.springframework.web.context.WebApplicationContext" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Tickler" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.TicklerLink" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.TicklerLinkDao" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilDateUtilities" %>
<%@page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.managers.TicklerManager" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.time.LocalDateTime" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_tickler" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_tickler");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
    if (!"POST".equalsIgnoreCase(request.getMethod())) {
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
        return;
    }
%>

<%!
    TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);

%>

<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    String module = "", module_id = "", doctype = "", docdesc = "", docxml = "", doccreator = "", docdate = "", docfilename = "", docpriority = "", docassigned = "";
    module_id = request.getParameter("demographic_no");
    doccreator = request.getParameter("user_no");
    docdate = request.getParameter("xml_appointment_date");
    docfilename = request.getParameter("ticklerMessage");
    docpriority = request.getParameter("priority");
    docassigned = request.getParameter("task_assigned_to");

    String docType = request.getParameter("docType");
    String docId = request.getParameter("docId");


    Tickler tickler = new Tickler();
    if (module_id == null || module_id.trim().isEmpty()) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing demographic_no");
        return;
    }
    try {
        tickler.setDemographicNo(Integer.parseInt(module_id));
    } catch (NumberFormatException e) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid demographic_no");
        return;
    }
    tickler.setUpdateDate(new java.util.Date());
    if (docpriority != null && docpriority.equalsIgnoreCase("High")) {
        tickler.setPriority(Tickler.PRIORITY.High);
    }
    if (docpriority != null && docpriority.equalsIgnoreCase("Low")) {
        tickler.setPriority(Tickler.PRIORITY.Low);
    }
    tickler.setTaskAssignedTo(docassigned);
    tickler.setCreator(doccreator);
    tickler.setMessage(docfilename);
    Date serviceDate = UtilDateUtilities.StringToDate(docdate);
    if (serviceDate == null) {
        serviceDate = new Date();
    }
    tickler.setServiceDate(serviceDate);
    tickler.setCreateDate(new Date());

    boolean rowsAffected = false;
    try {
        ticklerManager.addTickler(loggedInInfo, tickler);
        rowsAffected = true;
    } catch (Exception e) {
        MiscUtils.getLogger().error("Failed to add tickler for demographicNo=" + module_id, e);
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to save tickler");
        return;
    }

    int ticklerNo = tickler.getId();
    boolean ticklerLinkFailed = false;
    if (docType != null && docId != null && !docType.trim().equals("") && !docId.trim().equals("") && !docId.equalsIgnoreCase("null")) {
        if (ticklerNo > 0) {
            try {
                TicklerLink tLink = new TicklerLink();
                tLink.setTableId(Long.parseLong(docId));
                tLink.setTableName(docType);
                tLink.setTicklerNo(new Long(ticklerNo).intValue());
                TicklerLinkDao ticklerLinkDao = (TicklerLinkDao) SpringUtils.getBean(TicklerLinkDao.class);
                ticklerLinkDao.save(tLink);
            } catch (NumberFormatException e) {
                MiscUtils.getLogger().error("Invalid docId format for TicklerLink: ticklerNo=" + ticklerNo + ", docId=" + docId, e);
                ticklerLinkFailed = true;
            } catch (Exception e) {
                MiscUtils.getLogger().error("Failed to save TicklerLink for ticklerNo=" + ticklerNo + ", docType=" + docType + ", docId=" + docId, e);
                ticklerLinkFailed = true;
            }
        }
    }

    String parentAjaxId = request.getParameter("parentAjaxId");
    String updateParent = request.getParameter("updateParent");
    // updateTicklerNav and updateParent are no longer used server-side; refresh is handled
    // client-side in ticklerAdd.jsp via the iframe.onload callback.

    if (rowsAffected && !ticklerLinkFailed) {
%>
<%-- ticklerAdd.jsp reads this element to confirm the save succeeded before closing --%>
<span id="tickler-save-ok" style="display:none;"></span>
<script type="text/javascript">
    // Tickler saved successfully.
    // Refresh flow for regular save: iframe.onload in ticklerAdd.jsp calls
    //   window.opener.reloadNav('tickler') and broadcasts via
    //   BroadcastChannel('carlos_tickler_refresh_' + demographicNo), then closes the popup.
    // Refresh flow for write-to-encounter: iframe.onload navigates
    //   window.opener.location with updateParent=true, then closes the popup.
    // Either way, the popup closes itself after 500 ms.
</script>
<%} else if (ticklerLinkFailed) {
    // Tickler was saved but the document link failed. Emit both sentinels so the
    // iframe.onload in ticklerAdd.jsp proceeds with close/refresh while showing a warning.
%>
<span id="tickler-save-ok" style="display:none;"></span>
<span id="tickler-save-ok-link-failed" style="display:none;"></span>
<%}%>
