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

<!--
/*
*
* This software is published under the GPL GNU General Public License.
* This program is free software; you can redistribute it and/or
* modify it under the terms of the GNU General Public License
* as published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version. *
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
* You should have received a copy of the GNU General Public License
* along with this program; if not, write to the Free Software
* Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA. *
*
* <OSCAR TEAM>
*/
-->

<%@ include file="/taglibs.jsp" %>
<fmt:setBundle basename="oscarResources"/>
<%@ page import="java.util.Properties" %>
<%

    String msg = "Enter contact details.";
    Properties prop = new Properties();

%>
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="demographic.contactForm.title"/></title>
        <script language="JavaScript">

            <!--
            function setfocus() {
                this.focus();
                document.forms[0].referral_no.focus();
                document.forms[0].referral_no.select();
            }

            function onSearch() {
                //document.forms[0].submit.value="Search";
                var ret = checkreferral_no();
                return ret;
            }

            function onSave() {
                //document.forms[0].submit.value="Save";
                /*
                var ret = true;
                if(ret==true) {
                    ret = checkAllFields();
                }
                if(ret==true) {
                    ret = confirm("Are you sure you want to save?");
                }
                */
                return true;
            }

            function checkAllFields() {
                var b = true;
                if (document.forms[0].last_name.value.length <= 0) {
                    b = false;
                    alert("<fmt:message key='demographic.contactForm.msgLastNameRequired'/>");
                } else if (document.forms[0].first_name.value.length <= 0) {
                    b = false;
                    alert("<fmt:message key='demographic.contactForm.msgFirstNameRequired'/>");
                }
                return b;
            }

            function isNumber(s) {
                var i;
                for (i = 0; i < s.length; i++) {
                    // Check that current character is number.
                    var c = s.charAt(i);
                    if (c == ".") continue;
                    if (((c < "0") || (c > "9"))) return false;
                }
                // All characters are numbers.
                return true;
            }

            //-->

        </script>
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
                <th><fmt:message key="demographic.contactForm.heading"/>
                </th>
            </tr>
        </table>
    </center>
    <form action="${pageContext.request.contextPath}/demographic/Contact" method="post">
        <input type="hidden" name="contact.id" value="${e:forHtmlAttribute(contact.id)}"/>
        <input type="hidden" name="method" value="saveContact"/>
        <table width="100%" border="0" cellspacing="2" cellpadding="2">
            <tr>
                <td>&nbsp;</td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.lastName"/></b></td>
                <td>
                    <input type="text" name="contact.lastName" value="${e:forHtmlAttribute(contact.lastName)}" size="30">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.firstName"/></b></td>
                <td>
                    <input type="text" name="contact.firstName" value="${e:forHtmlAttribute(contact.firstName)}" size="30">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.address"/></b></td>
                <td>
                    <input type="text" name="contact.address" value="${e:forHtmlAttribute(contact.address)}" size="50">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.address2"/></b></td>
                <td>
                    <input type="text" name="contact.address2" value="${e:forHtmlAttribute(contact.address2)}" size="50">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.city"/></b></td>
                <td>
                    <input type="text" name="contact.city" value="${e:forHtmlAttribute(contact.city)}" size="30">
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
                    <input type="text" name="contact.country" value="${e:forHtmlAttribute(contact.country)}" size="2"
                           maxlength="2">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.postal"/></b></td>
                <td>
                    <input type="text" name="contact.postal" value="${e:forHtmlAttribute(contact.postal)}" size="30">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.resPhone"/></b></td>
                <td>
                    <input type="text" name="contact.residencePhone" value="${e:forHtmlAttribute(contact.residencePhone)}"
                           size="30">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.cellPhone"/></b></td>
                <td>
                    <input type="text" name="contact.cellPhone" value="${e:forHtmlAttribute(contact.cellPhone)}" size="30">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.workPhone"/></b></td>
                <td>
                    <input type="text" name="contact.workPhone" value="${e:forHtmlAttribute(contact.workPhone)}"
                           size="15"/>
                    <fmt:message key="demographic.contactForm.ext"/> <input type="text" name="contact.workPhoneExtension"
                                value="${e:forHtmlAttribute(contact.workPhoneExtension)}" size="10"/>
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.fax"/></b></td>
                <td>
                    <input type="text" name="contact.fax" value="${e:forHtmlAttribute(contact.fax)}" size="30">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.email"/></b></td>
                <td>
                    <input type="text" name="contact.email" value="${e:forHtmlAttribute(contact.email)}" size="30">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.note"/></b></td>
                <td>
                    <input type="text" name="contact.note" value="${e:forHtmlAttribute(contact.note)}" size="30">
                </td>
            </tr>
            <tr>
                <td>&nbsp;</td>
                <td>&nbsp;</td>
            </tr>
            <tr>
                <td align="center" bgcolor="#CCCCFF" colspan="2">
                    <input type="submit" name="submit" value="<fmt:message key="admin.resourcebaseurl.btnSave"/>"
                           onclick="javascript:return onSave();">
                    <input type="button" name="Cancel" value="<fmt:message key="admin.resourcebaseurl.btnExit"/>"
                           onClick="window.close()">
                </td>
            </tr>
        </table>
    </form>
    </body>
</html>
