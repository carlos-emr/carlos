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

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_demographic" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_demographic");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="java.util.Date" %>
<%@page import="io.github.carlos_emr.carlos.integration.mchcv.HCMagneticStripe" %>
<%@page import="io.github.carlos_emr.carlos.integration.mchcv.HCValidationResult" %>

<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title><fmt:message key="demographic.demographicswipe.title"/></title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/web.css"/>
    <script LANGUAGE="JavaScript">
        <!--


        function Attach(lname, fname, hin, yob, mob, dob, vercode, sex, effyear, effmonth, effdate, endyear, endmonth, enddate) {
            if (confirm("<fmt:message key='demographic.demographicswipe.confirmReplace'/>")) {

                self.close();
                self.opener.document.updatedelete.last_name.value = lname;
                self.opener.document.updatedelete.first_name.value = fname;
                self.opener.document.updatedelete.hin.value = hin;
                self.opener.document.updatedelete.year_of_birth.value = yob;
                self.opener.document.updatedelete.month_of_birth.value = mob;
                self.opener.document.updatedelete.date_of_birth.value = dob;
                self.opener.document.updatedelete.ver.value = vercode;
                self.opener.document.updatedelete.sex.value = sex;
                self.opener.document.updatedelete.eff_date_year.value = effyear;
                self.opener.document.updatedelete.eff_date_month.value = effmonth;
                self.opener.document.updatedelete.eff_date_date.value = effdate;
                self.opener.document.updatedelete.hc_renew_date_year.value = endyear;
                self.opener.document.updatedelete.hc_renew_date_month.value = endmonth;
                self.opener.document.updatedelete.hc_renew_date_date.value = enddate;
            }
        }

        -->
    </script>
</head>


<body topmargin="0" onLoad="setfocus();" leftmargin="0" rightmargin="0">
<table border="0" cellspacing="0" cellpadding="0" width="100%">
    <tr bgcolor="#486ebd">
        <th align=CENTER NOWRAP><font face="Helvetica" color="#FFFFFF"><fmt:message key="demographic.demographicswipe.heading"/></font></th>
    </tr>
</table>
<table BORDER="0" CELLPADDING="1" CELLSPACING="0" WIDTH="100%"
       BGCOLOR="#C4D9E7">
    <%
        HCMagneticStripe hcMagneticStripe = (HCMagneticStripe) request.getAttribute("hcMagneticStripe");
        HCValidationResult validationResult = (HCValidationResult) request.getAttribute("validationResult");

        String responseCode = validationResult.getResponseCode();
        String responseDescription = validationResult.getResponseDescription();
        String responseAction = validationResult.getResponseAction();

        String firstName = validationResult.getFirstName();
        if (firstName == null) {
            firstName = hcMagneticStripe.getFirstName();
        }
        if (firstName != null) {
            firstName = firstName.toUpperCase();
        }

        String lastName = validationResult.getLastName();
        if (lastName == null) {
            lastName = hcMagneticStripe.getLastName();
        }
        if (lastName != null) {
            lastName = lastName.toUpperCase();
        }

        String birthDate = validationResult.getBirthDate();
        if (birthDate == null) {
            birthDate = hcMagneticStripe.getBirthDate();
        }

        String dobyear = birthDate.substring(0, 4);
        String dobmonth = birthDate.substring(4, 6);
        String dobdate = birthDate.substring(6, 8);

        String expiryDate = validationResult.getExpiryDate();
        if (expiryDate == null) {
            expiryDate = hcMagneticStripe.getExpiryDate();
        }

        String endyear = expiryDate.substring(0, 4);
        String endmonth = expiryDate.substring(4, 6);
        String enddate = expiryDate.substring(6, 8);

        String issueDate = validationResult.getIssueDate();
        if (issueDate == null) {
            issueDate = hcMagneticStripe.getIssueDate();
        }
        String effyear = issueDate.substring(0, 4);
        String effmonth = issueDate.substring(4, 6);
        String effdate = issueDate.substring(6, 8);

        String gender = validationResult.getGender();
        if (gender == null) {
            gender = hcMagneticStripe.getSex();
        }
    %>

    <tr>
        <td align="left"><font size="-1"><b><fmt:message key="demographic.demographicswipe.validationResult"/> </b></font></td>
        <td><font size="-1"><carlos:encode value='<%= responseDescription %>' context="html"/>
        </font></td>
    </tr>
    <tr>
        <td align="left"><font size="-1"><b><fmt:message key="demographic.demographicswipe.responseAction"/> </b></font></td>
        <td><font size="-1">(<carlos:encode value='<%= responseCode %>' context="html"/>) <carlos:encode value='<%= responseAction %>' context="html"/>
        </font></td>
    </tr>

    <tr>
        <td align="right"><b><fmt:message key="demographic.demographicswipe.lastName"/> </b></td>
        <td align="left"><input type="text" name="last_name"
                                value="<carlos:encode value='<%= lastName %>' context=\"htmlAttribute\"/>"></td>
        <td align="right"><b><fmt:message key="demographic.demographicswipe.firstName"/> </b></td>
        <td align="left"><input type="text" name="first_name"
                                value="<carlos:encode value='<%= firstName %>' context=\"htmlAttribute\"/>"></td>
    </tr>
    <tr valign="top">
        <td align="right"><b>DOB</b><font size="-2">(yyyy-mm-dd)</font><b>:</b>
        </td>
        <td align="left">
            <table border="0" cellpadding="0" cellspacing="0">
                <tr>
                    <td><input type="text" name="year_of_birth"
                               value="<carlos:encode value='<%= dobyear %>' context=\"htmlAttribute\"/>" size="4" maxlength="4"></td>
                    <td>-</td>
                    <td><input type="text" name="month_of_birth"
                               value="<carlos:encode value='<%= dobmonth %>' context=\"htmlAttribute\"/>" size="2" maxlength="2"></td>
                    <td>-</td>
                    <td><input type="text" name="date_of_birth"
                               value="<carlos:encode value='<%= dobdate %>' context=\"htmlAttribute\"/>" size="2" maxlength="2"></td>
                </tr>
            </table>
        </td>
        <td align="right"><b> Sex:</b></td>
        <td align="left"><input type="text" name="sex" value="<carlos:encode value='<%= gender %>' context=\"htmlAttribute\"/>">
        </td>
    </tr>
    <tr valign="top">
        <td align="right"><b><fmt:message key="demographic.demographicswipe.hin"/> </b></td>
        <td align="left"><input type="text" name="hin" value="<carlos:encode value='<%= hcMagneticStripe.getHealthNumber() %>' context=\"htmlAttribute\"/>"></td>
        <td align="right"><b>Ver.</b></td>
        <td align="left"><input type="text" name="ver"
                                value="<carlos:encode value='<%= hcMagneticStripe.getCardVersion().toUpperCase() %>' context=\"htmlAttribute\"/>"></td>
    </tr>
    <tr valign="top">
        <td align="right"><b><fmt:message key="demographic.demographicswipe.effDate"/></b></td>
        <td align="left">
            <table border="0" cellpadding="0" cellspacing="0">
                <tr>
                    <td><input type="text" name="eff_date_year"
                               value="<carlos:encode value='<%= effyear %>' context=\"htmlAttribute\"/>" size="4" maxlength="4"></td>
                    <td>-</td>
                    <td><input type="text" name="eff_date_month"
                               value="<carlos:encode value='<%= effmonth %>' context=\"htmlAttribute\"/>" size="2" maxlength="2"></td>
                    <td>-</td>
                    <td><input type="text" name="eff_date_date"
                               value="<carlos:encode value='<%= effdate %>' context=\"htmlAttribute\"/>" size="2" maxlength="2"></td>
                </tr>
            </table>
        </td>
        <td align="right"><b><fmt:message key="demographic.demographicswipe.renewDate"/></b></td>
        <td align="left">
            <table border="0" cellpadding="0" cellspacing="0">
                <tr>
                    <td><input type="text" name="end_date_year"
                               value="<carlos:encode value='<%= endyear %>' context=\"htmlAttribute\"/>" size="4" maxlength="4"></td>
                    <td>-</td>
                    <td><input type="text" name="end_date_month"
                               value="<carlos:encode value='<%= endmonth %>' context=\"htmlAttribute\"/>" size="2" maxlength="2"></td>
                    <td>-</td>
                    <td><input type="text" name="end_date_date"
                               value="<carlos:encode value='<%= enddate %>' context=\"htmlAttribute\"/>" size="2" maxlength="2"></td>
                </tr>
            </table>
        </td>

    </tr>

</table>

<br>
<br>
<form><input type="button" name="Button1" value="<fmt:message key='demographic.demographicswipe.confirm'/>"
             onclick="javascript:Attach('<carlos:encode value='<%= lastName %>' context=\"javaScriptAttribute\"/>','<carlos:encode value='<%= firstName %>' context=\"javaScriptAttribute\"/>','<carlos:encode value='<%= hcMagneticStripe.getHealthNumber() %>' context=\"javaScriptAttribute\"/>','<carlos:encode value='<%= dobyear %>' context=\"javaScriptAttribute\"/>'
                     ,'<carlos:encode value='<%= dobmonth %>' context=\"javaScriptAttribute\"/>','<carlos:encode value='<%= dobdate %>' context=\"javaScriptAttribute\"/>', '<carlos:encode value='<%= hcMagneticStripe.getCardVersion().toUpperCase() %>' context=\"javaScriptAttribute\"/>','<carlos:encode value='<%= gender %>' context=\"javaScriptAttribute\"/>', '<carlos:encode value='<%= effyear %>' context=\"javaScriptAttribute\"/>', '<carlos:encode value='<%= effmonth %>' context=\"javaScriptAttribute\"/>', '<carlos:encode value='<%= effdate %>' context=\"javaScriptAttribute\"/>'
                     , '<carlos:encode value='<%= endyear %>' context=\"javaScriptAttribute\"/>', '<carlos:encode value='<%= endmonth %>' context=\"javaScriptAttribute\"/>', '<carlos:encode value='<%= enddate %>' context=\"javaScriptAttribute\"/>');"><input
        type="button" name="Button" value="<fmt:message key='demographic.demographicswipe.cancel'/>" onclick=self.close();>
</form>
</body>
</html>
