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

<%--
  generatePreviewPDF.jsp - Attach documents to messenger messages

  Displays a selection interface for attaching medical documents (demographics,
  encounters, prescriptions) to a messenger message. Supports preview and
  batch attachment modes via PDF conversion.

  Security:
  - Requires "_msg" object with read ("r") permissions

  Request parameters:
  - demographic_no: Required patient demographic number (validated as integer)

  The form posts item KEYS to messenger/Doc2PDF (item=demographic|encounter|prescriptions, or
  previewItem=<key> with isPreview=true) and the server renders each item itself
  (MsgAttachPDF2Action / MsgPdfAttachmentResolver). The page used to load each item into the
  frameset's hidden source frame and post the captured page HTML back; that let a forged request
  store arbitrary markup as a chart PDF and was refused by the front-door WAF (#4133).

  Session dependencies:
  - msgSessionBean: Message session state management
  - EctSessionBean: Encounter session for patient context

  @since 2003
--%>

<%@ page import="java.util.*" %>
<%@ page import="org.w3c.dom.*" %>
<%@ page import="java.sql.*" %>
<%@ page import="io.github.carlos_emr.*" %>
<%@ page import="java.text.*" %>
<%@ page import="java.lang.*" %>
<%@ page import="java.net.*" %>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ page import="io.github.carlos_emr.carlos.messenger.docxfer.send.*" %>
<%@ page import="io.github.carlos_emr.carlos.messenger.docxfer.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.data.*" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean" %>
<%@ page import="io.github.carlos_emr.carlos.messenger.pageUtil.MsgSessionBean" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.*" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.EChartDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.EChart" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicData" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ page import="io.github.carlos_emr.carlos.managers.SecurityInfoManager" %>
<%@ page import="io.github.carlos_emr.carlos.messenger.pageUtil.MsgAttachPDF2Action" %>
<%@ page import="io.github.carlos_emr.carlos.messenger.pageUtil.MsgPdfAttachmentResolver" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<fmt:message key="messenger.generatePreviewPDF.information" var="informationLabel"/>
<fmt:message key="messenger.generatePreviewPDF.encounter" var="encounterLabel"/>
<fmt:message key="messenger.generatePreviewPDF.currentPrescriptions" var="currentPrescTitle"/>
<fmt:message key="messenger.generatePreviewPDF.confirmClose" var="exitConfirmMsg"/>

<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
    EChartDao eChartDao = SpringUtils.getBean(EChartDao.class);
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_msg" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_msg");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<%
    String demographic_no_raw = request.getParameter("demographic_no");
    // Validate and parse demographic_no as integer to prevent trust boundary violation (CWE-501)
    int demographicNoInt;
    if (demographic_no_raw == null || demographic_no_raw.isEmpty()) {
        response.sendRedirect(request.getContextPath() + "/securityError?type=_msg");
        return;
    }
    try {
        demographicNoInt = Integer.parseInt(demographic_no_raw);
    } catch (NumberFormatException e) {
        response.sendRedirect(request.getContextPath() + "/securityError?type=_msg");
        return;
    }
    // Use the validated integer value as the canonical demographic number string
    String demographic_no = String.valueOf(demographicNoInt);

    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

    // Same patient-level gate Doc2PDF applies (it also honours a per-patient _eChart$<id> "o"
    // restriction): without it this page would show the patient's name, and seed the encounter
    // session, for a patient every preview and attach would then refuse.
    SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    if (!securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, demographicNoInt)) {
        response.sendRedirect(request.getContextPath() + "/securityError?type=_demographic");
        return;
    }

    DemographicData demoData = new DemographicData();
    Demographic demo = demoData.getDemographic(loggedInInfo, demographic_no);
    String demoName = "";
    if (demo != null) {
        demoName = demo.getLastName() + ", " + demo.getFirstName();
    }

    EctSessionBean bean = new EctSessionBean();
    // Use validated integer-derived string to prevent raw request data in session (CWE-501)
    bean.demographicNo = demographic_no;

    request.getSession().setAttribute("EctSessionBean", bean);

    // Expose display variables as page attributes for EL/OWASP encoding
    pageContext.setAttribute("demoName", demoName);

    // Offer only the items Doc2PDF would let this user attach for this patient (module read,
    // globally and for the patient). The encounter lookup itself is skipped without _eChart
    // read, so the page does not reveal whether, or when, an encounter exists.
    boolean canDemographic = MsgAttachPDF2Action.canReadItem(securityInfoManager, loggedInInfo,
            MsgPdfAttachmentResolver.Item.DEMOGRAPHIC, demographicNoInt);
    boolean canEncounter = MsgAttachPDF2Action.canReadItem(securityInfoManager, loggedInInfo,
            MsgPdfAttachmentResolver.Item.ENCOUNTER, demographicNoInt);
    boolean canPrescriptions = MsgAttachPDF2Action.canReadItem(securityInfoManager, loggedInInfo,
            MsgPdfAttachmentResolver.Item.PRESCRIPTIONS, demographicNoInt);
    pageContext.setAttribute("canDemographic", canDemographic);
    pageContext.setAttribute("canPrescriptions", canPrescriptions);

    EChart ec = canEncounter ? eChartDao.getLatestChart(demographicNoInt) : null;
    pageContext.setAttribute("hasEncounter", ec != null);
    if (ec != null) {
        pageContext.setAttribute("ecTimestamp", ec.getTimestamp().toString());
    }

%>

<!DOCTYPE html>
<html lang="${carlos:forHtmlAttribute(pageContext.request.locale.language)}">
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <meta charset="UTF-8">
    <title>CARLOS - <fmt:message key="messenger.generatePreviewPDF.title"/></title>
    <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>

    <script>
        // i18n message strings for JavaScript dialogs
        var MSGS = {
            exitConfirm: '${carlos:forJavaScript(exitConfirmMsg)}'
        };

        /**
         * Streams one item back as a PDF into this frame. Only the item key is posted; the
         * server renders the page (messenger/Doc2PDF, MsgAttachPDF2Action).
         *
         * @param {string} itemKey - demographic | encounter | prescriptions
         */
        function PreviewPDF(itemKey) {
            var form = document.getElementById('attachForm');
            form.previewItem.value = itemKey;
            form.isPreview.value = 'true';
            form.submit();
        }

        /**
         * Attaches every ticked item to the message being composed. The server renders each one
         * and closes this window when done; ticking nothing clears earlier chart attachments.
         */
        function AttachingPDF() {
            var form = document.getElementById('attachForm');
            form.previewItem.value = '';
            form.isPreview.value = 'false';
            form.querySelector('button[name="Attach"]').disabled = true;
            form.submit();
        }
    </script>
</head>

<body>
<div class="container-fluid px-2 py-2">

    <%-- Alert banner — hidden by default, shown via JS on error --%>
    <div id="jsAlertBanner"
         class="alert alert-danger alert-dismissible"
         style="display:none"
         role="alert">
        <span id="jsAlertText"></span>
        <button type="button"
                class="btn-close"
                onclick="this.closest('.alert').style.display='none'"
                aria-label="Close"></button>
    </div>

    <%-- Page header bar --%>
    <div class="page-header-bar d-flex align-items-center justify-content-between py-2 mb-2 border-bottom"
         id="header">
        <div class="d-flex align-items-center gap-2">
            <i class="fa-regular fa-paperclip" aria-hidden="true"></i>
            <span class="fw-semibold"><fmt:message key="messenger.CreateMessage.msgMessenger"/></span>
        </div>
        <div class="d-flex align-items-center gap-3">
            <span class="text-muted small">
                <fmt:message key="messenger.generatePreviewPDF.attachDocFor"/>
                ${carlos:forHtml(demoName)}
            </span>
            <a href="javascript:popupStart(300,400,'<%=request.getContextPath()%>/encounter/ViewAbout')" class="small text-decoration-none">
                <fmt:message key="global.about"/>
            </a>
            <a href="javascript:popupStart(300,400,'<%=request.getContextPath()%>/encounter/ViewLicense')" class="small text-decoration-none">
                <fmt:message key="global.license"/>
            </a>
        </div>
    </div>

    <div class="bg-light border rounded p-2">

        <%-- Close button --%>
        <div class="mb-2">
            <button type="button"
                    class="btn btn-outline-secondary btn-sm"
                    onclick="if (confirm(MSGS.exitConfirm)) { top.window.close(); }">
                <i class="fa-regular fa-circle-xmark" aria-hidden="true"></i>
                <fmt:message key="messenger.generatePreviewPDF.btnClose"/>
            </button>
        </div>

        <form id="attachForm" action="${pageContext.request.contextPath}/messenger/Doc2PDF" method="post">

            <table class="table table-sm table-bordered">

                <%-- Demographic information section --%>
                <tr class="table-secondary">
                    <th colspan="3">
                        <fmt:message key="messenger.generatePreviewPDF.secDemographic"/>
                    </th>
                </tr>
                <c:if test="${canDemographic}">
                <tr>
                    <td class="align-middle" style="width:2rem;">
                        <input type="checkbox" name="item" value="demographic"
                               aria-label="${carlos:forHtmlAttribute(demoName)} ${carlos:forHtmlAttribute(informationLabel)}"/>
                    </td>
                    <td class="align-middle">
                        ${carlos:forHtml(demoName)}
                        <fmt:message key="messenger.generatePreviewPDF.information"/>
                    </td>
                    <td class="align-middle" style="width:8rem;">
                        <button type="button"
                                class="btn btn-outline-secondary btn-sm"
                                data-preview-item="demographic"
                                onclick="PreviewPDF(this.dataset.previewItem)">
                            <fmt:message key="messenger.generatePreviewPDF.btnPreview"/>
                        </button>
                    </td>
                </tr>
                </c:if>

                <%-- Encounters section --%>
                <tr class="table-secondary">
                    <th colspan="3">
                        <fmt:message key="messenger.generatePreviewPDF.secEncounters"/>
                    </th>
                </tr>
                <c:if test="${hasEncounter}">
                <tr>
                    <td class="align-middle">
                        <input type="checkbox" name="item" value="encounter"
                               aria-label="${carlos:forHtmlAttribute(encounterLabel)} ${carlos:forHtmlAttribute(ecTimestamp)}"/>
                    </td>
                    <td class="align-middle">${carlos:forHtml(ecTimestamp)}</td>
                    <td class="align-middle">
                        <button type="button"
                                class="btn btn-outline-secondary btn-sm"
                                data-preview-item="encounter"
                                onclick="PreviewPDF(this.dataset.previewItem)">
                            <fmt:message key="messenger.generatePreviewPDF.btnPreview"/>
                        </button>
                    </td>
                </tr>
                </c:if>

                <%-- Prescriptions section --%>
                <tr class="table-secondary">
                    <th colspan="3">
                        <fmt:message key="messenger.generatePreviewPDF.secPrescriptions"/>
                    </th>
                </tr>
                <c:if test="${canPrescriptions}">
                <tr>
                    <td class="align-middle">
                        <input type="checkbox" name="item" value="prescriptions"
                               aria-label="${carlos:forHtmlAttribute(currentPrescTitle)}"/>
                    </td>
                    <td class="align-middle">
                        <fmt:message key="messenger.generatePreviewPDF.currentPrescriptions"/>
                    </td>
                    <td class="align-middle">
                        <button type="button"
                                class="btn btn-outline-secondary btn-sm"
                                data-preview-item="prescriptions"
                                onclick="PreviewPDF(this.dataset.previewItem)">
                            <fmt:message key="messenger.generatePreviewPDF.btnPreview"/>
                        </button>
                    </td>
                </tr>
                </c:if>

                <%-- Action row --%>
                <tr>
                    <td colspan="3" class="text-center">
                        <button type="button"
                                class="btn btn-primary btn-sm"
                                name="Attach"
                                onclick="AttachingPDF()">
                            <fmt:message key="messenger.generatePreviewPDF.btnAttach"/>
                        </button>
                    </td>
                </tr>
            </table>

            <input type="hidden" name="demographic_no" value="<%=SafeEncode.forHtmlAttribute(demographic_no)%>"/>
            <input type="hidden" name="isPreview" value="false"/>
            <input type="hidden" name="previewItem" value=""/>
        </form>
    </div>

</div>
</body>
</html>
