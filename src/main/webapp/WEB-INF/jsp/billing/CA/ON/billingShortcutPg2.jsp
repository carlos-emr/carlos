<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos
--%>
<%--
  Purpose: Supports billingShortcutPg2 in the Ontario billing workflow.
  Expected request model data includes: shortcutPg2Model.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%@page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="carlos" prefix="carlos" %>
<jsp:useBean id="providerBean" class="java.util.Properties" scope="session"/>

<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
    <title>CARLOS Billing</title>
    <script language="JavaScript">
        <!--
        function onSave() {
            var submitTypeString = document.forms[0].submitType.value;
            var ret = true;
            if (ret == true) {
                ret = confirm("Are you sure you want to " + submitTypeString + "?");
            }
            return ret;
        }

        //-->
    </script>
</head>

<body topmargin="0">

<table border="0" cellpadding="0" cellspacing="2" width="100%"
       bgcolor="#CCCCFF">
    <form method="post" name="titlesearch" action="${pageContext.request.contextPath}/billing/CA/ON/BillingShortcutPg2Save" onsubmit="return onSave();">
        <input type="hidden" value="" name="submitType"/>
        <tr>
            <td>
                <table border="0" cellspacing="0" cellpadding="0" width="100%">
                    <tr>
                        <td><b>Confirmation </b></td>
                        <td align="right"><input type="hidden" name="addition"
                                                 value="Confirm"/></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td>
                <table border="0" cellspacing="0" cellpadding="0" width="100%">
                    <tr bgcolor="#33CCCC">
                        <td nowrap bgcolor="#FFCC99" width="10%" align="center"><carlos:encode value="${shortcutPg2Model.demographicName}" context="html"/>
                            <carlos:encode value="${shortcutPg2Model.displaySex}" context="html"/>
                            DOB: <carlos:encode value="${shortcutPg2Model.demoDobYy}" context="html"/>/<carlos:encode value="${shortcutPg2Model.demoDobMm}" context="html"/>/<carlos:encode value="${shortcutPg2Model.demoDobDd}" context="html"/>
                            HIN: <carlos:encode value="${shortcutPg2Model.demoHin}" context="html"/>
                        </td>
                        <%-- combinedMsgs is pre-rendered HTML with deliberate
                             font/color tags from the assembler — output
                             unescaped to preserve the legacy warning markup. --%>
                        <td bgcolor="#99CCCC" align="center">${shortcutPg2Model.combinedMsgs}
                        </td>
                    </tr>
                </table>

                <table border="1" cellspacing="0" cellpadding="0" width="100%"
                       bordercolorlight="#99A005" bordercolordark="#FFFFFF"
                       bgcolor="#FFFFFF">
                    <tr>
                        <td width="50%">

                            <table border="1" cellspacing="2" cellpadding="0" width="100%"
                                   bordercolorlight="#99A005" bordercolordark="#FFFFFF"
                                   bgcolor="ivory">
                                <tr>
                                    <td nowrap width="30%" align="center" valign="top"><b>Service
                                        Date</b><br>
                                        <%-- billDateHtml is pre-rendered: each
                                             newline-split date is HTML-encoded
                                             and joined with <br>. --%>
                                        ${shortcutPg2Model.billDateHtml}
                                    </td>
                                    <td align="center" width="33%"><b>Diagnostic Code</b><br>
                                        <carlos:encode value="${shortcutPg2Model.dxCode}" context="html"/>
                                        <hr>
                                        <b>Cal.% mode</b><br>
                                        <carlos:encode value="${shortcutPg2Model.rulePerc}" context="html"/>
                                    </td>
                                    <td valign="top"><b>Refer.
                                        Doctor</b><br><carlos:encode value="${shortcutPg2Model.referralDocName}" context="html"/><br>
                                        <b>Refer. Doctor #</b><br><carlos:encode value="${shortcutPg2Model.referralCodeParam}" context="html"/>
                                    </td>
                                </tr>
                            </table>

                        </td>
                        <td valign="top">

                            <table border="1" cellspacing="2" cellpadding="0" width="100%"
                                   bordercolorlight="#99A005" bordercolordark="#FFFFFF"
                                   bgcolor="#EEEEFF">
                                <tr>
                                    <td nowrap width="30%"><b>Billing Physician</b></td>
                                    <td width="20%"><carlos:encode value="${shortcutPg2Model.billingProviderLabel}" context="html"/>
                                    </td>
                                    <td nowrap width="30%"><b>Assig. Physician</b></td>
                                    <td width="20%"><carlos:encode value="${shortcutPg2Model.assignedProviderLabel}" context="html"/>
                                    </td>
                                </tr>
                                <tr>

                                    <td width="30%"><b>Visit Type</b></td>
                                    <td width="20%"><carlos:encode value="${shortcutPg2Model.visitTypeLabel}" context="html"/>
                                    </td>

                                    <td width="30%"><b>Billing Type</b></td>
                                    <td width="20%"><carlos:encode value="${shortcutPg2Model.billTypeLabel}" context="html"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td><b>Visit Location</b></td>
                                    <td colspan="3"><carlos:encode value="${shortcutPg2Model.visitLocationLabel}" context="html"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td><b>SLI Code</b></td>
                                    <td>
                                        <c:choose>
                                            <c:when test="${shortcutPg2Model.sliNotApplicable}">
                                        Not Applicable &nbsp;
                                            </c:when>
                                            <c:otherwise>
                                        <carlos:encode value="${shortcutPg2Model.sliCode}" context="html"/> &nbsp;
                                            </c:otherwise>
                                        </c:choose>
                                    </td>
                                </tr>
                                <tr>
                                    <td><b>Admission Date</b></td>
                                    <td><carlos:encode value="${shortcutPg2Model.admissionDate}" context="html"/>
                                    </td>
                                    <td colspan="2"></td>

                                </tr>
                            </table>

                        </td>
                    </tr>
                </table>

            </td>

        </tr>
        <tr>
            <td align="center">
                <table border="1" width="50%" bordercolorlight="#99A005"
                       bordercolordark="#FFFFFF">

                    <%-- Calculation HTML is pre-rendered by the assembler. The
                         JSP previously built the same string inline as it walked
                         the per-line and percent-code vectors. Output unescaped
                         to preserve the legacy table markup. --%>
                    ${shortcutPg2Model.calculationHtml}

                    <tr>

                        <td colspan='2' align='center' bgcolor="silver">
                            <input type="submit" name="button" value="Back to Edit"
                                   onclick="document.forms[0].submitType.value='Back to Edit'" style="width: 120px;"/>
                            <input type="submit" name="button" value="Save"
                                   onclick="document.forms[0].submitType.value='Save'" style="width: 120px;"/>
                            <input type="submit" name="button" value="Save and Back"
                                   onclick="document.forms[0].submitType.value='Save and Back'" style="width: 120px;"/>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>

        <%-- Hidden-input echo loop — preserves every request parameter so
             a self-post round-trip retains form state. The assembler captures
             these into requestParamEchoes; the c:forEach below mirrors the
             legacy Enumeration walk over request.getParameterNames(). --%>
        <c:forEach var="__echo" items="${shortcutPg2Model.requestParamEchoes}">
        <input type="hidden" name="<carlos:encode value='${__echo.key}' context='htmlAttribute'/>"
               value="<carlos:encode value='${__echo.value}' context='htmlAttribute'/>">
        </c:forEach>
        <input type="hidden" name="hc_type" value="<carlos:encode value="${shortcutPg2Model.demoHcType}" context="htmlAttribute"/>">
        <input type="hidden" name="referralCode" value="<carlos:encode value="${shortcutPg2Model.referralDoctorOhip}" context="htmlAttribute"/>">
        <input type="hidden" name="sex" value="<carlos:encode value="${shortcutPg2Model.demoSex}" context="htmlAttribute"/>">
        <input type="hidden" name="proOHIPNO" value="<carlos:encode value="${shortcutPg2Model.providerOhipNo}" context="htmlAttribute"/>">
    </form>

</table>

<%-- Post-save navigation: action layer decides between "close popup"
     (Save button), redirect to pg1 (Save and Back / Next), or no-op
     (initial render or Back-to-Edit which is routed elsewhere). --%>
<c:choose>
    <c:when test="${shortcutPg2Model.postSaveAction == 'CLOSE_WINDOW'}">
<script language="JavaScript"> self.close();</script>
    </c:when>
    <c:when test="${shortcutPg2Model.postSaveAction == 'REDIRECT_TO_PG1'}">
<script language="JavaScript">window.location = '<carlos:encode value="${shortcutPg2Model.redirectUrl}" context="javaScript"/>';</script>
    </c:when>
</c:choose>

</body>
</html>
