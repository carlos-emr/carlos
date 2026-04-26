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
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ page import="io.github.carlos_emr.BillingBean" %>
<%@ page import="io.github.carlos_emr.BillingDataBean" %>
<%@ page import="io.github.carlos_emr.BillingPatientDataBean" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.data.BillingCorrectionReviewViewModel" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingCorrectionReviewDataAssembler" %>
<%@ page import="io.github.carlos_emr.carlos.managers.SecurityInfoManager" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<%--
  Defensive model-resolver: ensures ${reviewModel} is set on the request
  even on the unlikely path where this JSP is reached without going through
  BillingCorrectionReview2Action (e.g., a stray <jsp:forward> from an
  unguarded entry). The action's _billing w privilege check is duplicated
  here for parity: without it a future bypass would silently render PHI
  from session beans on an unauthenticated request. Mirrors billingON.jsp.
--%>
<%
    if (request.getAttribute("reviewModel") == null) {
        MiscUtils.getLogger().warn(
                "billingCorrectionReview.jsp reached without reviewModel — re-running assembler defensively. "
                        + "Caller should route through billing/CA/ON/BillingCorrectionReview.");
        LoggedInInfo __fallbackLii = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (__fallbackLii == null) {
            throw new SecurityException("billingCorrectionReview.jsp fallback: missing session");
        }
        SecurityInfoManager __secMgr;
        try {
            __secMgr = SpringUtils.getBean(SecurityInfoManager.class);
        } catch (RuntimeException __springEx) {
            MiscUtils.getLogger().error(
                    "billingCorrectionReview.jsp fallback: SecurityInfoManager bean lookup failed", __springEx);
            throw new SecurityException(
                    "billingCorrectionReview.jsp fallback: privilege check unavailable", __springEx);
        }
        if (!__secMgr.hasPrivilege(__fallbackLii, "_billing", "w", null)) {
            throw new SecurityException("billingCorrectionReview.jsp fallback: missing required sec object (_billing)");
        }
        BillingBean __billing = (BillingBean) request.getSession().getAttribute("billing");
        BillingDataBean __bdb = (BillingDataBean) request.getSession().getAttribute("billingDataBean");
        BillingPatientDataBean __bpdb = (BillingPatientDataBean) request.getSession().getAttribute("billingPatientDataBean");
        BillingCorrectionReviewViewModel __fallbackModel = new BillingCorrectionReviewDataAssembler()
                .assemble(__billing, __bdb, __bpdb);
        request.setAttribute("reviewModel", __fallbackModel);
    }
%>

<html>
    <head>
        <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
        <title><fmt:message key="billing.billingCorrection.title"/></title>
    </head>
    <body bgcolor="#FFFFFF" text="#000000" topmargin="5" leftmargin="0"
          rightmargin="0">
    <table width="100%" border="0" cellspacing="0" cellpadding="0">
        <tr bgcolor="#000000">
            <td height="40" width="10%"></td>
            <td width="90%" align="left">
                <p><font face="Verdana, Arial, Helvetica, sans-serif"
                         color="#FFFFFF"><b><font
                        face="Arial, Helvetica, sans-serif" size="4"><fmt:message key="billing.billingCorrection.msgBillingCorrection"/></font></b></font></p>
            </td>
        </tr>
    </table>

    <c:if test="${reviewModel.dataLoaded}">

    <table width="600" border="0">
        <tr>
            <td width="293"><b><font face="Arial, Helvetica, sans-serif"><u><fmt:message key="billing.billingCorrection.msgCorrectionReview"/></u></font></b></td>
            <td width="297"><font size="2"
                                  face="Arial, Helvetica, sans-serif"><b><fmt:message key="billing.billingCorrection.msgLastUpdate"/>:
                <carlos:encode value="${reviewModel.updateDate}" context="html"/>
            </b></font></td>
        </tr>
    </table>
    <br>
    <table width="600" border="0">
        <tr bgcolor="#CCCCFF">
            <td colspan="2" height="21"><font size="2"
                                              face="Arial, Helvetica, sans-serif"><b><font size="3"><fmt:message key="billing.billingCorrection.msgPatientInformation"/></font></b></font></td>
        </tr>
        <tr>
            <td width="54%"><b><font face="Arial, Helvetica, sans-serif"
                                     size="2"><fmt:message key="billing.billingCorrection.msgName"/>:
                <carlos:encode value="${reviewModel.demoName}" context="html"/>
            </font></b></td>
            <td width="46%"><b><font face="Arial, Helvetica, sans-serif"
                                     size="2"><fmt:message key="billing.billingCorrection.msgHealthNo"/> :
                <carlos:encode value="${reviewModel.hin}" context="html"/>
            </font></b></td>
        </tr>
        <tr bgcolor="#EEEEFF">
            <td><font size="2" face="Arial, Helvetica, sans-serif"><b><fmt:message key="billing.billingCorrection.msgSex"/>:
                <carlos:encode value="${reviewModel.demoSex}" context="html"/>
            </b></font></td>
            <td><font size="2"><b><font
                    face="Arial, Helvetica, sans-serif"><fmt:message key="billing.billingCorrection.msgDOB"/> :
                <carlos:encode value="${reviewModel.demoDob}" context="html"/>
            </font></b></font></td>
        </tr>
        <tr>
            <td><b><font size="2" face="Arial, Helvetica, sans-serif"><fmt:message key="billing.billingCorrection.msgAddress"/>:
                <carlos:encode value="${reviewModel.demoAddress}" context="html"/>
            </font></b></td>
            <td><b><font size="2" face="Arial, Helvetica, sans-serif"><fmt:message key="billing.billingCorrection.msgCity"/>:
                <carlos:encode value="${reviewModel.demoCity}" context="html"/>
            </font></b></td>
        </tr>
        <tr bgcolor="#EEEEFF">
            <td><b><font size="2" face="Arial, Helvetica, sans-serif"><fmt:message key="billing.billingCorrection.msgProvince"/>:
                <carlos:encode value="${reviewModel.demoProvince}" context="html"/>
            </font></b></td>
            <td><b><font size="2" face="Arial, Helvetica, sans-serif"><fmt:message key="billing.billingCorrection.msgPostalCode"/>:
                <carlos:encode value="${reviewModel.demoPostal}" context="html"/>
            </font></b></td>
        </tr>
        <tr>
            <td><b><font size="2" face="Arial, Helvetica, sans-serif"><fmt:message key="billing.billingCorrection.msgReferal"/>:
                <carlos:encode value="${reviewModel.referralDoctor}" context="html"/>
            </font></b></td>
            <td><b><font size="2" face="Arial, Helvetica, sans-serif"><fmt:message key="billing.billingCorrection.msgReferealNo"/>:
                <carlos:encode value="${reviewModel.referralDoctorOhip}" context="html"/>
            </font></b></td>
        </tr>
    </table>
    <table width="600" border="0">
        <tr bgcolor="#CCCCFF">
            <td colspan="2"><font face="Arial, Helvetica, sans-serif"><strong><fmt:message key="billing.billingCorrection.msgAdditionalInf"/></strong></font></td>
        </tr>
        <tr>
            <td width="320"><strong><font size="2"
                                          face="Arial, Helvetica, sans-serif"><fmt:message key="billing.billingCorrection.msgHCType"/>:
                <carlos:encode value="${reviewModel.hcType}" context="html"/>
            </font></strong></td>
            <td width="270"><strong><font size="2"
                                          face="Arial, Helvetica, sans-serif"><fmt:message key="billing.billingCorrection.msgManualReview"/>:
                <carlos:encode value="${reviewModel.manualReviewLabel}" context="html"/>
            </font></strong></td>
        </tr>
        <tr bgcolor="#EEEEFF">
            <td><strong><font size="2"
                              face="Arial, Helvetica, sans-serif"><fmt:message key="billing.billingCorrection.msgReferralDoctor"/>:
                <carlos:encode value="${reviewModel.referralCheckedLabel}" context="html"/>
            </font></strong></td>
            <td><strong><font size="2"
                              face="Arial, Helvetica, sans-serif"><fmt:message key="billing.billingCorrection.msgRosterStatus"/>:
                <carlos:encode value="${reviewModel.rosterStatus}" context="html"/>
            </font></strong></td>
        </tr>
    </table>
    <table width="600" border="0">
        <tr bgcolor="#CCCCFF">
            <td colspan="2"><font size="2"
                                  face="Arial, Helvetica, sans-serif"><b><font size="3"><fmt:message key="billing.billingCorrection.msgBillingInf"/></font></b></font></td>
        </tr>
        <tr>
            <td width="54%"><b><font face="Arial, Helvetica, sans-serif"
                                     size="2"><fmt:message key="billing.billingCorrection.msgBillingType"/>:
                <carlos:encode value="${reviewModel.billingType}" context="html"/>
            </font></b></td>
            <td width="46%"><b><font face="Arial, Helvetica, sans-serif"
                                     size="2"><fmt:message key="billing.billingCorrection.msgBillingDate"/>:
                <carlos:encode value="${reviewModel.billingDate}" context="html"/>
            </font></b></td>
        </tr>
        <tr bgcolor="#EEEEFF">
            <td width="54%"><b><font face="Arial, Helvetica, sans-serif"
                                     size="2"><fmt:message key="billing.billingCorrection.msgVisitLocation"/>:
                <carlos:encode value="${reviewModel.visitLocation}" context="html"/>
            </font></b></td>
            <td width="46%"><b><font face="Arial, Helvetica, sans-serif"
                                     size="2"><fmt:message key="billing.billingCorrection.msgBillingPhysicianNo"/>:
                <carlos:encode value="${reviewModel.billingPhysicianNo}" context="html"/>
            </font></b></td>
        </tr>
        <tr>
            <td width="54%"><b><font face="Arial, Helvetica, sans-serif"
                                     size="2"><fmt:message key="billing.billingCorrection.msgVisitType"/>:
                <carlos:encode value="${reviewModel.visitType}" context="html"/>
            </font></b></td>
            <td width="46%"><b><font face="Arial, Helvetica, sans-serif"
                                     size="2"><fmt:message key="billing.billingCorrection.msgVisitDate"/>:
                <carlos:encode value="${reviewModel.visitDate}" context="html"/>
            </font></b></td>
        </tr>
    </table>
    <table width="600" border="0">
        <tr bgcolor="#CCCCFF">
            <td width="25%"><b><font face="Arial, Helvetica, sans-serif"
                                     size="2"><fmt:message key="billing.billingCorrection.msgServiceCode"/></font></b></td>
            <td width="50%"><b><font face="Arial, Helvetica, sans-serif"
                                     size="2"><fmt:message key="billing.billingCorrection.msgDescription"/></font></b></td>
            <td width="12%"><b><font face="Arial, Helvetica, sans-serif"
                                     size="2"><fmt:message key="billing.billingCorrection.msgQuantity"/></font></b></td>
            <td width="13%">
                <div align="right"><b><font
                        face="Arial, Helvetica, sans-serif" size="2"><fmt:message key="billing.billingCorrection.msgFee"/></font></b></div>
            </td>
        </tr>
        <c:forEach var="__item" items="${reviewModel.billingItems}">
        <tr>
            <td width="25%"><font face="Arial, Helvetica, sans-serif"
                                  size="2"><carlos:encode value="${__item.serviceCode}" context="html"/>
            </font></td>

            <td width="50%"><font face="Arial, Helvetica, sans-serif"
                                  size="2"><carlos:encode value="${__item.description}" context="html"/>
            </font></td>
            <td width="12%"><font face="Arial, Helvetica, sans-serif"
                                  size="2"><carlos:encode value="${__item.quantity}" context="html"/>
            </font></td>
            <td width="13%">
                <div align="right"><font face="Arial, Helvetica, sans-serif"
                                         size="2"><carlos:encode value="${__item.formattedFee}" context="html"/>
                </font></div>
            </td>
        </tr>
        </c:forEach>
        <tr bgcolor="#CCCCFF">
            <td colspan="4"><font face="Arial, Helvetica, sans-serif"
                                  size="2"><b><fmt:message key="billing.billingCorrection.msgDiagCode"/></b></font></td>

        </tr>
        <tr>
            <td colspan="4"><font face="Arial, Helvetica, sans-serif"
                                  size="2"><carlos:encode value="${reviewModel.diagCode}" context="html"/>
            </font></td>

        </tr>
        <tr>
            <td width="25%">&nbsp;</td>
            <td width="50%">&nbsp;</td>
            <td width="12%">
                <div align="right"><font face="Arial, Helvetica, sans-serif"
                                         size="2"><fmt:message key="billing.billingCorrection.msgTotal"/>: </font></div>
            </td>
            <td width="13%">
                <div align="right"><font face="Arial, Helvetica, sans-serif"
                                         size="2"><carlos:encode value="${reviewModel.formattedTotal}" context="html"/>
                </font></div>
            </td>
        </tr>
    </table>

    </c:if>

    <form action="${pageContext.request.contextPath}/billing/CA/ON/BillingCorrectionSubmit" method="post">
        <input type="submit" name="submit"
               value="<fmt:message key="billing.billingCorrection.btnSubmit"/>"/>
        <input type="button" name="cancel"
               value="<fmt:message key="billing.billingCorrection.btnCancel"/>"
               onclick="history.go(-1);return false;"/>
    </form>
    </body>
</html>
