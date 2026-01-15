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

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_con" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_con");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page
        import="java.util.*, org.w3c.dom.*, ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil.*" %>
<%@ page import="ca.openosp.openo.clinic.*" %>
<%@ page import="ca.openosp.openo.clinic.ClinicData" %>
<%@ page import="ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil.EctConsultationFormRequestUtil" %>
<%@ page import="ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil.EctConsultationFaxForm" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>



<%
    String curUser_no, requestId;
    curUser_no = (String) session.getAttribute("user");
    requestId = (String) request.getAttribute("reqId");

    //if (requestId == null) { return "bad"; }
    EctConsultationFormRequestUtil reqFrm;
    reqFrm = new EctConsultationFormRequestUtil();
    reqFrm.estRequestFromId(LoggedInInfo.getLoggedInInfoFromSession(request), requestId);
    ClinicData clinic = new ClinicData();

    String strPhones = clinic.getClinicDelimPhone() == null ? "" : clinic.getClinicDelimPhone();
    String strFaxes = clinic.getClinicDelimFax() == null ? "" : clinic.getClinicDelimFax();
    Vector vecPhones = new Vector();
    Vector vecFaxes = new Vector();
    StringTokenizer st = new StringTokenizer(strPhones, "|");
    while (st.hasMoreTokens()) {
        vecPhones.add(st.nextToken());
    }

    st = new StringTokenizer(strFaxes, "|");
    while (st.hasMoreTokens()) {
        vecFaxes.add(st.nextToken());
    }


%>
<link rel="stylesheet" type="text/css" href="encounterStyles.css">
<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title>OscarFax - Consultations</title>
    <script type="text/javascript">
        // Declaring required variables
        var digits = "0123456789";
        // non-digit characters which are allowed in phone numbers
        var phoneNumberDelimiters = "()- ";
        // characters which are allowed in international phone numbers
        // (a leading + is OK)
        var validWorldPhoneChars = phoneNumberDelimiters + "+";
        // Minimum no of digits in an international phone no.
        var minDigitsInIPhoneNumber = 10;

        function isInteger(s) {
            var i;
            for (i = 0; i < s.length; i++) {
                // Check that current character is number.
                var c = s.charAt(i);
                if (((c < "0") || (c > "9"))) return false;
            }
            // All characters are numbers.
            return true;
        }

        function stripChars(s, bag) {
            var i;
            var returnString = "";
            // Search through string's characters one by one.
            // If character is not in bag, append to returnString.
            for (i = 0; i < s.length; i++) {
                // Check that current character isn't whitespace.
                var c = s.charAt(i);
                if (bag.indexOf(c) == -1) returnString += c;
            }
            return returnString;
        }

        function checkInternationalPhone(strPhone) {
            s = stripCharsInBag(strPhone, validWorldPhoneChars);
            return (isInteger(s) && s.length >= minDigitsInIPhoneNumber);
        }

        function ValidateForm() {
            //alert("I GET CALLED");
            var Phone = document.EctConsultationFaxForm.recipientFaxNumber

            if ((Phone.value == null) || (Phone.value == "")) {
                alert("Please Enter your Phone Number")
                Phone.focus()
                return false
            }
            var EditPhone = stripChars(Phone.value, "()- ");
            //alert(EditPhone);
            if (EditPhone.length < 9) {
                alert("Minimum number of digits is 10");
                Phone.focus();
                return false;
            }
            //alert(EditPhone.length);
            if (!isInteger(EditPhone)) {
                alert("Phone number must be numeric");
                Phone.focus();
                return false;
            }
            //alert(isInteger(EditPhone));
            return true;
        }

    </script>
</head>

<body class="BodyStyle" vlink="#0000FF">
<!--  -->
<table class="MainTable" id="scrollNumber1" name="encounterTable">
    <tr class="MainTableTopRow">
        <td class="MainTableTopRowLeftColumn">Fax</td>
        <td class="MainTableTopRowRightColumn">
            <table class="TopStatusBar">
                <tr>
                    <td>consultation Requests</td>
                    <td></td>
                    <td style="text-align: right"><a
                            href="javascript:popupStart(300,400,'About.jsp')">About</a> | <a
                            href="javascript:popupStart(300,400,'License.jsp')">License</a></td>
                </tr>
            </table>
        </td>
    </tr>
    <tr>
        <td class="MainTableLeftColumn">&nbsp;</td>
        <td class="MainTableRightColumn"><form
                action="${pageContext.request.contextPath}/oscarEncounter/oscarConsultationRequests/consultationFax.do" method="post"
                onsubmit="return ValidateForm()">
            <%
                EctConsultationFaxForm thisForm;
                thisForm = (EctConsultationFaxForm) request.getAttribute("EctConsultationFaxForm");
                thisForm.setRecipient(reqFrm.getSpecailistsName(reqFrm.specialist));
                thisForm.setFrom(reqFrm.getProviderName(curUser_no));
                thisForm.setRecipientFaxNumber(reqFrm.specFax);
            %>
            <table>
                <tr>
                    <td>To:</td>
                    <td><input type="text" name="recipient" id="recipient" /></td>
                </tr>
                <tr>
                    <td>From:</td>
                    <td><input type="text" name="from" id="from" /></td>
                </tr>
                <tr>
                    <td>Fax Number:</td>
                    <td><input type="text" name="recipientFaxNumber" id="recipientFaxNumber" /></td>
                </tr>

                <tr>
                    <td>Senders Phone:</td>
                    <td><select name="sendersPhone" id="sendersPhone">
                        <% for (int i = 0; i < vecPhones.size(); i++) {
                            String te = (String) vecPhones.elementAt(i);
                        %>
                        <option value="<%=te%>"><%=te%>
                        </option>
                        <% }%>
                    </select></td>
                </tr>
                <tr>
                    <td>Senders Fax:</td>
                    <td><select name="sendersFax" id="sendersFax">
                        <% for (int i = 0; i < vecFaxes.size(); i++) {
                            String te = (String) vecFaxes.elementAt(i);
                        %>
                        <option value="<%=te%>"><%=te%>
                        </option>
                        <% }%>
                    </select></td>
                </tr>
                <tr>
                    <td colspan=2>Comments</td>
                </tr>
                <tr>
                    <td colspan=2><textarea cols="30" rows="7" name="comments"></textarea></td>
                </tr>
                <tr>
                    <td colspan=2><input type="submit" value="Send to Fax Server"/>
                    </td>
                </tr>
            </table>
            <input type="hidden" name="requestId"
                   value="<%= (String) request.getAttribute("reqId")%>" \>
            <input type="hidden" name="printType"
                   value="<%= (String) request.getAttribute("printType")%>" \>
        </form></td>
    </tr>
    <tr>
        <td class="MainTableBottomRowLeftColumn">&nbsp;</td>
        <td class="MainTableBottomRowRightColumn">&nbsp;</td>
    </tr>
</table>
</body>
</html>
