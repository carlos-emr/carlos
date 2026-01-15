<%--

    Copyright (c) 2008-2012 Indivica Inc.

    This software is made available under the terms of the
    GNU General Public License, Version 2, 1991 (GPLv2).
    License details are available via "indivica.ca/gplv2"
    and "gnu.org/licenses/gpl-2.0.html".

--%>
<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@page import="org.apache.commons.lang3.StringUtils,ca.openosp.openo.log.*" %>
<%@page import="java.text.SimpleDateFormat" %>
<%@ page import="ca.openosp.OscarProperties" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_hrm" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_hrm");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
    Logger logger = MiscUtils.getLogger();
    HRMDocumentDao hrmDocumentDao = (HRMDocumentDao) SpringUtils.getBean(HRMDocumentDao.class);
    HRMDocumentToDemographicDao hrmDocumentToDemographicDao = (HRMDocumentToDemographicDao) SpringUtils.getBean(HRMDocumentToDemographicDao.class);
    HRMDocumentToProviderDao hrmDocumentToProviderDao = (HRMDocumentToProviderDao) SpringUtils.getBean(HRMDocumentToProviderDao.class);
    HRMDocumentSubClassDao hrmDocumentSubClassDao = (HRMDocumentSubClassDao) SpringUtils.getBean(HRMDocumentSubClassDao.class);
    HRMSubClassDao hrmSubClassDao = (HRMSubClassDao) SpringUtils.getBean(HRMSubClassDao.class);
    HRMCategoryDao hrmCategoryDao = (HRMCategoryDao) SpringUtils.getBean(HRMCategoryDao.class);
    HRMDocumentCommentDao hrmDocumentCommentDao = (HRMDocumentCommentDao) SpringUtils.getBean(HRMDocumentCommentDao.class);
    HRMProviderConfidentialityStatementDao hrmProviderConfidentialityStatementDao = (HRMProviderConfidentialityStatementDao) SpringUtils.getBean(HRMProviderConfidentialityStatementDao.class);
%>

<%@page import="ca.openosp.openo.hospitalReportManager.*, ca.openosp.openo.hospitalReportManager.model.*, ca.openosp.openo.utility.SpringUtils, ca.openosp.openo.PMmodule.dao.ProviderDao" %>
<%@ page import="java.util.*" %>
<%@ page import="org.apache.logging.log4j.Logger" %>
<%@ page import="ca.openosp.openo.utility.MiscUtils" %>
<%@ page import="ca.openosp.openo.hospitalReportManager.dao.*" %>
<%@ page import="ca.openosp.openo.encounter.data.EctFormData" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="org.owasp.csrfguard.CsrfGuard" %>
<%@ page import="ca.openosp.openo.log.LogAction" %>
<%@ page import="ca.openosp.openo.log.LogConst" %>
<%@ page import="ca.openosp.openo.hospitalReportManager.HRMDisplayReport2Action" %>
<%@ page import="ca.openosp.openo.hospitalReportManager.HRMReportParser" %>
<%@ page import="ca.openosp.openo.hospitalReportManager.HRMReport" %>
<%@ page import="ca.openosp.openo.hospitalReportManager.model.*" %>
<%@ page import="ca.openosp.openo.hospitalReportManager.dao.*" %>
<%@ page import="ca.openosp.openo.hospitalReportManager.model.HRMReportCriteria" %>
<!DOCTYPE html>

<%
    Integer hrmReportId = Integer.parseInt(request.getParameter("id"));
    // Access ModelDriven criteria object from request attributes
    HRMReportCriteria criteria =
        (ca.openosp.openo.hospitalReportManager.model.HRMReportCriteria) request.getAttribute("criteria");
    boolean isListView = criteria != null && criteria.getListView() != null ? criteria.getListView() : false;
    String hrmReportTime = "";
    Integer hrmDuplicateNum = null;
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    HRMDocument document = hrmDocumentDao.findById(hrmReportId).get(0);
    HRMReport hrmReport = null;
    Map<Integer, Date> dupReportDates = new HashMap<Integer, Date>();
    Map<Integer, Date> dupTimeReceived = new HashMap<Integer, Date>();
    HRMDocumentToDemographic demographicLink = null;
    List<HRMDocumentToDemographic> demographicLinkList = null;
    List<HRMCategory> hrmCategories = null;
    List<HRMDocumentToProvider> providerLinkList = null;
    List<HRMDocumentSubClass> subClassList;
    HRMDocumentToProvider thisProviderLink;
    HRMCategory category = null;
    List<HRMDocument> allDocumentsWithRelationship = null;
    List<HRMDocument> children = null;
    List<HRMDocumentComment> documentComments = null;
    String confidentialityStatement = null;


    if (document != null) {
        logger.debug("reading repotFile : " + document.getReportFile());
        hrmReport = HRMReportParser.parseReport(loggedInInfo, document.getReportFile());

        if (hrmReport != null) {
            hrmCategories = hrmCategoryDao.findAll();
            hrmReportTime = document.getTimeReceived().toString();
            hrmDuplicateNum = document.getNumDuplicatesReceived();

            demographicLinkList = hrmDocumentToDemographicDao.findByHrmDocumentId(document.getId());
            demographicLink = (demographicLinkList.size() > 0 ? demographicLinkList.get(0) : null);

            providerLinkList = hrmDocumentToProviderDao.findByHrmDocumentIdNoSystemUser(document.getId());


            subClassList = hrmDocumentSubClassDao.getSubClassesByDocumentId(document.getId());


            thisProviderLink = hrmDocumentToProviderDao.findByHrmDocumentIdAndProviderNo(document.getId(), loggedInInfo.getLoggedInProviderNo());


            if (thisProviderLink != null) {
                thisProviderLink.setViewed(1);
                hrmDocumentToProviderDao.merge(thisProviderLink);
            }

            HRMDocumentSubClass hrmDocumentSubClass = null;
            if (subClassList != null) {
                for (HRMDocumentSubClass temp : subClassList) {
                    if (temp.isActive()) {
                        hrmDocumentSubClass = temp;
                        break;
                    }
                }
            }

            if (document.getHrmCategoryId() != null) {
                List<HRMCategory> categoryResults = hrmCategoryDao.findById(document.getHrmCategoryId());
                if (categoryResults != null && !categoryResults.isEmpty()) {
                    category = categoryResults.get(0);
                }
            } else if (hrmDocumentSubClass != null) {
                String subClassName = hrmDocumentSubClass.getSubClass();
                String subClasMnemonic = hrmDocumentSubClass.getSubClassMnemonic();
                category = hrmCategoryDao.findBySubClassNameMnemonic(hrmDocumentSubClass.getSendingFacilityId(), subClassName + ':' + subClasMnemonic);

                if (category == null) {
                    HRMSubClass subClass = hrmSubClassDao.findApplicableSubClassMapping(document.getReportType(), subClassName, subClasMnemonic, hrmDocumentSubClass.getSendingFacilityId());
                    category = (subClass != null) ? subClass.getHrmCategory() : null;
                }
            } else {
                category = hrmCategoryDao.findBySubClassNameMnemonic("DEFAULT");
            }

            // Get all the other HRM documents that are either a child, sibling, or parent
            allDocumentsWithRelationship = hrmDocumentDao.findAllDocumentsWithRelationship(document.getId());
            request.setAttribute("allDocumentsWithRelationship", allDocumentsWithRelationship);

            // Get all the other HRM documents that are a child of this document
            children = hrmDocumentDao.getAllChildrenOf(document.getId());
            request.setAttribute("children", children);

            documentComments = hrmDocumentCommentDao.getCommentsForDocument(hrmReportId);


            confidentialityStatement = hrmProviderConfidentialityStatementDao.getConfidentialityStatementForProvider(loggedInInfo.getLoggedInProviderNo());


            String duplicateLabIdsString = StringUtils.trimToNull(request.getParameter("duplicateLabIds"));


            if (duplicateLabIdsString != null) {
                String[] duplicateLabIdsStringSplit = duplicateLabIdsString.split(",");
                for (String tempId : duplicateLabIdsStringSplit) {
                    HRMDocument doc = hrmDocumentDao.find(Integer.parseInt(tempId));
                    dupReportDates.put(Integer.parseInt(tempId), doc.getReportDate());
                    dupTimeReceived.put(Integer.parseInt(tempId), doc.getTimeReceived());
                }

            }
        }
    }

    ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);

    if (demographicLink != null) {
        LogAction.addLog((String) session.getAttribute("user"), LogConst.READ, LogConst.CON_HRM, "" + hrmReportId, request.getRemoteAddr(), "" + demographicLink.getDemographicNo());
    } else {
        LogAction.addLog((String) session.getAttribute("user"), LogConst.READ, LogConst.CON_HRM, "" + hrmReportId, request.getRemoteAddr());
    }

    String currentDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

    boolean obgynShortcuts = OscarProperties.getInstance().getProperty("show_obgyn_shortcuts", "false").equalsIgnoreCase("true");
    String formId = "0";


    String btnDisabled = "disabled";
    String demographicNo = "";
    if (demographicLink != null) {
        btnDisabled = "";
        demographicNo = demographicLink.getDemographicNo().toString();

        if (obgynShortcuts) {
            List<EctFormData.PatientForm> formsONAREnhanced = Arrays.asList(EctFormData.getPatientFormsFromLocalAndRemote(LoggedInInfo.getLoggedInInfoFromSession(request), demographicNo, "formONAREnhancedRecord", true));
            if (formsONAREnhanced != null && !formsONAREnhanced.isEmpty()) {
                formId = formsONAREnhanced.get(0).getFormId();
            }
        }
    }
    String csrfTokenJs = "{'" + CsrfGuard.getInstance().getTokenName() + "': '" + CsrfGuard.getInstance().getTokenValue(request) + "'}";

%>


<html>
<head>
    <title>HRM Report</title>
    <script src="${pageContext.request.contextPath}/csrfguard"></script>

    <script type="text/javascript"
            src="${pageContext.request.contextPath}/library/jquery/jquery-1.12.0.min.js"></script>
    <script type="text/javascript"
            src="${pageContext.request.contextPath}/library/jquery/jquery-ui-1.12.1.min.js"></script>
    <script language="javascript" type="text/javascript"
            src="${pageContext.request.contextPath}/share/javascript/Oscar.js"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/share/javascript/prototype.js"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/share/javascript/effects.js"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/share/javascript/controls.js"></script>

    <script type="text/javascript" src="${pageContext.request.contextPath}/share/yui/js/yahoo-dom-event.js"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/share/yui/js/connection-min.js"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/share/yui/js/animation-min.js"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/share/yui/js/datasource-min.js"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/share/yui/js/autocomplete-min.js"></script>
    <script type="text/javascript"
            src="${pageContext.request.contextPath}/js/demographicProviderAutocomplete.js"></script>
    <script type="text/javascript" src="<%=request.getContextPath()%>/hospitalReportManager/hrmActions.js"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>

    <link rel="stylesheet" href="${pageContext.request.contextPath}/library/jquery/jquery-ui-1.12.1.min.css"
          type="text/css"/>
    <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/share/yui/css/fonts-min.css"/>
    <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/share/yui/css/autocomplete.css"/>
    <link rel="stylesheet" type="text/css" media="all"
          href="${pageContext.request.contextPath}/share/css/demographicProviderAutocomplete.css"/>

    <style>


        table {
            width: 100%;
            border: none;
            margin: 0;
            padding: 0;
        }

        textarea {
            width: 100%;
        }

        div[id^='hrmdoc'] {
            display: flex;
            flex-direction: column;
            align-items: stretch;
        }

        #buttonBox {
            order: 1;
        }

        #reportViewer {
            display: flex;
            order: 2;
        }

        #hrmReportContent, #descriptionBox, #commentBox,
        #metadataBox, #infoBox, #duplicateAndSimilarBox, #duplicatesMessage {
            padding: 25px;
            margin: 10px;
            border: 1px solid black;
        }

        #hrmReportContent {
            flex-grow: 2;
            max-width: 100%;
            height: auto;
            width: auto \9;
            vertical-align: middle;
            box-shadow: 0px 1px 3px #333333;
            -webkit-box-shadow: 0px 1px 3px #333333;
            -moz-box-shadow: 0px 1px 3px #333333;
        }

        #descriptionBox {
            order: 4;

        }

        #commentBox {
            order: 5;
        }

        #metadataBox {
            order: 6;
        }

        #duplicateAndSimilarBox {
            order: 3;
        }

        #duplicatesMessage {
            order: 7;
        }

        #duplicatesMessage td, #duplicateAndSimilarBox td {
            text-align: center;
        }

        #infoBox {
            flex-grow: 1;
        }

        #infoBox th {
            text-align: right;
            vertical-align: top;
        }

        #hrmNotice {
            border-bottom: 1px solid black;
            padding-bottom: 15px;
            margin-bottom: 15px;
            font-style: italic;
        }

        .documentLink_statusC {
            background-color: red;
        }

        .documentComment {
            border: 1px solid black;
            margin: 10px;
        }


        #metadataBox th {
            text-align: right;
        }

        .boxButton {
            margin-top: 10px;
        }

        @media print {
            #infoBox {
                display: none;
            }

            .boxButton {
                display: none;
            }

            #hrmHeader {
                display: block;
            }
        }


    </style>

    <%
        // check for errors printing
        if (request.getAttribute("printError") != null && (Boolean) request.getAttribute("printError")) {
    %>
    <script language="JavaScript">
        alert("The HRM Report could not be printed due to an error.");
    </script>
    <% } %>

    <script type="text/javascript">
        var contextpath = "<%=request.getContextPath()%>";

        function popupPatient(height, width, url, windowName, docId, d) {
            if (!d) {
                d = $('demofind' + docId + 'hrm').value;
            }
            urlNew = url + d;
            return popup2(height, width, 0, 0, urlNew, windowName);
        }

        function popupPatientTickler(height, width, url, windowName, docId, d, n) {
            if (!d) {
                d = $('demofind' + docId + 'hrm').value;
            }
            urlNew = url + "method=edit&tickler.demographic_webName=" + n + "&tickler.demographicNo=" + d + "&docType=HRM&docId=" + docId;
            return popup2(height, width, 0, 0, urlNew, windowName);
        }

        function popupPage(vheight, vwidth, varpage) { //open a new popup window
            var page = "" + varpage;
            windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";//360,680
            var popup = window.open(page, "groupno", windowprops);
            if (popup != null) {
                if (popup.opener == null) {
                    popup.opener = self;
                }
                popup.focus();
            }
        }

        function openReport(id) {
            popupPage(700, 1200, 'Display.do?id=' + id);

        }


    </script>
</head>
<body>

<% if (hrmReport == null) { %>
<h1>HRM report not found! Please check the file location.</h1>
<% return;
} %>

<div id="hrmdoc_<%=hrmReportId%>">
    <div id="buttonBox">
        <input type="button" id="msgBtn_<%=hrmReportId%>" value="Msg"
               onclick="popupPatient(700,960,'<%= request.getContextPath() %>/messenger/SendDemoMessage.do?demographic_no=','msg', '<%=hrmReportId%>','<%=demographicNo %>')" <%=btnDisabled %>/>
        <input type="button" id="mainTickler_<%=hrmReportId%>" value="Tickler"
               onClick="popupPatient(710, 1024,'<%= request.getContextPath() %>/tickler/ForwardDemographicTickler.do?docType=HRM&docId=<%=hrmReportId%>&demographic_no=', 'Tickler','<%=hrmReportId%>','<%=demographicNo %>')" <%=btnDisabled %>>
        <input type="button" id="mainEchart_<%=hrmReportId%>"
               value=" <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.btnEChart"/> "
               onClick="popupPatient(710, 1024,'<%= request.getContextPath() %>/oscarEncounter/IncomingEncounter.do?updateParent=false&reason=
               <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.labResults"/>&curDate=<%=currentDate%>>&appointmentNo=&appointmentDate=&startTime=&status=&demographicNo=', 'encounter', '<%=hrmReportId%>','<%=demographicNo %>')" <%=btnDisabled %>>
        <input type="button" id="mainMaster_<%=hrmReportId%>"
               value=" <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.btnMaster"/>"
               onClick="popupPatient(710,1024,'<%= request.getContextPath() %>/demographic/demographiccontrol.jsp?displaymode=edit&dboperation=search_detail&demographic_no=','master','<%=hrmReportId%>','<%=demographicNo %>')" <%=btnDisabled %>>
        <input type="button" id="mainApptHistory_<%=hrmReportId%>"
               value=" <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.btnApptHist"/>"
               onClick="popupPatient(710,1024,'<%= request.getContextPath() %>/demographic/demographiccontrol.jsp?orderby=appttime&displaymode=appt_history&dboperation=appt_history&limit1=0&limit2=25&demographic_no=','ApptHist','<%=hrmReportId%>','<%=demographicNo %>')" <%=btnDisabled %>>
        <% if (obgynShortcuts && demographicLink != null) {%>
        <input type="button" value="AR1-ILI"
               onClick="popupONAREnhanced(290, 625, '<%=request.getContextPath()%>/form/formonarenhancedForm.jsp?demographic_no=<%=demographicNo %>&formId=<%=formId%>&section='+this.value)"/>
        <input type="button" value="AR1-PGI"
               onClick="popupONAREnhanced(225, 590,'<%=request.getContextPath()%>/form/formonarenhancedForm.jsp?demographic_no=<%=demographicNo %>&formId=<%=formId%>&section='+this.value)"/>
        <input type="button" value="AR2-US"
               onClick="popupONAREnhanced(395, 655, '<%=request.getContextPath()%>/form/formonarenhancedForm.jsp?demographic_no=<%=demographicNo %>&formId=<%=formId%>&section='+this.value)"/>
        <input type="button" value="AR2-ALI"
               onClick="popupONAREnhanced(375, 430, '<%=request.getContextPath()%>/form/formonarenhancedForm.jsp?demographic_no=<%=demographicNo %>&formId=<%=formId%>&section='+this.value)"/>
        <input type="button" value="AR2"
               onClick="popupPage(700, 1024, '<%=request.getContextPath()%>/form/formonarenhancedpg2.jsp?demographic_no=<%=demographicNo %>&formId=<%=formId%>&update=true')"/>
        <% } %>
    </div>

    <div id="reportViewer">
        <div id="hrmReportContent">
            <div id="hrmHeader"><b>Demographic Info:</b><br/>
                <%=hrmReport.getLegalName() %> <br/>
                <%=hrmReport.getHCN() %> &nbsp; <%=hrmReport.getHCNVersion() %> &nbsp; <%=hrmReport.getGender() %><br/>
                <b>DOB:</b><%=hrmReport.getDateOfBirthAsString() %>
            </div>


            <div id="hrmNotice">
                This report was received from the Hospital Report Manager (HRM) at <%=(String) hrmReportTime %>.
                <% if (hrmDuplicateNum != null && (hrmDuplicateNum > 0)) { %><br/><i>OSCAR has
                received <%=String.valueOf(hrmDuplicateNum) %> duplicates of this report.</i><% } %>
                <%
                    allDocumentsWithRelationship = (List<HRMDocument>) request.getAttribute("allDocumentsWithRelationship");
                    if (allDocumentsWithRelationship != null && allDocumentsWithRelationship.size() > 1) {
                %>
                <span id="similarNotice">OSCAR has also detected that the following reports are similar:
		<%
            List<Integer> seenBefore = new LinkedList<Integer>();
            for (HRMDocument relationshipDocument : allDocumentsWithRelationship) {
                if (!seenBefore.contains(relationshipDocument.getId().intValue())) { %>
			<span class="documentLink_status<%=relationshipDocument.getReportStatus() %>"
                  title="<%=relationshipDocument.getReportDate().toString() %>">
			<% if (relationshipDocument.getId().intValue() != hrmReportId.intValue()) { %><a
                    href="<%=request.getContextPath() %>/hospitalReportManager/Display.do?id=<%=relationshipDocument.getId() %>&segmentId=<%=relationshipDocument.getId() %> "><% } %>[<%=relationshipDocument.getId() %>]<% if (relationshipDocument.getId().intValue() != hrmReportId.intValue()) { %></a><% } %>
			</span>&nbsp;&nbsp;
		<% seenBefore.add(relationshipDocument.getId().intValue());
        }
        } %>
		 <div class="boxButton">
		   <input type="button" onClick="makeIndependent('<%=hrmReportId %>')"
                  value="Mark this report as not similar to the other report(s)"/>
		 </div>  
		</span>
                <% } %>
            </div>

            <%
                if (hrmReport.isBinary()) {
                    // Use the hash stored in the database instead of recalculating it
                    // This ensures compatibility with reports uploaded before UTF-8 encoding migration
                    String noMessageIdHash = document.getReportHash();

                    if (hrmReport.getFileExtension() != null && (".gif".equals(hrmReport.getFileExtension()) || ".jpg".equals(hrmReport.getFileExtension()) || ".png".equals(hrmReport.getFileExtension()))) {
            %><img
                src="<%=request.getContextPath() %>/hospitalReportManager/HRMDownloadFile.do?hash=<%=noMessageIdHash%>"/><br/><%
            }
            if (hrmReport.getFileExtension() != null && ".pdf".equals(hrmReport.getFileExtension())) {
        %>
            <object data="<%=request.getContextPath() %>/hospitalReportManager/HRMDownloadFile.do?hash=<%=noMessageIdHash%>"
                    width="100%" height="600" type="application/pdf">
                <p>(Your browser could not display the pdf)</p>
            </object>
            <br/>
            <%
                }
            %><a
                href="<%=request.getContextPath() %>/hospitalReportManager/HRMDownloadFile.do?hash=<%=noMessageIdHash%>"><%=(hrmReport.getLegalLastName() + "-" + hrmReport.getLegalFirstName() + "-" + hrmReport.getFirstReportClass() + hrmReport.getFileExtension()).replaceAll("\\s", "_") %>
        </a>&nbsp;&nbsp;
            <br/>
            <%
                if (hrmReport.getFileExtension() != null && (".pdf".equals(hrmReport.getFileExtension()) || ".gif".equals(hrmReport.getFileExtension()) || ".jpg".equals(hrmReport.getFileExtension()) || ".png".equals(hrmReport.getFileExtension()))) {
            %>
            <span>(Please use the link above to download the attachement.)</span>
            <%
            } else {
            %>
            <span style="color:red">(This report contains an attachment which cannot be viewed in your browser. Please use the link above to view/download the content contained within.)</span>
            <%
                }


            } else {

            %>
            <%=hrmReport.getFirstReportTextContent().replaceAll("\n", "<br />") %>

            <% } %>

            <%
                if (confidentialityStatement != null && confidentialityStatement.trim().length() > 0) {
            %>
            <hr/>
            <em><strong>Provider Confidentiality Statement</strong><br/><%=confidentialityStatement %>
            </em>
            <% } %>
        </div>

        <div id="infoBox">
            <table>
                <tr>
                    <th>Report Date:</th>
                    <td><%=(hrmReport.getFirstReportEventTime() != null ? hrmReport.getFirstReportEventTime().getTime().toString() :
                            hrmReport.getFirstAccompanyingSubClassDateTime()) %>
                    </td>
                </tr>
                <tr>
                    <th>Demographic Info:</th>
                    <td>
                        <%=hrmReport.getLegalName() %><br/>
                        <%=hrmReport.getAddressLine1() %><br/>
                        <%=hrmReport.getAddressLine2() != null ? hrmReport.getAddressLine2() : "" %><br/>
                        <%=hrmReport.getAddressCity() %>
                    </td>
                </tr>

                <tr>
                    <th>Report Class:</th>
                    <td><%=hrmReport.getFirstReportClass() %>
                    </td>
                </tr>
                <% if (hrmReport.getFirstReportClass().equalsIgnoreCase("Diagnostic Imaging Report") || hrmReport.getFirstReportClass().equalsIgnoreCase("Cardio Respiratory Report")) { %>
                <tr>
                    <th>Accompanying Subclass:</th>
                    <td>
                        <%
                            List<List<Object>> subClassListFromReport = hrmReport.getAccompanyingSubclassList();
                            List<HRMDocumentSubClass> subClassListFromDb = (List<HRMDocumentSubClass>) request.getAttribute("subClassList");

                            if (subClassListFromReport.size() > 0) {
                        %>
                        <i>From the Report</i><br/>
                        <% for (List<Object> subClass : subClassListFromReport) { %>
                        <abbr title="Type: <%=(String) subClass.get(0) %>; Date of Observation: <%=((Date) subClass.get(3)).toString() %>">(<%=(String) subClass.get(1) %>
                            ) <%=(String) subClass.get(2) %>
                        </abbr><br/>
                        <% }
                        } %><br/>
                        <%
                            if (subClassListFromDb != null && subClassListFromDb.size() > 0) { %>
                        <i>Stored in Database</i><br/>
                        <div id="subclassstatus<%=hrmReportId %>"></div>
                        <% for (HRMDocumentSubClass subClass : subClassListFromDb) { %>
                        <abbr title="Type: <%=subClass.getSubClass() %>; Date of Observation: <%=subClass.getSubClassDateTime().toString() %>">(<%=subClass.getSubClassMnemonic() %>
                            ) <%=subClass.getSubClassDescription() %>
                        </abbr>
                        <% if (!subClass.isActive()) { %> (<a href="#"
                                                              onclick="makeActiveSubClass('<%=hrmReportId %>', '<%=subClass.getId() %>')">make
                        active</a>)<% } %><br/>
                        <% }
                        } %>
                    </td>
                </tr>
                <% } else { %>
                <tr>
                    <th>Subclass:</th>
                    <td>
                        <%
                            String[] subClassFromReport = hrmReport.getFirstReportSubClass().split("\\^");
                            if (subClassFromReport.length == 2) {
                        %>
                        <abbr title="<%=subClassFromReport[0] %>"><%=subClassFromReport[1] %>
                        </abbr>
                        <% } else {%>
                        <abbr><%=subClassFromReport[0] %>
                        </abbr>
                        <% } %>
                    </td>
                </tr>
                <% } %>

                <th>Source Author(s):</th>
                <td>

                    <%
                        for (String author : hrmReport.getFirstReportAuthorPhysician()) {
                    %>
                    <%=author %>&nbsp;
                    <%} %>

                </td>
                </tr>

                <tr>
                    <td colspan=2>
                        <hr/>
                    </td>
                </tr>

                <tr>
                    <th>Linked with Demographic:</th>
                    <td>
                        <div id="demostatus<%=hrmReportId %>">
                            <% if (demographicLink != null) { %>
                            <oscar:nameage demographicNo="<%=demographicLink.getDemographicNo().toString()%>"/> <br/>
                            <a href="#" onclick="removeDemoFromHrm('<%=hrmReportId %>', <%=csrfTokenJs%>)">(remove)</a>
                            <% } else { %>
                            <i>Not currently linked</i>
                            <% } %>
                        </div>
                        <input type="hidden" id="demofind<%=hrmReportId %>hrm" value="<%=demographicNo%>"/>
                        <input type="hidden" id="demofind<%=hrmReportId %>hrm" value=""/>
                        <input type="hidden" id="routetodemo<%=hrmReportId %>hrm" value=""/>
                        <input type="checkbox" id="activeOnly<%=hrmReportId%>hrm" name="activeOnly" checked="checked"
                               value="true" onclick="setupHrmDemoAutoCompletion('<%=hrmReportId%>', <%=csrfTokenJs%>)">Active
                        Only<br>
                        <input type="text" id="autocompletedemo<%=hrmReportId %>hrm"
                               onchange="checkSave('<%=hrmReportId%>hrm')" name="demographicKeyword"
                               style="display:<%=(demographicLink != null) ? "none" : "block"%> "/>
                    </td>
                </tr>
                <tr>
                    <th>Assigned Providers:</th>
                    <td>
                        <div id="provstatus<%=hrmReportId %>"></div>
                        <% if (providerLinkList != null && providerLinkList.size() > 0) {
                            for (HRMDocumentToProvider p : providerLinkList) {
                                if (!p.getProviderNo().equalsIgnoreCase("-1")) { %>
                        <%=Encode.forHtml(providerDao.getProviderName(p.getProviderNo()))%> <%=p.getSignedOff() != null && p.getSignedOff() == 1 ? "<abbr title='" + p.getSignedOffTimestamp() + "'>(Signed-Off " + p.getSignedOffTimestamp() + ")</abbr>" : "" %>
                        <a href="#"
                           onclick="removeProvFromHrm('<%=p.getId() %>', '<%=hrmReportId %>')">(remove)</a><br/>
                        <% }
                        }
                        } else { %>
                        <i>No providers currently assigned</i><br/>
                        <% } %>
                        <% if (document.getUnmatchedProviders() != null && document.getUnmatchedProviders().trim().length() >= 1) {
                            String[] unmatchedProviders = document.getUnmatchedProviders().substring(1).split("\\|");
                            for (String unmatchedProvider : unmatchedProviders) { %>
                        <i><abbr title="From the HRM document"><%=unmatchedProvider %>
                        </abbr></i><br/>
                        <% }
                        } %>
                        <div id="providerList<%=hrmReportId %>hrm"></div>
                        <input type="hidden" name="provi" id="provfind<%=hrmReportId%>hrm"/>
                        <input type="text" id="autocompleteprov<%=hrmReportId%>hrm" name="demographicKeyword"/>
                        <div id="autocomplete_choicesprov<%=hrmReportId%>hrm" class="autocomplete"></div>
                    </td>
                </tr>
                <tr>
                    <td colspan=2>
                        <hr/>
                    </td>
                </tr>
                <tr>
                    <th>Report Class:</th>
                    <td><%=hrmReport.getFirstReportClass() %>
                    </td>
                </tr>
                <% if (hrmReport.getFirstReportClass().equalsIgnoreCase("Diagnostic Imaging Report") || hrmReport.getFirstReportClass().equalsIgnoreCase("Cardio Respiratory Report")) { %>
                <tr>
                    <th>Accompanying Subclass:</th>
                    <td>
                        <%
                            List<List<Object>> subClassListFromReport = hrmReport.getAccompanyingSubclassList();
                            List<HRMDocumentSubClass> subClassListFromDb = (List<HRMDocumentSubClass>) request.getAttribute("subClassList");

                            if (subClassListFromReport.size() > 0) {
                        %>
                        <i>From the Report</i><br/>
                        <% for (List<Object> subClass : subClassListFromReport) { %>
                        <abbr title="Date of Observation: <%=((String)subClass.get(4)) %>">(<%=(String) subClass.get(0) %>
                            : <%=(String) subClass.get(1) %>) <%=(String) subClass.get(2) %>
                        </abbr><br/>
                        <% }
                        } %><br/>
                        <%
                            if (subClassListFromDb != null && subClassListFromDb.size() > 0) { %>
                        <i>Stored in Database</i><br/>
                        <div id="subclassstatus<%=hrmReportId %>"></div>
                        <% for (HRMDocumentSubClass subClass : subClassListFromDb) { %>
                        <abbr title="Date of Observation: <%=subClass.getSubClassDateTime().toString() %>">(<%=subClass.getSubClass() %>
                            : <%=subClass.getSubClassMnemonic() %>) <%=subClass.getSubClassDescription() %>
                        </abbr>
                        <% if (!subClass.isActive()) { %> (<a href="#"
                                                              onclick="makeActiveSubClass('<%=hrmReportId %>', '<%=subClass.getId() %>')">make
                        active</a>)<% } %><br/>
                        <% }
                        } %>
                    </td>
                </tr>
                <% } else { %>
                <tr>
                    <th>Subclass:</th>
                    <td>
                        <%
                            String[] subClassFromReport = hrmReport.getFirstReportSubClass().split("\\^");
                            if (subClassFromReport.length == 2) {
                        %>
                        <abbr title="Subclass: <%=subClassFromReport[0] %>"><%=subClassFromReport[1] %>
                        </abbr>
                        <% } %>
                    </td>
                </tr>
                <% } %>
                <tr>
                    <th>Categorization:</th>
                    <td>
				<span id="chooseCategory_<%=hrmReportId%>" onchange="updateCategory('<%=hrmReportId %>');"
                      style="display:none">
					<select id="selectedCategory_<%=hrmReportId%>">
						<% for (HRMCategory hrmCategory : hrmCategories) { %>
						<option value="<%=hrmCategory.getId()%>" <%=(category != null && category.getId().equals(hrmCategory.getId())) ? "selected" : ""%>><%=hrmCategory.getCategoryName()%></option>
						<%}%>
					</select>
				</span>

                        <span id="showCategory_<%=hrmReportId%>">
					<span id="hrmCategory_<%=hrmReportId%>">
						<%
                            if (category != null) {
                        %>
						<%=Encode.forHtml(category.getCategoryName())%>
						<% }%>
					</span>

					<a href="javascript:void(0)" onclick="editCategory('<%=hrmReportId %>');">(edit)</a>
				</span>

                    </td>
                </tr>
                <tr>
                    <td colspan=2>
                        <form action="<%=request.getContextPath() %>/hospitalReportManager/PrintHRMReport.do">
                            <input type="hidden" value="<%=hrmReportId %>" name="hrmReportId"/>
                            <% if (request.getRequestURI().contains("oscarMDS/Page.jsp")) {%>
                            <input type="button" value="Print" onclick="printHrm('<%=hrmReportId%>')"/>
                            <%} else { %>
                            <input type="submit" value="Print"/>
                            <% }%>

                            <input type="button" style="display: none;" value="Save" id="save<%=hrmReportId %>hrm"/>
                            <%
                                HRMDocumentToProvider hrmDocumentToProvider = HRMDisplayReport2Action.getHRMDocumentFromProvider(loggedInInfo.getLoggedInProviderNo(), hrmReportId);
                                if (hrmDocumentToProvider != null && hrmDocumentToProvider.getSignedOff() != null && hrmDocumentToProvider.getSignedOff() == 1) {
                            %>
                            <input type="button" id="signoff<%=hrmReportId %>" value="Revoke Sign-Off"
                                   onClick="revokeSignOffHrm('<%=hrmReportId %>')"/>
                            <%
                            } else {
                            %>
                            <input type="button" id="signoff<%=hrmReportId %>" value="Sign-Off"
                                   onClick="signOffHrm('<%=hrmReportId %>', <%=isListView%>)"/>
                            <%
                                }
                            %>
                            <input type="button" value="Annotations"
                                   onClick="popupPage(500, 400, '<%=request.getContextPath() %>/annotation/annotation.jsp?display=HRM&table_id=<%=hrmReportId%>&demo=<%=demographicNo%>')"/>
                        </form>
                    </td>
                </tr>

            </table>
        </div>
    </div>

    <div class="aBox" id="duplicateAndSimilarBox">

        <% if (request.getAttribute("hrmDuplicateNum") != null && ((Integer) request.getAttribute("hrmDuplicateNum")) > 0) { %>
        Duplicates Received by HRM:  <%=request.getAttribute("hrmDuplicateNum") %>.<br/>
        <% } else { %>
        Duplicates Received by HRM: 0.<br/>
        <% } %>

        <br/>
        <%
            children = (List<HRMDocument>) request.getAttribute("children");

            if (children != null && children.size() > 0) {
        %>
        NOTE: This report might <b style="color:red">not be the most current report available</b>. Similar reports have
        been received as follows:<br/><br/>
        <table>
            <tr>
                <th>Id</th>
                <th>Report Date</th>
                <th>Received Date</th>
            </tr>
            <%for (HRMDocument child : children) { %>
            <tr>
                <td><a href="javascript:void(0)" onClick="openReport('<%=child.getId()%>')"><%=child.getId() %>
                </a></td>
                <td><%=child.getReportDate() %>
                </td>
                <td><%=child.getTimeReceived() %>
                </td>
            </tr>
            <% } %>
        </table>
        <%
            }
        %>

    </div>
    <div id="descriptionBox">

        Add a description:
        <input type="text" id="descriptionField_<%=hrmReportId %>_hrm" size="100"
               value="<%=Encode.forHtml(document.getDescription())%>"/><br/>

        <div class="boxButton">
            <input type="button" onClick="setDescription('<%=hrmReportId %>')" value="Set Description"/><span
                id="descriptionstatus<%=hrmReportId %>"></span><br/><br/>
        </div>

    </div>

    <div id="commentBox">
        Add a comment:
        <textarea rows="10" cols="50" id="commentField_<%=hrmReportId %>_hrm"></textarea>

        <div class="boxButton">
            <input type="button" onClick="addComment('<%=hrmReportId %>')" value="Add Comment"/><span
                id="commentstatus<%=hrmReportId %>"></span><br/><br/>
        </div>
        <%

            if (documentComments != null) {
        %>Displaying <%=documentComments.size() %> comment<%=documentComments.size() != 1 ? "s" : "" %><br/>
        <% for (HRMDocumentComment comment : documentComments) {
            String commentTime = comment.getCommentTime() != null ? " on " + comment.getCommentTime().toString() : ""; %>
        <div class="documentComment">
            <strong><%=Encode.forHtml(providerDao.getProviderName(comment.getProviderNo())) %><%=commentTime%>
                wrote...</strong><br/>
            <%=Encode.forHtml(comment.getComment()) %><br/>
            <a href="#" onClick="deleteComment('<%=comment.getId() %>', '<%=hrmReportId %>'); return false;">(Delete
                this comment)</a></div>
        <% }
        }
        %>
    </div>

    <div id="metadataBox">
        <table>
            <tr>

                <th>Media</th>
                <td><%=hrmReport.getMediaType()%>
                </td>
            </tr>
            <tr>
                <th>Message Unique ID</th>
                <td><%=hrmReport.getMessageUniqueId() %>
                </td>
            </tr>
            <tr>
                <th>Sending Author</th>
                <td><%=hrmReport.getSendingAuthor()%>
                </td>
            </tr>
            <tr>
                <th>Sending Facility ID</th>

                <td><%=hrmReport.getSendingFacilityId() %>
                </td>
            </tr>
            <tr>
                <th>Sending Facility Report No.</th>
                <td><%=hrmReport.getSendingFacilityReportNo() %>
                </td>
            </tr>
            <tr>

                <th>Date and Time of Report</th>
                <td><%=HRMReportParser.getAppropriateDateStringFromReport(hrmReport) %>
                </td>

            </tr>
            <tr>
                <th>Result Status</th>
                <td><%=(hrmReport.getResultStatus() != null && hrmReport.getResultStatus().equalsIgnoreCase("C")) ? "Cancelled" : "Signed by the responsible author and Released by health records"  %>
                </td>
            </tr>
        </table>
    </div>


    <script type="text/javascript">
        jQuery(setupHrmDemoAutoCompletion(<%=hrmReportId%>, <%=csrfTokenJs%>));

        YAHOO.example.BasicRemote = function () {
            var url = "<%= request.getContextPath() %>/provider/SearchProvider.do";
            var oDS = new YAHOO.util.XHRDataSource(url, {connMethodPost: true, connXhrMode: 'ignoreStaleResponses'});
            oDS.responseType = YAHOO.util.XHRDataSource.TYPE_JSON;// Set the responseType
            // Define the schema of the delimited resultsTEST, PATIENT(1985-06-15)
            oDS.responseSchema = {
                resultsList: "results",
                fields: ["providerNo", "firstName", "lastName"]
            };
            // Enable caching
            oDS.maxCacheEntries = 0;
            // Instantiate the AutoComplete
            var oAC = new YAHOO.widget.AutoComplete("autocompleteprov<%=hrmReportId%>hrm", "autocomplete_choicesprov<%=hrmReportId%>hrm", oDS);
            oAC.queryMatchSubset = true;
            oAC.minQueryLength = 3;
            oAC.maxResultsDisplayed = 25;
            oAC.formatResult = resultFormatter3;
            oAC.queryMatchContains = true;
            oAC.itemSelectEvent.subscribe(function (type, args) {
                var myAC = args[0];
                var str = myAC.getInputEl().id.replace("autocompleteprov", "provfind");
                var oData = args[2];
                $(str).value = args[2][0];//li.id;
                myAC.getInputEl().value = args[2][2] + "," + args[2][1];
                var adoc = document.createElement('div');
                adoc.appendChild(document.createTextNode(oData[2] + " " + oData[1]));
                var idoc = document.createElement('input');
                idoc.setAttribute("type", "hidden");
                idoc.setAttribute("name", "flagproviders");
                idoc.setAttribute("value", oData[0]);
                adoc.appendChild(idoc);

                var providerList = $('providerList<%=hrmReportId%>hrm');
                providerList.appendChild(adoc);

                myAC.getInputEl().value = '';//;oData.fname + " " + oData.lname ;

                addProvToHrm('<%=hrmReportId %>', args[2][0]);
            });


            return {
                oDS: oDS,
                oAC: oAC
            };
        }();
    </script>

    <%
        String duplicateLabIdsString = StringUtils.trimToNull(request.getParameter("duplicateLabIds"));
        if (duplicateLabIdsString != null) {

            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm");
    %>
    <div id="duplicatesMessage">
        Duplicate Report History:<br/><br/>

        <table>
            <tr>
                <th>ID</th>
                <th>Report Date</th>
                <th>Date Received</th>
                <th></th>
            </tr>
            <%
                //need datetime of report.
                String[] duplicateLabIdsStringSplit = duplicateLabIdsString.split(",");
                for (String tempId : duplicateLabIdsStringSplit) {
            %>
            <tr>
                <td><%=tempId %>
                </td>
                <td><%=formatter.format(dupReportDates.get(Integer.parseInt(tempId))) %>
                </td>
                <td><%=formatter.format(dupTimeReceived.get(Integer.parseInt(tempId))) %>
                </td>
                <td><input type="button" value="Open Report"
                           onclick="window.open('?id=<%=tempId%>&segmentId=<%=tempId%>&providerNo=<%=request.getParameter("providerNo")%>&searchProviderNo=<%=request.getParameter("searchProviderNo")%>&status=<%=request.getParameter("status")%>&demoName=<%=Encode.forHtml(request.getParameter("demoName"))%>', null)"/>
                </td>
            </tr>

            <%
                }

            %></table>
    </div>
    <%
        }
    %>


</div>
</body>
</html>
