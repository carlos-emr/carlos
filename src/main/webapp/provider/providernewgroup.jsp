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

<%@ page import="java.util.*,java.sql.*" errorPage="/errorpage.jsp" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="https://owasp.org/www-project-csrfguard/Owasp.CsrfGuard.tld" prefix="csrf" %>

<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.MyGroup" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.MyGroupPrimaryKey" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.MyGroupDao" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%
    MyGroupDao myGroupDao = SpringUtils.getBean(MyGroupDao.class);
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
%>
<html>
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providernewgroup.title"/></title>
        <script type="text/javascript">
            function setfocus() {
                this.focus();
                document.UPDATEPRE.mygroup_no.focus();
                document.UPDATEPRE.mygroup_no.select();
            }

            function checkForm() {
                if (UPDATEPRE.mygroup_no.value == "") {
                    alert("No Group No.!");
                    UPDATEPRE.mygroup_no.focus();
                    return false;
                }
                return true;
            }
        </script>
    </head>

    <body onLoad="setfocus()">
    <%
        if ("Delete".equals(request.getParameter("submit_form"))) {
            int rowsAffected = 0;
            String[] param = new String[2];

            for (Enumeration e = request.getParameterNames(); e.hasMoreElements(); ) {
                StringBuffer strbuf = new StringBuffer(e.nextElement().toString());
                if (strbuf.toString().indexOf("displaymode") != -1 || strbuf.toString().indexOf("submit_form") != -1
                        || strbuf.toString().indexOf("CSRF-TOKEN") != -1)
                    continue;
                param[0] = request.getParameter(strbuf.toString());
                param[1] = strbuf.toString().substring(param[0].length());
                myGroupDao.deleteGroupMember(param[0], param[1]);
                rowsAffected = 1;
            }
            out.println("<script type='text/javascript'>self.close();</script>");
        }
    %>

    <div class="container-fluid p-3">

        <div class="page-header-bar">
            <h4 class="page-header-title">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" viewBox="0 0 16 16" class="page-header-icon">
                    <path d="M6 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6m2-3a2 2 0 1 1-4 0 2 2 0 0 1 4 0m4 8c0 1-1 1-1 1H1s-1 0-1-1 1-4 6-4 6 3 6 4m-1-.004c-.001-.246-.154-.986-.832-1.664C9.516 10.68 8.289 10 6 10s-3.516.68-4.168 1.332c-.678.678-.83 1.418-.832 1.664z"/>
                    <path fill-rule="evenodd" d="M13.5 5a.5.5 0 0 1 .5.5V7h1.5a.5.5 0 0 1 0 1H14v1.5a.5.5 0 0 1-1 0V8h-1.5a.5.5 0 0 1 0-1H13V5.5a.5.5 0 0 1 .5-.5"/>
                </svg>
                &nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providernewgroup.msgTitle"/>
            </h4>
        </div>

    <form name="UPDATEPRE" method="post" action="providercontrol.jsp"
          onSubmit="return checkForm();">
        <input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>">
        <input type="hidden" name="displaymode" value="savemygroup">

        <div class="bg-light border rounded p-3 mb-3">
            <div class="row mb-3">
                <label class="col-sm-3 col-form-label fw-bold"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providernewgroup.msgGroupNo"/></label>
                <div class="col-sm-9 d-flex align-items-center gap-2">
                    <input type="text" name="mygroup_no" class="form-control form-control-sm" style="width: 120px;" maxlength="10">
                    <small class="text-muted"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providernewgroup.msgMaxChars"/></small>
                </div>
            </div>

            <table class="table table-sm table-bordered mb-0">
                <thead class="table-light">
                <tr>
                    <th>Provider</th>
                    <th style="width: 80px;" class="text-center">Select</th>
                </tr>
                </thead>
                <tbody>
                <%
                    int i = 0;
                    for (Provider p : providerDao.getActiveProviders()) {
                        i++;
                %>
                <tr>
                    <td><%=p.getLastName()%>, <%=p.getFirstName()%></td>
                    <td class="text-center">
                        <input type="checkbox" class="form-check-input" name="data<%=i%>" value="<%=i%>">
                        <input type="hidden" name="provider_no<%=i%>" value="<%=p.getProviderNo()%>">
                        <input type="hidden" name="last_name<%=i%>" value='<%=p.getLastName()%>'>
                        <input type="hidden" name="first_name<%=i%>" value='<%=p.getFirstName()%>'>
                    </td>
                </tr>
                <%
                    }
                %>
                </tbody>
            </table>
        </div>

        <div class="d-flex justify-content-between align-items-center">
            <div>
                <input type="submit" class="btn btn-primary btn-sm"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providernewgroup.btnSave"/>">
            </div>
            <div>
                <input type="button" class="btn btn-link btn-sm"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providernewgroup.btnExit"/>"
                       onClick="window.close();">
            </div>
        </div>

    </form>
    </div>

    </body>
</html>
