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

<%@ include file="/taglibs.jsp" %>
<fmt:setBundle basename="oscarResources"/>
<%@ page import="java.util.Properties" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Contact" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%

    String msg = "View Contact Details.";
    Properties prop = new Properties();

%>
<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <script>
            function edit() {
                <%
                Contact c = (Contact)request.getAttribute("contact");
                %>
                var id = '<%=c.getId()%>';
                location.href = '<%=request.getContextPath()%>/demographic/Contact?method=editContact&contact.id=' + id;
            }
        </script>
        <title><fmt:message key="demographic.contactForm.viewTitle"/></title>

    </head>
    <body bgcolor="ivory" onLoad="setfocus()" topmargin="0" leftmargin="0"
          rightmargin="0">
    <table BORDER="0" CELLPADDING="0" CELLSPACING="0" WIDTH="100%">
        <tr>
            <td align="left">&nbsp;</td>
        </tr>
    </table>

    <center>
        <table BORDER="1" CELLPADDING="0" CELLSPACING="0" WIDTH="80%">
            <tr BGCOLOR="#CCFFFF">
                <th><fmt:message key="demographic.contactForm.viewTitle"/>
                </th>
            </tr>
        </table>
    </center>
    <form action="${pageContext.request.contextPath}/demographic/Contact" method="post">
        <input type="hidden" name="contact.id" value="${carlos:forHtmlAttribute(contact.id)}"/>
        <input type="hidden" name="method" value="saveContact"/>
        <table width="100%" border="0" cellspacing="2" cellpadding="2">
            <tr>
                <td>&nbsp;</td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.lastName"/></b></td>
                <td>
                    <input type="text" name="contact.lastName" value="${carlos:forHtmlAttribute(contact.lastName)}" size="30"
                           readonly="readonly">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.firstName"/></b></td>
                <td>
                    <input type="text" name="contact.firstName" value="${carlos:forHtmlAttribute(contact.firstName)}" size="30"
                           readonly="readonly">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.address"/></b></td>
                <td>
                    <input type="text" name="contact.address" value="${carlos:forHtmlAttribute(contact.address)}" size="50"
                           readonly="readonly">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.address2"/></b></td>
                <td>
                    <input type="text" name="contact.address2" value="${carlos:forHtmlAttribute(contact.address2)}" size="50"
                           readonly="readonly">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.city"/></b></td>
                <td>
                    <input type="text" name="contact.city" value="${carlos:forHtmlAttribute(contact.city)}" size="30"
                           readonly="readonly">
                </td>
            </tr>
            <tr bgcolor="#EEEEFF">
                <td align="right"><b><fmt:message key="demographic.contactForm.province"/></b></td>
                <td>
                    <% String region = prop.getProperty("province", "");
                        region = "".equals(region) ? "ON" : region;
                    %> <select name="contact.province">
                    <option value="AB" <%=region.equals("AB") ? " selected" : ""%>><fmt:message key="admin.sitesAdminDetail.province.AB"/></option>
                    <option value="BC" <%=region.equals("BC") ? " selected" : ""%>><fmt:message key="admin.sitesAdminDetail.province.BC"/></option>
                    <option value="MB" <%=region.equals("MB") ? " selected" : ""%>><fmt:message key="admin.sitesAdminDetail.province.MB"/></option>
                    <option value="NB" <%=region.equals("NB") ? " selected" : ""%>><fmt:message key="admin.sitesAdminDetail.province.NB"/></option>
                    <option value="NL" <%=region.equals("NL") ? " selected" : ""%>><fmt:message key="admin.sitesAdminDetail.province.NL"/></option>
                    <option value="NT" <%=region.equals("NT") ? " selected" : ""%>><fmt:message key="admin.sitesAdminDetail.province.NT"/></option>
                    <option value="NS" <%=region.equals("NS") ? " selected" : ""%>><fmt:message key="admin.sitesAdminDetail.province.NS"/></option>
                    <option value="NU" <%=region.equals("NU") ? " selected" : ""%>><fmt:message key="admin.sitesAdminDetail.province.NU"/></option>
                    <option value="ON" <%=region.equals("ON") ? " selected" : ""%>><fmt:message key="admin.sitesAdminDetail.province.ON"/></option>
                    <option value="PE" <%=region.equals("PE") ? " selected" : ""%>><fmt:message key="admin.sitesAdminDetail.province.PE"/></option>
                    <option value="QC" <%=region.equals("QC") ? " selected" : ""%>><fmt:message key="admin.sitesAdminDetail.province.QC"/></option>
                    <option value="SK" <%=region.equals("SK") ? " selected" : ""%>><fmt:message key="admin.sitesAdminDetail.province.SK"/></option>
                    <option value="YT" <%=region.equals("YT") ? " selected" : ""%>><fmt:message key="admin.sitesAdminDetail.province.YT"/></option>
                    <option value="US" <%=region.equals("US") ? " selected" : ""%>><fmt:message key="demographic.contactForm.usResident"/></option>
                </select> <fmt:message key="demographic.contactForm.country"/>
                    <input type="text" name="contact.country" value="${carlos:forHtmlAttribute(contact.country)}" size="2"
                           maxlength="2" readonly="readonly">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.postal"/></b></td>
                <td>
                    <input type="text" name="contact.postal" value="${carlos:forHtmlAttribute(contact.postal)}"
                           size="30" readonly="readonly">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.resPhone"/></b></td>
                <td>
                    <input type="text" name="contact.residencePhone" value="${carlos:forHtmlAttribute(contact.residencePhone)}"
                           size="30" readonly="readonly">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.cellPhone"/></b></td>
                <td>
                    <input type="text" name="contact.cellPhone" value="${carlos:forHtmlAttribute(contact.cellPhone)}" size="30"
                           readonly="readonly">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.workPhone"/></b></td>
                <td>
                    <input type="text" name="contact.workPhone" value="${carlos:forHtmlAttribute(contact.workPhone)}" size="15"
                           readonly="readonly"/>
                    <fmt:message key="demographic.contactForm.ext"/> <input type="text" name="contact.workPhoneExtension"
                                value="${carlos:forHtmlAttribute(contact.workPhoneExtension)}" size="10" readonly="readonly"/>
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.fax"/></b></td>
                <td>
                    <input type="text" name="contact.fax" value="${carlos:forHtmlAttribute(contact.fax)}" size="30"
                           readonly="readonly">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.email"/></b></td>
                <td>
                    <input type="text" name="contact.email" value="${carlos:forHtmlAttribute(contact.email)}" size="30"
                           readonly="readonly">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.note"/></b></td>
                <td>
                    <input type="text" name="contact.note" value="${carlos:forHtmlAttribute(contact.note)}" size="30"
                           readonly="readonly">
                </td>
            </tr>
            <tr>
                <td>&nbsp;</td>
                <td>&nbsp;</td>
            </tr>
            <tr>
                <td align="center" bgcolor="#CCCCFF" colspan="2">
                    <input type="button" name="Edit" value="<fmt:message key='global.btnEdit'/>" onclick="edit()">
                    <input type="button" name="Cancel" value="<fmt:message key="admin.resourcebaseurl.btnExit"/>"
                           onClick="window.close()">
                </td>
            </tr>
        </table>
    </form>
    </body>
</html>
