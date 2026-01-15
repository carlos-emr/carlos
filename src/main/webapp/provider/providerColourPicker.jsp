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

<%@ page import="ca.openosp.openo.providers.data.*" %>
<%@ page import="ca.openosp.openo.providers.data.ProviderColourUpdater" %>


<%
    if (session.getValue("user") == null)
        response.sendRedirect(request.getContextPath() + "/logout.htm");
    String curUser_no = (String) session.getAttribute("user");
    boolean bFirstLoad = request.getAttribute("status") == null;

%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
"http://www.w3.org/TR/html4/loose.dtd">

<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setColour.title"/></title>

        <link rel="stylesheet" type="text/css"
              href="<%= request.getContextPath() %>/oscarEncounter/encounterStyles.css">
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/picker.js"></script>
        <script type="text/javascript">
            function update() {
                var elem = document.getElementById('cdisp');
                elem.style.backgroundColor = document.forms[0].elements['colour'].value;
                setTimeout('update()', 1000);
            }
        </script>
        <script>
            if (typeof TCP !== 'undefined') {
                TCP.popup = function(field, palette) {
                    this.field = field;
                    this.initPalette = !palette || palette > 3 ? 0 : palette;
        
                    var w = 194, h = 400,
                        move = screen ?
                            ',left=' + ((screen.width - w) >> 1) + ',top=' + ((screen.height - h) >> 1) : '',
                        o_colWindow = window.open('<%= request.getContextPath() %>/admin/picker.html', null,
                            "help=no,status=no,scrollbars=no,resizable=no" + move +
                            ",width=" + w + ",height=" + h + ",dependent=yes", true);
                    o_colWindow.opener = window;
                    o_colWindow.focus();
                }
            }
        </script>

    </head>

    <body class="BodyStyle" vlink="#0000FF"
            <%=bFirstLoad ? "onload='update()'" : ""%>>

    <table class="MainTable" id="scrollNumber1" name="encounterTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setColour.msgPrefs"/></td>
            <td style="color: white" class="MainTableTopRowRightColumn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setColour.msgProviderColour"/></td>
        </tr>
        <tr>
            <td class="MainTableLeftColumn">&nbsp;</td>
            <td class="MainTableRightColumn">
                <%
                    ProviderColourUpdater colourUpdater = new ProviderColourUpdater(curUser_no);
                    String colour = colourUpdater.getColour();

                    if (request.getAttribute("status") == null) {

                %>
                <form action="${pageContext.request.contextPath}/setProviderColour.do" method="post">
                <input type="hidden" name="colour" id="colour" value="<%=colour%>"/>
                <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setColour.msgEdit"/>
                <a href="javascript:TCP.popup(document.forms[0].elements['colour'])"><img
                        width="15" height="13" border="0" src="<%= request.getContextPath() %>/images/sel.gif"></a>
                <p><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setColour.msgSatus"/>
                <div id='cdisp' style='width: 33%'>&nbsp;</div>
                </p>
                <p><input type="submit" onclick="return validate();"
                          value="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setColour.btnSubmit"/>"/>
                    </form> <%
               }
               else if( ((String)request.getAttribute("status")).equals("complete") ) {
            %>
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setColour.msgSuccess"/> <br>

                            <%
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
