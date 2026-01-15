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

<%@ page import="java.nio.charset.StandardCharsets" %>
<%@page import="com.fasterxml.jackson.databind.ObjectMapper" %>
<%@page import="com.fasterxml.jackson.databind.node.ObjectNode" %>
<%@page import="com.fasterxml.jackson.databind.node.ArrayNode" %>
<%@page import="com.fasterxml.jackson.databind.JsonNode" %>
<%@ page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="ca.openosp.openo.util.ConversionUtils" %>
<%@ page import="ca.openosp.openo.commn.dao.PatientLabRoutingDao" %>
<%@ page import="ca.openosp.openo.commn.model.PatientLabRouting" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="org.apache.commons.lang3.builder.ReflectionToStringBuilder" %>
<%@ page import="ca.openosp.openo.utility.MiscUtils" %>
<%@ page import="org.w3c.dom.Document" %>
<%@ page import="ca.openosp.openo.caisi_integrator.ws.CachedDemographicLabResult" %>
<%@ page import="ca.openosp.openo.lab.ca.all.web.LabDisplayHelper" %>
<%@ page import="ca.openosp.openo.lab.ca.all.util.LabVersionComparator"%>

<%@ page import="java.util.*,
                 ca.openosp.openo.util.UtilDateUtilities,
                 ca.openosp.openo.lab.ca.all.*,
                 ca.openosp.openo.lab.ca.all.parsers.*,
                 ca.openosp.openo.lab.LabRequestReportLink,
                 ca.openosp.openo.mds.data.ReportStatus,
                 ca.openosp.openo.log.*,
                 ca.openosp.OscarProperties" %>
<%@ page import="ca.openosp.openo.casemgmt.model.CaseManagementNoteLink" %>
<%@ page import="ca.openosp.openo.casemgmt.model.CaseManagementNote" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.commn.dao.UserPropertyDAO, ca.openosp.openo.commn.model.UserProperty" %>
<%@ page import="ca.openosp.openo.commn.model.MeasurementMap, ca.openosp.openo.commn.dao.MeasurementMapDao" %>
<%@ page import="ca.openosp.openo.commn.model.Tickler" %>
<%@ page import="ca.openosp.openo.managers.TicklerManager" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page
        import="ca.openosp.openo.casemgmt.service.CaseManagementManager, ca.openosp.openo.commn.dao.Hl7TextMessageDao, ca.openosp.openo.commn.model.Hl7TextMessage,ca.openosp.openo.commn.dao.Hl7TextInfoDao,ca.openosp.openo.commn.model.Hl7TextInfo" %>
<jsp:useBean id="oscarVariables" class="java.util.Properties" scope="session"/>
<%@    page import="javax.swing.text.rtf.RTFEditorKit" %>
<%@    page import="java.io.ByteArrayInputStream" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.log.LogAction" %>
<%@ page import="ca.openosp.openo.log.LogConst" %>
<%@ page import="ca.openosp.openo.lab.ca.all.parsers.*" %>
<%@ page import="ca.openosp.openo.lab.ca.all.Hl7textResultsData" %>
<%@ page import="ca.openosp.openo.lab.ca.all.AcknowledgementData" %>
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
        recallTicklerPriority = getRecallTicklerPriority.getValue();
        if (getRecallTicklerAssignee.getValue().equals("yes")) {
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
            if (handler instanceof OLISHL7Handler) {
%>
<jsp:forward page="labDisplayOLIS.jsp"/>
<%
            }
        }

    } else // remote lab
    {
        CachedDemographicLabResult remoteLabResult = LabDisplayHelper.getRemoteLab(loggedInInfo, Integer.parseInt(remoteFacilityIdString), remoteLabKey, Integer.parseInt(demographicID));
        MiscUtils.getLogger().debug("retrieved remoteLab:" + ReflectionToStringBuilder.toString(remoteLabResult));
        isLinkedToDemographic = true;

        LogAction.addLog((String) session.getAttribute("user"), LogConst.READ, LogConst.CON_HL7_LAB, "segmentId=" + segmentID + ", remoteFacilityId=" + remoteFacilityIdString + ", remoteDemographicId=" + demographicID);

        Document cachedDemographicLabResultXmlData = LabDisplayHelper.getXmlDocument(remoteLabResult);
        ackList = LabDisplayHelper.getReportStatus(cachedDemographicLabResultXmlData);
        multiLabId = LabDisplayHelper.getMultiLabId(cachedDemographicLabResultXmlData);
        handler = LabDisplayHelper.getMessageHandler(cachedDemographicLabResultXmlData);
        handlers.add(handler);
        segmentIDs = new String[]{"0"};  //fake segment ID for the for loop below to execute
        hl7 = LabDisplayHelper.getHl7Body(cachedDemographicLabResultXmlData);

        try {
            remoteFacilityIdQueryString = "&remoteFacilityId=" + remoteFacilityIdString + "&remoteLabKey=" + URLEncoder.encode(remoteLabKey, "UTF-8");
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
    }

request.setAttribute("duplicateOfLab", duplicateOfLab);
request.setAttribute("missingTests", missingTests);

/********************** Converted to this sport *****************************/


// check for errors printing
    if (request.getAttribute("printError") != null && (Boolean) request.getAttribute("printError")) {
%>
<script language="JavaScript">
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
    <title><%=handler.getPatientName() + " Lab Results"%>
    </title>

    <link rel="stylesheet" type="text/css" media="all"
          href="${pageContext.servletContext.contextPath}/library/jquery/jquery-ui.theme-1.12.1.min.css"/>
    <link rel="stylesheet" type="text/css" media="all"
          href="${pageContext.servletContext.contextPath}/library/jquery/jquery-ui-1.12.1.min.css"/>
    <link rel="stylesheet" type="text/css" media="all"
          href="${pageContext.servletContext.contextPath}/library/jquery/jquery-ui.structure-1.12.1.min.css"/>
    <script language="javascript" type="text/javascript"
            src="${pageContext.request.contextPath}/share/javascript/Oscar.js"></script>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <script type="text/javascript"
            src="${pageContext.servletContext.contextPath}/library/jquery/jquery-1.12.0.min.js"></script>
    <script type="text/javascript"
            src="${pageContext.servletContext.contextPath}/library/jquery/jquery-ui-1.12.1.min.js"></script>
    <script type="text/javascript"
            src="<%= request.getContextPath() %>/share/javascript/jquery/jquery.form.js"></script>

    <script type="text/javascript" charset="utf-8">
        var contextpath = "${pageContext.servletContext.contextPath}";
        const ctx = contextpath;
    </script>


    <script language="javascript" type="text/javascript">
        // alternately refer to this function in oscarMDSindex.js as labDisplayAjax.jsp does
        function updateLabDemoStatus(labno) {
            if (document.getElementById("DemoTable" + labno)) {
                document.getElementById("DemoTable" + labno).style.backgroundColor = "#FFF";
            }
        }
    </script>
    <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/share/css/OscarStandardLayout.css">
    <style type="text/css">
        <!--
        .RollRes {
            font-weight: 700;
            font-size: 8pt;
            color: white;
            font-family: Verdana, Arial, Helvetica
        }

        .RollRes a:link {
            color: white
        }

        .RollRes a:hover {
            color: white
        }

        .RollRes a:visited {
            color: white
        }

        .RollRes a:active {
            color: white
        }

        .AbnormalRollRes {
            font-weight: 700;
            font-size: 8pt;
            color: red;
            font-family: Verdana, Arial, Helvetica
        }

        .AbnormalRollRes a:link {
            color: red
        }

        .AbnormalRollRes a:hover {
            color: red
        }

        .AbnormalRollRes a:visited {
            color: red
        }

        .AbnormalRollRes a:active {
            color: red
        }

        .CorrectedRollRes {
            font-weight: 700;
            font-size: 8pt;
            color: yellow;
            font-family: Verdana, Arial, Helvetica
        }

        .CorrectedRollRes a:link {
            color: yellow
        }

        .CorrectedRollRes a:hover {
            color: yellow
        }

        .CorrectedRollRes a:visited {
            color: yellow
        }

        .CorrectedRollRes a:active {
            color: yellow
        }

        .AbnormalRes {
            font-weight: bold;
            font-size: 8pt;
            color: red;
            font-family: Verdana, Arial, Helvetica
        }

        .AbnormalRes a:link {
            color: red
        }

        .AbnormalRes a:hover {
            color: red
        }

        .AbnormalRes a:visited {
            color: red
        }

        .AbnormalRes a:active {
            color: red
        }

        .NormalRes {
            font-weight: bold;
            font-size: 8pt;
            color: black;
            font-family: Verdana, Arial, Helvetica
        }

        .TDISRes {
            font-weight: bold;
            font-size: 10pt;
            color: black;
            font-family: Verdana, Arial, Helvetica
        }

        .NormalRes a:link {
            color: black
        }

        .NormalRes a:hover {
            color: black
        }

        .NormalRes a:visited {
            color: black
        }

        .NormalRes a:active {
            color: black
        }

        .HiLoRes {
            font-weight: bold;
            font-size: 8pt;
            color: blue;
            font-family: Verdana, Arial, Helvetica
        }

        .HiLoRes a:link {
            color: blue
        }

        .HiLoRes a:hover {
            color: blue
        }

        .HiLoRes a:visited {
            color: blue
        }

        .HiLoRes a:active {
            color: blue
        }

        .CorrectedRes {
            font-weight: bold;
            font-size: 8pt;
            color: #E000D0;
            font-family: Verdana, Arial, Helvetica
        }

        .CorrectedRes a:link {
            color: #6da997
        }

        .CorrectedRes a:hover {
            color: #6da997
        }

        .CorrectedRes a:visited {
            color: #6da997
        }

        .CorrectedRes a:active {
            color: #6da997
        }

        .Field {
            font-weight: bold;
            font-size: 8.5pt;
            color: black;
            font-family: Verdana, Arial, Helvetica
        }

        .NarrativeRes {
            font-weight: 700;
            font-size: 10pt;
            color: black;
            font-family: Courier New, Courier, mono
        }

        div.Field a:link {
            color: black
        }

        div.Field a:hover {
            color: black
        }

        div.Field a:visited {
            color: black
        }

        div.Field a:active {
            color: black
        }

        .Field2 {
            font-weight: bold;
            font-size: 8pt;
            color: #ffffff;
            font-family: Verdana, Arial, Helvetica
        }

        div.Field2 {
            font-weight: bold;
            font-size: 8pt;
            color: #ffffff;
            font-family: Verdana, Arial, Helvetica
        }

        div.FieldData {
            font-weight: normal;
            font-size: 8pt;
            color: black;
            font-family: Verdana, Arial, Helvetica
        }

        div.Field3 {
            font-weight: normal;
            font-size: 8pt;
            color: black;
            font-style: italic;
            font-family: Verdana, Arial, Helvetica
        }

        div.Title {
            font-weight: 800;
            font-size: 10pt;
            color: white;
            font-family: Verdana, Arial, Helvetica;
            padding-top: 4pt;
            padding-bottom: 2pt
        }

        div.Title a:link {
            color: white
        }

        div.Title a:hover {
            color: white
        }

        div.Title a:visited {
            color: white
        }

        div.Title a:active {
            color: white
        }

        div.Title2 {
            font-weight: bolder;
            font-size: 9pt;
            color: black;
            text-indent: 5pt;
            font-family: Verdana, Arial, Helvetica;
            padding: 10pt 15pt 2pt 2pt
        }

        div.Title2 a:link {
            color: black
        }

        div.Title2 a:hover {
            color: black
        }

        div.Title2 a:visited {
            color: black
        }

        div.Title2 a:active {
            color: black
        }

        .Cell {
            background-color: #9999CC;
            border-left: thin solid #CCCCFF;
            border-right: thin solid #6666CC;
            border-top: thin solid #CCCCFF;
            border-bottom: thin solid #6666CC
        }

        .Cell2 {
            background-color: #376c95;
            border-left-style: none;
            border-left-width: medium;
            border-right-style: none;
            border-right-width: medium;
            border-top: thin none #bfcbe3;
            border-bottom-style: none;
            border-bottom-width: medium
        }

        .Cell3 {
            background-color: #add9c7;
            border-left: thin solid #dbfdeb;
            border-right: thin solid #5d9987;
            border-top: thin solid #dbfdeb;
            border-bottom: thin solid #5d9987
        }

        .CellHdr {
            background-color: #cbe5d7;
            border-right-style: none;
            border-right-width: medium;
            border-bottom-style: none;
            border-bottom-width: medium
        }

        .Nav {
            font-weight: bold;
            font-size: 8pt;
            color: black;
            font-family: Verdana, Arial, Helvetica
        }

        .PageLink a:link {
            font-size: 8pt;
            color: white
        }

        .PageLink a:hover {
            color: red
        }

        .PageLink a:visited {
            font-size: 9pt;
            color: yellow
        }

        .PageLink a:active {
            font-size: 12pt;
            color: yellow
        }

        .PageLink {
            font-family: Verdana
        }

        .text1 {
            font-size: 8pt;
            color: black;
            font-family: Verdana, Arial, Helvetica
        }

        div.txt1 {
            font-size: 8pt;
            color: black;
            font-family: Verdana, Arial
        }

        div.txt2 {
            font-weight: bolder;
            font-size: 6pt;
            color: black;
            font-family: Verdana, Arial
        }

        div.Title3 {
            font-weight: bolder;
            font-size: 12pt;
            color: black;
            font-family: Verdana, Arial
        }

        .red {
            color: red
        }

        .text2 {
            font-size: 7pt;
            color: black;
            font-family: Verdana, Arial
        }

        .white {
            color: white
        }

        .title1 {
            font-size: 9pt;
            color: black;
            font-family: Verdana, Arial
        }

        div.Title4 {
            font-weight: 600;
            font-size: 8pt;
            color: white;
            font-family: Verdana, Arial, Helvetica
        }

        pre {
            display: block;
            font-family: Verdana, Arial, Helvetica;
            white-space: -moz-pre-space;
            margin: 0px;
            font-size: x-small;
            font-weight: 600;
        }

        -->

        input[type=button], button, input[id^='acklabel_'] {
            font-size: 12px !important;
            padding: 0px;
        }

        #ticklerWrap {
            position: relative;
            top: 0px;
            background-color: #FF6600;
            width: 100%;
        }

        .completedTickler {
            opacity: 0.8;
            filter: alpha(opacity=80); /* For IE8 and earlier */
        }

        @media print {
            .DoNotPrint {
                display: none;
            }
        }
    </style>

    <style>
        /* Dropdown Button */
        .dropbtn {
            /*  background-color: #4CAF50;
              color: white;
              padding: 16px;
              font-size: 16px;
              border: none;*/
        }

        /* The container <div> - needed to position the dropdown content */
        .dropdown {
            position: relative;
            display: inline-block;
        }

        /* Dropdown Content (Hidden by Default) */
        .dropdown-content {
            display: none;
            position: absolute;
            background-color: #f1f1f1;
            min-width: 160px;
            box-shadow: 0px 8px 16px 0px rgba(0, 0, 0, 0.2);
            z-index: 1;
        }

        /* Links inside the dropdown */
        .dropdown-content a {
            color: black;
            padding: 12px 16px;
            text-decoration: none;
            display: block;
        }

        /* Change color of dropdown links on hover */
        .dropdown-content a:hover {
            background-color: #ddd;
        }

        /* Show the dropdown menu on hover */
        .dropdown:hover .dropdown-content {
            display: block;
        }

        /* Change the background color of the dropdown button when the dropdown content is shown */
.dropdown:hover .dropbtn {background-color: #3e8e41;}

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

    <script language="JavaScript">
        var labNo = '<%=Encode.forJavaScript(segmentID)%>';
        var providerNo = '<%=providerNo%>';
        var demographicNo = '<%=isLinkedToDemographic ? demographicID : ""%>';

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


        function handleLab(formid, labid, action) {
            var url = '<%= request.getContextPath() %>/documentManager/inboxManage.do';
            var data = 'method=isLabLinkedToDemographic&labid=' + labid;
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
            var dataStr = "reason=" + encodeURIComponent(reason) + "&labNo=" + labNo;
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
            var data = new URLSearchParams(new FormData(formEl)).toString();
            console.log(url);
            console.log(data);
            fetch(url, {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: data
            })
            .then(function() {
                window.location.reload();
            });
        }

        function submitLabel(lblval, segmentID) {
            var ackForm = document.forms['acknowledgeForm_' + segmentID];
            var tdisForm = document.forms['TDISLabelForm_' + segmentID];
            if (ackForm && ackForm.label && tdisForm && tdisForm.label) {
                let newlabelvalue = ackForm.label.value;
                if (newlabelvalue.length > 1) {
                    tdisForm.label.value = newlabelvalue;
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

    jQuery(function () {
        jQuery("#createLabel_<%=Encode.forJavaScript(segmentID)%>").click(function () {
            jQuery.ajax({
                type: "POST",
                url: '<%=request.getContextPath()%>' + "/lab/CA/ALL/createLabelTDIS.do",
                dataType: "json",
                data: {
                    lab_no: jQuery("#labNum_<%=Encode.forJavaScript(segmentID)%>").val(),
                    accessionNum: jQuery("#accNum").val(),
                    label: jQuery("#label_<%=Encode.forJavaScript(segmentID)%>").val(),
                    ajaxcall: true
                }
            })
            jQuery("#labelspan_<%=Encode.forJavaScript(segmentID)%> i").text(jQuery("#label_<%=Encode.forJavaScript(segmentID)%>").val());
            var ackForm = document.forms['acknowledgeForm_<%=Encode.forJavaScript(segmentID)%>'];
            if (ackForm && ackForm.label) ackForm.label.value = "";
        });
    });

    var _in_window = <%= request.getParameter("inWindow") == null || "true".equals(request.getParameter("inWindow")) %>;
    var contextpath = "<%=request.getContextPath()%>";

</script>

<script>
    //first check to see if lab is linked, if it is, we can send the demographicNo to the macro
    function runMacro(name, formid, closeOnSuccess) {
        var url = '<%=request.getContextPath()%>/documentManager/inboxManage.do';
        var data = 'method=isLabLinkedToDemographic&labid=<%= Encode.forJavaScript(segmentID) %>';
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
        var data = new URLSearchParams(new FormData(formEl)).toString();

        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: data
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

        <c:if test="${showModal}">
            <!-- Modal -->
            <div id="labVersionInfoModal" title="Lab Version Information" style="display:none;">
                <div class="lab-version-modal">
                    <c:if test="${hasDuplicateInfo}">
                        <!-- Duplicate Information Section -->
                        <div class="info-section">
                            <div class="modal-title">Duplicate Lab Version Alert</div>
                            <p>Warning: You are viewing a version of a lab result that appears to be a duplicate of previously received version <c:out value="${duplicateOfLab}" />.</p>
                            <p style="margin-top: 5px">There is likely no new data in this version, but please double check regardless.</p>
                        </div>
                    </c:if>

                    <c:if test="${hasMissingTests}">
                        <!-- Missing Tests Information Section -->
                        <div class="info-section">
                            <div class="modal-title">Missing Test Results</div>
                            <p>Warning: At least the following tests were not included in this version of the lab results:</p>
                            <div class="test-list">
                                <c:forEach var="entry" items="${missingTests}">
                                    <div class="test-item">
                                        <span>${entry.key}</span>
                                        <span class="status">${entry.value.description}</span>
                                    </div>
                                </c:forEach>
                            </div>
                        </div>
                    </c:if>
                </div>
            </div>

            <script>
                // Include a short delay before showing modal to improve user experience
                setTimeout(function () {
                    jQuery(document).ready(function () {
                        jQuery("#labVersionInfoModal").dialog({ modal: true, width: 500 });
                    });
                }, 300);
            </script>
        </c:if>

    <form name="reassignForm_<%= Encode.forHtmlAttribute(segmentID) %>" method="post" action="<%= request.getContextPath() %>/lab/CA/ALL/Forward.do">
        <input type="hidden" name="flaggedLabs" value="<%= Encode.forHtmlAttribute(segmentID) %>"/>
        <input type="hidden" name="selectedProviders" value=""/>
        <input type="hidden" name="favorites" value=""/>
        <input type="hidden" name="labType" value="HL7"/>
        <input type="hidden" name="labType<%= Encode.forHtmlAttribute(segmentID) %>HL7" value="imNotNull"/>
        <input type="hidden" id="providerNo_<%= Encode.forHtmlAttribute(segmentID) %>" name="providerNo"
               value="<%= providerNo %>"/>
    </form>

    <form name="TDISLabelForm_<%= Encode.forHtmlAttribute(segmentID) %>" method='POST'
          action="<%=request.getContextPath()%>/lab/CA/ALL/createLabelTDIS.do">
        <input type="hidden" id="labNum_<%= Encode.forHtmlAttribute(segmentID) %>" name="lab_no" value="<%=lab_no%>">
        <input type="hidden" id="label_<%= Encode.forHtmlAttribute(segmentID) %>" name="label" value="<%=label%>">
    </form>

    <form name="acknowledgeForm_<%= Encode.forHtmlAttribute(segmentID) %>"
          id="acknowledgeForm_<%= Encode.forHtmlAttribute(segmentID) %>" method="post" onsubmit="javascript:void(0);"
          method="post" action="javascript:void(0);">

        <table width="100%" height="100%" border="0" cellspacing="0" cellpadding="0">
            <tr>
                <td valign="top">
                    <table class="MainTableTopRowRightColumn" width="100%" border="0" cellspacing="0" cellpadding="3">
                        <tr>
                            <td>
                                <input type="hidden" name="segmentID"
                                       value="<%= Encode.forHtmlAttribute(segmentID) %>"/>
                                <input type="hidden" name="multiID" value="<%= Encode.forHtmlAttribute(multiLabId) %>"/>
                                <input type="hidden" name="providerNo" id="providerNo"
                                       value="<%= Encode.forHtmlAttribute(providerNo) %>"/>
                                <input type="hidden" name="status" value="<%=Encode.forHtmlAttribute(labStatus)%>"
                                       id="labStatus_<%=Encode.forHtmlAttribute(segmentID)%>"/>
                                <input type="hidden" name="comment" value=""/>
                                <input type="hidden" name="labType" value="HL7"/>
                                <%
                                    if (!ackFlag) {
                                %>

                                <%
                                    UserPropertyDAO upDao = SpringUtils.getBean(UserPropertyDAO.class);
                                    UserProperty up = upDao.getProp(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), UserProperty.LAB_MACRO_JSON);
                                    if (up != null && !StringUtils.isEmpty(up.getValue())) {
                                %>
                                <div class="dropdown">
                                    <button class="dropbtn">Macros</button>
                                    <div class="dropdown-content">
                                        <%
                                            try {
                                                ObjectMapper mapper = new ObjectMapper();
                                                JsonNode macros = mapper.readTree(up.getValue());
                                                if (macros != null && macros.isArray()) {
                                                    for (int x = 0; x < macros.size(); x++) {
                                                        JsonNode macro = macros.get(x);
                                                        String name = macro.get("name").asText();
                                                        boolean closeOnSuccess = macro.has("closeOnSuccess") && macro.get("closeOnSuccess").asBoolean();

                                        %><a href="javascript:void(0);"
                                             onClick="runMacro('<%=name%>','acknowledgeForm_<%=Encode.forJavaScript(segmentID)%>',<%=closeOnSuccess%>)"><%=name %>
                                    </a><%
                                                }
                                            }
                                        } catch (Exception e) {
                                            MiscUtils.getLogger().warn("Invalid JSON for lab macros", e);
                                        }
                                    %>

                                    </div>
                                </div>
                                <% } %>

                                <input type="button"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.btnAcknowledge"/>"
                                       onclick="<%=ackLabFunc%>">
                                <% } %>
                                <input type="button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.btnComment"/>"
                                       onclick="return getComment('addComment',<%=Encode.forJavaScript(segmentID)%>);">
                                <input type="button" class="smallButton"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.index.btnForward"/>"
                                       onClick="ForwardSelectedRows(<%=Encode.forJavaScript(segmentID)%> + ':HL7', '', '')">
                                <input type="button" value=" <fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/> "
                                       onClick="window.close()">
                                <input type="button" value=" <fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnPrint"/> "
                                       onClick="printPDF('<%=Encode.forJavaScript(segmentID)%>')">

                                <input type="button" value="Msg"
                                       onclick="handleLab('','<%=Encode.forJavaScript(segmentID)%>','msgLab');"/>
                                <input type="button" value="Tickler"
                                       onclick="handleLab('','<%=Encode.forJavaScript(segmentID)%>','ticklerLab');"/>
                                <input type="button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.btnUnlinkDemo"/>"
                                       onclick="unlinkDemographic(<%=Encode.forJavaScript(segmentID)%>)"/>

                                <% if (searchProviderNo != null) { // null if we were called from e-chart%>
                                <input type="button" value=" <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.btnEChart"/>"
                                       onClick="popupStart(360, 680, '<%= request.getContextPath() %>/oscarMDS/SearchPatient.do?labType=HL7&segmentID=<%= Encode.forJavaScript(segmentID) %>&name=<%=java.net.URLEncoder.encode(handler.getLastName()+", "+handler.getFirstName(), StandardCharsets.UTF_8)%>', 'encounter')">
                                <% } %>
                                <input type="button" value="Req# <%=reqTableID%>" title="Link to Requisition"
                                       onclick="linkreq('<%=Encode.forJavaScript(segmentID)%>','<%=reqID%>');"/>


                                <% if (bShortcutForm) { %>
                                <input type="button" value="<%=formNameShort%>"
                                       onClick="popupStart(700, 1024, '/form/forwardshortcutname.do?formname=<%=formName%>&demographic_no=<%=demographicID%>', '<%=formNameShort%>')"/>
                                <% } %>
                                <% if (bShortcutForm2) { %>
                                <input type="button" value="<%=formName2Short%>"
                                       onClick="popupStart(700, 1024, '/form/forwardshortcutname.do?formname=<%=formName2%>&demographic_no=<%=demographicID%>', '<%=formName2Short%>')"/>
                                <% } %>

                                <% if (recall) {%>
                                <input type="button" value="Recall"
                                       onclick="handleLab('','<%=Encode.forJavaScript(segmentID)%>','msgLabRecall');">
                                <%}%>
                                <%
                                    if (remoteLabKey == null || "".equals(remoteLabKey.length())) {
                                %>


                                <span class="Field2"><i>Next Appointment: <oscar:nextAppt
                                        demographicNo="<%=demographicID%>"/></i></span>
                            </td>
                        </tr>
                        <tr>
                            <td>
                                <% if (!label.equals(null) && !label.equals("")) { %>
                                <button type="button" id="createLabel_<%= Encode.forHtmlAttribute(segmentID) %>"
                                        value="Label"
                                        onclick="submitLabel(this, '<%=Encode.forJavaScript(segmentID)%>');">Label
                                </button>
                                <%} else { %>
                                <button type="button" id="createLabel_<%= Encode.forHtmlAttribute(segmentID) %>"
                                        style="background-color:#6699FF" value="Label"
                                        onclick="submitLabel(this, '<%=Encode.forJavaScript(segmentID)%>');">Label
                                </button>
                                <%} %>
                                <input type="hidden" id="labNum_<%=Encode.forHtmlAttribute(segmentID) %>" name="lab_no"
                                       value="<%=lab_no%>">
                                <input type="text" id="acklabel_<%= Encode.forHtmlAttribute(segmentID) %>" name="label"
                                       value=""/>

                                <% String labelval = "";
                                    if (label != "" && label != null) {
                                        labelval = label;
                                    } else {
                                        labelval = "(not set)";

                                    } %>
                                <span id="labelspan_<%= Encode.forHtmlAttribute(segmentID) %>"
                                      class="Field2"><i><%= Encode.forHtml(labelval) %> </i></span>

                                <% } %>
                            </td>

                        </tr>
                    </table>
                    <table width="100%" border="1" cellspacing="0" cellpadding="3" bgcolor="#9999CC"
                           bordercolordark="#bfcbe3">
                        <%
                            if (multiLabId != null) {
                                String[] multiID = multiLabId.split(",");
                                if (multiID.length > 1) {
                        %>
                        <tr>
                            <td class="Cell" colspan="2" align="middle">
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
                            <td width="66%" align="middle" class="Cell">
                                <div class="Field2">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formDetailResults"/>
                                </div>
                            </td>
                            <td width="33%" align="middle" class="Cell">
                                <div class="Field2">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formResultsInfo"/>
                                </div>
                            </td>
                        </tr>
                        <tr>
                            <td bgcolor="white" valign="top">
                                <table valign="top" border="0" cellpadding="2" cellspacing="0" width="100%">
                                    <tr valign="top">
                                        <td valign="top" width="33%" align="left">
                                            <table width="100%" border="0" cellpadding="2" cellspacing="0"
                                                   valign="top"  <% if (!isLinkedToDemographic) { %>
                                                   bgcolor="orange" <% } %>
                                                   id="DemoTable<%= Encode.forHtmlAttribute(segmentID) %>">
                                                <tr>
                                                    <td valign="top" align="left">
                                                        <table valign="top" border="0" cellpadding="3" cellspacing="0"
                                                               width="100%">
                                                            <tr>
                                                                <td nowrap>
                                                                    <div class="FieldData">
                                                                        <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formPatientName"/>: </strong>
                                                                    </div>
                                                                </td>
                                                                <td nowrap>
                                                                    <div class="FieldData" nowrap="nowrap">
                                                                        <% if (searchProviderNo == null) { // we were called from e-chart%>
                                                                        <a href="javascript:window.close()">
                                                                                <% } else { // we were called from lab module%>
                                                                            <a href="javascript:popupStart(360, 680, '${pageContext.request.contextPath}/oscarMDS/SearchPatient.do?labType=HL7&segmentID=<%= Encode.forJavaScript(segmentID) %>&name=<%=java.net.URLEncoder.encode(handler.getLastName()+", "+handler.getFirstName(), StandardCharsets.UTF_8)%>', 'searchPatientWindow')">
                                                                                <% } %>
                                                                                <%=handler.getPatientName()%>
                                                                            </a>
                                                                    </div>
                                                                </td>
                                                                <td colspan="2"></td>
                                                            </tr>
                                                            <tr>
                                                                <td nowrap>
                                                                    <div class="FieldData">
                                                                        <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formDateBirth"/>: </strong>
                                                                    </div>
                                                                </td>
                                                                <td nowrap>
                                                                    <div class="FieldData" nowrap="nowrap">
                                                                        <%=handler.getDOB()%>
                                                                    </div>
                                                                </td>
                                                                <td colspan="2"></td>
                                                            </tr>
                                                            <tr>
                                                                <td nowrap>
                                                                    <div class="FieldData">
                                                                        <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formAge"/>: </strong>
                                                                    </div>
                                                                </td>
                                                                <td nowrap>
                                                                    <div class="FieldData">
                                                                        <%=handler.getAge()%>
                                                                    </div>
                                                                </td>

                                                            </tr>
                                                            <tr>
                                                                <td nowrap>
                                                                    <div class="FieldData">
                                                                        <strong>
                                                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formHealthNumber"/>
                                                                        </strong>
                                                                    </div>
                                                                </td>
                                                                <td nowrap>
                                                                    <div class="FieldData" nowrap="nowrap">
                                                                        <%=handler.getHealthNum()%>
                                                                    </div>
                                                                </td>
                                                                <td colspan="2"></td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td width="33%" valign="top">
                                                        <table valign="top" border="0" cellpadding="3" cellspacing="0"
                                                               width="100%">
                                                            <tr>
                                                                <td nowrap>
                                                                    <div align="left" class="FieldData">
                                                                        <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formHomePhone"/>: </strong>
                                                                    </div>
                                                                </td>
                                                                <td nowrap>
                                                                    <div align="left" class="FieldData" nowrap="nowrap">
                                                                        <%=handler.getHomePhone()%>
                                                                    </div>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td nowrap>
                                                                    <div align="left" class="FieldData">
                                                                        <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formWorkPhone"/>: </strong>
                                                                    </div>
                                                                </td>
                                                                <td nowrap>
                                                                    <div align="left" class="FieldData" nowrap="nowrap">
                                                                        <%=handler.getWorkPhone()%>
                                                                    </div>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td nowrap>
                                                                    <div class="FieldData">
                                                                        <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formSex"/>: </strong>
                                                                    </div>
                                                                </td>
                                                                <td align="left" nowrap>
                                                                    <div class="FieldData">
                                                                        <%=handler.getSex()%>
                                                                    </div>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td nowrap>
                                                                    <div align="left" class="FieldData">
                                                                        <% if ("ExcellerisON".equals(handler.getMsgType())) { %>
                                                                        <strong>Reported by:</strong>
                                                                        <% } else { %>
                                                                        <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formPatientLocation"/>: </strong>
                                                                        <% } %>
                                                                    </div>
                                                                </td>
                                                                <td nowrap>
                                                                    <div align="left" class="FieldData" nowrap="nowrap">
                                                                        <%=handler.getPatientLocation()%>
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
                            <td bgcolor="white" valign="top">
                                <table width="100%" border="0" cellspacing="0" cellpadding="1">
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
                                            <div class="FieldData" nowrap="nowrap">
                                                <%= handler.getServiceDate() %>
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
                                                <div class="FieldData" nowrap="nowrap">
                                                    <%= ((ExcellerisOntarioHandler) handler).getReportStatusChangeDate() %>
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
                                            <div class="FieldData" nowrap="nowrap">
                                                <%= handler.getRequestDate(0) %>
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
                                            <div class="FieldData" nowrap="nowrap">
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
                                            <div class="FieldData" nowrap="nowrap">
                                                <%=handler.getOrderStatus()%>
                                            </div>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td></td>
                                    </tr>
                                    <tr>
                                        <td nowrap>
                                            <div class="FieldData">
                                                <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formClientRefer"/>:</strong>
                                            </div>
                                        </td>
                                        <td nowrap>
                                            <div class="FieldData" nowrap="nowrap">
                                                <%= handler.getClientRef()%>
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
                                            <div class="FieldData" nowrap="nowrap">
                                                <%= handler.getAccessionNum()%>
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
                                            <div class="FieldData" nowrap="nowrap">
                                                <%= ((ExcellerisOntarioHandler) handler).getAlternativePatientIdentifier()%>
                                            </div>
                                        </td>
                                    </tr>
                                    <% } %>
                                    <% if (handler.getMsgType().equals("MEDVUE")) { %>
                                    <tr>
                                        <td>
                                            <div class="FieldData">
                                                <strong>MEDVUE Encounter Id:</strong>
                                            </div>
                                        </td>
                                        <td>
                                            <div class="FieldData" nowrap="nowrap">
                                                <%= handler.getEncounterId() %>
                                            </div>
                                        </td>
                                    </tr>
                                    <% }
                                        String comment = handler.getNteForPID();
                                        if (comment != null && !comment.equals("")) {%>
                                    <tr>
                                        <td>
                                            <div class="FieldData">
                                                <strong>Remarks:</strong>
                                            </div>
                                        </td>
                                        <td>
                                            <div class="FieldData" nowrap="nowrap">
                                                <%= comment %>
                                            </div>
                                        </td>
                                    </tr>
                                    <%} %>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td bgcolor="white" colspan="2">
                                <table width="100%" border="0" cellpadding="0" cellspacing="0" bordercolor="#CCCCCC">
                                    <tr>
                                        <td bgcolor="white">
                                            <div class="FieldData">
                                                <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formRequestingClient"/>: </strong>
                                                <%= handler.getDocName()%>
                                            </div>
                                        </td>
                                        <%-- <td bgcolor="white">
                                <div class="FieldData">
                                    <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formReportToClient"/>: </strong>
                                        <%= No admitting Doctor for CML messages%>
                                </div>
                            </td> --%>
                                        <td bgcolor="white" align="right">
                                            <div class="FieldData">
                                                <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formCCClient"/>: </strong>
                                                <%= handler.getCCDocs()%>

                                            </div>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td align="center" bgcolor="white" colspan="2" style="padding:0px;" cellspacing="0">
                                <%
                                    String[] multiID = multiLabId.split(",");

                                    for (int mcount = 0; mcount < multiID.length; mcount++) {
                                        if (demographicID != null && !demographicID.equals("")) {
                                            TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);
                                            List<Tickler> LabTicklers = null;
                                            if (demographicID != null) {
                                                LabTicklers = ticklerManager.getTicklerByLabIdAnyProvider(loggedInInfo, Integer.valueOf(multiID[mcount]), Integer.valueOf(demographicID));
                                            }

                                            if (LabTicklers != null && LabTicklers.size() > 0) {
                                %>
                                <div id="ticklerWrap" class="DoNotPrint">
                                    <h3 style="color:#fff"><a href="javascript:void(0)" id="open-ticklers"
                                                              onclick="showHideItem('ticklerDisplay')">View Ticklers</a>
                                        Linked to this Lab</h3><br>

                                    <div id="ticklerDisplay" style="display:none">
                                        <%
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
                                            <table width="100%">
                                                <tr>
                                                    <td><b>Priority:</b> <%=flag%> <%=tickler.getPriority()%>
                                                    </td>
                                                    <td><b>Service Date:</b> <%=tickler.getServiceDate()%>
                                                    </td>
                                                    <td><b>Assigned
                                                        To:</b> <%=tickler.getAssignee() != null ? Encode.forHtml(tickler.getAssignee().getLastName() + ", " + tickler.getAssignee().getFirstName()) : "N/A"%>
                                                    </td>
                                                    <td width="90px">
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
                                        %>
                                    </div><!-- end ticklerDisplay -->
                                </div>
                                <%
                                            }//no ticklers to display

                                        }
                                    }


                                    ReportStatus report;
                                    boolean startFlag = false;
                                    for (int j = multiID.length - 1; j >= 0; j--) {
                                        ackList = AcknowledgementData.getAcknowledgements(multiID[j]);
                                        if (multiID[j].equals(segmentID))
                                            startFlag = true;
                                        if (startFlag) {
                                            //if (ackList.size() > 0){{
                                %>
                                <table width="100%" height="20" cellpadding="2" cellspacing="2">
                                    <tr>
                                        <% if (multiID.length > 1) { %>
                                        <td align="center" bgcolor="white" width="20%" valign="top">
                                            <div class="FieldData">
                                                <b>Version:</b> v<%= j + 1 %>
                                            </div>
                                        </td>
                                        <td align="left" bgcolor="white" width="80%" valign="top">
                                                <% }else{ %>
                                        <td align="center" bgcolor="white">
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
                                                <font color="red"><%= ackStatus %>
                                                </font>
                                                <% if (ackStatus.equals("Acknowledged")) { %>
                                                <%= report.getTimestamp() %>,
                                                <% } %>
                                                <span id="<%=report.getOscarProviderNo() + "_" + segmentID%>commentLabel"><%=report.getComment() == null || report.getComment().equals("") ? "no comment" : "comment : "%></span><span
                                                    id="<%=report.getOscarProviderNo() + "_" + segmentID%>commentText"><%=report.getComment() == null ? "" : report.getComment()%></span>
                                                <br>
                                                <% }
                                                    if (ackList.size() == 0) {
                                                %><font color="red">N/A</font><%
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
                        String highlight = "#E0E0FF";

                        ArrayList<String> headers = handler.getHeaders();
                        int OBRCount = handler.getOBRCount();

                        if (handler.getMsgType().equals("MEDVUE")) { %>
                    <%-- MEDVUE Redirect. --%>
                    <table style="page-break-inside:avoid;" bgcolor="#003399" border="0" cellpadding="0" cellspacing="0"
                           width="100%">
                        <tr>
                            <td colspan="4" height="7">&nbsp;</td>
                        </tr>
                        <tr>
                            <td bgcolor="#FFCC00" width="300" valign="bottom">
                                <div class="Title2">
                                    <%=headers.get(0)%>
                                </div>
                            </td>
                            <%--<td align="right" bgcolor="#FFCC00" width="100">&nbsp;</td>--%>
                            <td width="9">&nbsp;</td>
                            <td width="9">&nbsp;</td>
                            <td width="*">&nbsp;</td>
                        </tr>
                    </table>
                    <table width="100%" border="0" cellspacing="0" cellpadding="2" bgcolor="#CCCCFF"
                           bordercolor="#9966FF" bordercolordark="#bfcbe3" name="tblDiscs" id="tblDiscs">
                        <tr class="Field2">
                            <td width="25%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formTestName"/></td>
                            <td width="15%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formResult"/></td>
                            <td width="5%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formAbn"/></td>
                            <td width="15%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formReferenceRange"/></td>
                            <td width="10%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formUnits"/></td>
                            <td width="15%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formDateTimeCompleted"/></td>
                            <td width="6%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formNew"/></td>
                            <td width="6%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formAnnotate"/></td>
                        </tr>
                        <tr class="TDISRes">
                            <td valign="top" align="left" colspan="8">
                                <pre style="margin:0px 0px 0px 100px;"><b>Radiologist: </b><b><%=handler.getRadiologistInfo()%></b></pre>
                            </td>
                </td>
            </tr>
            <tr class="TDISRes">
                <td valign="top" align="left" colspan="8">
                    <pre style="margin:0px 0px 0px 100px;"><b><%=handler.getOBXComment(1, 1, 1)%></b></pre>
                </td>
                </td>
                <td align="center" valign="top">
                    <a href="javascript:void(0);" title="Annotation"
                       onclick="window.open('<%=request.getContextPath()%>/annotation/annotation.jsp?display=<%=annotation_display%>&amp;table_id=<%=Encode.forJavaScript(segmentID)%>&amp;demo=<%=demographicID%>&amp;other_id=<%=String.valueOf(1) + "-" + String.valueOf(1) %>','anwin','width=400,height=500');">
                        <img src="<%= request.getContextPath() %>/images/notes.gif" alt="rxAnnotation" height="16" width="13" border="0"/>
                    </a>
                </td>
            </tr>
        </table>
        <%-- ALL OTHERS Redirect. --%>
        <% } else {

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

        <table style="page-break-inside:avoid;" bgcolor="#003399" border="0" cellpadding="0" cellspacing="0"
               width="100%">
            <tr>
                <td colspan="4" height="7">&nbsp;</td>
            </tr>
            <tr>
                <td bgcolor="#FFCC00" width="300" valign="bottom">
                    <div class="Title2">
                        <%=headers.get(i)%>
                    </div>
                </td>
                <%--<td align="right" bgcolor="#FFCC00" width="100">&nbsp;</td>--%>
                <td width="9">&nbsp;</td>
                <td width="9">&nbsp;</td>
                <td width="*">&nbsp;</td>
            </tr>
        </table>
        <% if ((handler.getMsgType().equals("MEDITECH") && isUnstructuredDoc) ||
                (handler.getMsgType().equals("MEDITECH") && ((MEDITECHHandler) handler).isReportData())) { %>
        <table style="width:100%;border-collapse:collapse;" bgcolor="#CCCCFF" bordercolor="#9966FF"
               bordercolordark="#bfcbe3" name="tblDiscs" id="tblDiscs">
            <tr>
                <td colspan="4" style="padding-left:10px;">

                        <%} else if( isUnstructuredDoc){%>
                    <table width="100%" border="0" cellspacing="0" cellpadding="2" bgcolor="#CCCCFF"
                           bordercolor="#9966FF" bordercolordark="#bfcbe3" name="tblDiscs" id="tblDiscs">

                        <tr class="Field2">

                            <td width="20%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formTestName"/></td>
                            <td width="60%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formResult"/></td>
                            <% if ("CLS".equals(handler.getMsgType())) { %>
                            <td width="20%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formDateTimeCompletedCLS"/></td>
                            <% } else { %>
                            <td width="20%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formDateTimeCompleted"/></td>
                            <% } %>

                            <td width="31%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formTestName"/></td>
                            <td width="31%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formResult"/></td>
                            <td width="31%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formDateTimeCompleted"/></td>

                        </tr>
                            <%
						} else {%>
                        <table width="100%" border="0" cellspacing="0" cellpadding="2" bgcolor="#CCCCFF"
                               bordercolor="#9966FF" bordercolordark="#bfcbe3" name="tblDiscs" id="tblDiscs">

                                <% if( handler instanceof MEDITECHHandler && "MIC".equals( ((MEDITECHHandler) handler).getSendingApplication() ) ) { %>
                            <tr>
                                <td colspan="8"></td>
                            </tr>
                            <tr>
                                <td valign="top" align="left" style="padding-left:20px;font-weight:bold;">SPECIMEN
                                    SOURCE:
                                </td>
                                <td valign="top" align="left" style="font-weight:bold;"
                                    colspan="7"><%= ((MEDITECHHandler) handler).getSpecimenSource(i) %>
                                </td>
                            </tr>
                            <tr>
                                <td valign="top" align="left" style="padding-left:20px;font-weight:bold;">SPECIMEN
                                    DESCRIPTION:
                                </td>
                                <td valign="top" align="left" style="font-weight:bold;"
                                    colspan="7"><%= ((MEDITECHHandler) handler).getSpecimenDescription(i) %>
                                </td>
                            </tr>
                            <tr>
                                <td colspan="8"></td>
                            </tr>
                                <% }%>

                            <tr class="Field2">
                                <td width="25%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formTestName"/></td>
                                <td width="15%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formResult"/></td>
                                <td width="5%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formAbn"/></td>
                                <td width="15%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formReferenceRange"/></td>
                                <td width="10%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formUnits"/></td>
                                <% if ("CLS".equals(handler.getMsgType())) { %>
                                <td width="15%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formDateTimeCompletedCLS"/></td>
                                <% } else { %>
                                <td width="15%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formDateTimeCompleted"/></td>
                                <% } %>
                                <td width="6%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formNew"/></td>
                                <td width="6%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formAnnotate"/></td>
                                <% if ("ExcellerisON".equals(handler.getMsgType())) { %>
                                <td width="6%" align="middle" valign="bottom" class="Cell">License #</td>
                            </tr>
                                <% } %>
            </tr>

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
                                <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>" >
                                    <td valign="top" align="left"><span style="font-size:16px;font-weight: bold;"><%=handler.getOBRName(j)%></span></td>
                                    <td colspan="1"><%=orderRequestStatus%></td>
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
                        } else if (handler.getMsgType().equals("PFHT") || handler.getMsgType().equals("CML") || handler.getMsgType().equals("HHSEMR")) {
                            b2 = true;
                        }

                        if (!fail && b1 && b2 && b3) { // <<--  DNS only needed for MDS messages

                            String obrName = handler.getOBRName(j);
                            b1 = !obrFlag && !obrName.equals("");
                            b2 = !(obxName.contains(obrName));
                            b3 = !(obxCount < 2 && !isUnstructuredDoc);
                            if (b1 && b2 && b3 && !handler.getMsgType().equals("ExcellerisON")) {
            %>
            <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>">
                <td valign="top" align="left"><span style="font-size:16px;font-weight: bold;"><%=obrName%></span></td>
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

            <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>" class="<%=lineClass%>">
                <td valign="top" align="left"><%= obrFlag ? "&nbsp; &nbsp; &nbsp;" : "&nbsp;" %><a
                        href="javascript:popupStart('660','900','${pageContext.request.contextPath}/lab/CA/ON/labValues.jsp?testName=<%=obxName%>&demo=<%=demographicID%>&labType=HL7&identifier=<%= URLEncoder.encode(handler.getOBXIdentifier(j, k).replaceAll("&","%26"),"UTF-8") %>')"><%=obxName %>
                </a>
                    &nbsp;<%if (loincCode != null) { %>
                    <a href="javascript:popupStart('660','1000','http://apps.nlm.nih.gov/medlineplus/services/mpconnect.cfm?mainSearchCriteria.v.cs=2.16.840.1.113883.6.1&mainSearchCriteria.v.c=<%=loincCode%>&informationRecipient.languageCode.c=en')">
                        info</a>
                    <%} %>
                </td>
                <td align="right">
                    <%= handler.getOBXResult(j, k) %>
                    <%= handler.isTestResultBlocked(j, k) ? "<a href='#' title='Do Not Disclose Without Explicit Patient Consent'>(BLOCKED)</a>" : ""%>
                </td>

                <td align="center">
                    <%= handler.getOBXAbnormalFlag(j, k)%>
                </td>
                <td align="left"><%=handler.getOBXReferenceRange(j, k)%>
                </td>
                <td align="left"><%=handler.getOBXUnits(j, k) %>
                </td>
                <td align="center"><%= handler.getTimeStamp(j, k) %>
                </td>
                <td align="center"><%= handler.getOBXResultStatus(j, k) %>
                </td>
                <td align="center" valign="top">
                    <a href="javascript:void(0);" title="Annotation"
                       onclick="window.open('<%=request.getContextPath()%>/annotation/annotation.jsp?display=<%=annotation_display%>&amp;table_id=<%=Encode.forJavaScript(segmentID)%>&amp;demo=<%=demographicID%>&amp;other_id=<%=String.valueOf(j) + "-" + String.valueOf(k) %>','anwin','width=400,height=500');">
                        <%if (!isPrevAnnotation) { %><img src="<%= request.getContextPath() %>/images/notes.gif" alt="rxAnnotation" height="16"
                                                          width="13" border="0"/><%} else { %><img
                            src="<%= request.getContextPath() %>/images/filledNotes.gif" alt="rxAnnotation" height="16" width="13"
                            border="0"/> <%} %>
                    </a>
                </td>
            </tr>
            <% } else if (handler.getOBXIdentifier(j, k).equals(headers.get(i)) && obxName.equals("")) { %>
            <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>" class="NormalRes">
                <td valign="top" align="left" colspan="8">
                    <pre style="margin:0px 0px 0px 100px;"><%=handler.getOBXResult(j, k)%><%= handler.isTestResultBlocked(j, k) ? "<a href='#' title='Do Not Disclose Without Explicit Patient Consent'>(BLOCKED)</a>" : ""%></pre>
                </td>

            </tr>
            <% }

            } else if (handler.getMsgType().equals("PFHT") || handler.getMsgType().equals("HHSEMR") || handler.getMsgType().equals("CML")) {
                if (!obxName.equals("")) { %>
            <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>" class="<%=lineClass%>">
                <td valign="top" align="left"><%= obrFlag ? "&nbsp; &nbsp; &nbsp;" : "&nbsp;" %><a
                        href="javascript:popupStart('660','900','${pageContext.request.contextPath}/lab/CA/ON/labValues.jsp?testName=<%=obxName%>&demo=<%=demographicID%>&labType=HL7&identifier=<%= URLEncoder.encode(handler.getOBXIdentifier(j, k).replaceAll("&","%26"),"UTF-8") %>')"><%=obxName %>
                </a>
                    &nbsp;
                    <%if (loincCode != null) { %>
                    <a href="javascript:popupStart('660','1000','http://apps.nlm.nih.gov/medlineplus/services/mpconnect.cfm?mainSearchCriteria.v.cs=2.16.840.1.113883.6.1&mainSearchCriteria.v.c=<%=loincCode%>&informationRecipient.languageCode.c=en')">
                        info</a>
                    <%} %></td>
                <td align="right">
                    <%= handler.getOBXResult(j, k) %>
                    <%= handler.isTestResultBlocked(j, k) ? "<a href='#' title='Do Not Disclose Without Explicit Patient Consent'>(BLOCKED)</a>" : ""%>
                </td>

                <td align="center">
                    <%= handler.getOBXAbnormalFlag(j, k)%>
                </td>
                <td align="left"><%=handler.getOBXReferenceRange(j, k)%>
                </td>
                <td align="left"><%=handler.getOBXUnits(j, k) %>
                </td>
                <td align="center"><%= handler.getTimeStamp(j, k) %>
                </td>
                <td align="center"><%= handler.getOBXResultStatus(j, k) %>
                </td>
                <td align="center" valign="top">
                    <a href="javascript:void(0);" title="Annotation"
                       onclick="window.open('<%=request.getContextPath()%>/annotation/annotation.jsp?display=<%=annotation_display%>&amp;table_id=<%=Encode.forJavaScript(segmentID)%>&amp;demo=<%=demographicID%>&amp;other_id=<%=String.valueOf(j) + "-" + String.valueOf(k) %>','anwin','width=400,height=500');">
                        <%if (!isPrevAnnotation) { %><img src="<%= request.getContextPath() %>/images/notes.gif" alt="rxAnnotation" height="16"
                                                          width="13" border="0"/><%} else { %><img
                            src="<%= request.getContextPath() %>/images/filledNotes.gif" alt="rxAnnotation" height="16" width="13"
                            border="0"/> <%} %>
                    </a>
                </td>
            </tr>

            <%} else { %>
            <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>" class="NormalRes">
                <td valign="top" align="left" colspan="8">
                    <pre style="margin:0px 0px 0px 100px;"><%=handler.getOBXResult(j, k)%><%= handler.isTestResultBlocked(j, k) ? "<a href='#' title='Do Not Disclose Without Explicit Patient Consent'>(BLOCKED)</a>" : ""%></pre>
                </td>

            </tr>
            <%
                }
                if (!handler.getNteForOBX(j, k).equals("") && handler.getNteForOBX(j, k) != null) {
            %>
            <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>" class="NormalRes">
                <td valign="top" align="left" colspan="8">
                    <pre style="margin:0px 0px 0px 100px;"><%=handler.getNteForOBX(j, k)%></pre>
                </td>
            </tr>
            <% }
                for (l = 0; l < handler.getOBXCommentCount(j, k); l++) {%>
            <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>" class="NormalRes">
                <td valign="top" align="left" colspan="8">
                    <pre style="margin:0px 0px 0px 100px;"><%=handler.getOBXComment(j, k, l)%></pre>
                </td>
            </tr>
            <%
                }

            } else if ((!handler.getOBXResultStatus(j, k).equals("TDIS") && handler.getMsgType().equals("Spire"))) {
            %>
            <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>" class="<%=lineClass%>">
                <td valign="top" align="left"><%= obrFlag ? "&nbsp; &nbsp; &nbsp;" : "&nbsp;" %><a
                        href="javascript:popupStart('660','900','${pageContext.request.contextPath}/lab/CA/ON/labValues.jsp?testName=<%=obxName%>&demo=<%=demographicID%>&labType=HL7&identifier=<%= URLEncoder.encode(handler.getOBXIdentifier(j, k).replaceAll("&","%26"),"UTF-8") %>')"><%=obxName %>
                </a>
                    &nbsp;<%if (loincCode != null) { %>
                    <a href="javascript:popupStart('660','1000','http://apps.nlm.nih.gov/medlineplus/services/mpconnect.cfm?mainSearchCriteria.v.cs=2.16.840.1.113883.6.1&mainSearchCriteria.v.c=<%=loincCode%>&informationRecipient.languageCode.c=en')">
                        info</a>
                    <%} %></td>
                <% if (handler.getOBXResult(j, k).length() > 20) {
                %>

                <td align="left" colspan="4">
                    <%= handler.getOBXResult(j, k) %>
                    <%= handler.isTestResultBlocked(j, k) ? "<a href='#' title='Do Not Disclose Without Explicit Patient Consent'>(BLOCKED)</a>" : ""%>
                </td>

                <% String abnormalFlag = handler.getOBXAbnormalFlag(j, k);
                    if (abnormalFlag != null && abnormalFlag.length() > 0) {
                %>
                <td align="center">
                    <%= abnormalFlag%>
                </td>
                <% } %>

                <% String refRange = handler.getOBXReferenceRange(j, k);
                    if (refRange != null && refRange.length() > 0) {
                %>
                <td align="left"><%=refRange%>
                </td>
                <% } %>

                <% String units = handler.getOBXUnits(j, k);
                    if (units != null && units.length() > 0) {
                %>
                <td align="left"><%=units %>
                </td>
                <% } %>
                <%
                } else {
                %>
                <td align="right" colspan="1">
                    <%= handler.getOBXResult(j, k) %>
                    <%= handler.isTestResultBlocked(j, k) ? "<a href='#' title='Do Not Disclose Without Explicit Patient Consent'>(BLOCKED)</a>" : ""%>
                </td>
                <td align="center"><%= handler.getOBXAbnormalFlag(j, k)%>
                </td>
                <td align="left"><%=handler.getOBXReferenceRange(j, k)%>
                </td>
                <td align="left"><%=handler.getOBXUnits(j, k) %>
                </td>
                <%
                    }
                %>

                <td align="center"><%= handler.getTimeStamp(j, k) %>
                </td>
                <td align="center"><%= handler.getOBXResultStatus(j, k) %>
                </td>

                <td align="center" valign="top">
                    <a href="javascript:void(0);" title="Annotation"
                       onclick="window.open('<%=request.getContextPath()%>/annotation/annotation.jsp?display=<%=annotation_display%>&amp;table_id=<%=Encode.forJavaScript(segmentID)%>&amp;demo=<%=demographicID%>&amp;other_id=<%=String.valueOf(j) + "-" + String.valueOf(k) %>','anwin','width=400,height=500');">
                        <%if (!isPrevAnnotation) { %><img src="<%= request.getContextPath() %>/images/notes.gif" alt="rxAnnotation" height="16"
                                                          width="13" border="0"/><%} else { %><img
                            src="<%= request.getContextPath() %>/images/filledNotes.gif" alt="rxAnnotation" height="16" width="13"
                            border="0"/> <%} %>
                    </a>
                </td>
            </tr>

            <%for (l = 0; l < handler.getOBXCommentCount(j, k); l++) {%>
            <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>" class="NormalRes">
                <td valign="top" align="left" colspan="8">
                    <pre style="margin:0px 0px 0px 100px;"><%=handler.getOBXComment(j, k, l)%></pre>
                </td>
            </tr>
            <%
                }


            } else if ((!handler.getOBXResultStatus(j, k).equals("TDIS") && !handler.getMsgType().equals("EPSILON"))) {

                if (isUnstructuredDoc) {
            %>
            <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>" class="<%="NarrativeRes"%>"><%
                                   			if(handler.getOBXIdentifier(j, k).equalsIgnoreCase(handler.getOBXIdentifier(j, k-1)) && (obxCount>1) && ! handler.getMsgType().equals("MEDITECH") ){%>
                <td valign="top" align="left"><%= obrFlag ? "&nbsp; &nbsp; &nbsp;" : "&nbsp;" %><a
                        href="javascript:popupStart('660','900','${pageContext.request.contextPath}/lab/CA/ON/labValues.jsp?testName=<%=obxName%>&demo=<%=demographicID%>&labType=HL7&identifier='<%= URLEncoder.encode(handler.getOBXIdentifier(j, k).replaceAll("&","%26"),"UTF-8")%>')"></a><%
                                   			}else if(! handler.getMsgType().equals("MEDITECH") ) {%>
                <td valign="top" align="left"><%= obrFlag ? "&nbsp; &nbsp; &nbsp;" : "&nbsp;" %><a
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
                <td align="left"><%= rtfText + disclaimer %>
                </td>
                    <%}else{%>
                <td align="left">
                    <span><%= handler.getOBXResult(j, k) %><%= handler.isTestResultBlocked(j, k) ? "<a href='#' title='Do Not Disclose Without Explicit Patient Consent'>(BLOCKED)</a>" : ""%></span>
                </td>
                    <%} %>

                    <% if(handler.getTimeStamp(j, k).equals(handler.getTimeStamp(j, k-1)) && (obxCount>1)){ %>
                <td align="center"></td>
                    <%} else {%>
                <td align="center"><%= handler.getTimeStamp(j, k) %>
                </td>
                    <%}

                                       		} else {//if it isn't a PATHL7 doc %>

            <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>" class="<%=lineClass%>">

                    <% if(handler.getMsgType().equals("PATHL7") && !isAllowedDuplicate && (obxCount>1) && handler.getOBXIdentifier(j, k).equalsIgnoreCase(handler.getOBXIdentifier(j, k-1)) && (handler.getOBXValueType(j, k).equals("TX") || handler.getOBXValueType(j, k).equals("FT"))){%>
                <td valign="top" align="left"><%= obrFlag ? "&nbsp; &nbsp; &nbsp;" : "&nbsp;" %><a
                        href="javascript:popupStart('660','900','${pageContext.request.contextPath}/lab/CA/ON/labValues.jsp?testName=<%=obxName%>&demo=<%=demographicID%>&labType=HL7&identifier=<%= URLEncoder.encode(handler.getOBXIdentifier(j, k).replaceAll("&","%26"),"UTF-8") %>')"></a><%
                                   			} else {


                               					if(handler instanceof AlphaHandler && lastObxSetId.equals(((AlphaHandler)handler).getObxSetId(j, k))) {
								%>
                <td></td>
                    <% } else { %>
                <td valign="top" align="left"><%= obrFlag ? "&nbsp; &nbsp; &nbsp;" : "&nbsp;" %><a
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
                    <pre style="font-family:Courier New, monospace;">       <%= handler.getOBXResult(j, k) %><%= handler.isTestResultBlocked(j, k) ? "<a href='#' title='Do Not Disclose Without Explicit Patient Consent'>(BLOCKED)</a>" : ""%></pre>
                </td>
                    <%
                                       			lastObxSetId = ((AlphaHandler)handler).getObxSetId(j,k);

                                           } else if(handler instanceof PATHL7Handler && "FT".equals(handler.getOBXValueType(j, k)) && (handler.getOBXReferenceRange(j,k).isEmpty() && handler.getOBXUnits(j,k).isEmpty())){
                                        	  %>
                <td colspan="4">
                    <%= handler.getOBXResult(j, k) %>
                    <%= handler.isTestResultBlocked(j, k) ? "<a href='#' title='Do Not Disclose Without Explicit Patient Consent'>(BLOCKED)</a>" : ""%>
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
                <td align="left" colspan="4">
                    <%= handler.getOBXResult(j, k) %>
                    <%= handler.isTestResultBlocked(j, k) ? "<a href='#' title='Do Not Disclose Without Explicit Patient Consent'>(BLOCKED)</a>" : ""%>
                </td>

                    <%
                                           		}

                                           		else if(handler.getMsgType().equals("MEDITECH")  && isUnstructuredDoc ) {
                                           	%>

                <pre>
					                             		<%= handler.getOBXResult(j, k) %><%= handler.isTestResultBlocked(j, k) ? "<a href='#' title='Do Not Disclose Without Explicit Patient Consent'>(BLOCKED)</a>" : ""%>
					                             		</pre>

                    <% } else if(handler.getMsgType().equals("MEDITECH")  && ((MEDITECHHandler) handler).isReportData() ) { %>
            <tr>
                <td>
                    <%= handler.getOBXResult(j, k) %>
                    <%= handler.isTestResultBlocked(j, k) ? "<a href='#' title='Do Not Disclose Without Explicit Patient Consent'>(BLOCKED)</a>" : ""%>
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
            <td align="<%=align%>"><a
                    href="<%=request.getContextPath() %>/lab/DownloadEmbeddedDocumentFromLab.do?labNo=<%= Encode.forHtmlAttribute(segmentID) %>&segment=<%=j%>&group=<%=k%><%=legacy%>">PDF
                Report</a></td>
            <%
            } else {
            %>
            <td align="<%=align%>">
                <% if (handler.getMsgType().equals("ExcellerisON") && !((ExcellerisOntarioHandler) handler).getOBXSubId(j, k).isEmpty()) { %>
                <em><%= ((ExcellerisOntarioHandler) handler).getOBXSubIdWithObservationValue( j, k) %></em>
                <% } else { %>
                <%= handler.getOBXResult(j, k) %>
                <% } %>
                <%= handler.isTestResultBlocked(j, k) ? "<a href='#' title='Do Not Disclose Without Explicit Patient Consent'>(BLOCKED)</a>" : ""%>
            </td>

            <% } %>
            <td align="center">
                <%= handler.getOBXAbnormalFlag(j, k)%>
            </td>
            <td align="left"><%=handler.getOBXReferenceRange(j, k)%>
            </td>
            <td align="left"><%=handler.getOBXUnits(j, k) %>
            </td>

            <%}%>

            <td align="center"><%= handler.getTimeStamp(j, k) %>
            </td>
            <td align="center">
                <%
                    String status = handler.getOBXResultStatus(j, k);
                    if ("GDML".equals(handler.getMsgType()) && ((GDMLHandler) handler).isTestResultBlocked(j, k)) {
                        if (!StringUtils.isEmpty(status)) {
                            status += "/";
                        }
                        status += "BLOCKED";
                    }
                %>
                <%=status %>

            </td>
            <td align="center" valign="top"><a href="javascript:void(0);" title="Annotation"
                                               onclick="window.open('<%=request.getContextPath()%>/annotation/annotation.jsp?display=<%=annotation_display%>&amp;table_id=<%=Encode.forJavaScript(segmentID)%>&amp;demo=<%=demographicID%>&amp;other_id=<%=String.valueOf(j) + "-" + String.valueOf(k) %>','anwin','width=400,height=500');">
                <%if (!isPrevAnnotation) { %><img src="<%= request.getContextPath() %>/images/notes.gif" alt="rxAnnotation" height="16"
                                                  width="13" border="0"/><%} else { %><img
                    src="<%= request.getContextPath() %>/images/filledNotes.gif" alt="rxAnnotation" height="16" width="13" border="0"/> <%} %>
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
            <td><%= !currentLicenseNo.equals(lastLicenseNo) ? currentLicenseNo : ""%>
            </td>
            <% } %>
            </tr>

            <%
                }

                for (l = 0; l < handler.getOBXCommentCount(j, k); l++) {
            %>
            <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>" class="NormalRes">
                <td valign="top" align="left" colspan="8">
                    <pre style="margin:0px 0px 0px 100px;"><%=handler.getOBXComment(j, k, l)%></pre>
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
            <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>" class="TDISRes">
                <td valign="top" align="left" colspan="8">
                    <pre style="margin:0px 0px 0px 100px;"><%=handler.getOBXComment(j, k, l)%></pre>
                </td>
                <td align="center" valign="top">
                    <a href="javascript:void(0);" title="Annotation"
                       onclick="window.open('<%=request.getContextPath()%>/annotation/annotation.jsp?display=<%=annotation_display%>&amp;table_id=<%=Encode.forJavaScript(segmentID)%>&amp;demo=<%=demographicID%>&amp;other_id=<%=String.valueOf(1) + "-" + String.valueOf(1) %>','anwin','width=400,height=500');">
                        <%if (!isPrevAnnotation) { %><img src="<%= request.getContextPath() %>/images/notes.gif" alt="rxAnnotation" height="16"
                                                          width="13" border="0"/><%} else { %><img
                            src="<%= request.getContextPath() %>/images/filledNotes.gif" alt="rxAnnotation" height="16" width="13"
                            border="0"/> <%} %>
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


                if (!handler.getMsgType().equals("PFHT")) {
                    if (headers.get(i).equals(handler.getObservationHeader(j, 0))) {
            %>
            <%
                for (k = 0; k < handler.getOBRCommentCount(j); k++) {
                    // the obrName should only be set if it has not been
                    // set already which will only have occured if the
                    // obx name is "" or if it is the same as the obr name
                    if (!obrFlag && handler.getOBXName(j, 0).equals("")) {
            %>
            <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>">
                <td valign="top" align="left"><%=handler.getOBRName(j)%>
                </td>
                <td colspan="6">&nbsp;</td>
            </tr>
            <%
                    obrFlag = true;
                }
            %>
            <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>" class="NormalRes">
                <td valign="top" align="left" colspan="1"></td>
                <td valign="top" align="left" colspan="7">
                    <pre style="margin:0px 0px 0px 0px;"><%=handler.getOBRComment(j, k)%></pre>
                </td>
            </tr>
            <% if (!handler.getMsgType().equals("HHSEMR") || !handler.getMsgType().equals("TRUENORTH")) {
                if (handler.getOBXName(j, k).equals("")) {
                    String result = handler.getOBXResult(j, k);%>
            <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>">
                <td colspan="7" valign="top" align="left"><%=result%>
                </td>
            </tr>
            <%
                                        }
                                    }
                                }//end for k=0


                            }//end if handler.getObservation..
                        } // end for if (PFHT)

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
                <td width="25%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formTestName"/></td>
                <td width="15%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formResult"/></td>
                <td width="15%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formProvider"/></td>
                <td width="15%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formDateTimeCompleted"/></td>
                <td width="6%" align="middle" valign="bottom" class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formNew"/></td>
            </tr>
            <%
                }

                for (int m = 0; m < numZDS; m++) {
            %>
            <tr bgcolor="<%=(lineNumber % 2 == 1 ? highlight : "")%>" class="<%=lineClass%>">
                <td valign="top" align="left"><%=((SpireHandler) handler).getZDSName(m)%>
                </td>
                <td align="right"><%= ((SpireHandler) handler).getZDSResult(m) %>
                </td>
                <td align="center"><%= ((SpireHandler) handler).getZDSProvider(m) %>
                </td>
                <td align="center"><%= ((SpireHandler) handler).getZDSTimeStamp(m) %>
                </td>
                <td align="center"><%= ((SpireHandler) handler).getZDSResultStatus(m) %>
                </td>
            </tr>
            <%
                        lineNumber++;
                    }
                }

            %>
        </table>
        <%

            } // end for handler.getMsgType().equals("MEDVUE")

        %>
        <%-- FOOTER --%>
        <table width="100%" border="0" cellspacing="0" cellpadding="3" class="MainTableBottomRowRightColumn"
               bgcolor="#003399">
            <tr>
                <td align="left" width="50%">
                    <% if (!ackFlag) { %>
                    <input type="button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.btnAcknowledge"/>"
                           onclick="<%=ackLabFunc%>">
                    <% } %>
                    <input type="button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.btnComment"/>"
                           onclick="return getComment('addComment',<%=Encode.forJavaScript(segmentID)%>);">
                    <input type="button" class="smallButton" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.index.btnForward"/>"
                           onClick="ForwardSelectedRows(<%=Encode.forJavaScript(segmentID)%> + ':HL7', '', '')">
                    <input type="button" value=" <fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/> " onClick="window.close()">
                    <input type="button" value=" <fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnPrint"/> "
                           onClick="printPDF('<%=Encode.forJavaScript(segmentID)%>')">
                    <% if (searchProviderNo != null) { // we were called from e-chart %>
                    <input type="button" value=" <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.btnEChart"/> "
                           onClick="popupStart(360, 680, '${pageContext.request.contextPath}/oscarMDS/SearchPatient.do?labType=HL7&segmentID=<%= Encode.forJavaScript(segmentID) %>&name=<%=java.net.URLEncoder.encode(handler.getLastName()+", "+handler.getFirstName(), StandardCharsets.UTF_8)%>', 'encounter')">

                    <% } %>
                </td>
                <td width="50%" valign="center" align="left">
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
            <%
                for (String lName : allLicenseNames) {
            %>
            <tr>
                <td><%=lName %>
                </td>
            </tr>

            <% } %>
        </table>
        </td>
        </tr>
        </table>

    </form>

    <%String s = "" + System.currentTimeMillis();%>
    <a style="color:white;" href="javascript: void(0);" onclick="showHideItem('rawhl7<%=s%>');">show</a>
    <pre id="rawhl7<%=s%>" style="display:none;"><%=hl7%></pre>
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
