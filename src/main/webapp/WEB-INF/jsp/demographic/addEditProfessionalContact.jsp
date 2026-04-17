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

<%@page import="io.github.carlos_emr.CarlosProperties" %>
<%@ include file="/taglibs.jsp" %>
<fmt:setBundle basename="oscarResources"/>
<%@ page import="java.util.Properties" %>
<%@ page import="java.util.List, io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ContactSpecialtyDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ContactSpecialty" %>

<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="demographic.profContactForm.title"/></title>
        <script type="text/javascript">

            //<!--
            function setfocus() {
                this.window.focus();
                captureParameters(this);
                forwardToParent();
                // document.forms[0].referral_no.focus();
                // document.forms[0].referral_no.select();
            }

            function captureParameters(id) {

                var keyword = '${ param.keyword }';
                var keywordLastName = null;
                var keywordFirstName = null;
                var firstName = '${ pcontact.firstName }';
                var lastName = '${ pcontact.lastName }';

                if (keyword && keyword.includes(",")) {
                    keywordLastName = keyword.split(",")[0].trim();
                    keywordFirstName = keyword.split(",")[1].trim();
                } else if (keyword) {
                    keywordLastName = keyword;
                }

                if (!lastName) {
                    document.getElementById("pcontact.lastName").value = keywordLastName;
                }
                if (!firstName) {
                    document.getElementById("pcontact.firstName").value = keywordFirstName;
                }
            }

            function forwardToParent() {

                var contactId = '${ requestScope.contactId }'; // server returns the id that was saved.
                var demographicContactId = '${ requestScope.demographicContactId }';
                var contactRole = '${ requestScope.contactRole }';
                var contactName = '${ requestScope.contactName }';
                var contactType = '${ requestScope.contactType }';

                if (contactId) {

                    var data = new Object();
                    data.contactId = contactId;
                    data.contactName = contactName;
                    data.contactRole = contactRole;
                    data.demographicContactId = demographicContactId;
                    data.method = "saveManage";
                    data.contactType = contactType;

                    try {
                        if (opener.popUpData(JSON.stringify(data))) {
                            this.window.close()
                        }
                    } catch (error) {
                        // do nothing
                    }

                }
            }

            function onSearch() {
                //document.forms[0].submit.value="Search";
                var ret = checkreferral_no();
                return ret;
            }

            function onSave() {

                if (checkAllFields()) {
                    document.contactForm.submit();
                }

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
                // return true;
            }

            function checkAllFields() {

                var verified = true;
                var fields = document.forms[0].elements;
                var fieldname;
                var fieldvalue;
                var fieldobject;

                for (var i = 0; i < fields.length; i++) {

                    fieldobject = fields[i];
                    fieldname = fieldobject.id;
                    fieldvalue = fieldobject.value.trim();

                    if (fieldname == "pcontact.lastName" && fieldvalue.length == 0) {
                        verified = false;
                        paintErrorField(fieldobject);
                    }

                    if (fieldname == "pcontact.firstName" && fieldvalue.length == 0) {
                        verified = false;
                        paintErrorField(fieldobject);
                    }

                    if (fieldname == "pcontact.workPhone" && fieldvalue.length == 0) {
                        verified = false;
                        paintErrorField(fieldobject);
                    }

                }

                return verified;
            }

            function paintErrorField(fieldobject) {
                fieldobject.style.border = "medium solid red";
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
    <body bgcolor="ivory" onLoad="setfocus()" topmargin="0" leftmargin="0" rightmargin="0">
    <table BORDER="0" CELLPADDING="0" CELLSPACING="0" WIDTH="100%">
        <tr>
            <td align="left">&nbsp;</td>
        </tr>
    </table>

        <table BORDER="1" CELLPADDING="0" CELLSPACING="0" WIDTH="100%">
            <tr BGCOLOR="#CCFFFF">
                <th style="text-align:center;">
                    <c:choose>
                        <c:when test="${ pcontact.id gt 0 }"><fmt:message key="demographic.profContactForm.edit"/></c:when>
                        <c:otherwise><fmt:message key="demographic.profContactForm.add"/></c:otherwise>
                    </c:choose>
                    <fmt:message key="demographic.profContactForm.titleSuffix"/>
            </th>
        </tr>
    </table>

    <form action="<%= request.getContextPath() %>/demographic/Contact" method="post" id="addEditProfessionalForm" name="contactForm">

        <c:if test="${ pcontact.id gt 0 }">
            <input type="hidden" name="pcontact.id" value="${ pcontact.id }"/>
        </c:if>

        <input type="hidden" name="method" value="saveProContact"/>
        <input type="hidden" name="demographicContactId" value="${ demographicContactId }"/>
        <input type="hidden" name="keywordFirstName" id="keywordFirstName" value=""/>
        <input type="hidden" name="keywordLastName" id="keywordLastName" value=""/>
        <input type="hidden" name="contactType" id="contactType" value="${ param.contactType }"/>

        <table width="100%" border="0" cellspacing="2" cellpadding="2">
            <tr>
                <td>&nbsp;</td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.lastName"/></b></td>
                <td>
                    <input type="text" name="pcontact.lastName" id="pcontact.lastName"
                           value="${ pcontact.lastName }" size="30">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.firstName"/></b></td>
                <td>
                    <input type="text" name="pcontact.firstName" id="pcontact.firstName"
                           value="${e:forHtmlAttribute(pcontact.firstName)}" size="30">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.address"/></b></td>
                <td>
                    <input type="text" name="pcontact.address" id="pcontact.address"
                           value="${e:forHtmlAttribute(pcontact.address)}" size="50">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.address2"/></b></td>
                <td>
                    <input type="text" name="pcontact.address2" id="pcontact.address2"
                           value="${e:forHtmlAttribute(pcontact.address2)}" size="50">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.city"/></b></td>
                <td>
                    <input type="text" name="pcontact.city" id="pcontact.city" value="${e:forHtmlAttribute(pcontact.city)}"
                           size="30">
                </td>
            </tr>
            <tr bgcolor="#EEEEFF">
                <td align="right"><b><fmt:message key="demographic.contactForm.province"/></b></td>
                <td>

                    <c:set var="select" value="${ region }" scope="page"/>
                    <c:if test="${ not empty pcontact.province }">
                        <c:set var="select" value="${ pcontact.province }"/>
                    </c:if>

                    <select name="pcontact.province" id="pcontact.province">
                        <option value="AB" ${ pageScope.select eq 'AB' ? 'selected' : '' }><fmt:message key="admin.sitesAdminDetail.province.AB"/></option>
                        <option value="BC" ${ pageScope.select eq 'BC' ? 'selected' : '' }><fmt:message key="admin.sitesAdminDetail.province.BC"/></option>
                        <option value="MB" ${ pageScope.select eq 'MB' ? 'selected' : '' }><fmt:message key="admin.sitesAdminDetail.province.MB"/></option>
                        <option value="NB" ${ pageScope.select eq 'NB' ? 'selected' : '' }><fmt:message key="admin.sitesAdminDetail.province.NB"/></option>
                        <option value="NL" ${ pageScope.select eq 'NL' ? 'selected' : '' }><fmt:message key="admin.sitesAdminDetail.province.NL"/></option>
                        <option value="NT" ${ pageScope.select eq 'NT' ? 'selected' : '' }><fmt:message key="admin.sitesAdminDetail.province.NT"/></option>
                        <option value="NS" ${ pageScope.select eq 'NS' ? 'selected' : '' }><fmt:message key="admin.sitesAdminDetail.province.NS"/></option>
                        <option value="NU" ${ pageScope.select eq 'NU' ? 'selected' : '' }><fmt:message key="admin.sitesAdminDetail.province.NU"/></option>
                        <option value="ON" ${ pageScope.select eq 'ON' ? 'selected' : '' }><fmt:message key="admin.sitesAdminDetail.province.ON"/></option>
                        <option value="PE" ${ pageScope.select eq 'PE' ? 'selected' : '' }><fmt:message key="admin.sitesAdminDetail.province.PE"/></option>
                        <option value="QC" ${ pageScope.select eq 'QC' ? 'selected' : '' }><fmt:message key="admin.sitesAdminDetail.province.QC"/></option>
                        <option value="SK" ${ pageScope.select eq 'SK' ? 'selected' : '' }><fmt:message key="admin.sitesAdminDetail.province.SK"/></option>
                        <option value="YT" ${ pageScope.select eq 'YT' ? 'selected' : '' }><fmt:message key="admin.sitesAdminDetail.province.YT"/></option>
                        <option value="US" ${ pageScope.select eq 'US' ? 'selected' : '' }><fmt:message key="demographic.contactForm.usResident"/></option>
                    </select>

                    <label for="pcontact.country"><fmt:message key="demographic.contactForm.country"/> </label>
                    <input type="text" name="pcontact.country" id="pcontact.country"
                           value="${e:forHtmlAttribute(pcontact.country)}" size="2" maxlength="2">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.postal"/></b></td>
                <td>
                    <input type="text" name="pcontact.postal" id="pcontact.postal"
                           value="${e:forHtmlAttribute(pcontact.postal)}" size="30">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.resPhone"/></b></td>
                <td>
                    <input type="text" name="pcontact.residencePhone" id="pcontact.residencePhone"
                           value="${e:forHtmlAttribute(pcontact.residencePhone)}" size="30">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.cellPhone"/></b></td>
                <td>
                    <input type="text" name="pcontact.cellPhone" id="pcontact.cellPhone"
                           value="${e:forHtmlAttribute(pcontact.cellPhone)}" size="30">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.workPhone"/></b></td>
                <td>
                    <input type="text" name="pcontact.workPhone" id="pcontact.workPhone"
                           value="${e:forHtmlAttribute(pcontact.workPhone)}" size="15"/>
                    <fmt:message key="demographic.contactForm.ext"/> <input type="text" name="pcontact.workPhoneExtension"
                                value="${e:forHtmlAttribute(pcontact.workPhoneExtension)}" size="10"/>
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.fax"/></b></td>
                <td>
                    <input type="text" name="pcontact.fax" id="pcontact.fax" value="${e:forHtmlAttribute(pcontact.fax)}"
                           size="30">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.email"/></b></td>
                <td>
                    <input type="text" name="pcontact.email" id="pcontact.email"
                           value="${e:forHtmlAttribute(pcontact.email)}" size="30">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.specialty"/></b></td>
                <td>
                    <oscar:oscarPropertiesCheck property="DEMOGRAPHIC_PATIENT_HEALTH_CARE_TEAM" value="true">
                        <%-- Determine which specialty value to use for selection: contactRole (from DemographicContact) or pcontact.specialty --%>
                        <c:set var="selectedSpecialty" value="${ not empty requestScope.contactRole ? requestScope.contactRole : pcontact.specialty }"/>
                        <select id="pcontact.specialty" name="pcontact.specialty">
                            <c:forEach items="${ specialties }" var="specialtyType">
                                <option value="${ specialtyType.id }" ${ specialtyType.id == selectedSpecialty ? 'selected' : '' } >
                                    ${e:forHtml(specialtyType.specialty)}
                                </option>
                            </c:forEach>
                        </select>
                    </oscar:oscarPropertiesCheck>
                    <oscar:oscarPropertiesCheck property="DEMOGRAPHIC_PATIENT_HEALTH_CARE_TEAM" value="false">
                        <input type="text" name="pcontact.specialty" value="${e:forHtmlAttribute(pcontact.specialty)}"
                               size="30">
                    </oscar:oscarPropertiesCheck>
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.cpso"/></b></td>
                <td>
                    <input type="text" name="pcontact.cpso" value="${e:forHtmlAttribute(pcontact.cpso)}" size="30">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.systemId"/></b></td>
                <td>
                    <input type="text" readonly="readonly"
                           name="pcontact.systemId" value="${e:forHtmlAttribute(pcontact.systemId)}" size="30">
                </td>
            </tr>
            <tr>
                <td align="right"><b><fmt:message key="demographic.contactForm.note"/></b></td>
                <td>
                    <input type="text" name="pcontact.note" value="${e:forHtmlAttribute(pcontact.note)}" size="30">
                </td>
            </tr>
            <tr>
                <td>&nbsp;</td>
                <td>&nbsp;</td>
            </tr>
            <tr>
                <td align="center" bgcolor="#CCCCFF" colspan="2">
                    <input type="button" name="submitbtn" value="<fmt:message key="admin.resourcebaseurl.btnSave"/>"
                           onclick="javascript: onSave();">
                    <input type="button" name="cancelbtn" value="<fmt:message key="admin.resourcebaseurl.btnExit"/>"
                           onClick="window.close()">
                </td>
            </tr>
        </table>
    </form>
    </body>
</html>
