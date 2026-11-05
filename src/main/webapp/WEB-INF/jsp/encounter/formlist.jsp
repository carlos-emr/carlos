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

<%--
    Chart Forms list (formlist.jsp), served by /encounter/ViewFormlist.
    Lists every saved form of one patient, grouped by form type, newest first
    within each type, and pages through them ten rows at a time.

    Parameters:
      demographic_no  the patient whose forms are listed
      provider_no     optional; carried into each form link and the paging links
      limit1          index of the first row shown, counted across all form types
      limit2          rows per page

    The page window and its Previous/Next links are worked out over the whole
    list by FormListPageCalculator, so a page never holds more than limit2 rows
    whichever form types they come from (issue #5684). A row's "(current)"
    label and highlight still depend on the row before it in its form type, so
    every row is walked and only the page's rows are rendered.

    @since 2001 (original), paging restored 2026-11-05
--%>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>

<fmt:setBundle basename="oscarResources"/>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_eChart" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_eChart");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page
        import="io.github.carlos_emr.carlos.encounter.data.*" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.data.EctFormData" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.data.FormListPageCalculator" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilDateUtilities" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Objects" %>

<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    String provNo = request.getParameter("provider_no");
    String demoNo = request.getParameter("demographic_no");
    String deepcolor = "#CCCCFF", weakcolor = "#EEEEFF", tableTitle = "#99ccff";
%>

<%
    EctFormData.Form[] forms = EctFormData.getForms();
    UtilDateUtilities dateConvert = new UtilDateUtilities();

    // Every form type's rows are loaded before any is rendered: the page window is counted
    // across all form types, so its bounds and the Next link need the whole list's size.
    List<EctFormData.PatientForm[]> patientFormsByType = new ArrayList<EctFormData.PatientForm[]>(forms.length);
    int totalFormRows = 0;
    for (EctFormData.Form frm : forms) {
        String table = frm.getFormTable();
        EctFormData.PatientForm[] pforms;
        if (table == null || table.isEmpty()) {
            pforms = new EctFormData.PatientForm[0];
        } else {
            pforms = EctFormData.getPatientFormsFromLocalAndRemote(loggedInInfo, demoNo, table);
        }
        patientFormsByType.add(pforms);
        totalFormRows += pforms.length;
    }
    FormListPageCalculator.Page formListPage = FormListPageCalculator.calculate(
            request.getParameter("limit1"), request.getParameter("limit2"), totalFormRows);

    // Paging links keep the patient and, when the list was opened with one, the provider.
    String formListPagingUrl = request.getContextPath() + "/encounter/ViewFormlist?demographic_no="
            + SafeEncode.forUriComponent(demoNo)
            + (provNo == null ? "" : "&provider_no=" + SafeEncode.forUriComponent(provNo));
    String formListPreviousUrl = formListPagingUrl + "&limit1=" + formListPage.previousOffset()
            + "&limit2=" + formListPage.size();
    String formListNextUrl = formListPagingUrl + "&limit1=" + formListPage.nextOffset()
            + "&limit2=" + formListPage.size();
%>
<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="encounter.formlist.title"/></title>
        <link rel="stylesheet" type="text/css" href="encounterStyles.css">
        <script type="text/javascript" language=javascript>

            function popupPageK(winname, page) {
                windowprops = "height=700,width=960,location=no,"
                    + "scrollbars=yes,menubars=no,toolbars=no,resizable=yes,top=0,left=0";
                var popup = window.open(page, winname, windowprops);
                popup.focus();

            }

            function urlencode(str) {
                var ns = (navigator.appName == "Netscape") ? 1 : 0;
                if (ns) {
                    return escape(str);
                }
                var ms = "%25#23 20+2B?3F<3C>3E{7B}7D[5B]5D|7C^5E~7E`60";
                var msi = 0;
                var i, c, rs, ts;
                while (msi < ms.length) {
                    c = ms.charAt(msi);
                    rs = ms.substring(++msi, msi + 2);
                    msi += 2;
                    i = 0;
                    while (true) {
                        i = str.indexOf(c, i);
                        if (i == -1) break;
                        ts = str.substring(0, i);
                        str = ts + "%" + rs + str.substring(++i, str.length);
                    }
                }
                return str;
            }

            function popupStart(vheight, vwidth, varpage) {
                var page = varpage;
                windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
                var popup = window.open(varpage, "", windowprops);
                if (popup != null) {
                    if (popup.opener == null) {
                        popup.opener = self;
                    }
                }
            }
        </script>
    </head>
    <body bgcolor="ivory" onLoad="setfocus()" topmargin="0" leftmargin="0"
          rightmargin="0">
    <table border=0 cellspacing=0 cellpadding=0 width="100%">
        <tr bgcolor="<%=deepcolor%>">
            <th><font face="Helvetica"><fmt:message key="encounter.formlist.msgFormList"/></font></th>
        </tr>
    </table>
    <center>
        <table BORDER="0" CELLPADDING="2" CELLSPACING="2" WIDTH="65%" BGCOLOR="white">
            <tr BGCOLOR="<%=tableTitle%>">
                <th width=35% nowrap><fmt:message key="encounter.formlist.formName"/></th>
                <th width=30% nowrap><fmt:message key="encounter.formlist.formCreated"/></th>
                <th width=35% nowrap><fmt:message key="encounter.formlist.formEditedTime"/></th>
            </tr>

            <%
                int formRowIndex = 0;
                for (int j = 0; j < forms.length; j++) {
                    EctFormData.Form frm = forms[j];
                    EctFormData.PatientForm[] pforms = patientFormsByType.get(j);

                    String current = "";
                    for (int i = 0; i < pforms.length; i++, formRowIndex++) {
                        EctFormData.PatientForm pfrm = pforms[i];
                        String created = pfrm.getCreated();

                        // yellow highlight the key forms. formCreated is nullable, hence the
                        // null-safe comparison. A row's highlight depends on the row before it
                        // in its form type, so it is worked out for rows on other pages too,
                        // and only this page's rows are rendered.
                        boolean yellow = !Objects.equals(current, created);
                        current = created;
                        if (!formListPage.contains(formRowIndex)) {
                            continue;
                        }

                        // formEdited is nullable in formBPMH; render a missing time as blank.
                        String edited = pfrm.edited == null ? "" : pfrm.getEdited();
                        String winName = frm.getFormName() + demoNo + created;
                        int hash = Math.abs(winName.hashCode());
            %>
            <tr bgcolor='<%= yellow ? "yellow" : j%2 == 0 ? (i%2 == 0 ?weakcolor:deepcolor) : (i%2 == 0 ?"white":"#eeeeee")%>'>
                <c:set var="__encFormListFormName"><carlos:encode value='<%= frm.getFormName() %>' context="uriComponent"/></c:set>
                <c:set var="__encFormListDemoNo"><carlos:encode value='<%= demoNo %>' context="uriComponent"/></c:set>
                <c:set var="__encFormListProvNo"><carlos:encode value='<%= provNo %>' context="uriComponent"/></c:set>
                <c:set var="__encFormListFormId" value="<%= String.valueOf(pfrm.getFormId()) %>" />
                <c:set var="__encFormListUrl" value="${pageContext.request.contextPath}/form/forwardshortcutname?formname=${__encFormListFormName}&demographic_no=${__encFormListDemoNo}&formId=${__encFormListFormId}&provNo=${__encFormListProvNo}" />
                <td><a href=# onClick="popupPageK('<carlos:encode value='<%= hash + \"started\" %>' context="javaScriptAttribute"/>','<carlos:encode value='${__encFormListUrl}' context="javaScriptAttribute"/>'); return false;">

                    <carlos:encode value='<%= frm.getFormName() + (yellow ? " (current)" : "") %>' context="html"/>
                </a></td>
                <td align='center'><carlos:encode value='<%= created %>' context="html"/>
                </td>
                <td align='center'><carlos:encode value='<%= edited %>' context="html"/>
                </td>
            </tr>
            <%
                    }
                }
            %>
            <%
                if (formListPage.hasPrevious() || formListPage.hasNext()) {
            %>
            <tr>
                <td colspan=3 align='center'>
                    <% if (formListPage.hasPrevious()) { %>
                    <a href="<carlos:encode value='<%= formListPreviousUrl %>' context="htmlAttribute"/>"><fmt:message key="encounter.formlist.formLastpage"/></a>
                    <% } %>
                    <% if (formListPage.hasPrevious() && formListPage.hasNext()) { %>
                    |
                    <% } %>
                    <% if (formListPage.hasNext()) { %>
                    <a href="<carlos:encode value='<%= formListNextUrl %>' context="htmlAttribute"/>"><fmt:message key="encounter.formlist.formNextPage"/></a>
                    <% } %>
                </td>
            </tr>
            <%
                }
            %>

        </table>
    </center>
    </body>
</html>
