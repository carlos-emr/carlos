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

<%@page import="io.github.carlos_emr.carlos.util.ConversionUtils"%>
<%@page import="io.github.carlos_emr.carlos.casemgmt.web.NoteDisplay"%>
<%  long start = System.currentTimeMillis(); %>
<%@include file="/casemgmt/taglibs.jsp"%>
<%@page
	import="java.util.List, java.util.Set, java.util.Iterator, io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue, io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteExt, io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote"%>
<%@page import="io.github.carlos_emr.carlos.commn.model.Provider"%>
<%@page import="io.github.carlos_emr.carlos.provider.web.CppPreferencesUIBean"%>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo"%>
<%@page import="io.github.carlos_emr.carlos.casemgmt.web.CaseManagementViewAction"%>
<%@page import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO"%>
<%@page import="io.github.carlos_emr.carlos.commn.model.UserProperty"%>
<%@page import="io.github.carlos_emr.carlos.commn.model.PartialDate"%>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils"%>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo"%>
<%@page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.services.security.SecurityManager" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilDateUtilities" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>

<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>

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
<div class="nav-menu-heading" style="background-color:#<%= Encode.forCssString(request.getParameter("hc") != null ? request.getParameter("hc") : "") %>">
<div class="nav-menu-add-button">
<h3>
<%
    String paramTitle = io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("title"));
    String paramCmd = io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("cmd"));
    String paramDemoNo = io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("demographicNo"));

	LoggedInInfo loggedInInfo=LoggedInInfo.getLoggedInInfoFromSession(request);
	SecurityManager securityManager = new SecurityManager();
	if(securityManager.hasWriteAccess("_" + request.getParameter("issue_code"),roleName$)) {
%>
<a href="javascript:void(0)" title='Add Item' onclick="return showEdit(event,'<fmt:setBundle basename="oscarResources"/><fmt:message key="<%= Encode.forHtmlAttribute(paramTitle) %>" />','',0,'','','','<%= Encode.forJavaScriptAttribute((String) request.getAttribute("addUrl")) %>0', '<%= Encode.forJavaScriptAttribute(paramCmd) %>','<%= Encode.forJavaScriptAttribute((String) request.getAttribute("identUrl")) %>','<%= Encode.forJavaScriptAttribute((String) request.getAttribute("cppIssue")) %>','','<%= Encode.forJavaScriptAttribute(paramDemoNo) %>');">+</a>
<% } else { %>
	&nbsp;
<% } %>
</h3>
</div>
<div class="nav-menu-title">
<h3>
	<a href="javascript:void(0)" onclick="return showIssueHistory('<%= Encode.forJavaScriptAttribute(io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("demographicNo"))) %>','<%= Encode.forJavaScriptAttribute(String.valueOf(request.getAttribute("issueIds"))) %>');">
<fmt:setBundle basename="oscarResources"/><fmt:message key="<%= Encode.forHtmlAttribute(paramTitle) %>" /></a>
</h3>
</div>
</div>
        <c:choose>
            <c:when test='${param.title == "encounter.oMeds.title" || param.title == "encounter.riskFactors.title" || param.title == "encounter.famHistory.title"|| param.noheight == "true"}'>
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
    <input type="hidden" id="<%= Encode.forHtmlAttribute(StringUtils.noNull(request.getParameter("cmd"))) + note.getId() %>" value="<%= i %>" />

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

            String htmlNoteTxt = Encode.forHtml(note.getNote() + addlData);

            boolean singleLine = Boolean.valueOf(CarlosProperties.getInstance().getProperty("echart.cpp.single_line", "false"));
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
            noteTxt = Encode.forJavaScript(noteTxt);

            Set<CaseManagementIssue> setIssues = note.getIssues();
            StringBuffer strNoteIssues = new StringBuffer();
            Iterator<CaseManagementIssue> iter = setIssues.iterator();
            while (iter.hasNext()) {
                CaseManagementIssue iss = iter.next();
                strNoteIssues.append(iss.getIssue_id()).append(";")
                             .append(iss.getIssue().getCode()).append(";")
                             .append(Encode.forJavaScript(iss.getIssue().getDescription()));
                if (iter.hasNext()) {
                    strNoteIssues.append(";");
                }
            }
%>
    <span id="spanListNote<%= note.getId() %>">
        <c:choose>
            <c:when test='${param.title == "encounter.oMeds.title" || param.title == "encounter.riskFactors.title" || param.title == "encounter.famHistory.title" || param.noheight == "true"}'>
                <a class="links"
                   onmouseover="this.className='linkhover'"
                   onmouseout="this.className='links'"
                   title="Rev:<%= note.getRevision() %> - <%= note.getUpdate_date() %>&#10;<%= Encode.forHtmlAttribute(note.getNote()) %>"
                   id="listNote<%= note.getId() %>"
                   href="javascript:void(0)"
                   onclick="showEdit(event,'<fmt:setBundle basename="oscarResources"/><fmt:message key="${param.title}" />','<%= note.getId() %>','<%= Encode.forJavaScript(editors.toString()) %>','<%= note.getObservation_date() %>','<%= note.getRevision() %>','<%= noteTxt %>', '<%= Encode.forJavaScriptAttribute((String) request.getAttribute("addUrl")) %><%= note.getId() %>', '<%= Encode.forJavaScriptAttribute(StringUtils.noNull(request.getParameter("cmd"))) %>','<%= Encode.forJavaScriptAttribute((String) request.getAttribute("identUrl")) %>','<%= Encode.forJavaScriptAttribute(strNoteIssues.toString()) %>','<%= Encode.forJavaScriptAttribute(strNoteExts) %>','<%= Encode.forJavaScriptAttribute(StringUtils.noNull(request.getParameter("demographicNo"))) %>');return false;">
            </c:when>
            <c:otherwise>
                <a class="topLinks"
                   onmouseover="this.className='topLinkhover'"
                   onmouseout="this.className='topLinks'"
                   title="Rev:<%= note.getRevision() %> - <%= note.getUpdate_date() %>&#10;<%= Encode.forHtmlAttribute(note.getNote()) %>"
                   id="listNote<%= note.getId() %>"
                   href="javascript:void(0)"
                   onclick="showEdit(event,'<fmt:setBundle basename="oscarResources"/><fmt:message key="${param.title}" />','<%= note.getId() %>','<%= Encode.forJavaScript(editors.toString()) %>','<%= note.getObservation_date() %>','<%= note.getRevision() %>','<%= noteTxt %>', '<%= Encode.forJavaScriptAttribute((String) request.getAttribute("addUrl")) %><%= note.getId() %>', '<%= Encode.forJavaScriptAttribute(StringUtils.noNull(request.getParameter("cmd"))) %>','<%= Encode.forJavaScriptAttribute((String) request.getAttribute("identUrl")) %>','<%= Encode.forJavaScriptAttribute(strNoteIssues.toString()) %>','<%= Encode.forJavaScriptAttribute(strNoteExts) %>','<%= Encode.forJavaScriptAttribute(StringUtils.noNull(request.getParameter("demographicNo"))) %>');return false;">
            </c:otherwise>
        </c:choose>

        <%= htmlNoteTxt %></a>
    </span></li>
<%
        } // end for
    } // end if
%>



<input type="hidden" id="<%= Encode.forHtmlAttribute(io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("cmd"))) %>num" value="${num}">
<input type="hidden" id="<%= Encode.forHtmlAttribute(io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("cmd"))) %>threshold" value="0">

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
		    val = Encode.forJavaScript(cme.getValue());
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
