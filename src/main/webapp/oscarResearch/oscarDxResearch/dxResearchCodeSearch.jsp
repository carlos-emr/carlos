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

<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>


<%
    String user_no;
    user_no = (String) session.getAttribute("user");
%>

<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxResearchCodeSearch.title"/></title>
        <script LANGUAGE="JavaScript">
            //<!--
            function CodeAttach(File0) {

                self.close();
                self.opener.document.forms[0].xml_research1.value = File0;
                self.opener.document.forms[0].xml_research2.value = '';
                self.opener.document.forms[0].xml_research3.value = '';
                self.opener.document.forms[0].xml_research4.value = '';
                self.opener.document.forms[0].xml_research5.value = '';

            }

            function CodesAttach() {

                var nbSearchCodes = document.codeSearchForm.searchCodes;
                if (nbSearchCodes.length == undefined) {
                    if (nbSearchCodes.checked) self.opener.document.forms[0].xml_research1.value = nbSearchCodes.value;
                } else {
                    var j = 0;
                    for (var i = 0; i < nbSearchCodes.length; i++) {
                        if (nbSearchCodes[i].checked) {
                            if (j == 0) {
                                self.opener.document.forms[0].xml_research1.value = nbSearchCodes[i].value;
                                j++;
                            } else if (j == 1) {
                                self.opener.document.forms[0].xml_research2.value = nbSearchCodes[i].value;
                                j++;
                            } else if (j == 2) {
                                self.opener.document.forms[0].xml_research3.value = nbSearchCodes[i].value;
                                j++;
                            } else if (j == 3) {
                                self.opener.document.forms[0].xml_research4.value = nbSearchCodes[i].value;
                                j++;
                            } else if (j == 4) {
                                self.opener.document.forms[0].xml_research5.value = nbSearchCodes[i].value;
                                j++;
                            } else {
                                break;
                            }
                        }
                    }
                }
                self.close();
            }

            //-->
        </script>
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>
    </head>

    <body bgcolor="#FFFFFF">
    <form name="codeSearchForm" method="post">

        <table width="600" cellspacing="1">
            <tr>
                <td colspan="2"><h3><%=session.getAttribute("codeType").toString().toUpperCase()%> <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxResearchCodeSearch.msgCodeSearch"/></h3></td>
            </tr>
            <tr class="heading">
                <td width="20%"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxResearchCodeSearch.msgCode"/></td>
                <td width="80%"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxResearchCodeSearch.msgDescription"/></td>
            </tr>

            <%
                int intCount = 0;
                String color = "#EEEEFF";
                int Count = 0;
            %>
            <c:forEach var="code" items="${allMatchedCodes.dxCodeSearchBeanVector}" varStatus="loopStatus">
                <c:set var="color" value="${loopStatus.index % 2 == 0 ? '#FFFFFF' : '#EEEEFF'}"/>
                <tr bgcolor="${color}">
                    <td>
                        <input type="checkbox" name="searchCodes" value="${code.dxSearchCode}"
                            ${code.exactMatch == 'true' ? 'checked' : ''} />
                        <c:out value="${code.dxSearchCode}"/>
                    </td>
                    <td><c:out value="${code.description}"/></td>
                </tr>
            </c:forEach>
            <% if (intCount == 0) { %>
            <tr bgcolor="<%=color%>">
                <td colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxResearchCodeSearch.msgNoMatch"/>.
                    <%// =i%>
                </td>

            </tr>
            <% }%>

        </table>
        <input type="button" name="confirm" value="Confirm"
               onclick="javascript:CodesAttach();"><input type="button"
                                                          name="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnCancel"/>" value="Cancel"
                                                          onclick="javascript:window.close()">

        <p></p>
        <p>&nbsp;</p>
        <h3>&nbsp;</h3>
    </form>
    </body>
</html>
