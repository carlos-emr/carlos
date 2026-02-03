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

<%@page import="io.github.carlos_emr.carlos.utility.SessionConstants" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.ProviderPreference" %>
<%
    String oldGroup_no = request.getParameter("mygroup_no") == null ? "." : request.getParameter("mygroup_no");
%>
<%@ page import="java.util.*,java.sql.*"
         errorPage="/errorpage.jsp" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.MyGroup" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.MyGroupDao" %>

<%
    MyGroupDao dao = SpringUtils.getBean(MyGroupDao.class);
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangemygroup.title"/></title>
        <script language="javascript">
            <!--
            start
            javascript--
            --check
            to
            see
            if it is
            really
            empty in database

            function setfocus() {
                this.focus();
                //document.UPDATEPRE.start_hour.focus();
            }

            // stop javascript -->
        </script>
    </head>

    <body onLoad="setfocus()" topmargin="0" leftmargin="0" rightmargin="0">
    <FORM NAME="UPDATEPRE" METHOD="post" ACTION="providercontrol.jsp">
        <table border=0 cellspacing=0 cellpadding=0 width="100%">
            <tr bgcolor="#486ebd">
                <th align=CENTER NOWRAP><font face="Helvetica" color="#FFFFFF"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangemygroup.msgTitle"/></font></th>
            </tr>
        </table>

        <center>
            <table border="0" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangemygroup.msgChangeGroup"/>:
                    </TD>
                    <TD align="right"><select name="mygroup_no">
                        <%
                            List<MyGroup> myGroups = dao.findAll();
                            Collections.sort(myGroups, MyGroup.MyGroupNoComparator);
                            for (MyGroup myGroup : myGroups) {
                        %>
                        <option value="<%=myGroup.getId().getMyGroupNo()%>"
                                <%=oldGroup_no.equals(myGroup.getId().getMyGroupNo()) ? "selected" : ""%>><%=myGroup.getId().getMyGroupNo()%>
                        </option>
                        <%
                            }
                        %>
                    </select> &nbsp;<INPUT TYPE="submit"
                                           VALUE="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangemygroup.btnChange"/>">
                        <INPUT TYPE="RESET"
                               VALUE="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangemygroup.btnCancel"/>"
                               onClick="window.close();"></td>
                </tr>
            </TABLE>
            <table border="0" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td width="100%">

                        <table BORDER="0" CELLPADDING="0" CELLSPACING="1" WIDTH="100%"
                               BGCOLOR="#C0C0C0">
                            <tr BGCOLOR="#C4D9E7">
                                <td ALIGN="center"><font face="arial"> <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangemygroup.msgGroup"/></font></td>
                                <td ALIGN="center"><font face="arial"> <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangemygroup.msgName"/></font></td>
                            </tr>
                            <%
                                boolean bNewNo = false;
                                String oldNo = "";
                                myGroups = dao.findAll();
                                Collections.sort(myGroups, MyGroup.MyGroupNoComparator);
                                for (MyGroup myGroup : myGroups) {
                                    if (!(oldNo.equals(myGroup.getId().getMyGroupNo()))) {
                                        bNewNo = bNewNo ? false : true;
                                        oldNo = String.valueOf(myGroup.getId().getMyGroupNo());
                                    }
                            %>
                            <tr BGCOLOR="<%=bNewNo?"white":"ivory"%>">
                                <td ALIGN="center"><font face="arial"><%=myGroup.getId().getMyGroupNo()%>
                                </font></td>
                                <td><font face="arial"> &nbsp;<%=myGroup.getLastName() + ", " + myGroup.getFirstName()%>
                                </font>
                                </td>
                            </tr>
                            <%
                                }

                                ProviderPreference providerPreference = (ProviderPreference) session.getAttribute(SessionConstants.LOGGED_IN_PROVIDER_PREFERENCE);
                            %>
                            <INPUT TYPE="hidden" NAME="start_hour"
                                   VALUE='<%=providerPreference.getStartHour()%>'>
                            <INPUT TYPE="hidden" NAME="end_hour"
                                   VALUE='<%=providerPreference.getEndHour()%>'>
                            <INPUT TYPE="hidden" NAME="every_min"
                                   VALUE='<%=providerPreference.getEveryMin()%>'>
                            <INPUT TYPE="hidden" NAME="provider_no"
                                   VALUE='<%=(String) session.getAttribute("user")%>'>
                            <INPUT TYPE="hidden" NAME="color_template" VALUE='deepblue'>
                            <INPUT TYPE="hidden" NAME="dboperation" VALUE='updatepreference'>
                            <INPUT TYPE="hidden" NAME="displaymode" VALUE='updatepreference'>

                        </table>

                    </td>
                </tr>
            </table>
        </center>

        <table width="100%" BGCOLOR="#486ebd">
            <tr>
                <TD align="center"><INPUT TYPE="button"
                                          VALUE="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangemygroup.btnCancel"/>"
                                          onClick="window.close();"></TD>
            </tr>
        </TABLE>
        <INPUT TYPE="hidden" NAME="color_template" VALUE='deepblue'> <INPUT
            TYPE="hidden" NAME="dboperation" VALUE='updatepreference'> <INPUT
            TYPE="hidden" NAME="displaymode" VALUE='updatepreference'></FORM>


    </body>
</html>
