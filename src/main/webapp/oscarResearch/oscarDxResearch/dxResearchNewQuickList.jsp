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


<%@ page import="java.util.*,io.github.carlos_emr.carlos.report.pageUtil.*" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>


<link rel="stylesheet" type="text/css" href="${pageContext.servletContext.contextPath}/oscarResearch/oscarDxResearch/dxResearch.css">
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxCustomization.selectQuickList"/>
        </title>

        <script type="text/javascript">

            function setfocus() {
                window.focus();
                window.resizeTo(450, 220);
            }
        </script>

    </head>

    <body class="BodyStyle" vlink="#0000FF" rightmargin="0" leftmargin="0"
          topmargin="0" marginwidth="0" marginheight="0" onload="setfocus()">
          
    <form action="${pageContext.request.contextPath}/oscarResearch/oscarDxResearch/dxResearchLoadQuickListItems.do" method="post">
        <table width="100%" border="0" cellspacing="0" cellpadding="0">
            <tr>
                <td>
                    <table width="100%" border="0" cellspacing="0" cellpadding="0">
                        <tr bgcolor="#000000">
                            <td class="subject" colspan="2">&nbsp;&nbsp;&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxResearch.msgDxResearch"/></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td>
                    <table width="100%" border="0" cellpadding="0" cellspacing="1"
                           bgcolor="#EEEEFF" height="125">
                        <tr>
                            <td align="left"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxCustomization.pleaseEnterTheNewQuickListName"/>:
                                <input type="text" name="quickListName"/></td>
                        </tr>
                        <tr>
                            <td>
                                <table>
                                    <tr>
                                        <input type="hidden" name="forward" value="error"/>
                                        <td><input type="button" class="mbttn" name="Button"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/>"
                                                   onClick="window.close()"></td>
                                        <td><input type="submit" class="mbttn" name="Button"
                                                   value="Continue"/></td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
        </table>
    </form>
    </body>
</html>
