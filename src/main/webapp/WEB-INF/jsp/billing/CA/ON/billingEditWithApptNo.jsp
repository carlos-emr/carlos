<%--


    Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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

    This software was written for
    Centre for Research on Inner City Health, St. Michael's Hospital,
    Toronto, Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.data.BillingEditWithApptNoViewModel" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingEditWithApptNoDataAssembler" %>
<%@ page import="io.github.carlos_emr.carlos.managers.SecurityInfoManager" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>

<%--
  Defensive model-resolver: ensures ${editApptModel} is set on the request
  even on the unlikely path where this JSP is reached without going through
  BillingEditWithApptNo2Action (e.g., a stray <jsp:forward> from an
  unguarded entry). The action's own _billing w privilege check is
  duplicated here for parity: without it a future bypass would silently
  render PHI on an unauthenticated request. Mirrors billingON.jsp.
--%>
<%
    if (request.getAttribute("editApptModel") == null) {
        MiscUtils.getLogger().warn(
                "billingEditWithApptNo.jsp reached without editApptModel — re-running assembler defensively. "
                        + "Caller should route through billing/CA/ON/BillingEditWithApptNo.");
        LoggedInInfo __fallbackLii = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (__fallbackLii == null) {
            throw new SecurityException("billingEditWithApptNo.jsp fallback: missing session");
        }
        SecurityInfoManager __secMgr;
        try {
            __secMgr = SpringUtils.getBean(SecurityInfoManager.class);
        } catch (RuntimeException __springEx) {
            MiscUtils.getLogger().error(
                    "billingEditWithApptNo.jsp fallback: SecurityInfoManager bean lookup failed", __springEx);
            throw new SecurityException(
                    "billingEditWithApptNo.jsp fallback: privilege check unavailable", __springEx);
        }
        if (!__secMgr.hasPrivilege(__fallbackLii, "_billing", "w", null)) {
            throw new SecurityException("billingEditWithApptNo.jsp fallback: missing required sec object (_billing)");
        }
        BillingEditWithApptNoViewModel __fallbackModel = new BillingEditWithApptNoDataAssembler().assemble(request);
        request.setAttribute("editApptModel", __fallbackModel);
    }
%>

<c:choose>
<c:when test="${editApptModel.billedItemBlocked}">
<p>
<h1>Sorry, cannot delete billed items.</h1>
</p>
<form><input type="button" value="Back to previous page"
             onClick="window.close()"></form>
</c:when>
<c:otherwise>


<form method="post" name="editBillingForm" action="/billing">
    <input type="hidden" name="billNo_old" id="billNo_old" value="${carlos:forHtmlAttribute(editApptModel.billNo)}"/>
    <input type="hidden" name="billStatus_old" id="billStatus_old" value="${carlos:forHtmlAttribute(editApptModel.status)}"/>
    <input type="hidden" name="apptProvider_no" id="apptProvider_no" value="${carlos:forHtmlAttribute(editApptModel.apptProviderNo)}"/>
    <input type="hidden" name="providerview" id="providerview" value="${carlos:forHtmlAttribute(editApptModel.providerView)}"/>
    <input type="hidden" name="service_date" id="service_date" value="${carlos:forHtmlAttribute(editApptModel.serviceDate)}"/>
    <input type="hidden" name="appointment_date" id="appointment_date" value="${carlos:forHtmlAttribute(editApptModel.appointmentDate)}"/>
    <input type="hidden" name="billing_date" id="billing_date" value="${carlos:forHtmlAttribute(editApptModel.billingDate)}"/>
    <input type="hidden" name="demographic_name" id="demographic_name" value="${carlos:forHtmlAttribute(editApptModel.demoName)}"/>
    <input type="hidden" name="appointment_no" id="appointment_no" value="${carlos:forHtmlAttribute(editApptModel.appointmentNo)}"/>
    <input type="hidden" name="clinic_no" id="clinic_no" value="${carlos:forHtmlAttribute(editApptModel.clinicNo)}"/>
    <input type="hidden" name="demographic_no" id="demographic_no" value="${carlos:forHtmlAttribute(editApptModel.demographicNo)}"/>
    <input type="hidden" name="asstProvider_no" id="asstProvider_no" value="${carlos:forHtmlAttribute(editApptModel.asstProviderNo)}"/>
    <input type="hidden" name="assgProvider_no" id="assgProvider_no" value="${carlos:forHtmlAttribute(editApptModel.assgProviderNo)}"/>
    <input type="hidden" name="sex" id="sex"/>
    <input type="hidden" name="m_review" id="m_review" value="${carlos:forHtmlAttribute(editApptModel.mReview)}"/>
    <input type="hidden" name="xml_provider" id="xml_provider" value="${carlos:forHtmlAttribute(editApptModel.xmlProvider)}"/>
    <input type="hidden" name="dxCode" id="dxCode" value="${carlos:forHtmlAttribute(editApptModel.dxCode)}"/>
    <input type="hidden" name="dxCode1" id="dxCode1" value="${carlos:forHtmlAttribute(editApptModel.dxCode1)}"/>
    <input type="hidden" name="dxCode2" id="dxCode2" value="${carlos:forHtmlAttribute(editApptModel.dxCode2)}"/>
    <input type="hidden" name="service_code" id="service_code" value="${carlos:forHtmlAttribute(editApptModel.serviceCode)}"/>
    <input type="hidden" name="xml_visittype" id="xml_visittype" value="${carlos:forHtmlAttribute(editApptModel.visitType)}"/>
    <input type="hidden" name="xml_location" id="xml_location" value="${carlos:forHtmlAttribute(editApptModel.location)}"/>
    <input type="hidden" name="xml_vdate" id="xml_vdate" value="${carlos:forHtmlAttribute(editApptModel.visitDate)}"/>


    <input type="hidden" name="checkFlag" id="checkFlag"/>
    <input type="hidden" name="rfcheck" id="rfcheck"/>
    <input type="hidden" name="referralDocName" id="referralDocName"/>
    <input type="hidden" name="referralCode" id="referralCode" value="${carlos:forHtmlAttribute(editApptModel.referralCode)}"/>
    <input type="hidden" name="referralSpet" id="referralSpet"/>
    <input type="hidden" name="site" id="site" value="${carlos:forHtmlAttribute(editApptModel.site)}"/>
    <input type="hidden" name="xml_billtype" id="xml_billtype" value="${carlos:forHtmlAttribute(editApptModel.xmlBilltype)}"/>
    <input type="hidden" name="ohip_version" id="ohip_version" value="V03G"/>
    <input type="hidden" name="hin" id="hin" value="${carlos:forHtmlAttribute(editApptModel.demoHin)}"/>
    <input type="hidden" name="ver" id="ver" value="${carlos:forHtmlAttribute(editApptModel.demoVer)}"/>
    <input type="hidden" name="hc_type" id="hc_type" value="${carlos:forHtmlAttribute(editApptModel.demoHcType)}"/>
    <input type="hidden" name="start_time" id="start_time" value="${carlos:forHtmlAttribute(editApptModel.startTime)}"/>
    <input type="hidden" name="demographic_dob" id="demographic_dob" value="${carlos:forHtmlAttribute(editApptModel.demoDob)}"/>
    <input type="hidden" name="url_back" id="url_back">

    <c:forEach var="__svc" items="${editApptModel.serviceFields}">
    <c:choose>
    <c:when test="${__svc.checkedVariant}">
    <input type="hidden" name="${carlos:forHtmlAttribute(__svc.name)}" id="${carlos:forHtmlAttribute(__svc.name)}" value="checked"/>
    </c:when>
    <c:otherwise>
    <input type="hidden" name="${carlos:forHtmlAttribute(__svc.name)}" id="${carlos:forHtmlAttribute(__svc.name)}" value="${carlos:forHtmlAttribute(__svc.value)}"/>
    <input type="hidden" name="${carlos:forHtmlAttribute(__svc.unitName)}" id="${carlos:forHtmlAttribute(__svc.unitName)}" value="${carlos:forHtmlAttribute(__svc.unitValue)}"/>
    </c:otherwise>
    </c:choose>
    </c:forEach>
    <input type="hidden" name="services_checked" id="services_checked" value="${editApptModel.servicesCheckedNum}">
    <input type="hidden" name="curBillForm" id="curBillForm" value="${carlos:forHtmlAttribute(editApptModel.curBillForm)}"/>
    <input type="hidden" name="billForm" id="billForm" value="${carlos:forHtmlAttribute(editApptModel.billForm)}"/>
    <center>
        <p>
            Do you want to edit the billing?
        <p>
            <input type="submit" name="submit2" value="Yes"/>
            <input type="button" name="close" value="No" onclick="window.close()"/>
    </center>
</form>
<SCRIPT LANGUAGE="JavaScript"><!--
setTimeout('document.editBillingForm.submit()', 50);
//--></SCRIPT>
</c:otherwise>
</c:choose>
