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

<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.MyGroup" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.MyGroupPrimaryKey" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.MyGroupDao" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%
    MyGroupDao myGroupDao = SpringUtils.getBean(MyGroupDao.class);
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
%>
<!DOCTYPE html>
<html lang="en">
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providernewgroup.title"/></title>
        <script>
            function setfocus() {
                document.UPDATEPRE.mygroup_no.focus();
                document.UPDATEPRE.mygroup_no.select();
            }

            function checkForm() {
                if (UPDATEPRE.mygroup_no.value === "") {
                    alert("No Group No.!");
                    UPDATEPRE.mygroup_no.focus();
                    return false;
                }
                return true;
            }
        </script>
    </head>

    <body onload="setfocus()">
    <%
        if ("Delete".equals(request.getParameter("submit_form"))) {
            int rowsAffected = 0;
            String[] param = new String[2];

            for (Enumeration e = request.getParameterNames(); e.hasMoreElements(); ) {
                StringBuffer strbuf = new StringBuffer(e.nextElement().toString());
                if (strbuf.toString().indexOf("displaymode") != -1 || strbuf.toString().indexOf("submit_form") != -1)
                    continue;
                param[0] = request.getParameter(strbuf.toString());
                param[1] = strbuf.toString().substring(param[0].length());
                myGroupDao.deleteGroupMember(param[0], param[1]);
                rowsAffected = 1;
            }
            out.println("<script>self.close();</script>");
        }
    %>

    <div class="container">

        <div class="page-header-bar">
            <h4 class="page-header-title">
                <i class="fas fa-users page-header-icon"></i>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providernewgroup.msgTitle"/>
            </h4>
        </div>

        <form name="UPDATEPRE" method="post" action="providercontrol.jsp"
              onsubmit="return checkForm();">

            <div class="d-flex align-items-center gap-2 mb-3">
                <label class="fw-bold"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providernewgroup.msgGroupNo"/></label>
                <input type="text" name="mygroup_no" size="10" maxlength="10" class="form-control form-control-sm" style="width:120px">
                <span class="text-muted" style="font-size:0.8em"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providernewgroup.msgMaxChars"/></span>
            </div>

            <table class="table table-hover table-sm table-striped">
                <thead>
                    <tr>
                        <th>Provider</th>
                        <th style="width:60px" class="text-center">Select</th>
                    </tr>
                </thead>
                <tbody>
                    <%
                        int i = 0;
                        for (Provider p : providerDao.getActiveProviders()) {
                            i++;
                    %>
                    <tr>
                        <td><%=Encode.forHtmlContent(p.getLastName())%>, <%=Encode.forHtmlContent(p.getFirstName())%></td>
                        <td class="text-center">
                            <input type="checkbox" name="data<%=i%>" value="<%=i%>">
                            <input type="hidden" name="provider_no<%=i%>"
                                   value="<%=Encode.forHtmlAttribute(p.getProviderNo())%>">
                            <input type="hidden" name="last_name<%=i%>"
                                   value="<%=Encode.forHtmlAttribute(p.getLastName())%>">
                            <input type="hidden" name="first_name<%=i%>"
                                   value="<%=Encode.forHtmlAttribute(p.getFirstName())%>">
                        </td>
                    </tr>
                    <%
                        }
                    %>
                </tbody>
            </table>

            <input type="hidden" name="displaymode" value="savemygroup">

            <div class="d-flex gap-2">
                <input type="hidden" name="Submit" value=" Save ">
                <input type="submit" class="btn btn-primary btn-sm"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providernewgroup.btnSave"/>">
            </div>

        </form>

    </div>

    </body>
</html>
