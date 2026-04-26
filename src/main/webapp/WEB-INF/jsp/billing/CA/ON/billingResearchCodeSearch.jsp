<%--

    Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%@page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@page import="io.github.carlos_emr.carlos.billings.ca.on.data.BillingCodeSearchViewModel" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>

<%
    // ViewBillingResearchCodeSearch2Action enforces _billing r and assembles
    // the view model with the IchppccodeDao lookup the JSP body used to perform.
    BillingCodeSearchViewModel codeSearchModel =
            (BillingCodeSearchViewModel) request.getAttribute("codeSearchModel");
    if (codeSearchModel == null) {
        io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().warn(
                "billingResearchCodeSearch.jsp reached without codeSearchModel — caller "
              + "should route through billing/CA/ON/ViewBillingResearchCodeSearch.");
        codeSearchModel = BillingCodeSearchViewModel.builder().build();
    }
%>
<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title>Research Code Search</title>
    <script LANGUAGE="JavaScript">
        <!--
        function CodeAttach(File0) {

            self.close();
            self.opener.document.serviceform.xml_research1.value = File0;
            self.opener.document.serviceform.xml_research2.value = '';
            self.opener.document.serviceform.xml_research3.value = '';
        }

        -->
    </script>

</head>

<body bgcolor="#FFFFFF" text="#000000">


<h3><font face="Arial, Helvetica, sans-serif">Research
    (ICHPPC) Code Search <font face="Arial, Helvetica, sans-serif"
                               color="#FF0000">(Maximum 3 selections)</font></font></h3>
<form name="servicecode" id="servicecode" method="post"
      action="<%= request.getContextPath() %>/billing/CA/ON/BillingResearchCodeUpdate">
    <table width="600" border="1">

        <tr bgcolor="#FFBC9B">
            <td width="12%"><b><font face="Arial, Helvetica, sans-serif"
                                     size="2">Code</font></b></td>
            <td width="88%"><b><font face="Arial, Helvetica, sans-serif"
                                     size="2">Description</font></b></td>
        </tr>


        <%
            int rowIndex = 0;
            for (BillingCodeSearchViewModel.CodeRow __row : codeSearchModel.getRows()) {
                String __color = (rowIndex++ % 2 == 0) ? "#FFFFFF" : "#F9E6F0";
        %>

        <tr bgcolor="<%=__color%>">
            <td width="12%"><font face="Arial, Helvetica, sans-serif"
                                  size="2"><input type="checkbox" name="code_<carlos:encode value='<%= __row.code() %>' context="htmlAttribute"/>"><carlos:encode value='<%= __row.code() %>' context="html"/>
            </font></td>
            <td width="88%"><font face="Arial, Helvetica, sans-serif"
                                  size="2"><carlos:encode value='<%= __row.description() %>' context="html"/>
            </font></td>
        </tr>
        <% } %>

        <% if (codeSearchModel.isNoMatch()) { %>
        <tr>
            <td colspan="2"><font face="Arial, Helvetica, sans-serif"
                                  size="2">No match found.</font></td>
        </tr>
        <% } %>

        <% if (codeSearchModel.isAutoSelect()) { %>
        <script LANGUAGE="JavaScript">
            <!--
            CodeAttach('<carlos:encode value='<%= codeSearchModel.getAutoSelectCode() %>' context="javaScriptBlock"/>');
            -->
        </script>
        <% } %>
    </table>
    <input type="submit" name="submit" value="Confirm"><input
        type="button" name="cancel" value="Cancel"
        onclick="javascript:window.close()">
    <p></p>
</form>
</body>
</html>
