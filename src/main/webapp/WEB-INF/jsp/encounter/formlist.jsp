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
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
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
        import="io.github.carlos_emr.carlos.util.UtilMisc,io.github.carlos_emr.carlos.encounter.data.*,java.net.*,java.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.data.EctFormData" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilDateUtilities" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    String provNo = request.getParameter("provider_no");
    String demoNo = request.getParameter("demographic_no");
    String deepcolor = "#CCCCFF", weakcolor = "#EEEEFF", tableTitle = "#99ccff";
    String strLimit1 = "0";
    String strLimit2 = "10";
    if (request.getParameter("limit1") != null) strLimit1 = request.getParameter("limit1");
    if (request.getParameter("limit2") != null) strLimit2 = request.getParameter("limit2");
%>

<%
    EctFormData.Form[] forms = EctFormData.getForms();
    UtilDateUtilities dateConvert = new UtilDateUtilities();
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

                for (int j = 0; j < forms.length; j++) {
                    EctFormData.Form frm = forms[j];
                    String table = frm.getFormTable();

                    EctFormData.PatientForm[] pforms;
                    if (table.length() == 0) {
                        pforms = new EctFormData.PatientForm[0];
                    } else {
                        pforms = EctFormData.getPatientFormsFromLocalAndRemote(loggedInInfo, demoNo, table);
                    }
                    int nItems = 0;

                    String current = "";
                    for (int i = 0; i < pforms.length; i++) {
                        nItems++;
                        EctFormData.PatientForm pfrm = pforms[i];

                        String winName = frm.getFormName() + demoNo + pfrm.getCreated();
                        int hash = Math.abs(winName.hashCode());

                        // yellow highlight the key forms.
                        boolean yellow = false;
                        if (!current.equals(pfrm.getCreated())) {
                            yellow = true;
                        }
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
                <td align='center'><carlos:encode value='<%= pfrm.getCreated() %>' context="html"/>
                </td>
                <td align='center'><carlos:encode value='<%= pfrm.getEdited() %>' context="html"/>
                </td>
            </tr>
            <%
                    current = pfrm.getCreated();
                }
            %>
            <%
                int nLastPage = 0, nNextPage = 0;
                nNextPage = Integer.parseInt(strLimit2) + Integer.parseInt(strLimit1);
                nLastPage = Integer.parseInt(strLimit1) - Integer.parseInt(strLimit2);
                int intLimit2 = Integer.parseInt(strLimit2);
                if ((nLastPage >= 0 || nItems == intLimit2) && nItems != 0) {
                    out.println("<tr><td colspan=3  align='center'>");
                    if (nLastPage >= 0) {
            %>
            <a
                    href="<%= request.getContextPath() %>/encounter/ViewFormlist?demographic_no=<carlos:encode value='<%= demoNo %>' context="uriComponent"/>&limit1=<%=nLastPage%>&limit2=<%=intLimit2%>"><fmt:message key="encounter.formlist.formLastpage"/></a>
            |
            <%
                }
                if (nItems == intLimit2) {
            %>
            <a
                    href="<%= request.getContextPath() %>/encounter/ViewFormlist?demographic_no=<carlos:encode value='<%= demoNo %>' context="uriComponent"/>&limit1=<%=nNextPage%>&limit2=<%=intLimit2%>">
                <fmt:message key="encounter.formlist.formNextPage"/></a>
            </td>
            </tr>
            <%
                } else {
                    out.println("</td></tr>");
                }
            %>
            <%
                    }
                }
            %>

        </table>
    </center>
    </body>
</html>
