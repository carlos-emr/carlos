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
    ================================================================================
    labDisplay.jsp 
    ================================================================================
    Purpose:
        Comprehensive lab results display and acknowledgment interface for CARLOS EMR.
        This JSP renders HL7 lab results with full formatting, segmentation, and
        provider-initiated acknowledgment workflows. Supports multi-jurisdictional
        lab systems (Ontario OLIS, etc.) with custom parsing and display rules.

    Key Features:
        - HL7 v2 lab result parsing and display with segment handling
        - Rich formatting for critical values, abnormal flags, and reference ranges
        - Patient-friendly and provider-friendly view options
        - Lab result acknowledgment with auto-populated comments via macros
        - Macro system integration: Quick-action templates for common acknowledgments
        - Tickler (follow-up reminder) creation during acknowledgment
        - Multi-lab display with result status tracking
        - Case management note linking and comment attachment
        - Results history and comparative views
        - Print-friendly output with RTF support
        - OWASP XSS encoding for all user inputs and data outputs
        - OWASP CSRF protection via security tokens

    Architecture:
        - Displays HL7 lab results from database or file storage
        - Parses HL7 v2 segments: MSH, PID, OBR, OBX, NTE, etc.
        - Provides acknowledgment UI with optional macro quick-actions
        - Macro dropdown renders user-configured lab result templates
        - Macro application via runMacro() JavaScript function (defined in this file)
        - Result acknowledgment submitted to backend action for persistence
        - Audit logging for all result views and acknowledgments

    Lab Macro Integration:
        - Loads provider's configured lab macros from UserProperty.LAB_MACRO_JSON
        - Renders macro names as clickable links in results page
        - Macro application pre-fills acknowledgment comment and optionally creates tickler
        - Supports multiple macro templates per result (e.g., "Abnormal", "Critical")
        - Macro parsing uses Jackson ObjectMapper with error handling
        - Failed macro parsing displays user-friendly error in macro dropdown

    Security:
        - Requires "_lab" READ privilege via security.oscarSec tag
        - All JSP outputs escaped with OWASP Encoder for XSS prevention
        - Macro names XSS-encoded: Encode.forJavaScript() in onClick, Encode.forHtml() in link text
        - Audit logging via LogAction for all sensitive operations
        - HL7 message output encoding (OWASP) to prevent XSS

    Known Issues & Limitations:
        - RTF conversion may fail silently on unsupported characters

    Dependencies:
        - HL7 parsing: io.github.carlos_emr.carlos.lab.ca.all.parsers.*
        - Lab display helper: io.github.carlos_emr.carlos.lab.ca.all.web.LabDisplayHelper
        - User properties: io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO
        - Macros: Jackson ObjectMapper for JSON parsing
        - Security: OWASP Encoder for XSS prevention

    @since 2007-07-13 (Macro improvements 2026-02-17)
--%>

<%@ page import="com.fasterxml.jackson.databind.JsonNode" %>
<%@ page import="com.fasterxml.jackson.databind.ObjectMapper" %>
<%@ page import="com.fasterxml.jackson.databind.node.ArrayNode" %>
<%@ page import="com.fasterxml.jackson.databind.node.ObjectNode" %>
<%@ page import="io.github.carlos_emr.MyDateFormat" %>
<%@ page import="io.github.carlos_emr.OscarProperties" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DemographicDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.MeasurementMapDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Hl7TextInfo" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Hl7TextMessage" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.MeasurementMap" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.PatientLabRouting" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Tickler" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.UserProperty" %>
<%@ page import="io.github.carlos_emr.carlos.lab.LabRequestReportLink" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.all.*" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.all.AcknowledgementData" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.all.Hl7textResultsData" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.all.parsers.*" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.all.util.LabVersionComparator"%>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.all.web.LabDisplayHelper" %>
<%@ page import="io.github.carlos_emr.carlos.log.*" %>
<%@ page import="io.github.carlos_emr.carlos.log.LogAction" %>
<%@ page import="io.github.carlos_emr.carlos.log.LogConst" %>
<%@ page import="io.github.carlos_emr.carlos.managers.SecurityInfoManager" %>
<%@ page import="io.github.carlos_emr.carlos.managers.TicklerManager" %>
<%@ page import="io.github.carlos_emr.carlos.mds.data.ReportStatus" %>
<%@ page import="io.github.carlos_emr.carlos.util.ConversionUtils" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilDateUtilities" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="java.io.ByteArrayInputStream" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.time.LocalDate" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.util.*" %>
<%@ page import="javax.swing.text.rtf.RTFEditorKit" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.apache.commons.lang3.builder.ReflectionToStringBuilder" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="org.w3c.dom.Document" %>

<jsp:useBean id="oscarVariables" class="java.util.Properties" scope="session"/>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/oscarProperties-tag.tld" prefix="oscarProperties" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_lab" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_lab");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    OscarProperties props = OscarProperties.getInstance();
    String segmentID = request.getParameter("segmentID");
    String providerNo = request.getParameter("providerNo");
    String searchProviderNo = StringUtils.trimToEmpty(request.getParameter("searchProviderNo"));
    String patientMatched = request.getParameter("patientMatched");
    String remoteFacilityIdString = request.getParameter("remoteFacilityId");
    String remoteLabKey = request.getParameter("remoteLabKey");
    String demographicID = request.getParameter("demographicId");
    String showAllstr = request.getParameter("all");

    String showLatest = request.getParameter("showLatest");

    List<String> allLicenseNames = new ArrayList<String>();
    String lastLicenseNo = null, currentLicenseNo = null;

    if (providerNo == null) {
        providerNo = loggedInInfo.getLoggedInProviderNo();
    }


    UserPropertyDAO userPropertyDAO = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
    UserProperty uProp = userPropertyDAO.getProp(providerNo, UserProperty.LAB_ACK_COMMENT);
    boolean skipComment = false;
    if (uProp != null && uProp.getValue().equalsIgnoreCase("yes")) {
        skipComment = true;
    }

    UserProperty getRecallDelegate = userPropertyDAO.getProp(providerNo, UserProperty.LAB_RECALL_DELEGATE);
    UserProperty getRecallTicklerAssignee = userPropertyDAO.getProp(providerNo, UserProperty.LAB_RECALL_TICKLER_ASSIGNEE);
    UserProperty getRecallTicklerPriority = userPropertyDAO.getProp(providerNo, UserProperty.LAB_RECALL_TICKLER_PRIORITY);
    boolean recall = false;
    String recallDelegate = "";
    String ticklerAssignee = "";
    String recallTicklerPriority = "";

    if (getRecallDelegate != null) {
        recall = true;
        recallDelegate = getRecallDelegate.getValue();
        if (getRecallTicklerPriority != null) {
            recallTicklerPriority = getRecallTicklerPriority.getValue();
        }
        if (getRecallTicklerAssignee != null && "yes".equals(getRecallTicklerAssignee.getValue())) {
            ticklerAssignee = "&taskTo=" + recallDelegate;
        }
    }

    Hl7TextMessageDao hl7TxtMsgDao = SpringUtils.getBean(Hl7TextMessageDao.class);
    MeasurementMapDao measurementMapDao = SpringUtils.getBean(MeasurementMapDao.class);
    Hl7TextMessage hl7TextMessage = null;
    if (StringUtils.isNotBlank(segmentID) && StringUtils.isNumeric(segmentID)) {
        hl7TextMessage = hl7TxtMsgDao.find(Integer.parseInt(segmentID));
    }

    String dateLabReceived = "n/a";
    if (hl7TextMessage != null) {
        java.util.Date date = hl7TextMessage.getCreated();
        String stringFormat = "yyyy-MM-dd HH:mm";
        dateLabReceived = UtilDateUtilities.DateToString(date, stringFormat);
    }

    boolean isLinkedToDemographic = false;
    ArrayList<ReportStatus> ackList = null;
    String multiLabId = null;
    MessageHandler handler = null;
    String hl7 = null;
    String reqID = null, reqTableID = null;
    String remoteFacilityIdQueryString = "";

    boolean bShortcutForm = OscarProperties.getInstance().getProperty("appt_formview", "").equalsIgnoreCase("on") ? true : false;
    String formName = bShortcutForm ? OscarProperties.getInstance().getProperty("appt_formview_name") : "";
    String formNameShort = formName.length() > 3 ? (formName.substring(0, 2) + ".") : formName;
    String formName2 = bShortcutForm ? OscarProperties.getInstance().getProperty("appt_formview_name2", "") : "";
    String formName2Short = formName2.length() > 3 ? (formName2.substring(0, 2) + ".") : formName2;
    boolean bShortcutForm2 = bShortcutForm && !formName2.equals("");
    List<MessageHandler> handlers = new ArrayList<MessageHandler>();
    String[] segmentIDs = null;
    Boolean showAll = showAllstr != null && !"null".equalsIgnoreCase(showAllstr);

    String duplicateOfLab = null;
    Map<String, ExcellerisOntarioHandler.OrderStatus> missingTests = new HashMap<>();

    if (remoteFacilityIdString == null) // local lab
    {

        HashMap<String, Object> reqMap = LabRequestReportLink.getLinkByReport("hl7TextMessage", Long.valueOf(segmentID));
        if (reqMap.get("id") != null) {
            reqID = reqMap.get("id").toString();
            reqTableID = reqMap.get("request_id").toString();
        } else {
            reqID = "";
            reqTableID = "";
        }


        PatientLabRoutingDao dao = SpringUtils.getBean(PatientLabRoutingDao.class);
        for (PatientLabRouting r : dao.findByLabNoAndLabType(ConversionUtils.fromIntString(segmentID), "HL7")) {
            demographicID = "" + r.getDemographicNo();
        }

        if (demographicID != null && !demographicID.equals("") && !demographicID.equals("0")) {
            isLinkedToDemographic = true;
            LogAction.addLog((String) session.getAttribute("user"), LogConst.READ, LogConst.CON_HL7_LAB, segmentID, request.getRemoteAddr(), demographicID);
        } else {
            LogAction.addLog((String) session.getAttribute("user"), LogConst.READ, LogConst.CON_HL7_LAB, segmentID, request.getRemoteAddr());
        }


        if (showAll) {
            multiLabId = request.getParameter("multiID");
            segmentIDs = multiLabId.split(",");
            for (int i = 0; i < segmentIDs.length; ++i) {
                handlers.add(Factory.getHandler(segmentIDs[i]));
            }

            handler = handlers.get(0);
        } else {
            multiLabId = Hl7textResultsData.getMatchingLabs(segmentID);
            segmentIDs = multiLabId.split(",");

		int totalMatchingLabs = segmentIDs.length;
		if (showLatest != null && "true".equals(showLatest) && totalMatchingLabs > 1) {
			segmentID = segmentIDs[totalMatchingLabs - 1];
		}

            List<String> segmentIdList = new ArrayList<String>();
            handler = Factory.getHandler(segmentID);
            handlers.add(handler);

        if ("ExcellerisON".equals(handler.getMsgType()) && segmentIDs.length > 1) {
            LabVersionComparator labVersionComparator = new LabVersionComparator(Arrays.asList(segmentIDs));
            duplicateOfLab = labVersionComparator.isLabDuplicate(segmentID);
            missingTests = labVersionComparator.findMissingTests(segmentID, true);
        }

            segmentIdList.add(segmentID);

            //this is where it gets weird. We want to show all messages with different filler order num but same accession in a single report
            segmentIDs = segmentIdList.toArray(new String[segmentIdList.size()]);

            hl7 = Factory.getHL7Body(segmentID);
        }

    }

request.setAttribute("duplicateOfLab", duplicateOfLab);
request.setAttribute("missingTests", missingTests);

/********************** Converted to this sport *****************************/

String tickler_no="";
String tickler_note="";
Integer demoI = 0;
Integer numTickler = 0;

if (demographicID != null && !demographicID.isEmpty()) {
    try {
        demoI = Integer.parseInt(demographicID);
    } catch (NumberFormatException e) {
        MiscUtils.getLogger().warn("Invalid demographicID: " + demographicID, e);
    }
}


LocalDate nearFuture = LocalDate.now().plusWeeks(6);
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
String strDate = nearFuture.format(formatter);

SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
TicklerManager ticklerManager= SpringUtils.getBean(TicklerManager.class);

if (securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", "r", demoI) && isLinkedToDemographic ) {
    // Note: tickler_note is intentionally raw HTML. All dynamic values MUST remain encoded using OWASP Encoder methods.
    String tlinkf="\n <a class=\"alert-link\" href='"+request.getContextPath()+"/tickler/ticklerEdit.jsp?tickler_no=";
    List<String> notes = new java.util.ArrayList<>();
    List<Tickler> ticklers = ticklerManager.search_tickler(loggedInInfo, demoI, MyDateFormat.getSysDate(strDate));

    for (Tickler t: ticklers) {
        if (t.getMessage() != null && !t.getMessage().trim().isEmpty()) {
            notes.add(tlinkf + Encode.forUriComponent(String.valueOf(t.getId())) + "' target='_blank'>" + Encode.forHtml(t.getMessage()) + "</a>");
        }
    }
    numTickler = notes.size();
    tickler_note = String.join(", ", notes);
}

// check for errors printing
    if (request.getAttribute("printError") != null && (Boolean) request.getAttribute("printError")) {
%>
<script>
    alert("The lab could not be printed due to an error. Please see the server logs for more detail.");
</script>
<%
    }


    String annotation_display = CaseManagementNoteLink.DISP_LABTEST;
    CaseManagementManager caseManagementManager = (CaseManagementManager) SpringUtils.getBean(CaseManagementManager.class);

%>

<!DOCTYPE HTML>

<html>
<head>
    <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
    <title><%=Encode.forHtml(handler.getPatientName()) + " Lab Results"%>
    </title>

    <script type="text/javascript"
            src="${pageContext.request.contextPath}/share/javascript/Oscar.js"></script>
    <script type="text/javascript" src="${pageContext.servletContext.contextPath}/js/global.js"></script>

    <script>
        var contextpath = "${pageContext.servletContext.contextPath}";
        const ctx = contextpath;
    </script>


    <script type="text/javascript">
        // alternately refer to this function in oscarMDSindex.js as labDisplayAjax.jsp does
        function updateLabDemoStatus(labno) {
            if (document.getElementById("DemoTable" + labno)) {
                document.getElementById("DemoTable" + labno).style.backgroundColor = "#FFF";
            }
        }
    </script>
    <!--<link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/share/css/OscarStandardLayout.css">-->
<!-- Bootstrap -->
<link rel="stylesheet" type="text/css" media="all" href="${pageContext.request.contextPath}/library/bootstrap/5.0.2/css/bootstrap.css">
<script src="${pageContext.request.contextPath}/library/bootstrap/5.0.2/js/bootstrap.bundle.js"></script>

     <style type="text/css">
body { line-height: 12px; font-size: 12px; }
.RollRes     { font-weight: 700; font-size: 8pt; color: white; font-family:
               Verdana, Arial, Helvetica }
.RollRes a:link { color: white }
.RollRes a:hover { color: white }
.RollRes a:visited { color: white }
.RollRes a:active { color: white }
.AbnormalRollRes { font-weight: 700; font-size: 8pt; color: red; font-family:
               Verdana, Arial, Helvetica }
.AbnormalRollRes a:link { color: red }
.AbnormalRollRes a:hover { color: red }
.AbnormalRollRes a:visited { color: red }
.AbnormalRollRes a:active { color: red }
.CorrectedRollRes { font-weight: 700; font-size: 8pt; color: yellow; font-family:
               Verdana, Arial, Helvetica }
.CorrectedRollRes a:link { color: yellow }
.CorrectedRollRes a:hover { color: yellow }
.CorrectedRollRes a:visited { color: yellow }
.CorrectedRollRes a:active { color: yellow }
.AbnormalRes { font-weight: bold; font-size: 8pt; color: red; font-family:
               Verdana, Arial, Helvetica }
.AbnormalRes a:link { color: red }
.AbnormalRes a:hover { color: red }
.AbnormalRes a:visited { color: red }
.AbnormalRes a:active { color: red }
.NormalRes   { font-weight: bold; font-size: 8pt; color: black; font-family:
                      Verdana, Arial, Helvetica }
.NormalRes a:link { color: black }
.NormalRes a:hover { color: black }
.NormalRes a:visited { color: black }
.NormalRes a:active { color: black }
.HiLoRes     { font-weight: bold; font-size: 8pt; color: blue; font-family:
               Verdana, Arial, Helvetica }
.HiLoRes a:link { color: blue }
.HiLoRes a:hover { color: blue }
.HiLoRes a:visited { color: blue }
.HiLoRes a:active { color: blue }
.CorrectedRes { font-weight: bold; font-size: 8pt; color: #E000D0; font-family:
               Verdana, Arial, Helvetica }
.CorrectedRes         a:link { color: #6da997 }
.CorrectedRes a:hover { color: #6da997 }
.CorrectedRes a:visited { color: #6da997 }
.CorrectedRes a:active { color: #6da997 }
.Field       { font-weight: bold; font-size: 8.5pt; color: black; font-family:
               Verdana, Arial, Helvetica }
.NarrativeRes { font-weight: 700; font-size: 10pt; color: black; font-family:
               Courier New, Courier, mono }
div.Field a:link { color: black }
div.Field a:hover { color: black }
div.Field a:visited { color: black }
div.Field a:active { color: black }
.Field2      { font-weight: bold; font-size: 8pt; color: #ffffff; font-family:
               Verdana, Arial, Helvetica }
div.Field2   { font-weight: bold; font-size: 8pt; color: #ffffff; font-family:
               Verdana, Arial, Helvetica }
div.FieldData { font-weight: normal; font-size: 8pt; color: black; font-family:
               Verdana, Arial, Helvetica }
div.Field3   { font-weight: normal; font-size: 8pt; color: black; font-style: italic;
               font-family: Verdana, Arial, Helvetica }
div.Title    { font-weight: 800; font-size: 10pt; color: white; font-family:
               Verdana, Arial, Helvetica; padding-top: 4pt; padding-bottom:
               2pt }
div.Title a:link { color: white }
div.Title a:hover { color: white }
div.Title a:visited { color: white }
div.Title a:active { color: white }
div.Title2   { font-weight: bolder; font-size: 9pt; color: black; text-indent: 5pt;
               font-family: Verdana, Arial, Helvetica; padding: 10pt 15pt 2pt 2pt}
div.Title2 a:link { color: black }
div.Title2 a:hover { color: black }
div.Title2 a:visited { color: black }
div.Title2 a:active { color: black }

.Cell        { background-color: silver; border-left: thin solid grey;
               text-align: center;
               border-right: thin solid black;
               border-top: thin solid grey;
               border-bottom: thin solid black }
.Cell2       { background-color: #376c95; border-left-style: none; border-left-width: medium;
               border-right-style: none; border-right-width: medium;
               border-top: thin none #bfcbe3; border-bottom-style: none;
               border-bottom-width: medium }
.Cell3       { background-color: #add9c7; border-left: thin solid #dbfdeb;
               border-right: thin solid #5d9987;
               border-top: thin solid #dbfdeb;
               border-bottom: thin solid #5d9987 }
.CellHdr     { background-color: #cbe5d7; border-right-style: none; border-right-width:
               medium; border-bottom-style: none; border-bottom-width: medium }
.Nav         { font-weight: bold; font-size: 8pt; color: black; font-family:
               Verdana, Arial, Helvetica }
.PageLink a:link { font-size: 8pt; color: white }
.PageLink a:hover { color: red }
.PageLink a:visited { font-size: 9pt; color: yellow }
.PageLink a:active { font-size: 12pt; color: yellow }
.PageLink    { font-family: Verdana }
.text1       { font-size: 8pt; color: black; font-family: Verdana, Arial, Helvetica }
div.txt1     { font-size: 8pt; color: black; font-family: Verdana, Arial }
div.txt2     { font-weight: bolder; font-size: 6pt; color: black; font-family: Verdana, Arial }
div.Title3   { font-weight: bolder; font-size: 12pt; color: black; font-family:
               Verdana, Arial }
.red         { color: red }
.text2       { font-size: 7pt; color: black; font-family: Verdana, Arial }
.white       { color: white }
.title1      { font-size: 9pt; color: black; font-family: Verdana, Arial }
div.Title4   { font-weight: 600; font-size: 8pt; color: white; font-family:
               Verdana, Arial, Helvetica }
pre {
	display: block;
    font-family:  Verdana, Arial, Helvetica;
    background-color: #f5f5f5;
    border: 1px solid rgba(0, 0, 0, 0.15);
    white-space: -moz-pre-space;
    margin:0px;
    font-size: x-small;
    font-weight:400;
}

[id^=ticklerWrap]{position:relative;top:0px;background-color:#FF6600;width:100%;}

input[id^='acklabel_']{
    margin-top: 10px; /* align with bootstrap buttons */
}


.completedTickler{
    opacity: 0.8;
}

@media print {
.DoNotPrint{display:none;}
}


#labVersionInfoModal .modal-title {
    font-size: 18px;
    font-weight: bold;
    margin-bottom: 15px;
}

#labVersionInfoModal .info-section {
    margin-bottom: 20px;
}

#labVersionInfoModal .info-section p {
    margin: 5px 0;
    color: #555;
}

#labVersionInfoModal .test-list {
    margin-left: 10px;
}

#labVersionInfoModal .test-item {
    display: flex;
    justify-content: space-between;
    margin: 5px 0;
}

#labVersionInfoModal .status {
    font-weight: bold;
}
    </style>

    <script>
        var labNo = '<%=Encode.forJavaScript(segmentID)%>';
        var providerNo = '<%=Encode.forJavaScript(providerNo)%>';
        var demographicNo = '<%=Encode.forJavaScript(isLinkedToDemographic ? demographicID : "")%>';

        function popupStart(vheight, vwidth, varpage, windowname) {
            var page = varpage;
            windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes";
            var popup = window.open(varpage, windowname, windowprops);
        }

        function getComment(action, segmentId) {
            var ret = true;
            var comment = "";
            var text = providerNo + "_" + segmentId + "commentText";
            var textEl = document.getElementById(text);
            if (textEl != null) {
                comment = textEl.innerHTML;
                if (comment == null) {
                    comment = "";
                }
            }
            var commentVal = prompt('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.msgComment"/>', comment);

            var ackForm = document.forms['acknowledgeForm_' + segmentId];
            if (commentVal == null) {
                ret = false;
            } else if (ackForm && ackForm.comment) {
                if (commentVal.length > 0) {
                    ackForm.comment.value = commentVal;
                } else {
                    ackForm.comment.value = comment;
                }
            }

            if (ret) handleLab('acknowledgeForm_' + segmentId, segmentId, action);

            return false;
        }

        function printPDF(labid) {
            var frm = "acknowledgeForm_" + labid;
            var form = document.forms[frm];
            if (form) {
                form.action = "lab/CA/ALL/PrintPDF.do";
                form.submit();
            }
        }

        function linkreq(rptId, reqId) {
            var link = "<%= request.getContextPath() %>/lab/LinkReq.jsp?table=hl7TextMessage&rptid=" + rptId + "&reqid=" + reqId + "<%=demographicID != null ? "&demographicNo=" + demographicID : ""%>";
            window.open(link, "linkwin", "width=500, height=200");
        }


        function matchMe() {
            <% if ( !isLinkedToDemographic) { %>
            popupStart(360, 680, '${pageContext.request.contextPath}/oscarMDS/SearchPatient.do?labType=HL7&segmentID=<%= Encode.forJavaScript(segmentID) %>&name=<%=java.net.URLEncoder.encode(handler.getLastName()+", "+handler.getFirstName(), StandardCharsets.UTF_8)%>', 'searchPatientWindow');
            <% } %>
        }


        function getCsrfToken() {
            var el = document.querySelector('input[name="CSRF-TOKEN"]');
            return el ? el.value : '';
        }

        function handleLab(formid, labid, action) {
            var url = '<%= request.getContextPath() %>/documentManager/inboxManage.do';
            var data = 'method=isLabLinkedToDemographic&labid=' + labid + '&CSRF-TOKEN=' + encodeURIComponent(getCsrfToken());
            fetch(url, {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: data
            })
            .then(function(response) { return response.json(); })
            .then(function(json) {
                if (json != null) {
                    var success = json.isLinkedToDemographic;
                    var demoid = '';
                    //check if lab is linked to a providers
                    if (success) {
                        console.log("Lab IS linked to demographic: " + success);
                        console.log("Processing action: " + action);

                        if (action === 'ackLab') {
                            console.log("Acknowledging lab results");
                            if (confirmAck()) {
                                console.log("Acknowledge confirmed. Labid: " + labid);
                                document.getElementById("labStatus_" + labid).value = "A";
                                updateStatus(formid, labid);
                            }
                        } else if (action === 'msgLab') {
                            console.log("Sending message about lab. Demoid: " + demoid);
                            demoid = json.demoId;
                            if (demoid != null && demoid.length > 0) {
                                window.popup(700, 960, '${pageContext.request.contextPath}/messenger/SendDemoMessage.do?demographic_no=' + demoid, 'msg');
                            }
                        } else if (action === 'msgLabRecall') {
                            demoid = json.demoId;
                            if (demoid != null && demoid.length > 0) {
                                window.popup(700, 980, '${pageContext.request.contextPath}/messenger/SendDemoMessage.do?demographic_no=' + demoid + "&recall", 'msgRecall');
                                window.popup(450, 600, '${pageContext.request.contextPath}/tickler/ForwardDemographicTickler.do?docType=HL7&docId=' + labid + '&demographic_no=' + demoid + '<%=ticklerAssignee%>&priority=<%=recallTicklerPriority%>&recall', 'ticklerRecall');
                            }
                        } else if (action === 'ticklerLab') {
                            console.log("Setting lab Tickler. Labid: " + labid + " Demoid: " + demoid);
                            demoid = json.demoId;
                            if (demoid != null && demoid.length > 0) {
                                window.popup(450, 600, '${pageContext.request.contextPath}/tickler/ForwardDemographicTickler.do?docType=HL7&docId=' + labid + '&demographic_no=' + demoid, 'tickler')
                            }
                        } else if (action === 'addComment') {
                            console.log("Adding comment. Formid: " + formid + " labid: " + labid);
                            addComment(formid, labid);
                        }

                    } else {
                        console.log("Lab is NOT linked to demographic: " + success);
                        console.log("Processing action: " + action);

                        if (action === 'ackLab') {
                            if (confirmAckUnmatched()) {
                                document.getElementById("labStatus_" + labid).value = "A";
                                updateStatus(formid, labid);
                            } else {
                                matchMe();
                            }

                        } else {
                            alert("Please relate lab to a patient");
                            matchMe();
                        }
                    }
                }
            });
        }

        function confirmAck() {
            <% if (props.getProperty("confirmAck", "").equals("yes")) { %>
            return confirm('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.index.msgConfirmAcknowledge"/>');
            <% } else { %>
            return true;
            <% } %>
        }

        function confirmCommentUnmatched() {
            return confirm('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.index.msgConfirmAcknowledgeUnmatched"/>');
        }

        function confirmAckUnmatched() {
            return confirm('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.index.msgConfirmAcknowledgeUnmatched"/>');
        }

        function unlinkDemographic(labNo) {
            var reason = "Incorrect demographic";
            reason = prompt('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.msgUnlink"/>', reason);

            //must include reason
            if (reason == null || reason.length === 0) {
                return false;
            }

            var urlStr = '<%=request.getContextPath()%>' + "/lab/CA/ALL/UnlinkDemographic.do";
            var dataStr = "reason=" + encodeURIComponent(reason) + "&labNo=" + encodeURIComponent(labNo) + "&CSRF-TOKEN=" + encodeURIComponent(getCsrfToken());
            fetch(urlStr, {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: dataStr
            })
            .then(function(response) { return response.json(); })
            .then(function(data) {
                if (data.success) {
                    // refresh the opening page with new results
                    top.opener.location.reload();
                    // refresh the lab display page and offer dialog to rematch.
                    window.location.reload();
                }
            });
        }

        function addComment(formid, labid) {
            var url = '<%=request.getContextPath()%>' + "/oscarMDS/UpdateStatus.do?method=addComment";

            var labStatusEl = document.getElementById("labStatus_" + labid);
            if (labStatusEl && labStatusEl.value === "") {
                labStatusEl.value = "N";
            }

            var formEl = document.getElementById(formid);
            if (!formEl) {
                console.error("Form not found: " + formid);
                return;
            }
            var params = new URLSearchParams(new FormData(formEl));
            if (!params.has('CSRF-TOKEN')) { params.append('CSRF-TOKEN', getCsrfToken()); }
            console.log(url);
            console.log(params.toString());
            fetch(url, {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: params.toString()
            })
            .then(function() {
                window.location.reload();
            });
        }
        function submitLabel(lblval, segmentID) {
            var ackForm = document.forms['acknowledgeForm_' + segmentID];
            var labForm = document.forms['labLabelForm_' + segmentID];
            if (ackForm && ackForm.label && labForm && labForm.label) {
                let newlabelvalue = ackForm.label.value;
                if (newlabelvalue.length > 1) {
                    labForm.label.value = newlabelvalue;
                }
            }
        }

    </script>

</head>

<body onLoad="javascript:matchMe();">

<!-- form forwarding of the lab -->
<%
    for (int idx = 0; idx < segmentIDs.length; ++idx) {

        if (remoteFacilityIdString == null) {
            ackList = AcknowledgementData.getAcknowledgements(segmentID);
            segmentID = segmentIDs[idx];
            handler = handlers.get(idx);
        }

        boolean notBeenAcked = ackList.size() == 0;
        boolean ackFlag = false;
        String labStatus = "";
        if (ackList != null) {
            for (int i = 0; i < ackList.size(); i++) {
                ReportStatus reportStatus = ackList.get(i);
                if (providerNo.equals(reportStatus.getOscarProviderNo())) {
                    labStatus = reportStatus.getStatus();
                    if (labStatus.equals("A")) {
                        ackFlag = true;//lab has been ack by this providers.
                        break;
                    }
                }
            }
        }

        Hl7TextInfoDao hl7TextInfoDao = (Hl7TextInfoDao) SpringUtils.getBean(Hl7TextInfoDao.class);
        int lab_no = Integer.parseInt(segmentID);
        Hl7TextInfo hl7Lab = hl7TextInfoDao.findLabId(lab_no);
        String label = "";
        if (hl7Lab != null && hl7Lab.getLabel() != null) label = hl7Lab.getLabel();

        String ackLabFunc;
        if (skipComment) {
            ackLabFunc = "handleLab('acknowledgeForm_" + segmentID + "','" + segmentID + "','ackLab');";
        } else {
            ackLabFunc = "getComment('ackLab', " + segmentID + ");";
        }

%>
<script type="text/javascript">

    document.addEventListener('DOMContentLoaded', function () {
        var btn = document.getElementById('createLabel_<%=Encode.forJavaScript(segmentID)%>');
        if (btn) {
            btn.addEventListener('click', function () {
                var labNo = document.getElementById('labNum_<%=Encode.forJavaScript(segmentID)%>');
                var accNum = document.getElementById('accNum');
                var labelInput = document.getElementById('label_<%=Encode.forJavaScript(segmentID)%>');
                var params = new URLSearchParams();
                if (labNo) params.append('lab_no', labNo.value);
                if (accNum) params.append('accessionNum', accNum.value);
                if (labelInput) params.append('label', labelInput.value);
                params.append('ajaxcall', 'true');
                var csrfEl = document.querySelector('input[name="CSRF-TOKEN"]');
                if (csrfEl) params.append('CSRF-TOKEN', csrfEl.value);
                fetch('<%=request.getContextPath()%>/lab/CA/ALL/createLabLabel.do', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                    body: params.toString()
                });
                var spanI = document.querySelector('#labelspan_<%=Encode.forJavaScript(segmentID)%> i');
                if (spanI && labelInput) spanI.textContent = labelInput.value;
                var ackForm = document.forms['acknowledgeForm_<%=Encode.forJavaScript(segmentID)%>'];
                if (ackForm && ackForm.label) ackForm.label.value = '';
            });
        }
    });

    var _in_window = <%= request.getParameter("inWindow") == null || "true".equals(request.getParameter("inWindow")) %>;
    var contextpath = "<%=request.getContextPath()%>";

</script>

<script>
    //first check to see if lab is linked, if it is, we can send the demographicNo to the macro
    function runMacro(name, formid, closeOnSuccess) {
        var url = '<%=request.getContextPath()%>/documentManager/inboxManage.do';
        var data = 'method=isLabLinkedToDemographic&labid=<%= Encode.forJavaScript(segmentID) %>&CSRF-TOKEN=' + encodeURIComponent(getCsrfToken());
        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: data
        })
        .then(function(response) { return response.json(); })
        .then(function(json) {
            if (json != null) {
                var success = json.isLinkedToDemographic;
                var demoid = '';
                if (success) {
                    demoid = json.demoId;
                }
                runMacroInternal(name, formid, closeOnSuccess, demoid);
            }
        });
    }

    function runMacroInternal(name, formid, closeOnSuccess, demographicNo) {
        var url = '<%=request.getContextPath()%>' + "/oscarMDS/RunMacro.do?name=" + name + (demographicNo.length > 0 ? "&demographicNo=" + demographicNo : "");
        var formEl = document.getElementById(formid);
        var params = new URLSearchParams(new FormData(formEl));
        if (!params.has('CSRF-TOKEN')) { params.append('CSRF-TOKEN', getCsrfToken()); }

        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: params.toString()
        })
        .then(function() {
            if (closeOnSuccess) {
                window.close();
            }
        });
    }
</script>

<div id="lab_<%= Encode.forHtmlAttribute(segmentID) %>">

        <c:set var="hasDuplicateInfo" value="${not empty duplicateOfLab}" />
        <c:set var="hasMissingTests" value="${not empty missingTests}" />
        <c:set var="showModal" value="${hasDuplicateInfo or hasMissingTests}" />



    <form name="reassignForm_<%= Encode.forHtmlAttribute(segmentID) %>" method="post" action="<%= request.getContextPath() %>/lab/CA/ALL/Forward.do">
        <input type="hidden" name="flaggedLabs" value="<%= Encode.forHtmlAttribute(segmentID) %>">
        <input type="hidden" name="selectedProviders" value="">
        <input type="hidden" name="favorites" value="">
        <input type="hidden" name="labType" value="HL7">
        <input type="hidden" name="labType<%= Encode.forHtmlAttribute(segmentID) %>HL7" value="imNotNull">
        <input type="hidden" id="providerNo_<%= Encode.forHtmlAttribute(segmentID) %>" name="providerNo"
               value="<%= Encode.forHtmlAttribute(providerNo) %>">
    </form>

    <form name="labLabelForm_<%= Encode.forHtmlAttribute(segmentID) %>" method='POST'
          action="<%=request.getContextPath()%>/lab/CA/ALL/createLabLabel.do">
        <input type="hidden" id="labellabNum_<%= Encode.forHtmlAttribute(segmentID) %>" name="lab_no" value="<%=lab_no%>">
        <input type="hidden" id="label_<%= Encode.forHtmlAttribute(segmentID) %>" name="label" value="<%= Encode.forHtmlAttribute(label) %>">
    </form>

    <form name="acknowledgeForm_<%= Encode.forHtmlAttribute(segmentID) %>"
          id="acknowledgeForm_<%= Encode.forHtmlAttribute(segmentID) %>" method="post" onsubmit="javascript:void(0);"
          action="javascript:void(0);">

        <table style="width:100%;">
            <tr>
                <td style="vertical-align:top;">
                    <table class="MainTableTopRowRightColumn" style="width:100%;">
                        <tr>
                            <td>
                                <input type="hidden" name="segmentID"
                                       value="<%= Encode.forHtmlAttribute(segmentID) %>">
                                <input type="hidden" name="multiID" value="<%= Encode.forHtmlAttribute(multiLabId) %>">
                                <input type="hidden" name="providerNo" id="providerNo"
                                       value="<%= Encode.forHtmlAttribute(providerNo) %>">
                                <input type="hidden" name="status" value="<%=Encode.forHtmlAttribute(labStatus)%>"
                                       id="labStatus_<%=Encode.forHtmlAttribute(segmentID)%>">
                                <input type="hidden" name="comment" value="">
                                <input type="hidden" name="labType" value="HL7">
                                <%
                                    if (!ackFlag) {
                                %>

                                <%
                                    UserPropertyDAO upDao = SpringUtils.getBean(UserPropertyDAO.class);
                                    UserProperty up = upDao.getProp(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), UserProperty.LAB_MACRO_JSON);

                                %>
<div class="d-flex align-items-center input-group-sm">
                                <%
                                    if (up != null && !StringUtils.isEmpty(up.getValue())) {

                                %>
                                <div class="dropdown">
                                    <button class="btn btn-outline-primary btn-sm dropdown-toggle" type="button" id="dropdownMenuButton1" data-bs-toggle="dropdown" aria-expanded="false">Macros</button>
                                    <ul class="dropdown-menu" aria-labelledby="dropdownMenuButton1">
                                        <%
                                            try {
                                                ObjectMapper mapper = new ObjectMapper();
                                                JsonNode macros = mapper.readTree(up.getValue());
                                                if (macros != null && macros.isArray()) {
                                                    for (int x = 0; x < macros.size(); x++) {
                                                        JsonNode macro = macros.get(x);
                                                        String name = macro.get("name").asText();
                                                        boolean closeOnSuccess = macro.has("closeOnSuccess") && macro.get("closeOnSuccess").asBoolean();

                                        %><li><a class="dropdown-item" href="javascript:void(0);"
                                             onClick="runMacro('<%=Encode.forJavaScript(name)%>','acknowledgeForm_<%=Encode.forJavaScript(segmentID)%>',<%=closeOnSuccess%>)"><%=Encode.forHtml(name)%>
                                    </a></li><%
                                                }
                                            }
                                        } catch (Exception e) {
                                            MiscUtils.getLogger().warn("Invalid JSON for lab macros", e);
                                        }
                                    %>
                                      </ul>
                                    </div>
                                <% } %>

                                <input type="button" class="btn btn-sm btn-outline-primary"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.btnAcknowledge"/>"
                                       onclick="<%=ackLabFunc%>">
                                <% } %>
                                <input type="button" class="btn btn-sm btn-outline-secondary" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.btnComment"/>"
                                       onclick="return getComment('addComment',<%=Encode.forJavaScript(segmentID)%>);">
                                <input type="button" class="btn btn-sm btn-outline-secondary"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.index.btnForward"/>"
                                       onClick="ForwardSelectedRows(<%=Encode.forJavaScript(segmentID)%> + ':HL7', '', '')">
                                <input type="button" class="btn btn-sm btn-outline-secondary" value=" <fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/> "
                                       onClick="window.close()">
                                <input type="button" class="btn btn-sm btn-outline-secondary" value=" <fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnPrint"/> "
                                       onClick="printPDF('<%=Encode.forJavaScript(segmentID)%>')">

                                <input type="button" class="btn btn-sm btn-outline-secondary" value="Msg"
                                       onclick="handleLab('','<%=Encode.forJavaScript(segmentID)%>','msgLab');">
                                <input type="button" class="btn btn-sm btn-outline-secondary" value="Tickler"
                                       onclick="handleLab('','<%=Encode.forJavaScript(segmentID)%>','ticklerLab');">
                                <input type="button" class="btn btn-sm btn-outline-secondary" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.btnUnlinkDemo"/>"
                                       onclick="unlinkDemographic(<%=Encode.forJavaScript(segmentID)%>)">

                                <% if (searchProviderNo != null) { // null if we were called from e-chart%>
                                <input type="button" class="btn btn-sm btn-outline-secondary" value=" <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.btnEChart"/>"
                                       onClick="popupStart(360, 680, '<%= request.getContextPath() %>/oscarMDS/SearchPatient.do?labType=HL7&segmentID=<%= Encode.forJavaScript(segmentID) %>&name=<%=java.net.URLEncoder.encode(handler.getLastName()+", "+handler.getFirstName(), StandardCharsets.UTF_8)%>', 'encounter')">
                                <% } %>
                                <input type="button" class="btn btn-sm btn-outline-secondary" value="Req# <%=Encode.forHtmlAttribute(reqTableID)%>" title="Link to Requisition"
                                       onclick="linkreq('<%=Encode.forJavaScript(segmentID)%>','<%=reqID%>');">


                                <% if (bShortcutForm) { %>
                                <input type="button" class="btn btn-sm btn-outline-secondary" value="<%=Encode.forHtmlAttribute(formNameShort)%>"
                                       onClick="popupStart(700, 1024, '/form/forwardshortcutname.do?formname=<%=formName%>&demographic_no=<%=demographicID%>', '<%=Encode.forJavaScript(formNameShort)%>')">
                                <% } %>
                                <% if (bShortcutForm2) { %>
                                <input type="button" class="btn btn-sm btn-outline-secondary" value="<%=formName2Short%>"
                                       onClick="popupStart(700, 1024, '/form/forwardshortcutname.do?formname=<%=formName2%>&demographic_no=<%=demographicID%>', '<%=formName2Short%>')">
                                <% } %>

                                <% if (recall) {%>
                                <input type="button" class="btn btn-sm btn-outline-secondary" value="Recall"
                                       onclick="handleLab('','<%=Encode.forJavaScript(segmentID)%>','msgLabRecall');">
                                <%}%>
                                <%
                                    if (remoteLabKey == null || remoteLabKey.isEmpty()) {
                                %>


                                <span style="font-size:10px; font-style:italic;">Next Appointment: <oscar:nextAppt
                                        demographicNo="<%=demographicID%>"/></span>
                                <% if (!label.equals(null) && !label.equals("")) { %>
                                <button type="button" class="btn btn-sm btn-outline-secondary" id="createLabel_<%= Encode.forHtmlAttribute(segmentID) %>"
                                        value="Label"
                                        onclick="submitLabel(this, '<%=Encode.forJavaScript(segmentID)%>');">Label
                                </button>
                                <%} else { %>
                                <button type="button" class="btn btn-sm btn-outline-secondary" id="createLabel_<%= Encode.forHtmlAttribute(segmentID) %>"
                                        value="Label"
                                        onclick="submitLabel(this, '<%=Encode.forJavaScript(segmentID)%>');">Label
                                </button>
                                <%} %>
                                <input type="hidden" id="labNum_<%=Encode.forHtmlAttribute(segmentID) %>" name="lab_no"
                                       value="<%=lab_no%>">
                                <input type="text" class="form-control form-control-sm" style="width: 140px; margin-top: 0px;" id="acklabel_<%= Encode.forHtmlAttribute(segmentID) %>" name="label"
                                       value="">

                                <% String labelval = "";
                                    if (label != "" && label != null) {
                                        labelval = label;
                                    } else {
                                        labelval = "(not set)";

                                    } %>
                                <span id="labelspan_<%= Encode.forHtmlAttribute(segmentID) %>"
                                      ><i><%= Encode.forHtml(labelval) %> </i></span>

                                <% } %>
</div>
                            </td>
                        </tr>
                        <tr>
                            <td>


                            </td>

                        </tr>
                    </table>
                    <table style="width:100%; background-color:#9999CC;">
                        <%
                            if (multiLabId != null) {
                                String[] multiID = multiLabId.split(",");
                                if (multiID.length > 1) {
                        %>
                        <tr>
                            <td class="Cell" colspan="2" >
                                <div class="Field2">
                                    Version:&#160;&#160;
                                    <%
                                        for (int i = 0; i < multiID.length; i++) {
                                            if (multiID[i].equals(segmentID)) {
                                    %>v<%= i + 1 %>&#160;<%
                                } else {
                                    if (searchProviderNo != null) { // null if we were called from e-chart
                                %><a href="${pageContext.request.contextPath}/lab/CA/ALL/labDisplay.jsp?segmentID=<%=multiID[i]%>&multiID=<%=multiLabId%>&providerNo=<%= providerNo %>&searchProviderNo=<%= searchProviderNo %>">v<%= i + 1 %>
                                </a>&#160;<%
                                } else {
                                %><a href="${pageContext.request.contextPath}/lab/CA/ALL/labDisplay.jsp?segmentID=<%=multiID[i]%>&multiID=<%=multiLabId%>&providerNo=<%= providerNo %>">v<%= i + 1 %>
                                </a>&#160;<%
                                            }
                                        }
                                    }
//                                                if( multiID.length > 1 ) {
                                    if (searchProviderNo != null) { // null if we were called from e-chart
                                %><a href="${pageContext.request.contextPath}/lab/CA/ALL/labDisplay.jsp?segmentID=<%= Encode.forHtmlAttribute(segmentID) %>&multiID=<%=multiLabId%>&providerNo=<%= providerNo %>&searchProviderNo=<%= searchProviderNo %>&all=true">All</a>&#160;<%
                                } else {
                                %><a href="${pageContext.request.contextPath}/lab/CA/ALL/labDisplay.jsp?segmentID=<%= Encode.forHtmlAttribute(segmentID) %>&multiID=<%=multiLabId%>&providerNo=<%= providerNo %>&all=true">All</a>&#160;<%
                                    }
//                                                }
                                %>
                                </div>
                            </td>
                        </tr>
                        <%
                                }
                            }
                        %>
                        <tr>
                            <td style="width:66%;"  class="Cell">
                                <div class="Field2">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formDetailResults"/>
                                </div>
                            </td>
                            <td style="width:33%;"  class="Cell">
                                <div class="Field2">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formResultsInfo"/>
                                </div>
                            </td>
                        </tr>
                        <tr>
                            <td style="background-color:white; vertical-align:top; ">
                                <table style="width:100%; vertical-align:top;">
                                    <tr style="vertical-align:top; ">
                                        <td style="width:33%; text-align:left; vertical-align:top; ">
                                            <table style="width:100%; vertical-align:top; <% if (!isLinkedToDemographic) { %>
                                                   background-color:orange;<% } %>"
                                                   id="DemoTable<%= Encode.forHtmlAttribute(segmentID) %>">
                                                <tr>
                                                    <td style="vertical-align:top;  text-align:left;">
                                                        <table style="width:100%; vertical-align:top; ">
                                                            <tr>
                                                                <td style="white-space:nowrap;">
                                                                    <div class="FieldData">
                                                                        <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formPatientName"/>: </strong>
                                                                    </div>
                                                                </td>
                                                                <td style="white-space:nowrap;">
                                                                    <div class="FieldData">
                                                                        <% if (searchProviderNo == null) { // we were called from e-chart%>
                                                                        <a href="javascript:window.close()">
                                                                                <% } else { // we were called from lab module%>
                                                                            <a href="javascript:popupStart(360, 680, '${pageContext.request.contextPath}/oscarMDS/SearchPatient.do?labType=HL7&segmentID=<%= Encode.forJavaScript(segmentID) %>&name=<%=java.net.URLEncoder.encode(handler.getLastName()+", "+handler.getFirstName(), StandardCharsets.UTF_8)%>', 'searchPatientWindow')">
                                                                                <% } %>
                                                                                <%=Encode.forHtml(handler.getPatientName())%>
                                                                            </a>
                                                                    </div>
                                                                </td>
                                                                <td colspan="2"></td>
                                                            </tr>
                                                            <tr>
                                                                <td style="white-space:nowrap;">
                                                                    <div class="FieldData">
                                                                        <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formDateBirth"/>: </strong>
                                                                    </div>
                                                                </td>
                                                                <td style="white-space:nowrap;">
                                                                    <div class="FieldData">
                                                                        <%=Encode.forHtml(handler.getDOB())%>
                                                                    </div>
                                                                </td>
                                                                <td colspan="2"></td>
                                                            </tr>
                                                            <tr>
                                                                <td style="white-space:nowrap;">
                                                                    <div class="FieldData">
                                                                        <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formAge"/>: </strong>
                                                                    </div>
                                                                </td>
                                                                <td style="white-space:nowrap;">
                                                                    <div class="FieldData">
                                                                        <%=Encode.forHtml(handler.getAge())%>
                                                                    </div>
                                                                </td>

                                                            </tr>
                                                            <tr>
                                                                <td style="white-space:nowrap;">
                                                                    <div class="FieldData">
                                                                        <strong>
                                                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formHealthNumber"/>
                                                                        </strong>
                                                                    </div>
                                                                </td>
                                                                <td style="white-space:nowrap;">
                                                                    <div class="FieldData">
                                                                        <%=Encode.forHtml(handler.getHealthNum())%>
                                                                    </div>
                                                                </td>
                                                                <td colspan="2"></td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td style="width:33%;vertical-align:top; ">
                                                        <table style="width:100%;vertical-align:top; ">
                                                            <tr>
                                                                <td style="white-space:nowrap;">
                                                                    <div class="FieldData">
                                                                        <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formHomePhone"/>: </strong>
                                                                    </div>
                                                                </td>
                                                                <td style="white-space:nowrap;">
                                                                    <div class="FieldData">
                                                                        <%=Encode.forHtml(handler.getHomePhone())%>
                                                                    </div>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td style="white-space:nowrap;">
                                                                    <div class="FieldData">
                                                                        <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formWorkPhone"/>: </strong>
                                                                    </div>
                                                                </td>
                                                                <td style="white-space:nowrap;">
                                                                    <div class="FieldData">
                                                                        <%=Encode.forHtml(handler.getWorkPhone())%>
                                                                    </div>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td style="white-space:nowrap;">
                                                                    <div class="FieldData">
                                                                        <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formSex"/>: </strong>
                                                                    </div>
                                                                </td>
                                                                <td style="text-align:left; white-space:nowrap;">
                                                                    <div class="FieldData">
                                                                        <%=Encode.forHtml(handler.getSex())%>
                                                                    </div>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td style="white-space:nowrap;">
                                                                    <div class="FieldData">
                                                                        <% if ("ExcellerisON".equals(handler.getMsgType())) { %>
                                                                        <strong>Reported by:</strong>
                                                                        <% } else { %>
                                                                        <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formPatientLocation"/>: </strong>
                                                                        <% } %>
                                                                    </div>
                                                                </td>
                                                                <td style="white-space:nowrap;">
                                                                    <div class="FieldData">
                                                                        <%=Encode.forHtml(handler.getPatientLocation())%>
                                                                    </div>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                            <td style="background-color:white; vertical-align:top; ">
                                <table style="width:100%;">
                                    <tr>
                                        <td>
                                            <div class="FieldData">
                                                <% if ("CLS".equals(handler.getMsgType())) { %>
                                                <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formDateServiceCLS"/>:</strong>
                                                <% } else { %>
                                                <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formDateService"/>:</strong>
                                                <% } %>
                                            </div>
                                        </td>
                                        <td>
                                            <div class="FieldData">
                                                <%=Encode.forHtml(handler.getServiceDate()) %>
                                            </div>
                                        </td>
                                    </tr>

                                    <% if ("ExcellerisON".equals(handler.getMsgType())) { %>
                                        <tr>
                                            <td>
                                                <div class="FieldData">
                                                    <strong>Reported on:</strong>
                                                </div>
                                            </td>
                                            <td>
                                                <div class="FieldData">
                                                    <%=Encode.forHtml(((ExcellerisOntarioHandler) handler).getReportStatusChangeDate())%>
                                                </div>
                                            </td>
                                        </tr>
                                    <% } %>

                                    <tr>
                                        <td>
                                            <div class="FieldData">
                                                <strong>Date of Request:</strong>

                                            </div>
                                        </td>
                                        <td>
                                            <div class="FieldData">
                                                <%=Encode.forHtml(handler.getRequestDate(0)) %>
                                            </div>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td>
                                            <div class="FieldData">
                                                <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formDateReceivedCLS"/>:</strong>

                                            </div>
                                        </td>
                                        <td>
                                            <div class="FieldData">
                                                <%= dateLabReceived %>
                                            </div>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td>
                                            <div class="FieldData">
                                                <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formReportStatus"/>:</strong>
                                            </div>
                                        </td>
                                        <td>
                                            <div class="FieldData">
                                                <%=Encode.forHtml(handler.getOrderStatus())%>
                                            </div>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td></td>
                                    </tr>
                                    <tr>
                                        <td style="white-space:nowrap;">
                                            <div class="FieldData">
                                                <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formClientRefer"/>:</strong>
                                            </div>
                                        </td>
                                        <td style="white-space:nowrap;">
                                            <div class="FieldData">
                                                <%=Encode.forHtml(handler.getClientRef())%>
                                            </div>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td>
                                            <div class="FieldData">
                                                <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formAccession"/>:</strong>
                                            </div>
                                        </td>
                                        <td>
                                            <div class="FieldData">
                                                <%=Encode.forHtml(handler.getAccessionNum())%>
                                            </div>
                                        </td>
                                    </tr>
                                    <% if (handler.getMsgType().equals("ExcellerisON") && !((ExcellerisOntarioHandler) handler).getAlternativePatientIdentifier().isEmpty()) { %>
                                    <tr>
                                        <td>
                                            <div class="FieldData">
                                                <strong>Reference #:</strong>
                                            </div>
                                        </td>
                                        <td>
                                            <div class="FieldData">
                                                <%=Encode.forHtml(((ExcellerisOntarioHandler) handler).getAlternativePatientIdentifier())%>
                                            </div>
                                        </td>
                                    </tr>
                                    <% } %>
                                    <%
                                        String comment =Encode.forHtml(handler.getNteForPID());
                                        if (comment != null && !comment.equals("")) {%>
                                    <tr>
                                        <td>
                                            <div class="FieldData">
                                                <strong>Remarks:</strong>
                                            </div>
                                        </td>
                                        <td>
                                            <div class="FieldData">
                                                <%=Encode.forHtml(comment) %>
                                            </div>
                                        </td>
                                    </tr>
                                    <%} %>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td style="background-color:white;" colspan="2">
                                <table style="width:100%; border-color:#CCCCCC;">
                                    <tr>
                                        <td style="background-color:white;">
                                            <div class="FieldData">
                                                <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formRequestingClient"/>: </strong>
                                                <%=Encode.forHtml(handler.getDocName())%>
                                            </div>
                                        </td>
                                        <%-- <td style="background-color:white;">
                                <div class="FieldData">
                                    <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formReportToClient"/>: </strong>
                                        <%= No admitting Doctor for CML messages%>
                                </div>
                            </td> --%>
                                        <td style="background-color:white; text-align:right">
                                            <div class="FieldData">
                                                <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formCCClient"/>: </strong>
                                                <%=Encode.forHtml(handler.getCCDocs())%>

                                            </div>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td style="background-color:white; padding:0px; text-align:center;" colspan="2">
                                <%
                                    String[] multiID = multiLabId.split(",");
                                    boolean isTickler = false;

                                    for (int mcount = 0; mcount < multiID.length; mcount++) {
                                        if (demographicID != null && !demographicID.equals("")) {

                                            List<Tickler> LabTicklers = null;
                                            if (demographicID != null) {
                                                LabTicklers = ticklerManager.getTicklerByLabIdAnyProvider(loggedInInfo, Integer.valueOf(multiID[mcount]), Integer.valueOf(demographicID));
                                            }

                                            if (LabTicklers != null && LabTicklers.size() > 0) {
                                    if(!isTickler){
                                        // Note: Opening divs (ticklerWrap, ticklerDisplay) are closed at lines 1464-1465
%>
                            <div id="ticklerWrap" class="DoNotPrint">
							    <h4 style="color:#fff"><a href="javascript:void(0)" id="open-ticklers" onclick="showHideItem('ticklerDisplay')">View Ticklers</a> Linked to this Lab</h4>
                                <div id="ticklerDisplay" style="display:none">
<%

                                        isTickler = true;
                                    }
                                            String flag;
                                            String ticklerClass;
                                            String ticklerStatus;
                                            for (Tickler tickler : LabTicklers) {

                                                ticklerStatus = tickler.getStatus().toString();
                                                if (!ticklerStatus.equals("C") && tickler.getPriority().toString().equals("High")) {
                                                    flag = "<span style='color:red'>&#9873;</span>";
                                                } else if (ticklerStatus.equals("C") && tickler.getPriority().toString().equals("High")) {
                                                    flag = "<span>&#9873;</span>";
                                                } else {
                                                    flag = "";
                                                }

                                                if (ticklerStatus.equals("C")) {
                                                    ticklerClass = "completedTickler";
                                                } else {
                                                    ticklerClass = "";
                                                }
                                        %>
                                        <div style="text-align:left;background-color:#fff;padding:5px; width:600px;"
                                             class="<%=ticklerClass%>">
                                            <table style="width:100%;">
                                                <tr>
                                                    <td><b>Priority:</b> <%=flag%> <%=tickler.getPriority()%>
                                                    </td>
                                                    <td><b>Service Date:</b> <%=tickler.getServiceDate()%>
                                                    </td>
                                                    <td><b>Assigned
                                                        To:</b> <%=tickler.getAssignee() != null ? Encode.forHtml(tickler.getAssignee().getLastName() + ", " + tickler.getAssignee().getFirstName()) : "N/A"%>
                                                    </td>
                                                    <td style="width:90px;">
                                                        <b>Status:</b> <%=ticklerStatus.equals("C") ? "Completed" : "Active" %>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td colspan="4"><%=Encode.forHtml(tickler.getMessage())%>
                                                    </td>
                                                </tr>
                                            </table>
                                        </div>
                                        <br>
                                        <%
                                            }

                                            }//no ticklers to display OR

                                        }
                                    }
    if(isTickler){
        // Note: Closing divs here (ticklerDisplay, ticklerWrap) were opened at lines 1407-1408
    %>
                                </div><!-- end ticklerDisplay-->
                            </div><!-- end ticklerWrap-->
    <%
    }
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_tickler" rights="r">
    <% if (numTickler > 0 && !searchProviderNo.isEmpty() ) {%>
        <table style="width:100%;">
            <tr>
                <td class="alert alert-info alert-dismissible fade show"><button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
                    <strong>INFO</strong> The following <%=numTickler%> <a class="alert-link" onclick="popup(450, 1200, '<%=request.getContextPath()%>/tickler/ticklerDemoMain.jsp?demoview=<%=Encode.forUriComponent(demographicID)%>', 'openTicklers')">ticklers</a> are marked pending:<%=tickler_note%>
                </td>
            </tr>
         </table>
    <% } %>
</security:oscarSec>

            <c:if test="${hasDuplicateInfo}">
                <table style="width:100%; height:20px">
                    <tr>
                        <td class="alert alert-info alert-dismissible fade show"><button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
                                    <!-- Duplicate Information Section -->
                                    <div class="info-section">
                                        <p><b>Warning:</b> You are viewing a version of a lab result that is a duplicate of previously received version <b>v<c:out value="${duplicateOfLab}" /></b>.</p>
                                    </div>
                        </td>
                    </tr>
                </table>
            </c:if>
<%

                                    ReportStatus report;
                                    boolean startFlag = false;
                                    for (int j = multiID.length - 1; j >= 0; j--) {
                                        ackList = AcknowledgementData.getAcknowledgements(multiID[j]);
                                        if (multiID[j].equals(segmentID))
                                            startFlag = true;
                                        if (startFlag) {
                                            //if (ackList.size() > 0){{
                                %>
                                <table style="width:100%" >
                                    <tr>
                                        <% if (multiID.length > 1) { %>
                                        <td style="background-color:white; text-align:center; width:20%">
                                            <div class="FieldData">
                                                <b>Version:</b> v<%= j + 1 %>
                                            </div>
                                        </td>
                                        <td style="background-color:white; text-align:left; width:80%;" >
                                                <% }else{ %>
                                        <td style="background-color:white; text-align:center;">
                                            <% } %>
                                            <div class="FieldData">
                                                <!--center-->
                                                <% for (int i = 0; i < ackList.size(); i++) {
                                                    report = ackList.get(i); %>
                                                <%= Encode.forHtml(report.getProviderName()) %> :

                                                <% String ackStatus = report.getStatus();
                                                    if (ackStatus.equals("A")) {
                                                        ackStatus = "Acknowledged";
                                                    } else if (ackStatus.equals("F")) {
                                                        ackStatus = "Filed but not Acknowledged";
                                                    } else {
                                                        ackStatus = "Not Acknowledged";
                                                    }
                                                %>
                                                <span style="color:red"><%= ackStatus %>
                                                </span>
                                                <% if (ackStatus.equals("Acknowledged")) { %>
                                                <%= report.getTimestamp() %>,
                                                <% } %>
                                                <span id="<%=report.getOscarProviderNo() + "_" + segmentID%>commentLabel"><%=report.getComment() == null || report.getComment().equals("") ? "no comment" : "comment : "%></span><span
                                                    id="<%=report.getOscarProviderNo() + "_" + segmentID%>commentText"><%=report.getComment() == null ? "" : Encode.forHtml(report.getComment())%></span>
                                                <br>
                                                <% }
                                                    if (ackList.size() == 0) {
                                                %><span style="color:red">N/A</span><%
                                                }
                                            %>
                                                <!--/center-->
                                            </div>
                                        </td>
                                    </tr>
                                </table>

                                <%
                                            //}
                                        }
                                    }
                                %>

                            </td>
                        </tr>
                    </table>

                    <% int i = 0;
                        int j = 0;
                        int k = 0;
                        int l = 0;
                        int linenum = 0;
                        String highlight = "silver";

                        ArrayList<String> headers = handler.getHeaders();
                        int OBRCount = handler.getOBRCount();

                        %>
        <%

            for (i = 0; i < headers.size(); i++) {
                linenum = 0;
                boolean isUnstructuredDoc = false;
                boolean isVIHARtf = false;
                boolean isSGorCDC = false;

                //Checks to see if the PATHL7 lab is an unstructured document, a VIHA RTF pathology report, or if the patient location is SG/CDC
                //labs that fall into any of these categories have certain requirements per Excelleris
                if (handler.getMsgType().equals("PATHL7")) {
                    isUnstructuredDoc = ((PATHL7Handler) handler).unstructuredDocCheck(headers.get(i));
                    isVIHARtf = ((PATHL7Handler) handler).vihaRtfCheck(headers.get(i));
                    if (handler.getPatientLocation().equals("SG") || handler.getPatientLocation().equals("CDC")) {
                        isSGorCDC = true;
                    }

                } else if (handler.getMsgType().equals("CLS")) {
                    isUnstructuredDoc = ((CLSHandler) handler).isUnstructured();
                }


                if (handler.getMsgType().equals("MEDITECH")) {
                    isUnstructuredDoc = ((MEDITECHHandler) handler).isUnstructured();
                } %>

        <table style="page-break-inside:avoid; width:100%;">
            <tr>
                <td colspan="4">&nbsp;</td>
            </tr>
            <tr>
                <td style="background-color:#FFCC00; width:300px; vertical-align: bottom">
                    <div class="Title2">
                        <%=Encode.forHtml(headers.get(i))%>
                    </div>
                </td>
                <%--<td style="text-align:right" style="background-color:#FFCC00; width:100px">&nbsp;</td>--%>
                <td style="width:9px">&nbsp;</td>
                <td style="width:9px">&nbsp;</td>
                <td>&nbsp;</td>
            </tr>
        </table>
        <% if ((handler.getMsgType().equals("MEDITECH") && isUnstructuredDoc) ||
                (handler.getMsgType().equals("MEDITECH") && ((MEDITECHHandler) handler).isReportData())) { %>
        <table style="width:100%;border-collapse:collapse; border-color:#9966FF;"
               id="tblDiscs2">
            <tr>
                <td colspan="4" style="padding-left:10px;">

                        <%} else if( isUnstructuredDoc){%>
                    <table style="width:100%; border-color:#9966FF;" id="tblDiscs3">

                        <tr class="Field2">

                            <td style="width:20%;  vertical-align:bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formTestName"/></td>
                            <td style="width:60%;  vertical-align:bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formResult"/></td>
                            <% if ("CLS".equals(handler.getMsgType())) { %>
                            <td style="width:20%;  vertical-align:bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formDateTimeCompletedCLS"/></td>
                            <% } else { %>
                            <td style="width:20%;  vertical-align:bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formDateTimeCompleted"/></td>
                            <% } %>

                            <td style="width:31%;  vertical-align:bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formTestName"/></td>
                            <td style="width:31%;  vertical-align:bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formResult"/></td>
                            <td style="width:31%;  vertical-align:bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formDateTimeCompleted"/></td>

                        </tr>
                            <%
						} else {%>
                        <table style="width:100%; border-color:#9966FF;" id="tblDiscs4">

                                <% if( handler instanceof MEDITECHHandler && "MIC".equals( ((MEDITECHHandler) handler).getSendingApplication() ) ) { %>
                            <tr>
                                <td colspan="9"></td>
                            </tr>
                            <tr>
                                <td style="padding-left:20px;font-weight:bold; vertical-align:top; text-align:left;">SPECIMEN
                                    SOURCE:
                                </td>
                                <td style="font-weight:bold;vertical-align:top;text-align:left;"
                                    colspan="7"><%=Encode.forHtml(((MEDITECHHandler) handler).getSpecimenSource(i))%>
                                </td>
                            </tr>
                            <tr>
                                <td style="padding-left:20px;font-weight:bold;vertical-align:top;">SPECIMEN
                                    DESCRIPTION:
                                </td>
                                <td style="font-weight:bold;vertical-align:top;text-align:left;"
                                    colspan="7"><%=Encode.forHtml(((MEDITECHHandler) handler).getSpecimenDescription(i))%>
                                </td>
                            </tr>
                            <tr>
                                <td colspan="9"></td>
                            </tr>
                                <% }%>

                            <tr class="Field2">
                                <td style="width:25%;  vertical-align:bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formTestName"/></td>
                                <td style="width:15%;  vertical-align:bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formResult"/></td>
                                <td style="width:5%;  vertical-align:bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formAbn"/></td>
                                <td style="width:15%;  vertical-align:bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formReferenceRange"/></td>
                                <td style="width:10%;  vertical-align:bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formUnits"/></td>
                                <% if ("CLS".equals(handler.getMsgType())) { %>
                                <td style="width:15%;  vertical-align:bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formDateTimeCompletedCLS"/></td>
                                <% } else { %>
                                <td style="width:15%;  vertical-align:bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formDateTimeCompleted"/></td>
                                <% } %>
                                <td style="width:6%;  vertical-align:bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formNew"/></td>
                                <td style="width:6%;  vertical-align:bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formAnnotate"/></td>
                                <% if ("ExcellerisON".equals(handler.getMsgType())) { %>
                                <td style="width:6%;  vertical-align:bottom" class="Cell">License #</td>
                            </tr>
                                <% } %>
            <!--</tr>-->

            <%
                } // end else / if isUnstructured

                for (j = 0; j < OBRCount; j++) {

                    String lastObxSetId = "0";
                    boolean obrFlag = false;
                    int obxCount = handler.getOBXCount(j);

                    if (handler.getMsgType().equals("ExcellerisON") && handler.getObservationHeader(j, 0).equals(headers.get(i))) {
                        String orderRequestStatus = ((ExcellerisOntarioHandler) handler).getOrderStatus(j);
                        int obrCommentCount = handler.getOBRCommentCount(j);
                        if (orderRequestStatus.equals(ExcellerisOntarioHandler.OrderStatus.DELETED.getDescription())) { continue; }

                        if (obxCount > 0 || !orderRequestStatus.isEmpty() || obrCommentCount > 0) {
                            obrFlag = true;
                            %>
                                <tr style="background-color:<%=(linenum % 2 == 1 ? highlight : "white")%>;" >
                                    <td style="vertical-align:top;  text-align:left;"><span style="font-size:16px;font-weight: bold;"><%=Encode.forHtml(handler.getOBRName(j))%></span></td>
                                    <td colspan="1"><%=Encode.forHtml(orderRequestStatus)%></td>
                                </tr>
                            <%
                        }
                    }

                    for (k = 0; k < obxCount; k++) {

                        String obxName = handler.getOBXName(j, k);
                        String nameLong = handler.getOBXNameLong(j, k);
                        if (StringUtils.isNotEmpty(nameLong)) {
                            nameLong = ": " + nameLong;
                        }

                        boolean isAllowedDuplicate = false;
                        if (handler.getMsgType().equals("PATHL7")) {
                            //if the obxidentifier and result name are any of the following, they must be displayed (they are the Excepetion to Excelleris TX/FT duplicate result name display rules)
                            if ((handler.getOBXName(j, k).equals("Culture") && handler.getOBXIdentifier(j, k).equals("6463-4")) ||
                                    (handler.getOBXName(j, k).equals("Organism") && (handler.getOBXIdentifier(j, k).equals("X433") || handler.getOBXIdentifier(j, k).equals("X30011")))) {
                                isAllowedDuplicate = true;
                            }
                        }
                        boolean b1 = false, b2 = false, b3 = false;

                        boolean fail = true;
                        try {
                            b1 = !handler.getOBXResultStatus(j, k).equals("DNS");
                            b2 = !obxName.equals("");
                            String currheader = headers.get(i);
                            String obsHeader = handler.getObservationHeader(j, k);
                            b3 = handler.getObservationHeader(j, k).equals(headers.get(i));
                            fail = false;
                        } catch (Exception e) {
                            //logger.info("ERROR :"+e);
                        }

                        if (handler.getMsgType().equals("MEDITECH")) {
                            b2 = true;
                        }
                        if (handler.getMsgType().equals("EPSILON")) {
                            b2 = true;
                            b3 = true; //Because Observation header can never be the same as the header. Observation header = OBX-4.2 and header= OBX-4.1
                        } else if (handler.getMsgType().equals("CML") || handler.getMsgType().equals("HHSEMR")) {
                            b2 = true;
                        }

                        if (!fail && b1 && b2 && b3) { // <<--  DNS only needed for MDS messages

                            String obrName = handler.getOBRName(j);
                            b1 = !obrFlag && !obrName.equals("");
                            b2 = !(obxName.contains(obrName));
                            b3 = !(obxCount < 2 && !isUnstructuredDoc);
                            if (b1 && b2 && b3 && !handler.getMsgType().equals("ExcellerisON")) {
            %>
            <tr style="background-color:<%=(linenum % 2 == 1 ? highlight : "white")%>;">
                <td style="vertical-align:top;  text-align:left;"><span style="font-size:16px;font-weight: bold;"><%=Encode.forHtml(obrName)%></span></td>
                <td colspan="6">&nbsp;</td>
            </tr>
            <%
                    obrFlag = true;
                }

                String lineClass = "NormalRes";
                String abnormal = handler.getOBXAbnormalFlag(j, k);
                if (abnormal != null && abnormal.startsWith("L")) {
                    lineClass = "HiLoRes";
                } else if (abnormal != null && (abnormal.equals("A") || abnormal.startsWith("H") || handler.isOBXAbnormal(j, k))) {
                    lineClass = "AbnormalRes";
                }

                boolean isPrevAnnotation = false;
                CaseManagementNoteLink cml = caseManagementManager.getLatestLinkByTableId(CaseManagementNoteLink.LABTEST, Long.valueOf(segmentID), j + "-" + k);
                CaseManagementNote p_cmn = null;
                if (cml != null) {
                    p_cmn = caseManagementManager.getNote(cml.getNoteId().toString());
                }
                if (p_cmn != null) {
                    isPrevAnnotation = true;
                }

                String loincCode = null;
                try {
                    List<MeasurementMap> mmapList = measurementMapDao.getMapsByIdent(handler.getOBXIdentifier(j, k));
                    if (mmapList.size() > 0) {
                        MeasurementMap mmap = mmapList.get(0);
                        loincCode = mmap.getLoincCode();
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("loincProb", e);
                }

                if (handler.getMsgType().equals("EPSILON")) {
                    if (handler.getOBXIdentifier(j, k).equals(headers.get(i)) && !obxName.equals("")) {
            %>

            <tr style="background-color:<%=(linenum % 2 == 1 ? highlight : "white")%>;" class="<%=lineClass%>">
                <td style="vertical-align:top;  text-align:left;"><%= obrFlag ? "&nbsp; &nbsp; &nbsp;" : "&nbsp;" %><a
                        href="javascript:popupStart('660','900','${pageContext.request.contextPath}/lab/CA/ON/labValues.jsp?testName=<%=obxName%>&demo=<%=demographicID%>&labType=HL7&identifier=<%= URLEncoder.encode(handler.getOBXIdentifier(j, k).replaceAll("&","%26"),"UTF-8") %>')"><%=obxName %>
                </a>
                    &nbsp;<%if (loincCode != null) { %>
                    <a href="javascript:popupStart('660','1000','http://apps.nlm.nih.gov/medlineplus/services/mpconnect.cfm?mainSearchCriteria.v.cs=2.16.840.1.113883.6.1&mainSearchCriteria.v.c=<%=loincCode%>&informationRecipient.languageCode.c=en')">
                        info</a>
                    <%} %>
                </td>
                <td style="text-align:right">
                    <%=Encode.forHtml(handler.getOBXResult(j, k)) %>
                    <%= handler.isTestResultBlocked(j, k) ? "<a href='#' title='Do Not Disclose Without Explicit Patient Consent'>(BLOCKED)</a>" : ""%>
                </td>

                <td style="text-align:center">
                    <%=Encode.forHtml(handler.getOBXAbnormalFlag(j, k))%>
                </td>
                <td style="text-align:left"><%=Encode.forHtml(handler.getOBXReferenceRange(j, k))%>
                </td>
                <td style="text-align:left"><%=Encode.forHtml(handler.getOBXUnits(j, k)) %>
                </td>
                <td style="text-align:center"><%=Encode.forHtml(handler.getTimeStamp(j, k)) %>
                </td>
                <td style="text-align:center"><%=Encode.forHtml(handler.getOBXResultStatus(j, k)) %>
                </td>
                <td style="text-align:center; vertical-align:top;  ">
                    <a href="javascript:void(0);" title="Annotation"
                       onclick="window.open('<%=request.getContextPath()%>/annotation/annotation.jsp?display=<%=annotation_display%>&amp;table_id=<%=Encode.forJavaScript(segmentID)%>&amp;demo=<%=demographicID%>&amp;other_id=<%=String.valueOf(j) + "-" + String.valueOf(k) %>','anwin','width=400,height=500');">
                        <%if (!isPrevAnnotation) { %><img src="<%= request.getContextPath() %>/images/notes.gif" alt="rxAnnotation" height="16"
                                                          width="13"/><%} else { %><img
                            src="<%= request.getContextPath() %>/images/filledNotes.gif" alt="rxAnnotation" height="16" width="13"
                           /> <%} %>
                    </a>
                </td>
            </tr>
            <% } else if (handler.getOBXIdentifier(j, k).equals(headers.get(i)) && obxName.equals("")) { %>
            <tr style="background-color:<%=(linenum % 2 == 1 ? highlight : "white")%>;" class="NormalRes">
                <td style="vertical-align:top;  text-align:left;" colspan="9">
                    <pre style="margin:0px 0px 0px 100px;"><%=Encode.forHtml(handler.getOBXResult(j, k))%><%=handler.isTestResultBlocked(j, k) ? "<a href='#' title='Do Not Disclose Without Explicit Patient Consent'>(BLOCKED)</a>" : ""%></pre>
                </td>

            </tr>
            <% }

            } else if (handler.getMsgType().equals("HHSEMR") || handler.getMsgType().equals("CML")) {
                if (!obxName.equals("")) { %>
            <tr style="background-color:<%=(linenum % 2 == 1 ? highlight : "white")%>;" class="<%=lineClass%>">
                <td style="vertical-align:top;  text-align:left;"><%= obrFlag ? "&nbsp; &nbsp; &nbsp;" : "&nbsp;" %><a
                        href="javascript:popupStart('660','900','${pageContext.request.contextPath}/lab/CA/ON/labValues.jsp?testName=<%=obxName%>&demo=<%=demographicID%>&labType=HL7&identifier=<%= URLEncoder.encode(handler.getOBXIdentifier(j, k).replaceAll("&","%26"),"UTF-8") %>')"><%=obxName %>
                </a>
                    &nbsp;
                    <%if (loincCode != null) { %>
                    <a href="javascript:popupStart('660','1000','http://apps.nlm.nih.gov/medlineplus/services/mpconnect.cfm?mainSearchCriteria.v.cs=2.16.840.1.113883.6.1&mainSearchCriteria.v.c=<%=loincCode%>&informationRecipient.languageCode.c=en')">
                        info</a>
                    <%} %></td>
                <td style="text-align:right">
                    <%=Encode.forHtml(handler.getOBXResult(j, k)) %>
                    <%=handler.isTestResultBlocked(j, k) ? "<a href='#' title='Do Not Disclose Without Explicit Patient Consent'>(BLOCKED)</a>" : ""%>
                </td>

                <td style="text-align:center">
                    <%=Encode.forHtml(handler.getOBXAbnormalFlag(j, k))%>
                </td>
                <td style="text-align:left"><%=Encode.forHtml(handler.getOBXReferenceRange(j, k))%>
                </td>
                <td style="text-align:left"><%=Encode.forHtml(handler.getOBXUnits(j, k)) %>
                </td>
                <td style="text-align:center"><%=Encode.forHtml(handler.getTimeStamp(j, k)) %>
                </td>
                <td style="text-align:center"><%=Encode.forHtml(handler.getOBXResultStatus(j, k)) %>
                </td>
                <td style="vertical-align:center; text-align:left;">
                    <a href="javascript:void(0);" title="Annotation"
                       onclick="window.open('<%=request.getContextPath()%>/annotation/annotation.jsp?display=<%=annotation_display%>&amp;table_id=<%=Encode.forJavaScript(segmentID)%>&amp;demo=<%=demographicID%>&amp;other_id=<%=String.valueOf(j) + "-" + String.valueOf(k) %>','anwin','width=400,height=500');">
                        <%if (!isPrevAnnotation) { %><img src="<%= request.getContextPath() %>/images/notes.gif" alt="rxAnnotation" height="16"
                                                          width="13"/><%} else { %><img
                            src="<%= request.getContextPath() %>/images/filledNotes.gif" alt="rxAnnotation" height="16" width="13"
                           /> <%} %>
                    </a>
                </td>
            </tr>

            <%} else { %>
            <tr style="background-color:<%=(linenum % 2 == 1 ? highlight : "white")%>;" class="NormalRes">
                <td style="vertical-align:top;  text-align:left;" colspan="9">
                    <pre style="margin:0px 0px 0px 100px;"><%=Encode.forHtml(handler.getOBXResult(j, k))%><%=handler.isTestResultBlocked(j, k) ? "<a href='#' title='Do Not Disclose Without Explicit Patient Consent'>(BLOCKED)</a>" : ""%></pre>
                </td>

            </tr>
            <%
                }
                if (!handler.getNteForOBX(j, k).equals("") && handler.getNteForOBX(j, k) != null) {
            %>
            <tr style="background-color:<%=(linenum % 2 == 1 ? highlight : "white")%>;" class="NormalRes">
                <td style="vertical-align:top;  text-align:left;" colspan="9">
                    <pre style="margin:0px 0px 0px 100px;"><%=Encode.forHtml(handler.getNteForOBX(j, k))%></pre>
                </td>
            </tr>
            <% }
                for (l = 0; l < handler.getOBXCommentCount(j, k); l++) {%>
            <tr style="background-color:<%=(linenum % 2 == 1 ? highlight : "white")%>;" class="NormalRes">
                <td style="vertical-align:top;  text-align:left;" colspan="9">
                    <pre style="margin:0px 0px 0px 100px;"><%=Encode.forHtml(handler.getOBXComment(j, k, l).replaceAll("<br />", " "))%></pre>
                </td>
            </tr>
            <%
                }

            } else if (handler.getMsgType().equals("Spire")) {
            %>
            <tr style="background-color:<%=(linenum % 2 == 1 ? highlight : "white")%>;" class="<%=lineClass%>">
                <td style="vertical-align:top;  text-align:left;"><%= obrFlag ? "&nbsp; &nbsp; &nbsp;" : "&nbsp;" %><a
                        href="javascript:popupStart('660','900','${pageContext.request.contextPath}/lab/CA/ON/labValues.jsp?testName=<%=obxName%>&demo=<%=demographicID%>&labType=HL7&identifier=<%= URLEncoder.encode(handler.getOBXIdentifier(j, k).replaceAll("&","%26"),"UTF-8") %>')"><%=obxName %>
                </a>
                    &nbsp;<%if (loincCode != null) { %>
                    <a href="javascript:popupStart('660','1000','http://apps.nlm.nih.gov/medlineplus/services/mpconnect.cfm?mainSearchCriteria.v.cs=2.16.840.1.113883.6.1&mainSearchCriteria.v.c=<%=loincCode%>&informationRecipient.languageCode.c=en')">
                        info</a>
                    <%} %></td>
                <% if (handler.getOBXResult(j, k).length() > 20) {
                %>

                <td style="text-align:left" colspan="4">
                    <%=Encode.forHtml(handler.getOBXResult(j, k)) %>
                    <%=handler.isTestResultBlocked(j, k) ? "<a href='#' title='Do Not Disclose Without Explicit Patient Consent'>(BLOCKED)</a>" : ""%>
                </td>

                <% String abnormalFlag =Encode.forHtml(handler.getOBXAbnormalFlag(j, k));
                    if (abnormalFlag != null && abnormalFlag.length() > 0) {
                %>
                <td style="text-align:center">
                    <%= abnormalFlag%>
                </td>
                <% } %>

                <% String refRange =Encode.forHtml(handler.getOBXReferenceRange(j, k));
                    if (refRange != null && refRange.length() > 0) {
                %>
                <td style="text-align:left"><%=refRange%>
                </td>
                <% } %>

                <% String units =Encode.forHtml(handler.getOBXUnits(j, k));
                    if (units != null && units.length() > 0) {
                %>
                <td style="text-align:left"><%=units %>
                </td>
                <% } %>
                <%
                } else {
                %>
                <td style="text-align:right" colspan="1">
                    <%=Encode.forHtml(handler.getOBXResult(j, k)) %>
                    <%=handler.isTestResultBlocked(j, k) ? "<a href='#' title='Do Not Disclose Without Explicit Patient Consent'>(BLOCKED)</a>" : ""%>
                </td>
                <td style="text-align:center"><%=Encode.forHtml(handler.getOBXAbnormalFlag(j, k))%>
                </td>
                <td style="text-align:left"><%=Encode.forHtml(handler.getOBXReferenceRange(j, k))%>
                </td>
                <td style="text-align:left"><%=Encode.forHtml(handler.getOBXUnits(j, k)) %>
                </td>
                <%
                    }
                %>

                <td style="text-align:center"><%=Encode.forHtml(handler.getTimeStamp(j, k)) %>
                </td>
                <td style="text-align:center"><%=Encode.forHtml(handler.getOBXResultStatus(j, k)) %>
                </td>

                <td style="vertical-align:top;  text-align:left;">
                    <a href="javascript:void(0);" title="Annotation"
                       onclick="window.open('<%=request.getContextPath()%>/annotation/annotation.jsp?display=<%=annotation_display%>&amp;table_id=<%=Encode.forJavaScript(segmentID)%>&amp;demo=<%=demographicID%>&amp;other_id=<%=String.valueOf(j) + "-" + String.valueOf(k) %>','anwin','width=400,height=500');">
                        <%if (!isPrevAnnotation) { %><img src="<%= request.getContextPath() %>/images/notes.gif" alt="rxAnnotation" height="16"
                                                          width="13"/><%} else { %><img
                            src="<%= request.getContextPath() %>/images/filledNotes.gif" alt="rxAnnotation" height="16" width="13"
                           /> <%} %>
                    </a>
                </td>
            </tr>

            <%for (l = 0; l < handler.getOBXCommentCount(j, k); l++) {%>
            <tr style="background-color:<%=(linenum % 2 == 1 ? highlight : "white")%>;" class="NormalRes">
                <td style="vertical-align:top;  text-align:left;" colspan="9">
                    <pre style="margin:0px 0px 0px 100px;"><%=Encode.forHtml(handler.getOBXComment(j, k, l).replaceAll("<br />", " "))%></pre>
                </td>
            </tr>
            <%
                }


            } else if (!handler.getMsgType().equals("EPSILON")) {

                if (isUnstructuredDoc) {
            %>
            <tr style="background-color:<%=(linenum % 2 == 1 ? highlight : "white")%>;" class="<%="NarrativeRes"%>"><%
                                   			if(handler.getOBXIdentifier(j, k).equalsIgnoreCase(handler.getOBXIdentifier(j, k-1)) && (obxCount>1) && ! handler.getMsgType().equals("MEDITECH") ){%>
                <td style="vertical-align:top;  text-align:left;"><%= obrFlag ? "&nbsp; &nbsp; &nbsp;" : "&nbsp;" %><a
                        href="javascript:popupStart('660','900','${pageContext.request.contextPath}/lab/CA/ON/labValues.jsp?testName=<%=obxName%>&demo=<%=demographicID%>&labType=HL7&identifier='<%= URLEncoder.encode(handler.getOBXIdentifier(j, k).replaceAll("&","%26"),"UTF-8")%>')"></a><%
                                   			}else if(! handler.getMsgType().equals("MEDITECH") ) {%>
                <td style="vertical-align:top;  text-align:left;"><%= obrFlag ? "&nbsp; &nbsp; &nbsp;" : "&nbsp;" %><a
                        href="javascript:popupStart('660','900','${pageContext.request.contextPath}/lab/CA/ON/labValues.jsp?testName=<%=obxName%>&demo=<%=demographicID%>&labType=HL7&identifier=<%= URLEncoder.encode(handler.getOBXIdentifier(j, k).replaceAll("&","%26"),"UTF-8") %>')"><%=obxName %>
                </a>
                        <%}%>
                        <%if(isVIHARtf){

											    //create bytes from the rtf string
										    	byte[] rtfBytes = handler.getOBXResult(j, k).getBytes();
										    	ByteArrayInputStream rtfStream = new ByteArrayInputStream(rtfBytes);

										    	//Use RTFEditor Kit to get plaintext from RTF
										    	RTFEditorKit rtfParser = new RTFEditorKit();
										    	javax.swing.text.Document doc = rtfParser.createDefaultDocument();
										    	rtfParser.read(rtfStream, doc, 0);
										    	String rtfText = doc.getText(0, doc.getLength()).replaceAll("\n", "<br>");
										    	String disclaimer = "<br>IMPORTANT DISCLAIMER: You are viewing a PREVIEW of the original report. The rich text formatting contained in the original report may convey critical information that must be considered for clinical decision making. Please refer to the ORIGINAL report, by clicking 'Print', prior to making any decision on diagnosis or treatment.";%>
                <td style="text-align:left"><%= rtfText + disclaimer %>
                </td>
                    <%}else{%>
                <td style="text-align:left">
                    <span><%=Encode.forHtml(handler.getOBXResult(j, k)) %><%=handler.isTestResultBlocked(j, k) ? "<a href='#' title='Do Not Disclose Without Explicit Patient Consent'>(BLOCKED)</a>" : ""%></span>
                </td>
                    <%} %>

                    <% if(handler.getTimeStamp(j, k).equals(handler.getTimeStamp(j, k-1)) && (obxCount>1)){ %>
                <td style="text-align:center"></td>
                    <%} else {%>
                <td style="text-align:center"><%=Encode.forHtml(handler.getTimeStamp(j, k)) %>
                </td>
                    <%}

                                       		} else {//if it isn't a PATHL7 doc %>

            <tr style="background-color:<%=(linenum % 2 == 1 ? highlight : "white")%>;" class="<%=lineClass%>">

                    <% if(handler.getMsgType().equals("PATHL7") && !isAllowedDuplicate && (obxCount>1) && handler.getOBXIdentifier(j, k).equalsIgnoreCase(handler.getOBXIdentifier(j, k-1)) && (handler.getOBXValueType(j, k).equals("TX") || handler.getOBXValueType(j, k).equals("FT"))){%>
                <td style="vertical-align:top;  text-align:left;"><%= obrFlag ? "&nbsp; &nbsp; &nbsp;" : "&nbsp;" %><a
                        href="javascript:popupStart('660','900','${pageContext.request.contextPath}/lab/CA/ON/labValues.jsp?testName=<%=obxName%>&demo=<%=demographicID%>&labType=HL7&identifier=<%= URLEncoder.encode(handler.getOBXIdentifier(j, k).replaceAll("&","%26"),"UTF-8") %>')"></a><%
                                   			} else {


                               					if(handler instanceof AlphaHandler && lastObxSetId.equals(((AlphaHandler)handler).getObxSetId(j, k))) {
								%>
                <td></td>
                    <% } else { %>
                <td style="vertical-align:top;  text-align:left;"><%= obrFlag ? "&nbsp; &nbsp; &nbsp;" : "&nbsp;" %><a
                        href="javascript:popupStart('660','900','${pageContext.request.contextPath}/lab/CA/ON/labValues.jsp?testName=<%=obxName%>&demo=<%=demographicID%>&labType=HL7&identifier=<%= URLEncoder.encode(handler.getOBXIdentifier(j, k).replaceAll("&","%26"),"UTF-8") %>')"><%=obxName %>
                </a>

                    <% if (loincCode != null) { %>
                    <a href="javascript:popupStart('660','1000','http://apps.nlm.nih.gov/medlineplus/services/mpconnect.cfm?mainSearchCriteria.v.cs=2.16.840.1.113883.6.1&mainSearchCriteria.v.c=<%=loincCode%>&informationRecipient.languageCode.c=en')">
                        info</a>
                    <%} %>

                </td>
                    <% }


                               				}%>


                    <% if(handler instanceof AlphaHandler && "FT".equals(handler.getOBXValueType(j, k))) { %>
                <td colspan="4">
                    <pre style="font-family:Courier New, monospace;">       <%=Encode.forHtml(handler.getOBXResult(j, k)) %><%=handler.isTestResultBlocked(j, k) ? "<a href='#' title='Do Not Disclose Without Explicit Patient Consent'>(BLOCKED)</a>" : ""%></pre>
                </td>
                    <%
                                       			lastObxSetId = ((AlphaHandler)handler).getObxSetId(j,k);

                                           } else if(handler instanceof PATHL7Handler && "FT".equals(handler.getOBXValueType(j, k)) && (handler.getOBXReferenceRange(j,k).isEmpty() && handler.getOBXUnits(j,k).isEmpty())){
                                        	  %>
                <td colspan="4">
                    <%=Encode.forHtml(handler.getOBXResult(j, k)) %>
                    <%=handler.isTestResultBlocked(j, k) ? "<a href='#' title='Do Not Disclose Without Explicit Patient Consent'>(BLOCKED)</a>" : ""%>
                </td>
                    <%
                                           } else { %>
                    <%
                                           	String align = "right";
                                          	//for pathl7, if it is an SG/CDC result greater than 100 characters, left justify it
                                           	if((handler.getOBXResult(j, k) != null && handler.getOBXResult(j, k).length() > 100) && (isSGorCDC)){
                                           		align="left";
                                           	}
                                           	if(handler instanceof PATHL7Handler && "FT".equals(handler.getOBXValueType(j, k))) {
                                           		align="left";
                                           	}
                                           	%>

                    <%
                                           		//CLS textual results - use 4 columns.
                                           		if(handler instanceof CLSHandler && ( (CLSHandler) handler).isUnstructured()) {
                                           	%>
                <td style="text-align:left" colspan="4">
                    <%=Encode.forHtml(handler.getOBXResult(j, k)) %>
                    <%=handler.isTestResultBlocked(j, k) ? "<a href='#' title='Do Not Disclose Without Explicit Patient Consent'>(BLOCKED)</a>" : ""%>
                </td>

                    <%
                                           		}

                                           		else if(handler.getMsgType().equals("MEDITECH")  && isUnstructuredDoc ) {
                                           	%>

                <pre> <%=Encode.forHtml(handler.getOBXResult(j, k)) %><%=handler.isTestResultBlocked(j, k) ? "<a href='#' title='Do Not Disclose Without Explicit Patient Consent'>(BLOCKED)</a>" : ""%>
					                             		</pre>

                    <% } else if(handler.getMsgType().equals("MEDITECH")  && ((MEDITECHHandler) handler).isReportData() ) { %>
            <tr>
                <td>
                    <%=Encode.forHtml(handler.getOBXResult(j, k)) %>
                    <%=handler.isTestResultBlocked(j, k) ? "<a href='#' title='Do Not Disclose Without Explicit Patient Consent'>(BLOCKED)</a>" : ""%>
                </td>
            </tr>

            <%
                }
                // else {
            %>

            <%
                // for Excelleris Embedded Content - ie: PDF, RTF, etc...
                if ((handler.getMsgType().equals("ExcellerisON") || handler.getMsgType().equals("PATHL7")) && handler.getOBXValueType(j, k).equals("ED")) {
                    String legacy = "";
                    if (handler.getMsgType().equals("PATHL7") && ((PATHL7Handler) handler).isLegacy(j, k)) {
                        legacy = "&legacy=true";
                    }

            %>
            <td style="text-align:<%=align%>"><a
                    href="<%=request.getContextPath() %>/lab/DownloadEmbeddedDocumentFromLab.do?labNo=<%= Encode.forHtmlAttribute(segmentID) %>&segment=<%=j%>&group=<%=k%><%=legacy%>">PDF
                Report</a></td>
            <%
            } else {
            %>
            <td style="text-align:<%=align%>">
                <% if (handler.getMsgType().equals("ExcellerisON") && !((ExcellerisOntarioHandler) handler).getOBXSubId(j, k).isEmpty()) { %>
                <em><%=Encode.forHtml(((ExcellerisOntarioHandler) handler).getOBXSubIdWithObservationValue(j, k))%></em>
                <% } else { %>
                <%=Encode.forHtml(handler.getOBXResult(j, k)) %>
                <% } %>
                <%=handler.isTestResultBlocked(j, k) ? "<a href='#' title='Do Not Disclose Without Explicit Patient Consent'>(BLOCKED)</a>" : ""%>
            </td>

            <% } %>
            <td style="text-align:center">
                <%=Encode.forHtml(handler.getOBXAbnormalFlag(j, k))%>
            </td>
            <td style="text-align:left"><%=Encode.forHtml(handler.getOBXReferenceRange(j, k))%>
            </td>
            <td style="text-align:left"><%=Encode.forHtml(handler.getOBXUnits(j, k)) %>
            </td>

            <%}%>

            <td style="text-align:center"><%=Encode.forHtml(handler.getTimeStamp(j, k)) %>
            </td>
            <td style="text-align:center">
                <%
                    String status =Encode.forHtml(handler.getOBXResultStatus(j, k));
                    if ("GDML".equals(handler.getMsgType()) && ((GDMLHandler) handler).isTestResultBlocked(j, k)) {
                        if (!StringUtils.isEmpty(status)) {
                            status += "/";
                        }
                        status += "BLOCKED";
                    }
                %>
                <%=status %>

            </td>
            <td style="vertical-align:top;  text-align:left;"><a href="javascript:void(0);" title="Annotation"
                                               onclick="window.open('<%=request.getContextPath()%>/annotation/annotation.jsp?display=<%=annotation_display%>&amp;table_id=<%=Encode.forJavaScript(segmentID)%>&amp;demo=<%=demographicID%>&amp;other_id=<%=String.valueOf(j) + "-" + String.valueOf(k) %>','anwin','width=400,height=500');">
                <%if (!isPrevAnnotation) { %><img src="<%= request.getContextPath() %>/images/notes.gif" alt="rxAnnotation" height="16"
                                                  width="13"/><%} else { %><img
                    src="<%= request.getContextPath() %>/images/filledNotes.gif" alt="rxAnnotation" height="16" width="13"/> <%} %>
            </a>
            </td>

            <% if ("ExcellerisON".equals(handler.getMsgType())) {
                lastLicenseNo = currentLicenseNo;
                currentLicenseNo = ((ExcellerisOntarioHandler) handler).getLabLicenseNo(j, k);
                String licenseName = ((ExcellerisOntarioHandler) handler).getLabLicenseName(j, k);
                if (!allLicenseNames.contains(licenseName)) {
                    allLicenseNames.add(licenseName);
                }
            %>
            <td><%=!currentLicenseNo.equals(lastLicenseNo) ? Encode.forHtml(currentLicenseNo) : ""%>
            </td>
            <% } %>
            </tr>

            <%
                }

                for (l = 0; l < handler.getOBXCommentCount(j, k); l++) {
            %>
            <tr style="background-color:<%=(linenum % 2 == 1 ? highlight : "white")%>;" class="NormalRes">
                <td style="vertical-align:top;  text-align:left;" colspan="9">
                    <pre style="margin:0px 0px 0px 100px;"><%=Encode.forHtml(handler.getOBXComment(j, k, l).replaceAll("<br />", " "))%></pre>
                </td>
            </tr>
            <%
                }


            } else {
            %>
            <%
                for (l = 0; l < handler.getOBXCommentCount(j, k); l++) {
                    if (!handler.getOBXComment(j, k, l).equals("")) {
            %>
            <tr style="background-color:<%=(linenum % 2 == 1 ? highlight : "white")%>;">
                <td style="vertical-align:top;  text-align:left;" colspan="9">
                    <pre style="margin:0px 0px 0px 100px;"><%=Encode.forHtml(handler.getOBXComment(j, k, l).replaceAll("<br />", " "))%></pre>
                </td>
                <td style="vertical-align:top;  text-align:left;">
                    <a href="javascript:void(0);" title="Annotation"
                       onclick="window.open('<%=request.getContextPath()%>/annotation/annotation.jsp?display=<%=annotation_display%>&amp;table_id=<%=Encode.forJavaScript(segmentID)%>&amp;demo=<%=demographicID%>&amp;other_id=<%=String.valueOf(1) + "-" + String.valueOf(1) %>','anwin','width=400,height=500');">
                        <%if (!isPrevAnnotation) { %><img src="<%= request.getContextPath() %>/images/notes.gif" alt="rxAnnotation" height="16"
                                                          width="13"/><%} else { %><img
                            src="<%= request.getContextPath() %>/images/filledNotes.gif" alt="rxAnnotation" height="16" width="13"
                           /> <%} %>
                    </a>
                </td>
            </tr>
            <%
                    }
                } // end for loop
            %>

            <% }

            }

            }


                if (headers.get(i).equals(handler.getObservationHeader(j, 0))) {
            %>
            <%
                for (k = 0; k < handler.getOBRCommentCount(j); k++) {
                    // the obrName should only be set if it has not been
                    // set already which will only have occured if the
                    // obx name is "" or if it is the same as the obr name
                    if (!obrFlag && handler.getOBXName(j, 0).equals("")) {
            %>
            <tr style="background-color:<%=(linenum % 2 == 1 ? highlight : "white")%>;">
                <td style="vertical-align:top;  text-align:left;"><%=Encode.forHtml(handler.getOBRName(j))%>
                </td>
                <td colspan="6">&nbsp;</td>
            </tr>
            <%
                    obrFlag = true;
                }
            %>
            <tr style="background-color:<%=(linenum % 2 == 1 ? highlight : "white")%>;" class="NormalRes">
                <td style="vertical-align:top;  text-align:left;" colspan="1"></td>
                <td style="vertical-align:top;  text-align:left;" colspan="7">
                    <pre style="margin:0px 0px 0px 0px;"><%=Encode.forHtml(handler.getOBRComment(j, k).replaceAll("<br />", " "))%></pre>
                </td>
            </tr>
            <% if (!handler.getMsgType().equals("HHSEMR") || !handler.getMsgType().equals("TRUENORTH")) {
                if (handler.getOBXName(j, k).equals("")) {
                    String result = handler.getOBXResult(j, k);%>
            <tr style="background-color:<%=(linenum % 2 == 1 ? highlight : "white")%>;">
                <td colspan="7" style="vertical-align:top; text-align:left;"><%=result%>
                </td>
            </tr>
            <%
                                        }
                                    }
                                }//end for k=0


                            }//end if handler.getObservation..

                    } //end for j=0; j<obrCount;
                } // // end for headersfor i=0... (headers) line 625

                if (handler.getMsgType().equals("Spire")) {

                    int numZDS = ((SpireHandler) handler).getNumZDSSegments();
                    String lineClass = "NormalRes";
                    int lineNumber = 0;
                    MiscUtils.getLogger().info("HERE: " + numZDS);

                    if (numZDS > 0) {
            %>
            <tr class="Field2">
                <td width="25%;  vertical-align:bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formTestName"/></td>
                <td width="15%;  vertical-align:bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formResult"/></td>
                <td width="15%;  vertical-align:bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formProvider"/></td>
                <td width="15%;  vertical-align:bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formDateTimeCompleted"/></td>
                <td width="6%;  vertical-align:bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formNew"/></td>
            </tr>
            <%
                }

                for (int m = 0; m < numZDS; m++) {
            %>
            <tr style="background-color:<%=(lineNumber % 2 == 1 ? highlight : "white")%>;" class="<%=lineClass%>">
                <td style="vertical-align:top;  text-align:left;"><%=Encode.forHtml(((SpireHandler) handler).getZDSName(m))%>
                </td>
                <td style="text-align:right"><%=Encode.forHtml(((SpireHandler) handler).getZDSResult(m))%>
                </td>
                <td style="text-align:center"><%=Encode.forHtml(((SpireHandler) handler).getZDSProvider(m))%>
                </td>
                <td style="text-align:center"><%=Encode.forHtml(((SpireHandler) handler).getZDSTimeStamp(m))%>
                </td>
                <td style="text-align:center"><%=Encode.forHtml(((SpireHandler) handler).getZDSResultStatus(m))%>
                </td>
            </tr>
            <%
                        lineNumber++;
                    }
                }

            %>
        </table>
        <%
        %>
        <%-- FOOTER --%>
        <table style="width:100%" class="MainTableBottomRowRightColumn">
            <tr>
                <td style="text-align:left; width:50%">
                    <% if (!ackFlag) { %>
                    <input type="button" class="btn btn-sm btn-outline-primary" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.btnAcknowledge"/>"
                           onclick="<%=ackLabFunc%>">
                    <% } %>
                    <input type="button" class="btn btn-sm btn-outline-secondary" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.btnComment"/>"
                           onclick="return getComment('addComment',<%=Encode.forJavaScript(segmentID)%>);">
                    <input type="button" class="btn btn-sm btn-outline-secondary" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.index.btnForward"/>"
                           onClick="ForwardSelectedRows(<%=Encode.forJavaScript(segmentID)%> + ':HL7', '', '')">
                    <input type="button" class="btn btn-sm btn-outline-secondary" value=" <fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/> " onClick="window.close()">
                    <input type="button" class="btn btn-sm btn-outline-secondary" value=" <fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnPrint"/> "
                           onClick="printPDF('<%=Encode.forJavaScript(segmentID)%>')">
                    <% if (searchProviderNo != null) { // we were called from e-chart %>
                    <input type="button" class="btn btn-sm btn-outline-secondary" value=" <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.btnEChart"/> "
                           onClick="popupStart(360, 680, '${pageContext.request.contextPath}/oscarMDS/SearchPatient.do?labType=HL7&segmentID=<%= Encode.forJavaScript(segmentID) %>&name=<%=java.net.URLEncoder.encode(handler.getLastName()+", "+handler.getFirstName(), StandardCharsets.UTF_8)%>', 'encounter')">

                    <% } %>
                </td>
                <td style="vertical-align:top; text-align:left; width:50%;">
                    <% if ("CLS".equals(handler.getMsgType())) { %>
                    <span class="Field2"><i><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.msgReportEndCLS"/></i></span>
                    <% } else { %>
                    <span class="Field2"><i><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.msgReportEnd"/></i></span>
                    <% } %>
                </td>
            </tr>
        </table>

        <br/>
        <table>
                    <c:if test="${hasMissingTests}">
                        <tr><td class="alert alert-info">
                        <!-- Missing Tests Information Section -->
                        <div class="info-section">
                            <p>&nbsp;&nbsp;<b>Info:</b> The following tests were not included in this version of the lab results:</p>
                            <table class="test-list" >
                                <c:forEach var="entry" items="${missingTests}">
                                    <tr>
                                        <td><span>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<c:out value="${entry.key}"/></span></td>
                                        <td><b>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="status"><c:out value="${entry.value.description}"/></span></b></td>
                                    </tr>
                                </c:forEach>
                                    <tr>
                                        <td><span>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;If items are *reported under separate cover* see v1</span></td>
                                        <td></td>
                                    </tr>
                            </table>
                        </div>
                        </td></tr>
                    </c:if>
            <%
                for (String lName : allLicenseNames) {
            %>
            <tr>
                <td><%=Encode.forHtml(lName)%>
                </td>
            </tr>

            <% } %>
        </table>
        </td>
        </tr>
        </table>

    </form>

    <%String s = "" + System.currentTimeMillis();%>
    <a style="color:lightgrey;" href="javascript: void(0);" onclick="showHideItem('rawhl7<%=s%>');">show</a>
    <pre id="rawhl7<%=s%>" style="display:none;"><%=Encode.forHtml(hl7)%></pre>
</div>
<%} %>

<script type="text/javascript"
        src="${pageContext.servletContext.contextPath}/share/javascript/oscarMDSIndex.js"></script>

</body>
</html>
<%!
    public String[] divideStringAtFirstNewline(String s) {
        int i = s.indexOf("<br />");
        String[] ret = new String[2];
        if (i == -1) {
            ret[0] = new String(s);
            ret[1] = null;
        } else {
            ret[0] = s.substring(0, i);
            ret[1] = s.substring(i + 6);
        }
        return ret;
    }
%>
<%--
   AD Address
   CE Coded Entry
   CF Coded Element With Formatted Values
   CK Composite ID With Check Digit
   CN Composite ID And Name
   CP Composite Price
   CX Extended Composite ID With Check Digit
   DT Date
   ED Encapsulated Data
   FT Formatted Text (Display)
   MO Money
   NM Numeric
   PN Person Name
   RP Reference Pointer
   SN Structured Numeric
   ST String Data.
   TM Time
   TN Telephone Number
   TS Time Stamp (Date & Time)
   TX Text Data (Display)
   XAD Extended Address
   XCN Extended Composite Name And Number For Persons
   XON Extended Composite Name And Number For Organizations
   XPN Extended Person Number
   XTN Extended Telecommunications Number
--%>
