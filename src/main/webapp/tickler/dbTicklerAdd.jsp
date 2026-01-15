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

--%>

<%@ taglib uri="/WEB-INF/rewrite-tag.tld" prefix="rewrite" %>

<%@ page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@ page import="org.springframework.web.context.WebApplicationContext" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.commn.model.Tickler" %>
<%@ page import="ca.openosp.openo.commn.model.TicklerLink" %>
<%@ page import="ca.openosp.openo.commn.dao.TicklerLinkDao" %>
<%@ page import="ca.openosp.openo.util.UtilDateUtilities" %>
<%@page import="ca.openosp.openo.utility.MiscUtils" %>
<%@ page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="ca.openosp.openo.managers.TicklerManager" %>
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
    tickler.setDemographicNo(Integer.parseInt(module_id));
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

    ticklerManager.addTickler(loggedInInfo, tickler);

    int ticklerNo = tickler.getId();
    if (docType != null && docId != null && !docType.trim().equals("") && !docId.trim().equals("") && !docId.equalsIgnoreCase("null")) {
        if (ticklerNo > 0) {
            try {
                TicklerLink tLink = new TicklerLink();
                tLink.setTableId(Long.parseLong(docId));
                tLink.setTableName(docType);
                tLink.setTicklerNo(new Long(ticklerNo).intValue());
                TicklerLinkDao ticklerLinkDao = (TicklerLinkDao) SpringUtils.getBean(TicklerLinkDao.class);
                ticklerLinkDao.save(tLink);
            } catch (Exception e) {
                MiscUtils.getLogger().error("No link with this tickler", e);
            }
        }
    }

    boolean rowsAffected = true;

    String parentAjaxId = request.getParameter("parentAjaxId");
    String updateParent = request.getParameter("updateParent");
    String updateTicklerNav = request.getParameter("updateTicklerNav");

    if (rowsAffected) {
%>
<script LANGUAGE="JavaScript">

    var parentId = "<%=parentAjaxId%>";
    var updateParent = <%=updateParent%>;
    var demo = "<%=module_id%>";
    var updateTicklerNav = <%=updateTicklerNav%>;
    var Url = window.opener.URLs;

    /*because the url for demomaintickler is truncated by the delete action, we need
      to reconstruct it if necessary
    */
    if (parentId != "" && updateParent == true && !window.opener.closed) {
        if (updateTicklerNav != "" && updateTicklerNav == true) {
            window.opener.reloadNav(parentId);
            window.close();
        } else {
            var ref = window.opener.location.href;
            if (ref.indexOf("?") > -1 && ref.indexOf("updateParent") == -1)
                ref = ref + "&updateParent=true";
            else if (ref.indexOf("?") == -1)
                ref = ref + "?demoview=" + demo + "&parentAjaxId=" + parentId + "&updateParent=true";

            window.opener.location = ref;
        }
    } else if (parentId != "" && !window.opener.closed) {
        if (window.opener.document.forms['encForm']) {
            window.opener.document.forms['encForm'].elements['reloadDiv'].value = parentId;
        }
        window.opener.updateNeeded = true;
    } else if (updateParent == true && !window.opener.closed)
        window.opener.location.reload();

    self.close();
</script>
<%}%>
