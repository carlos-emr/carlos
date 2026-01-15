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

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ page import="ca.openosp.openo.commn.model.PreventionsLotNrs" %>
<%@ page import="ca.openosp.openo.commn.dao.PreventionsLotNrsDao" %>

<%
    String curProvider_no = (String) session.getAttribute("user");
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");

    boolean isSiteAccessPrivacy = false;
    boolean authed = true;
%>

<security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<security:oscarSec objectName="_site_access_privacy" roleName="<%=roleName$%>" rights="r" reverse="false">
    <%isSiteAccessPrivacy = true; %>
</security:oscarSec>


<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.lotnrsearchrecordshtm.title"/></title>
        <link rel="stylesheet" href="<%= request.getContextPath() %>/web.css">
        <script LANGUAGE="JavaScript">
            <!--

            function setfocus() {
                document.searchlotnr.keyword.focus();
                document.searchlotnr.keyword.select();
            }

            function onsub() {

                // make keyword lower case
                var keyword = document.searchprovider.keyword.value;
                var keywordLowerCase = keyword.toLowerCase();
                document.searchprovider.keyword.value = keywordLowerCase;
            }

            function upCaseCtrl(ctrl) {
                ctrl.value = ctrl.value.toUpperCase();
            }

            //-->
        </script>
    </head>

    <body onLoad="setfocus()" topmargin="0" leftmargin="0" rightmargin="0">
    <center>
        <table border="0" cellspacing="0" cellpadding="0" width="100%">
            <tr bgcolor="#486ebd">
                <th align="CENTER"><font face="Helvetica" color="#FFFFFF"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.lotnrsearchrecordshtm.description"/></font></th>
            </tr>
        </table>

        <table cellspacing="0" cellpadding="2" width="100%" border="0"
               BGCOLOR="#C4D9E7">

            <form method="post" action="lotnrsearchresults.jsp" name="searchlotnr"
                  onsubmit="return onsub();">
                <tr valign="top">
                    <td rowspan="2" align="right" valign="middle"><font
                            face="Verdana" color="#0000FF"><b><i><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.search.formSearchCriteria"/></i></b></font></td>
                    <td nowrap><font size="1" face="Verdana" color="#0000FF">
                        <input type="radio" checked name="search_mode" value="search_prev"
                               onclick="document.forms['searchlotnr'].keyword.focus();"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.lotnrsearch.prevention"/></font></td>


                    <td valign="middle" rowspan="2" ALIGN="left"><input type="text"
                                                                        NAME="keyword" SIZE="17" MAXLENGTH="100"> <INPUT
                            TYPE="hidden" NAME="orderby" VALUE="prevention_type">

                        <INPUT TYPE="hidden" NAME="limit1" VALUE="0"> <INPUT
                                TYPE="hidden" NAME="limit2" VALUE="10"> <INPUT
                                TYPE="SUBMIT" NAME="button"
                                VALUE=
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.lotnrsearch.btnSubmit"/> SIZE="17"></td>
                </tr>
            </form>
        </table>

        <p><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providersearchrecordshtm.msgInstructions"/></p>

    </center>
    </body>
</html>
