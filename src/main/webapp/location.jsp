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
<%@ page import="java.util.*" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.commn.model.Facility" %>
<%@ page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="org.apache.commons.text.StringEscapeUtils" %>
<%@ page import="ca.openosp.openo.util.LabelValueBean" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ include file="/common/webAppContextAndSuperMgr.jsp" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<p>&nbsp;</p>
<h3 align="center"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.selectClinicSite"/></h3>
<head>
    <script type="text/javascript" src="<%=request.getContextPath()%>/js/jquery.js"></script>
</head>
<body>
<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    String providerNo = loggedInInfo.getLoggedInProviderNo();
    Facility facility = loggedInInfo.getCurrentFacility();
    List<LabelValueBean> programs = new ArrayList<LabelValueBean>();
    int defaultprogramId = 0;
%>
<p>&nbsp;</p>
<table align="center">
    <tr>
        <td align="right" width="30%"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.clinicSite"/>:</td>
        <td align="left" width="60%">

            <select id="programIdForLocation" name="programIdForLocation">
                <%
                    if (programs != null && !programs.isEmpty()) {
                        for (LabelValueBean program : programs) {
                            String selected = (Integer.parseInt(program.getValue()) == defaultprogramId) ? " selected=\"selected\" " : "";
                %>
                <option value="<%=program.getValue()%>" <%=selected%>><%=StringEscapeUtils.escapeHtml4(program.getLabel())%>
                </option>
                <% }
                }
                %>
            </select>
            <input type="button" value="Go -->" onClick="javascript:setLocation();"/>
        </td>
    </tr>
</table>
</body>
<script type="text/javascript">
    function setLocation() {
        var programIdForLocation = jQuery("#programIdForLocation").val();
        window.location.href = "provider/providercontrol.jsp?<%=ca.openosp.openo.utility.SessionConstants.CURRENT_PROGRAM_ID%>=" + encodeURIComponent(programIdForLocation);
    }
</script>
</html>
