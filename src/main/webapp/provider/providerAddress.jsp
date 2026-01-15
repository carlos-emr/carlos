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

<%@ page language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ page import="ca.openosp.openo.providers.data.*" %>
<%@ page import="ca.openosp.openo.commn.dao.UserPropertyDAO" %>
<%@ page import="ca.openosp.openo.commn.model.UserProperty" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>

<%
    UserPropertyDAO propertyDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
%>
<%
    if (session.getValue("user") == null) response.sendRedirect(request.getContextPath() + "/logout.htm");
    String curUser_no = (String) session.getAttribute("user");
%>
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>

        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <link rel="stylesheet" type="text/css"
              href="<%= request.getContextPath() %>/oscarEncounter/encounterStyles.css">

        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.editRxAddress.title"/></title>

        <script type="text/javascript">
            function validate() {
                return true;
            }
        </script>

        <style type="text/css">
            table.outline {
                margin-top: 50px;
                border-bottom: 1pt solid #888888;
                border-left: 1pt solid #888888;
                border-top: 1pt solid #888888;
                border-right: 1pt solid #888888;
            }

            table.grid {
                border-bottom: 1pt solid #888888;
                border-left: 1pt solid #888888;
                border-top: 1pt solid #888888;
                border-right: 1pt solid #888888;
            }

            td.gridTitles {
                border-bottom: 2pt solid #888888;
                font-weight: bold;
                text-align: center;
            }

            td.gridTitlesWOBottom {
                font-weight: bold;
                text-align: center;
            }

            td.middleGrid {
                border-left: 1pt solid #888888;
                border-right: 1pt solid #888888;
                text-align: center;
            }

            label {
                float: left;
                width: 120px;
                font-weight: bold;
            }

            label.checkbox {
                float: left;
                width: 116px;
                font-weight: bold;
            }

            label.fields {
                float: left;
                width: 80px;
                font-weight: bold;
            }

            span.labelLook {
                font-weight: bold;
            }

            input, textarea, select {
            / / margin-bottom: 5 px;
            }

            textarea {
                width: 450px;
                height: 100px;
            }

            .boxes {
                width: 1em;
            }

            #submitbutton {
                margin-left: 120px;
                margin-top: 5px;
                width: 90px;
            }

            br {
                clear: left;
            }
        </style>
    </head>

    <body class="BodyStyle" vlink="#0000FF">

    <table class="MainTable" id="scrollNumber1" name="encounterTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.editRxFax.msgPrefs"/></td>
            <td style="color: white" class="MainTableTopRowRightColumn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.editRxAddress.msgProviderAddress"/></td>
        </tr>
        <tr>
            <td class="MainTableLeftColumn">&nbsp;</td>
            <td class="MainTableRightColumn">
                <%
                    String address = "", city = "", province = "", postal = "";

                    UserProperty prop = null;
                    prop = propertyDao.getProp(curUser_no, "rxAddress");
                    if (prop != null) {
                        address = prop.getValue();
                    }
                    prop = propertyDao.getProp(curUser_no, "rxCity");
                    if (prop != null) {
                        city = prop.getValue();
                    }
                    prop = propertyDao.getProp(curUser_no, "rxProvince");
                    if (prop != null) {
                        province = prop.getValue();
                    }
                    prop = propertyDao.getProp(curUser_no, "rxPostal");
                    if (prop != null) {
                        postal = prop.getValue();
                    }


                    if (request.getAttribute("status") == null) {

                %>

                <form action="${pageContext.request.contextPath}/EditAddress.do" method="post">

			<span style="color:blue">By entering in values, you will 
			<ul>
			<li>Override the address in prescriptions</li>
			<li> When choosing your letterhead in consult requests, the clinic address and your provider record's address will be overridden
			</li>
			</ul>
			</span>
                    <br/>


                    <label for="address">Address</label>
                    <input type="text" name="address" value="<%=address %>" />
                    <br/>
                    <label for="city">City</label>
                    <input type="text" name="city" value="<%=city %>" />
                    <br/>
                    <label for="province">Province</label>
                    <input type="text" name="province" value="<%=province %>" />
                    <br/>
                    <label for="postal">Postal</label>
                    <input type="text" name="postal" value="<%=postal %>" />
                    <br/>

                    <input type="submit" onclick="return validate();"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.editRxFax.btnSubmit"/>"/>
                </form> <%
            } else if (((String) request.getAttribute("status")).equals("complete")) {
            %> <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.editRxAddress.msgSuccess"/> <br>
                <%=address%>, <%=city%>, <%=province%>, <%=postal%>  <%
                }
            %>
            </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn"></td>
            <td class="MainTableBottomRowRightColumn"></td>
        </tr>
    </table>
    </body>
</html>
