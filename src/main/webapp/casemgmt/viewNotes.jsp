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

<%@page import="org.apache.commons.text.StringEscapeUtils"%>
<%@page import="ca.openosp.openo.util.ConversionUtils"%>
<%@page import="ca.openosp.openo.casemgmt.web.NoteDisplay"%>
<%  long start = System.currentTimeMillis(); %>
<%@include file="/casemgmt/taglibs.jsp"%>
<%@page
	import="java.util.List, java.util.Set, java.util.Iterator, ca.openosp.openo.casemgmt.model.CaseManagementIssue, ca.openosp.openo.casemgmt.model.CaseManagementNoteExt, ca.openosp.openo.casemgmt.model.CaseManagementNote"%>
<%@page import="ca.openosp.openo.commn.model.Provider"%>
<%@page import="ca.openosp.openo.provider.web.CppPreferencesUIBean"%>
<%@page import="ca.openosp.openo.utility.LoggedInInfo"%>
<%@page import="ca.openosp.openo.casemgmt.web.CaseManagementViewAction"%>
<%@page import="ca.openosp.openo.commn.dao.UserPropertyDAO"%>
<%@page import="ca.openosp.openo.commn.model.UserProperty"%>
<%@page import="ca.openosp.openo.commn.model.PartialDate"%>
<%@page import="ca.openosp.openo.utility.SpringUtils"%>
<%@page import="ca.openosp.openo.utility.LoggedInInfo"%>
<%@page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.services.security.SecurityManager" %>
<%@ page import="ca.openosp.openo.util.UtilDateUtilities" %>
<%@ page import="ca.openosp.OscarProperties" %>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<%
    String roleName$ = (String)session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed=true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_casemgmt.notes" rights="r" reverse="<%=true%>">
	<%authed=false; %>
	<%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_casemgmt.notes");%>
</security:oscarSec>
<%
	if(!authed) {
		return;
	}
%>

<c:set var="ctx" value="${pageContext.request.contextPath}"
	scope="request" />
<c:set var="num" value="${fn:length(Notes)}" />
<div class="nav-menu-heading" style="background-color:#<c:out value="${param.hc}"/>">
<div class="nav-menu-add-button">
<h3>
<%
	LoggedInInfo loggedInInfo=LoggedInInfo.getLoggedInInfoFromSession(request);
	SecurityManager securityManager = new SecurityManager();
	if(securityManager.hasWriteAccess("_" + request.getParameter("issue_code"),roleName$)) {
%>
<a href="javascript:void(0)" title='Add Item' onclick="return showEdit(event,'<fmt:setBundle basename="oscarResources"/><fmt:message key="${param.title}" />','',0,'','','','<%=request.getAttribute("addUrl")%>0', '<c:out value="${param.cmd}"/>','<%=request.getAttribute("identUrl")%>','<%=request.getAttribute("cppIssue")%>','','<c:out value="${param.demographicNo}"/>');">+</a>
<% } else { %>
	&nbsp;
<% } %>
</h3>
</div>
<div class="nav-menu-title">
<h3>
	<a href="javascript:void(0)" onclick="return showIssueHistory('<c:out value="${param.demographicNo}"/>','<%=request.getAttribute("issueIds")%>');"><fmt:setBundle basename="oscarResources"/><fmt:message key="${param.title}" /></a>
</h3>
</div>
</div>
        <c:choose>
            <c:when test='${param.title == "oscarEncounter.oMeds.title" || param.title == "oscarEncounter.riskFactors.title" || param.title == "oscarEncounter.famHistory.title"|| param.noheight == "true"}'>
                <div style='clear:both;' class='topBox-notes'>
            </c:when>
            <c:otherwise>
                <div style='clear:both;' class='topBox-notes'>
            </c:otherwise>
        </c:choose>

<ul>
<%
    List<CaseManagementNoteExt> noteExts = (List<CaseManagementNoteExt>)request.getAttribute("NoteExts");
    List<CaseManagementNote> notes = (List<CaseManagementNote>) request.getAttribute("Notes");
    if (notes != null) {
        for (int i = 0; i < notes.size(); i++) {
            CaseManagementNote note = notes.get(i);
%>
    <input type="hidden" id="<%= request.getParameter("cmd") + note.getId() %>" value="<%= i %>" />

    <% if (i % 2 == 0) { %>
        <li class="cpp" style="background-color: #F3F3F3;">
    <% } else { %>
        <li class="cpp">
    <% } %>

<%
            CppPreferencesUIBean prefsBean = new CppPreferencesUIBean(loggedInInfo.getLoggedInProviderNo());
            prefsBean.loadValues();

            String addlData = CaseManagementViewAction.getCppAdditionalData(note.getId(), (String) request.getAttribute("cppIssue"), noteExts, prefsBean);
            String strNoteExts = getNoteExts(note.getId(), noteExts);

            List<Provider> listEditors = note.getEditors();
            StringBuffer editors = new StringBuffer();
            for (Provider p : listEditors) {
                editors.append(p.getFormattedName()).append(";");
            }

            String htmlNoteTxt = note.getNote() + addlData;

            boolean singleLine = Boolean.valueOf(OscarProperties.getInstance().getProperty("echart.cpp.single_line", "false"));
            UserPropertyDAO userPropertyDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
            UserProperty prop = userPropertyDao.getProp(loggedInInfo.getLoggedInProviderNo(), UserProperty.CPP_SINGLE_LINE);
            if (prop != null) {
                singleLine = "yes".equals(prop.getValue());
            }
            if (singleLine) {
                if (htmlNoteTxt.indexOf("\n") != -1) {
                    htmlNoteTxt = htmlNoteTxt.substring(0, htmlNoteTxt.indexOf("\n")) + "...";
                }
            } else {
                htmlNoteTxt = htmlNoteTxt.replaceAll("\n", "<br>");
            }

            String noteTxt = note.getNote().replaceAll("\"", "");
            noteTxt = org.apache.commons.text.StringEscapeUtils.escapeEcmaScript(noteTxt);

            Set<CaseManagementIssue> setIssues = note.getIssues();
            StringBuffer strNoteIssues = new StringBuffer();
            Iterator<CaseManagementIssue> iter = setIssues.iterator();
            while (iter.hasNext()) {
                CaseManagementIssue iss = iter.next();
                strNoteIssues.append(iss.getIssue_id()).append(";")
                             .append(iss.getIssue().getCode()).append(";")
                             .append(org.apache.commons.text.StringEscapeUtils.escapeEcmaScript(iss.getIssue().getDescription()));
                if (iter.hasNext()) {
                    strNoteIssues.append(";");
                }
            }
%>
    <span id="spanListNote<%= note.getId() %>">
        <c:choose>
            <c:when test='${param.title == "oscarEncounter.oMeds.title" || param.title == "oscarEncounter.riskFactors.title" || param.title == "oscarEncounter.famHistory.title" || param.noheight == "true"}'>
                <a class="links"
                   onmouseover="this.className='linkhover'"
                   onmouseout="this.className='links'"
                   title="Rev:<%= note.getRevision() %> - Last update:<%= note.getUpdate_date() %>"
                   id="listNote<%= note.getId() %>"
                   href="javascript:void(0)"
                   onclick="showEdit(event,'<fmt:setBundle basename="oscarResources"/><fmt:message key="${param.title}" />','<%= note.getId() %>','<%= StringEscapeUtils.escapeEcmaScript(editors.toString()) %>','<%= note.getObservation_date() %>','<%= note.getRevision() %>','<%= noteTxt %>', '<%= request.getAttribute("addUrl") %><%= note.getId() %>', '<%= request.getParameter("cmd") %>','<%= request.getAttribute("identUrl") %>','<%= strNoteIssues.toString() %>','<%= strNoteExts %>','<%= request.getParameter("demographicNo") %>');return false;">
            </c:when>
            <c:otherwise>
                <a class="topLinks"
                   onmouseover="this.className='topLinkhover'"
                   onmouseout="this.className='topLinks'"
                   title="Rev:<%= note.getRevision() %> - Last update:<%= note.getUpdate_date() %>"
                   id="listNote<%= note.getId() %>"
                   href="javascript:void(0)"
                   onclick="showEdit(event,'<fmt:setBundle basename="oscarResources"/><fmt:message key="${param.title}" />','<%= note.getId() %>','<%= StringEscapeUtils.escapeEcmaScript(editors.toString()) %>','<%= note.getObservation_date() %>','<%= note.getRevision() %>','<%= noteTxt %>', '<%= request.getAttribute("addUrl") %><%= note.getId() %>', '<%= request.getParameter("cmd") %>','<%= request.getAttribute("identUrl") %>','<%= strNoteIssues.toString() %>','<%= strNoteExts %>','<%= request.getParameter("demographicNo") %>');return false;">
            </c:otherwise>
        </c:choose>

        <%= htmlNoteTxt %></a>
    </span></li>
<%
        } // end for
    } // end if
%>

    <%-- Remote Notes Section --%>
    <fmt:setBundle basename="oscarResources"/>
<fmt:message key="${param.title}" var="resolvedTitleRaw"/>
<c:set var="resolvedTitle" value="${fn:escapeXml(resolvedTitleRaw)}"/>

<%
  // JS‑escaped version of the title
  String resolvedTitleJs = Encode.forJavaScript(
    (String) pageContext.getAttribute("resolvedTitle")
  );
%>

<ul>
  <c:forEach var="remoteNote" items="${remoteNotes}" varStatus="status">
    <% 
       // pull the JSP var "remoteNote" into a Java variable
       NoteDisplay note =
         (NoteDisplay) pageContext.getAttribute("remoteNote");

       // now you can use 'note' in your scriptlet
       String rawText   = note.getNote().replaceAll("\n","<br>");
       String rawTextJs = Encode.forJavaScript(rawText);

       String locAttr = note.getLocation();
       String locJs   = Encode.forJavaScript(locAttr);

       String provAttr = note.getProviderName();
       String provJs   = Encode.forJavaScript(provAttr);

       String timestamp = ConversionUtils
                            .toTimestampString(note.getObservationDate());
       String timeJs   = Encode.forJavaScript(timestamp);
    %>

    <li class="cpp ${status.index % 2 == 0 ? 'row-even' : 'row-odd'}">
      <a class="links"
         onmouseover="this.className='linkhover'"
         onmouseout="this.className='links'"
         title="<%= Encode.forHtmlAttribute(locAttr) %> by <%= Encode.forHtmlAttribute(provAttr) %> on <%= Encode.forHtmlAttribute(timestamp) %>"
         href="javascript:void(0)"
         onclick="showIntegratedNote(
           '<%= resolvedTitleJs %>',
           '<%= rawTextJs      %>',
           '<%= locJs          %>',
           '<%= provJs         %>',
           '<%= timeJs         %>'
         ); return false;">
        <%= Encode.forHtml(rawText) %>
      </a>
    </li>
  </c:forEach>
</ul>


<input type="hidden" id="${param.cmd}num" value="${num}">
<input type="hidden" id="${param.cmd}threshold" value="0">

<%!
    String getNoteExts(Long noteId, List<CaseManagementNoteExt> lcme) {
	StringBuffer strcme = new StringBuffer();
	for (CaseManagementNoteExt cme : lcme) {
	    if (cme.getNoteId().equals(noteId)) {
		String key = cme.getKeyVal();
		String val = null;
		if (key.contains(" Date")) {
		    val = readPartialDate(cme);
		} else {
		    val = org.apache.commons.text.StringEscapeUtils.escapeEcmaScript(cme.getValue());
		}
		if (strcme.length()>0) strcme.append(";");
		strcme.append(key + ";" + val);
	    }
	}
	return strcme.toString();
    }

        String readPartialDate(CaseManagementNoteExt cme) {
            String type = cme.getValue();
            String val = null;

            if (type!=null && !type.trim().equals("")) {
                if (type.equals(PartialDate.YEARONLY))
                    val = UtilDateUtilities.DateToString(cme.getDateValue(),"yyyy");
                else if (type.equals(PartialDate.YEARMONTH))
                    val = UtilDateUtilities.DateToString(cme.getDateValue(),"yyyy-MM");
                else val = UtilDateUtilities.DateToString(cme.getDateValue(),"yyyy-MM-dd");
            } else {
                val = UtilDateUtilities.DateToString(cme.getDateValue(),"yyyy-MM-dd");
            }
            return val;
        }
%>
