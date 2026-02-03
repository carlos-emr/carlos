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
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title>Billing Reconcilliation</title>
        <link rel="stylesheet" href="<%= request.getContextPath() %>/billing.css">
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>
    </head>

    <body class="BodyStyle">
    <table width="100%" border="0" cellspacing="0" cellpadding="0">
        <tr>
            <td height="40" width="10%" class="Header"><input type='button'
                                                              name='print' value='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnPrint"/>'
                                                              onClick='window.print()'></td>
            <td width="90%" align="left" class="Header">oscar<font size="3">Billing</font>
            </td>
        </tr>
    </table>

    <table width="100%">
        <tr>
            <td class="Header1"><c:out value="${ReportName}"/></td>
        </tr>
    </table>
    <table width="100%">
        <tr>
            <td>
                <c:if test="${not empty claimsErrors}">
                <c:forEach var="claimsError" items="${claimsErrors.claimsErrorReportBeanVector}">
                <c:if test="${not empty claimsError.techSpec}">
                <table width="100%" border="0" cellspacing="2" cellpadding="2" bgcolor="#CCCCFF">
                    <tr>
                        <td width="15%"><b>MOH Office: <c:out value="${claimsError.MOHoffice}"/></b></td>
                        <td width="15%"><b>Provider #: <c:out value="${claimsError.providerNumber}"/> </b></td>
                        <td width="11%"><b>Group #: <c:out value="${claimsError.groupNumber}"/> </b></td>
                        <td width="11%"><b>Opr.#: <c:out value="${claimsError.operatorNumber}"/></b></td>
                        <td width="11%"><b>Sp. Code: <c:out value="${claimsError.specialtyCode}"/></b></td>
                        <td width="11%"><b>Spec.#: <c:out value="${claimsError.techSpec}"/> </b></td>
                        <td width="11%"><b>Station #: <c:out value="${claimsError.stationNumber}"/> </b></td>
                        <td width="15%"><b>Clm Date: <c:out value="${claimsError.claimProcessDate}"/></b></td>
                    </tr>
                </table>
                <table width="100%" border="0" cellspacing="2" cellpadding="2" bgcolor="#F1E9FE">
                    <tr>
                        <td width="10%">Health#</td>
                        <td width="6%">D.O.B</td>
                        <td width="7%">Invoice #</td>
                        <td width="3%">Type</td>
                        <td width="9%">Ref Phy#</td>
                        <td width="7%">Hosp #</td>
                        <td width="9%">Admitted</td>
                        <td width="5%">Claim Errors</td>
                        <td width="5%">Code</td>
                        <td width="6%">Fee Unit</td>
                        <td width="4%">Unit</td>
                        <td width="7%">Date</td>
                        <td width="4%">Diag</td>
                        <td width="2%">Exp.</td>
                        <td width="12%">Code Error</td>
                    </tr>
                </table>
                </c:if>

                <c:if test="${not empty claimsError.patient_last}">
                <table width="100%" border="0" cellspacing="2" cellpadding="2" bgcolor="#F1E9FE">
                    <tr bgcolor="#F9F1FE">
                        <td width="23%" colspan="3">
                            <c:out value="${claimsError.patient_last}"/>, &nbsp;<c:out value="${claimsError.patient_first}"/>
                        </td>
                        <td width="3%"><c:out value="${claimsError.patient_sex}"/></td>
                        <td width="9%"><c:out value="${claimsError.province_code}"/></td>
                        <td width="65%" colspan="10">
                            <c:out value="${claimsError.reCode1}"/> &nbsp;
                            <c:out value="${claimsError.reCode2}"/> &nbsp;
                            <c:out value="${claimsError.reCode3}"/> &nbsp;
                            <c:out value="${claimsError.reCode4}"/> &nbsp;
                            <c:out value="${claimsError.reCode5}"/>&nbsp;
                        </td>
                    </tr>
                </table>
                </c:if>

                <c:if test="${not empty claimsError.servicecode}">
                <table width="100%" border="0" cellspacing="2" cellpadding="2" bgcolor="#F1E9FE">
                    <tr bgcolor="#F9F1FE">
                        <td width="10%">
                            <c:out value="${claimsError.hin}"/> &nbsp; <c:out value="${claimsError.ver}"/>
                        </td>
                        <td width="6%"><c:out value="${claimsError.dob}"/></td>
                        <td width="7%"><c:out value="${claimsError.account}"/></td>
                        <td width="3%"><c:out value="${claimsError.payee}"/></td>
                        <td width="9%"><c:out value="${claimsError.referNumber}"/></td>
                        <td width="7%"><c:out value="${claimsError.facilityNumber}"/></td>
                        <td width="9%"><c:out value="${claimsError.admitDate}"/></td>
                        <td width="5%">
                            <c:out value="${claimsError.heCode1}"/> &nbsp;
                            <c:out value="${claimsError.heCode2}"/> &nbsp;
                            <c:out value="${claimsError.heCode3}"/> &nbsp;
                            <c:out value="${claimsError.heCode4}"/> &nbsp;
                            <c:out value="${claimsError.heCode5}"/>&nbsp;
                        </td>
                        <td width="5%"><c:out value="${claimsError.servicecode}"/></td>
                        <td width="6%"><c:out value="${claimsError.amountsubmit}"/></td>
                        <td width="4%"><c:out value="${claimsError.serviceno}"/></td>
                        <td width="7%"><c:out value="${claimsError.servicedate}"/></td>
                        <td width="4%"><c:out value="${claimsError.dxcode}"/></td>
                        <td width="2%"></td>
                        <td width="12%">
                            <c:out value="${claimsError.code1}"/> &nbsp;
                            <c:out value="${claimsError.code2}"/> &nbsp;
                            <c:out value="${claimsError.code3}"/> &nbsp;
                            <c:out value="${claimsError.code4}"/> &nbsp;
                            <c:out value="${claimsError.code5}"/>&nbsp;
                        </td>
                    </tr>
                </table>
                </c:if>

                <c:if test="${not empty claimsError.explain}">
                <table width="100%" border="0" cellspacing="2" cellpadding="2" bgcolor="#F1E9FE">
                    <tr>
                        <td width="20%"><b>Error/Description</b></td>
                        <td width="20%"><c:out value="${claimsError.explain}"/></td>
                        <td width="60%"><c:out value="${claimsError.error}"/></td>
                    </tr>
                </table>
                </c:if>

                <c:if test="${not empty claimsError.header1Count}">
                <table width="100%" border="0" cellspacing="2" cellpadding="2" bgcolor="#CCCCFF">
                    <tr>
                        <td width="20%"><b>Record Counts: [ </b></td>
                        <td width="20%"><b>Header 1: <c:out value="${claimsError.header1Count}"/> </b></td>
                        <td width="20%"><b>Header 2: <c:out value="${claimsError.header2Count}"/></b></td>
                        <td width="20%"><b>Item: <c:out value="${claimsError.itemCount}"/> </b></td>
                        <td width="20%"><b>Message: <c:out value="${claimsError.messageCount}"/> ]</b></td>
                    </tr>
                </table>
                </c:if>
                </c:forEach>
                </c:if>



            <c:if test="${not empty batchAcks}">
        <tr>
            <td class="fieldName" width="5%">Batch #</td>
            <td class="fieldName" width="5%">Oper.#</td>
            <td class="fieldName" width="7%">Provider #</td>
            <td class="fieldName" width="4%">Group#</td>
            <td class="fieldName" width="7%">Create Date</td>
            <td class="fieldName" width="5%">Seq#</td>
            <td class="fieldName" width="7%">Rec Start</td>
            <td class="fieldName" width="5%">Rec End</td>
            <td class="fieldName" width="7%">Rec Type</td>
            <td class="fieldName" width="5%">Claims</td>
            <td class="fieldName" width="5%">Records</td>
            <td class="fieldName" width="12%">Batch Process Date</td>
            <td class="fieldName" width="15%">Reject Reason</td>
        </tr>
        <c:forEach var="batchAck" items="${batchAcks.batchAckReportBeanVector}">
            <tr>
                <td class="dataTable" width="5%"><c:out value="${batchAck.batchNumber}"/></td>
                <td class="dataTable" width="5%"><c:out value="${batchAck.operatorNumber}"/></td>
                <td class="dataTable" width="7%"><c:out value="${batchAck.providerNumber}"/></td>
                <td class="dataTable" width="4%"><c:out value="${batchAck.groupNumber}"/></td>
                <td class="dataTable" width="7%"><c:out value="${batchAck.batchCreateDate}"/></td>
                <td class="dataTable" width="5%"><c:out value="${batchAck.batchSequenceNumber}"/></td>
                <td class="dataTable" width="7%"><c:out value="${batchAck.microStart}"/></td>
                <td class="dataTable" width="5%"><c:out value="${batchAck.microEnd}"/></td>
                <td class="dataTable" width="7%"><c:out value="${batchAck.microType}"/></td>
                <td class="dataTable" width="5%"><c:out value="${batchAck.claimNumber}"/></td>
                <td class="dataTable" width="5%"><c:out value="${batchAck.recordNumber}"/></td>
                <td class="dataTable" width="12%"><c:out value="${batchAck.batchProcessDate}"/></td>
                <td class="dataTable" width="15%"><c:out value="${batchAck.explain}"/></td>
            </tr>
        </c:forEach>
        </c:if>


        <c:if test="${not empty messages}">
            <c:forEach var="msg" items="${messages}">
                <tr>
                    <td>
                        <pre><c:out value="${msg}"/></pre>
                    </td>
                </tr>
            </c:forEach>
        </c:if>


        <c:if test="${not empty outputSpecs}">
            <tr>
                <td class="fieldName" width="8%">Health #</td>
                <td class="fieldName" width="3%">Ver</td>
                <td class="fieldName" width="10%">Response Code</td>
                <td class="fieldName" width="10%">Identifier</td>
                <td class="fieldName" width="3%">Sex</td>
                <td class="fieldName" width="10%">DOB</td>
                <td class="fieldName" width="10%">Expiry</td>
                <td class="fieldName" width="10%">Last Name</td>
                <td class="fieldName" width="10%">First Name</td>
                <td class="fieldName" width="10%">Second Name</td>
                <td class="fieldName" width="16%">Reserved for MOH</td>
            </tr>
            <c:forEach var="outputSpec" items="${outputSpecs.EDTOBECOutputSecifiationBeanVector}">
                <tr>
                    <td class="dataTable" width="8%"><c:out value="${outputSpec.healthNo}"/></td>
                    <td class="dataTable" width="3%"><c:out value="${outputSpec.version}"/></td>
                    <td class="dataTable" width="10%"><c:out value="${outputSpec.responseCode}"/></td>
                    <td class="dataTable" width="10%"><c:out value="${outputSpec.identifier}"/></td>
                    <td class="dataTable" width="3%"><c:out value="${outputSpec.sex}"/></td>
                    <td class="dataTable" width="10%"><c:out value="${outputSpec.DOB}"/></td>
                    <td class="dataTable" width="10%"><c:out value="${outputSpec.expiry}"/></td>
                    <td class="dataTable" width="10%"><c:out value="${outputSpec.lastName}"/></td>
                    <td class="dataTable" width="10%"><c:out value="${outputSpec.firstName}"/></td>
                    <td class="dataTable" width="10%"><c:out value="${outputSpec.secondName}"/></td>
                    <td class="dataTable" width="16%"><c:out value="${outputSpec.MOH}"/></td>
                </tr>
            </c:forEach>
        </c:if>

        <tr>
            <td><input type="button" name="Button"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/>"
                       onClick="window.close()"></td>
        </tr>

        </td>
        </tr>
    </table>
    </body>
</html>
