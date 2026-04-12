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
  - isAttaching: Present when in batch attachment processing mode
  - isPreview: Boolean flag for preview mode
  - attachmentCount: Current attachment count for batch processing

  Session dependencies:
  - msgSessionBean: Message session state management
  - EctSessionBean: Encounter session for patient context
  - RxSessionBean: Prescription session for medication data
  - Patient object for prescription profile generation

  @since 2003
--%>

<%@ page import="java.util.*" %>
<%@ page import="org.w3c.dom.*" %>
<%@ page import="java.sql.*" %>
<%@ page import="io.github.carlos_emr.*" %>
<%@ page import="java.text.*" %>
<%@ page import="java.lang.*" %>
<%@ page import="java.net.*" %>
<%@ page errorPage="/errorpage.jsp" %>
<%@ page import="io.github.carlos_emr.carlos.messenger.docxfer.send.*" %>
<%@ page import="io.github.carlos_emr.carlos.messenger.docxfer.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.data.*" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean" %>
<%@ page import="io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBean" %>
<%@ page import="io.github.carlos_emr.carlos.prescript.data.RxPatientData" %>
<%@ page import="io.github.carlos_emr.carlos.messenger.pageUtil.MsgSessionBean" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.*" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.EChartDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.EChart" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicData" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>
<fmt:setBundle basename="oscarResources"/>

<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
    EChartDao eChartDao = SpringUtils.getBean(EChartDao.class);
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_msg" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_msg");%>
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
        response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_msg");
        return;
    }
    try {
        demographicNoInt = Integer.parseInt(demographic_no_raw);
    } catch (NumberFormatException e) {
        response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_msg");
        return;
    }
    // Use the validated integer value as the canonical demographic number string
    String demographic_no = String.valueOf(demographicNoInt);
    // Pre-encode for reuse in URI construction
    String encDemoNo = Encode.forUriComponent(demographic_no);

    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

    DemographicData demoData = new DemographicData();
    Demographic demo = demoData.getDemographic(loggedInInfo, demographic_no);
    String demoName = "";
    if (demo != null) {
        demoName = demo.getLastName() + ", " + demo.getFirstName();
    }

    int indexCount = 0;

    EctSessionBean bean = new EctSessionBean();
    // Use validated integer-derived string to prevent raw request data in session (CWE-501)
    bean.demographicNo = demographic_no;

    MsgSessionBean MsgSessionBean = (MsgSessionBean) request.getSession().getAttribute("msgSessionBean");

    request.getSession().setAttribute("EctSessionBean", bean);

    // Expose display variables as page attributes for EL/OWASP encoding
    pageContext.setAttribute("demoName", demoName);
    // Build the demographic titleArray metadata value (used as PDF attachment title)
    pageContext.setAttribute("demoTitleValue", demoName + " information");

    // Resolve encounter data for the patient
    EChart ec = eChartDao.getLatestChart(Integer.parseInt(demographic_no));
    pageContext.setAttribute("hasEncounter", ec != null);
    if (ec != null) {
        pageContext.setAttribute("ecTimestamp", ec.getTimestamp().toString());
        // Build encounter titleArray metadata value (used as PDF attachment title)
        pageContext.setAttribute("ecTitleValue", "Encounter: " + ec.getTimestamp().toString());
    }

    // Compute URIs for each document type
    String demoUri = request.getContextPath() + "/demographic/DemographicPdfLabel.do?demographic_no=" + encDemoNo;
    pageContext.setAttribute("demoUri", demoUri);

    String ecUri = "";
    if (ec != null) {
        ecUri = request.getContextPath() + "/encounter/echarthistoryprint.jsp?echartid="
                + Encode.forUriComponent(String.valueOf(ec.getId()))
                + "&demographic_no=" + encDemoNo;
        pageContext.setAttribute("ecUri", ecUri);
    }

    // Setup prescription session bean and patient data for drug profile generation
    RxSessionBean Rxbean;
    if (request.getSession().getAttribute("RxSessionBean") != null) {
        Rxbean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
    } else {
        Rxbean = new RxSessionBean();
    }
    request.getSession().setAttribute("RxSessionBean", Rxbean);

    RxPatientData.Patient patient = RxPatientData.getPatient(loggedInInfo, demographic_no);
    if (patient != null) {
        request.getSession().setAttribute("Patient", patient);
    }
    Rxbean.setProviderNo((String) request.getSession().getAttribute("user"));
    Rxbean.setDemographicNo(demographicNoInt);

    String rxUri = request.getContextPath() + "/oscarRx/PrintDrugProfile2.jsp?demographic_no=" + encDemoNo;
    pageContext.setAttribute("rxUri", rxUri);
%>

<%-- Pre-compute i18n message strings for use in JavaScript --%>
<fmt:message key="messenger.generatePreviewPDF.confirmClose" var="exitConfirmMsg"/>
<fmt:message key="messenger.generatePreviewPDF.msgAttachingCount" var="jsAttachingTemplate"/>

<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head>
    <meta charset="UTF-8">
    <title>CARLOS - <fmt:message key="messenger.generatePreviewPDF.title"/></title>
    <%@ include file="/includes/global-head.jspf" %>

    <script>
        // Global timer variables for monitoring iframe content loading
        var timerID = null;
        var timerRunning = false;

        // i18n message strings for JavaScript dialogs
        var MSGS = {
            exitConfirm: '${e:forJavaScript(exitConfirmMsg)}'
        };

        /**
         * Updates the iframe source URL for document loading.
         * @param {string} url - URL to load in the preview iframe
         */
        function SetBottomURL(url) {
            var f = parent.srcFrame;
            f.location = (url !== "") ? url : document.forms[0].url.value;
        }

        /**
         * Extracts HTML content from the loaded iframe and stores in form field.
         */
        function GetBottomSRC() {
            var f = parent.srcFrame;
            document.forms[0].srcText.value = f.document.body.innerHTML;
        }

        /**
         * Initiates PDF preview generation process.
         * @param {string} url - Document URL to preview
         */
        function PreviewPDF(url) {
            document.forms[0].srcText.value = "";
            document.forms[0].isPreview.value = true;
            SetBottomURL(url);
            setTimeout("GetBottomSRC()", 1000);
            timerID = setInterval("CheckSrcText()", 1000);
            timerRunning = true;
        }

        function testing() {
            document.forms[0].isPreview.value = true;
            timerID = setInterval("CheckSrcText()", 1000);
            timerRunning = true;
        }

        /**
         * Handles batch PDF attachment processing for selected documents.
         * @param {number} number - Index of attachment to process (-1 for batch mode)
         */
        function AttachingPDF(number) {
            var uriArray = document.forms[0].uriArray;
            var titleArray = document.forms[0].titleArray;
            var indexArray = document.forms[0].indexArray;
            var wantedIndex = 0;

            // Reset form state for attachment processing
            document.forms[0].srcText.value = "";
            document.forms[0].isPreview.value = false;
            document.forms[0].isAttaching.value = true;

            if (number === -1) {
                document.forms[0].isNew.value = true;
                wantedIndex = -1;
            } else {
                document.forms[0].isNew.value = false;
            }

            var j = 0;

            // Find the specific attachment to process by index
            if (number !== -1) {
                for (var i = 0; i < indexArray.length; i++) {
                    if (indexArray[i].checked) {
                        if (number === j) {
                            wantedIndex = i;
                        }
                        j++;
                    }
                }
            } else {
                // Count checked items and find first one for batch processing
                for (var i = 0; i < indexArray.length; i++) {
                    if (indexArray[i].checked) {
                        j++;
                        if (wantedIndex < 0) {
                            wantedIndex = i;
                        }
                    }
                }
            }

            // Submit immediately if no items selected
            if (j === 0) {
                document.forms[0].submit();
                return;
            }

            document.forms[0].attachmentCount.value = j;
            document.forms[0].attachmentTitle.value = titleArray[wantedIndex].value;
            SetBottomURL(uriArray[wantedIndex].value);
            setTimeout("GetBottomSRC()", 1000);
            timerID = setInterval("CheckSrcText()", 1000);
            timerRunning = true;
        }

        /**
         * Monitors iframe content loading and submits form when content is ready.
         */
        function CheckSrcText() {
            if (document.forms[0].srcText.value !== "") {
                if (timerRunning) {
                    clearInterval(timerID);
                }
                timerRunning = false;
                document.forms[0].submit();
            }
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
                ${e:forHtml(demoName)}
            </span>
            <a href="javascript:popupStart(300,400,'About.jsp')" class="small text-decoration-none">
                <fmt:message key="global.about"/>
            </a>
            <a href="javascript:popupStart(300,400,'License.jsp')" class="small text-decoration-none">
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

        <form action="${pageContext.request.contextPath}/messenger/Doc2PDF.do" method="post">

            <table class="table table-sm table-bordered">

                <%-- Demographic information section --%>
                <tr class="table-secondary">
                    <th colspan="3">
                        <fmt:message key="messenger.generatePreviewPDF.secDemographic"/>
                    </th>
                </tr>
                <tr>
                    <td class="align-middle" style="width:2rem;">
                        <input type="checkbox" name="uriArray"
                               value="<%=Encode.forHtmlAttribute(demoUri)%>"
                               style="display:none"/>
                        <input type="checkbox" name="indexArray"
                               value="<%= Integer.toString(indexCount++) %>"/>
                        <input type="checkbox" name="titleArray"
                               value="${e:forHtmlAttribute(demoTitleValue)}"
                               style="display:none"/>
                    </td>
                    <td class="align-middle">
                        ${e:forHtml(demoName)}
                        <fmt:message key="messenger.generatePreviewPDF.information"/>
                    </td>
                    <td class="align-middle" style="width:8rem;">
                        <% if (request.getParameter("isAttaching") == null) { %>
                        <button type="button"
                                class="btn btn-outline-secondary btn-sm"
                                data-preview-uri="<%=Encode.forHtmlAttribute(demoUri)%>"
                                onclick="PreviewPDF(this.dataset.previewUri)">
                            <fmt:message key="messenger.generatePreviewPDF.btnPreview"/>
                        </button>
                        <% } %>
                    </td>
                </tr>

                <%-- Encounters section --%>
                <tr class="table-secondary">
                    <th colspan="3">
                        <fmt:message key="messenger.generatePreviewPDF.secEncounters"/>
                    </th>
                </tr>
                <% if (ec != null) { %>
                <tr>
                    <td class="align-middle">
                        <input type="checkbox" name="uriArray"
                               value="<%=Encode.forHtmlAttribute(ecUri)%>"
                               style="display:none"/>
                        <input type="checkbox" name="indexArray"
                               value="<%= Integer.toString(indexCount++) %>"/>
                        <input type="checkbox" name="titleArray"
                               value="${e:forHtmlAttribute(ecTitleValue)}"
                               style="display:none"/>
                    </td>
                    <td class="align-middle">${e:forHtml(ecTimestamp)}</td>
                    <td class="align-middle">
                        <% if (request.getParameter("isAttaching") == null) { %>
                        <button type="button"
                                class="btn btn-outline-secondary btn-sm"
                                data-preview-uri="<%=Encode.forHtmlAttribute(ecUri)%>"
                                onclick="PreviewPDF(this.dataset.previewUri)">
                            <fmt:message key="messenger.generatePreviewPDF.btnPreview"/>
                        </button>
                        <% } %>
                    </td>
                </tr>
                <% } %>

                <%-- Prescriptions section --%>
                <tr class="table-secondary">
                    <th colspan="3">
                        <fmt:message key="messenger.generatePreviewPDF.secPrescriptions"/>
                    </th>
                </tr>
                <tr>
                    <td class="align-middle">
                        <input type="checkbox" name="uriArray"
                               value="<%=Encode.forHtmlAttribute(rxUri)%>"
                               style="display:none"/>
                        <input type="checkbox" name="indexArray"
                               value="<%= Integer.toString(indexCount++) %>"/>
                        <input type="checkbox" name="titleArray"
                               value="Current prescriptions"
                               style="display:none"/>
                    </td>
                    <td class="align-middle">
                        <fmt:message key="messenger.generatePreviewPDF.currentPrescriptions"/>
                    </td>
                    <td class="align-middle">
                        <% if (request.getParameter("isAttaching") == null) { %>
                        <button type="button"
                                class="btn btn-outline-secondary btn-sm"
                                data-preview-uri="<%=Encode.forHtmlAttribute(rxUri)%>"
                                onclick="PreviewPDF(this.dataset.previewUri)">
                            <fmt:message key="messenger.generatePreviewPDF.btnPreview"/>
                        </button>
                        <% } %>
                    </td>
                </tr>

                <%-- Action / status row --%>
                <tr>
                    <td colspan="3" class="text-center">
                        <% if (request.getParameter("isAttaching") != null) { %>
                        <input type="text" name="status"
                               class="form-control form-control-sm"
                               value="" readonly/>
                        <% } else { %>
                        <button type="button"
                                class="btn btn-primary btn-sm"
                                name="Attach"
                                onclick="AttachingPDF(-1)">
                            <fmt:message key="messenger.generatePreviewPDF.btnAttach"/>
                        </button>
                        <% } %>
                    </td>
                </tr>

                <%-- Hidden processing fields --%>
                <tr>
                    <td colspan="3" class="d-none">
                        <input type="hidden" name="srcText" id="srcText" value=""/>
                        <input type="hidden" name="attachmentCount" id="attachmentCount"
                               value="<%=Encode.forHtmlAttribute(request.getParameter("attachmentCount") == null ? "0" : request.getParameter("attachmentCount"))%>"/>
                        <input type="hidden" name="demographic_no" id="demographic_no"
                               value="<%=Encode.forHtmlAttribute(demographic_no != null ? demographic_no : "")%>"/>
                        <input type="hidden" name="isPreview" id="isPreview"
                               value="<%=Encode.forHtmlAttribute(request.getParameter("isPreview") == null ? "false" : request.getParameter("isPreview"))%>"/>
                        <input type="hidden" name="isAttaching" id="isAttaching"
                               value="<%=Encode.forHtmlAttribute(request.getParameter("isAttaching") == null ? "false" : request.getParameter("isAttaching"))%>"/>
                        <input type="hidden" name="isNew" id="isNew" value="true"/>
                        <input type="hidden" name="attachmentTitle" id="attachmentTitle" value=""/>
                    </td>
                </tr>

            </table>

        </form>
    </div>

    <%-- Auto-submit script when page is re-loaded in attachment processing mode --%>
    <script>
        if (document.forms[0].isAttaching.value === "true") {
            var j = 0;
            var indexArray = document.forms[0].indexArray;
            for (var i = 0; i < indexArray.length; i++) {
                if (indexArray[i].checked) {
                    j++;
                }
            }
            var attachingTemplate = '${e:forJavaScript(jsAttachingTemplate)}';
            document.forms[0].status.value = attachingTemplate
                .replace('{0}', <%=MsgSessionBean.getCurrentAttachmentCount() + 1%>)
                .replace('{1}', j);
            AttachingPDF(<%=MsgSessionBean.getCurrentAttachmentCount()%>);
        }
    </script>

</div>
</body>
</html>
