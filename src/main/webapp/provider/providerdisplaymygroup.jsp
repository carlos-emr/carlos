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

<%
    if (session.getAttribute("user") == null) response.sendRedirect(request.getContextPath() + "/logout.htm");
%>
<%@ page import="java.util.*,java.sql.*"
         errorPage="/errorpage.jsp" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.commn.model.MyGroup" %>
<%@ page import="ca.openosp.openo.commn.dao.MyGroupDao" %>

<%
    MyGroupDao dao = SpringUtils.getBean(MyGroupDao.class);
%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerdisplaymygroup.title"/></title>
    </head>

    <body onLoad="setfocus()" topmargin="0" leftmargin="0" rightmargin="0">
    <FORM NAME="UPDATEPRE" METHOD="post" ACTION="providercontrol.jsp">

        <div id="topMenuDiv" style="position:fixed;width:100%">
            <div style="background-color:#486ebd;text-align:center;font-family:Helvetica,sans-serif;color:#ffffff;font-weight:bold;padding:1px">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerdisplaymygroup.msgTitle"/>
            </div>
            <div style="background-color:#486ebd;text-align:center;border-top:solid white 1px;padding:1px">
                <input type="hidden" name="submit_form" value="">
                <INPUT TYPE="submit" VALUE="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerdisplaymygroup.btnDelete"/>"
                       onclick="document.forms['UPDATEPRE'].submit_form.value='Delete'; document.forms['UPDATEPRE'].submit();">
                <INPUT TYPE="submit" VALUE="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerdisplaymygroup.btnNew"/>"
                       onclick="document.forms['UPDATEPRE'].submit_form.value='New Group/Add a Member'; document.forms['UPDATEPRE'].submit();">
                <INPUT TYPE="RESET" VALUE="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerdisplaymygroup.btnClose"/>"
                       onClick="window.close();">
            </div>
        </div>
        <br/>
            <%-- This DIV and following javascript spaces out the content properly below the fixed position menu  --%>
        <div id="topMenuSpacerDiv" style="height:3em">&nbsp;</div>
        <script type="text/javascript">
            document.getElementById('topMenuSpacerDiv').style.height = document.getElementById('topMenuDiv').offsetHeight + 'px';
        </script>

        <center>
            <table border="0" cellpadding="0" cellspacing="0" width="80%">
                <tr>
                    <td width="100%">

                        <table BORDER="0" CELLPADDING="0" CELLSPACING="1" WIDTH="100%"
                               BGCOLOR="#C0C0C0">
                            <tr BGCOLOR="#CCFFFF">
                                <td ALIGN="center" colspan="2"><font face="arial"> <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerdisplaymygroup.msgGroupNo"/></font></td>
                                <td ALIGN="center"><font face="arial"> <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerdisplaymygroup.msgProvider"/></font></td>
                            </tr>
                            <%
                                boolean bNewNo = false;
                                String oldNo = "";
                                List<MyGroup> myGroups = dao.findAll();
                                Collections.sort(myGroups, MyGroup.MyGroupNoComparator);
                                for (MyGroup myGroup : myGroups) {

                                    String groupNo = myGroup.getId().getMyGroupNo();
                                    if (!(groupNo.equals(oldNo))) {
                                        bNewNo = bNewNo ? false : true;
                                        oldNo = groupNo;
                                    }
                            %>
                            <tr BGCOLOR="<%=bNewNo?"white":"ivory"%>">
                                <td width="10%" align="center"><input type="checkbox"
                                                                      name="<%=groupNo+myGroup.getId().getProviderNo()%>"
                                                                      value="<%=groupNo%>"></td>
                                <td ALIGN="center"><font face="arial"><%=groupNo%>
                                </font></td>
                                <td ALIGN="center"><font
                                        face="arial"><%=myGroup.getLastName() + ", " + myGroup.getFirstName()%>
                                </font>
                                </td>
                            </tr>
                            <%
                                }
                            %>
                            <INPUT TYPE="hidden" NAME="displaymode" VALUE='newgroup'>

                        </table>

                    </td>
                </tr>
            </table>
        </center>

    </FORM>

    </body>
</html>
