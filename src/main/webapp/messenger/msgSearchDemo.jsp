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

<%--
/**
 * Demographic Search Modal for Message Recipients
 *
 * This JSP page provides a demographic search interface used as a popup window
 * for selecting message recipients in the OpenO EMR messenger system. It allows
 * searching for active, inactive, or all patients and writing the selection back
 * to the parent window's message composition form.
 *
 * Main Features:
 * - Patient search with configurable search modes (name, phone, etc.)
 * - Support for active, inactive, and all patient status filtering
 * - Automatic form submission on first load when triggered from parent window
 * - Selected demographic data written back to parent window's message form
 * - Integrated with OpenO's demographic control system
 *
 * Security Requirements:
 * - Requires "_msg" object read permissions via security taglib
 * - User session validation and role-based access control
 *
 * Request Parameters:
 * - keyword: Search term for patient lookup
 * - demographic_no: Selected patient ID (returned from search results)
 * - firstSearch: Boolean flag to auto-submit search on page load
 * - search_mode: Search type (name, phone, etc.) from oscar.properties
 *
 * Session Dependencies:
 * - Standard user session with role information
 * - Integration with demographic control system
 *
 * JavaScript Functions:
 * - searchInactive(): Search for inactive patients only
 * - searchAll(): Search across all patient statuses
 * - write2Parent(): Write selected demographic back to opener window
 * - checkTypeIn(): Form validation (placeholder, always returns true)
 *
 * Parent Window Integration:
 * - Updates opener's demographic_no field with selected patient ID
 * - Updates selectedDemo field with patient display name
 * - Automatically closes search window after selection
 *
 * @since 2003
 */
--%>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_msg" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_msg");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="java.lang.*" errorPage="/errorpage.jsp" %>
<%@ page import="ca.openosp.OscarProperties" %>
<%
    String demographic_no = request.getParameter("demographic_no");
    boolean firstSearch = request.getParameter("firstSearch") == null ? false : (request.getParameter("firstSearch")).equalsIgnoreCase("true") ? true : false;

%>
<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title>Demographic Search</title>
</head>
<body
        onload="<% if ( firstSearch) { %> document.forms[0].submit() <% } %>">
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>



<script language="JavaScript">
    /**
     * Search for inactive patients only
     */
    function searchInactive() {
        document.titlesearch.ptstatus.value = "inactive";
        if (checkTypeIn()) document.titlesearch.submit();
    }

    /**
     * Search across all patient statuses (active and inactive)
     */
    function searchAll() {
        document.titlesearch.ptstatus.value = "";
        if (checkTypeIn()) document.titlesearch.submit();
    }

    /**
     * Form validation placeholder - always returns true
     * @return {boolean} Always true, no actual validation implemented
     */
    function checkTypeIn() {
        return true;
    }

    /**
     * Write selected demographic data back to parent message window
     * @param {string} keyword - Patient display name
     * @param {string} demographic_no - Patient demographic number
     */
    function write2Parent(keyword, demographic_no) {
        // Update parent window's message form with selected patient
        opener.document.forms[0].demographic_no.value = demographic_no;
        opener.document.forms[0].selectedDemo.value = keyword;
        opener.document.forms[0].keyword.value = "";
        
        // Focus back to keyword field in parent window
        opener.document.forms[0].keyword.focus();
    }

</script>

<table BORDER="0" CELLPADDING="0" CELLSPACING="2" WIDTH="100%"
       bgcolor="#CCCCFF">
    <form method="post" name="titlesearch"
          action="<%= request.getContextPath() %>/demographic/demographiccontrol.jsp"
          onsubmit="return checkTypeIn()">
        <tr>
            <td colspan="6" class="RowTop"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.msgSearch"/></b></td>
        </tr>
        <%
            String keyword = request.getParameter("keyword");

            if (keyword == null) {
                keyword = "";
            }
        %>
        <tr>
            <td>
                <table bgcolor="white" width="100%">
                    <tr>
                        <td width="10%" nowrap></td>
                        <td nowrap></td>
                        <td nowrap></td>
                        <td valign="middle" rowspan="2" ALIGN="left"><input type="text"
                                                                            NAME="keyword" VALUE="<%=keyword%>"
                                                                            SIZE="17" MAXLENGTH="100">
                            <%
                                String searchMode = request.getParameter("search_mode");
                                if (searchMode == null || searchMode.isEmpty()) {
                                    searchMode = OscarProperties.getInstance().getProperty("default_search_mode", "search_name");
                                }
                            %>
                            <input type="hidden" name="outofdomain" value="">
                            <input type="hidden" name="search_mode" value="<%=searchMode%>">
                            <INPUT TYPE="hidden" NAME="orderby" VALUE="last_name, first_name">
                            <INPUT TYPE="hidden" NAME="dboperation" VALUE="search_titlename">
                            <INPUT TYPE="hidden" NAME="limit1" VALUE="0"> <INPUT
                                    TYPE="hidden" NAME="limit2" VALUE="10"> <INPUT
                                    TYPE="hidden" NAME="displaymode" VALUE="Search"> <INPUT
                                    TYPE="hidden" NAME="ptstatus" VALUE="active"> <INPUT
                                    TYPE="hidden" NAME="fromMessenger" VALUE="true"> <INPUT
                                    TYPE="SUBMIT"
                                    VALUE="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.msgSearch"/>"
                                    SIZE="17"
                                    TITLE="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.tooltips.searchActive"/>">
                            &nbsp;&nbsp;&nbsp; <INPUT TYPE="button" onclick="searchInactive();"
                                                      TITLE="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.tooltips.searchInactive"/>"
                                                      VALUE="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.Inactive"/>">
                            <INPUT TYPE="button" onclick="searchAll();"
                                   TITLE="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.tooltips.searchAll"/>"
                                   VALUE="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.All"/>"></td>
                    </tr>
                    <tr bgcolor="white">
                        <td nowrap></td>
                        <td nowrap></td>
                        <td nowrap></td>
                    </tr>
                </table>
            </td>
        </tr>
    </form>
</table>
<script>
    // Auto-close window and write selection back to parent if demographic was selected
    if ("<%=demographic_no%>" != "null") {
        write2Parent("<%=keyword%>", "<%=demographic_no%>");
        self.window.close();
    }
</script>

</body>
</html>
