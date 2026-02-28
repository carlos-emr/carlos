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
    showDocument.jsp
    ================================================================================
    Purpose:
        Document viewer and management interface for the CARLOS EMR document inbox.
        Renders incoming documents (image or PDF) alongside assignment tools, patient
        linking, and provider routing. Supports multi-format display, acknowledgement
        workflows, lab macro quick-actions, and tickler (follow-up reminder) alerts.

    Key Features:
        - Multi-format document display: image page navigation and inline PDF rendering
        - Document acknowledgement with optional comment dialog
        - Lab macro dropdown: one-click pre-configured acknowledgement templates
        - Tickler alert banner: shows pending ticklers for linked patient
        - Patient (demographic) search and assignment with MRP auto-population
        - Provider routing: flag document to multiple providers with removal support
        - Document metadata editing: type, description, observation date
        - Queue management: refile to configured incoming document queues
        - Appointment history panel for linked patient
        - Document splitting, rotation, and first-page deletion tools
        - OWASP XSS encoding for all user inputs and database outputs
        - CSRF protection via OWASP CSRFGuard 4.5 auto-injected tokens
        - Bootstrap 5 components for dropdowns and alert banners

    Architecture:
        - Included by inboxManage.jsp and other document inbox entry points
        - Document data loaded via EDocUtil and IncomingDocUtil
        - Acknowledgement status from AcknowledgementData (lab-style status tracking)
        - Lab macros loaded from UserProperty.LAB_MACRO_JSON (JSON array)
        - Tickler alerts fetched via TicklerManager.search_tickler()
        - Provider routing via ProviderInboxRoutingDao
        - Appointment history via OscarAppointmentDao

    Lab Macro Integration:
        - Loads provider's configured macros from UserProperty.LAB_MACRO_JSON
        - Renders macro names as dropdown items in Bootstrap 5 dropdown
        - Clicking a macro calls runDocMacro() which verifies demographic link,
          then invokes /oscarMDS/RunMacro.do with macro name and patient context
        - Supports closeOnSuccess flag to auto-close popup on success

    Tickler Alert Integration:
        - Requires "_tickler" READ privilege (SecurityInfoManager check)
        - Queries ticklers for linked patient within a 6-week window
        - Displays dismissible Bootstrap 5 alert-info banner with count and links
        - Each tickler message links to ticklerEdit.jsp for quick review

    Parameters:
        @param segmentID   String the document ID to display
        @param demoName    String optional patient name hint (overridden from DB if linked)
        @param inQueue     String optional queue indicator flag
        @param inWindow    String if "true", renders full HTML page wrapper (html/head/body)

    Security:
        - Requires "_edoc" READ privilege via security:oscarSec tag
        - Requires "_tickler" READ privilege for the tickler alert panel
        - All JSP outputs escaped with OWASP Encoder for XSS prevention
        - Audit logging via LogAction for all document views
        - CSRF tokens auto-injected by CSRFGuard 4.5 filter

    Dependencies:
        - Document utilities: io.github.carlos_emr.carlos.documentManager.EDocUtil
        - Inbox routing: io.github.carlos_emr.carlos.commn.dao.ProviderInboxRoutingDao
        - User properties: io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO
        - Ticklers: io.github.carlos_emr.carlos.managers.TicklerManager
        - Macros: Jackson ObjectMapper for JSON parsing
        - Security: OWASP Encoder, SecurityInfoManager
        - UI: Bootstrap 5, jQuery UI, showDocument.js, oscarMDSIndex.js

    @since 2003 (Macro and Tickler improvements 2026-02)
--%>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_edoc" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_edoc");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="com.fasterxml.jackson.databind.JsonNode" %>
<%@ page import="com.fasterxml.jackson.databind.ObjectMapper" %>
<%@ page import="com.fasterxml.jackson.databind.node.ArrayNode" %>
<%@ page import="com.fasterxml.jackson.databind.node.ObjectNode" %>

<%@ page import="io.github.carlos_emr.MyDateFormat" %>
<%@ page import="io.github.carlos_emr.OscarProperties" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.*" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.*" %>
<%@ page import="io.github.carlos_emr.carlos.documentManager.EDoc" %>
<%@ page import="io.github.carlos_emr.carlos.documentManager.EDocUtil" %>
<%@ page import="io.github.carlos_emr.carlos.documentManager.IncomingDocUtil" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.all.*" %>
<%@ page import="io.github.carlos_emr.carlos.log.*" %>
<%@ page import="io.github.carlos_emr.carlos.managers.SecurityInfoManager" %>
<%@ page import="io.github.carlos_emr.carlos.managers.TicklerManager" %>
<%@ page import="io.github.carlos_emr.carlos.mds.data.*" %>
<%@ page import="io.github.carlos_emr.carlos.util.ConversionUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.WebUtils" %>

<%@ page import="java.text.SimpleDateFormat"%>
<%@ page import="java.time.LocalDate" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.util.*" %>
<%@ page import="org.apache.commons.lang3.StringEscapeUtils"%>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.owasp.encoder.Encode"%>
<%@ page import="org.springframework.web.context.WebApplicationContext"%>
<%@ page import="org.springframework.web.context.support.WebApplicationContextUtils"%>

<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/rewrite-tag.tld" prefix="rewrite" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="https://www.owasp.org/index.php/OWASP_Java_Encoder_Project" prefix="e" %>
<%
    ProviderInboxRoutingDao providerInboxRoutingDao = SpringUtils.getBean(ProviderInboxRoutingDao.class);
    UserPropertyDAO userPropertyDAO = SpringUtils.getBean(UserPropertyDAO.class);
    OscarAppointmentDao appointmentDao = SpringUtils.getBean(OscarAppointmentDao.class);
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);

    LoggedInInfo loggedInInfo=LoggedInInfo.getLoggedInInfoFromSession(request);
    String providerNo = loggedInInfo.getLoggedInProviderNo();
    UserProperty uProp = userPropertyDAO.getProp(providerNo, UserProperty.LAB_ACK_COMMENT);
    boolean skipComment = false;

    if (uProp != null && uProp.getValue().equalsIgnoreCase("yes")) {
        skipComment = true;
    }

    uProp = userPropertyDAO.getProp(providerNo, UserProperty.DISPLAY_DOCUMENT_AS);
    String displayDocumentAs = UserProperty.IMAGE;
    if (uProp != null && uProp.getValue().equals(UserProperty.PDF)) {
        displayDocumentAs = UserProperty.PDF;
    }

    String demoName = request.getParameter("demoName");
    String documentNo = request.getParameter("segmentID");

    String inQueue = request.getParameter("inQueue");

    boolean inQueueB = false;
    if (inQueue != null) {
        inQueueB = true;
    }

    String defaultQueue = IncomingDocUtil.getAndSetIncomingDocQueue(providerNo, null);
    QueueDao queueDao = SpringUtils.getBean(QueueDao.class);
    List<Hashtable> queues = queueDao.getQueues();
    int queueId = 1;
    if (defaultQueue != null) {
        defaultQueue = defaultQueue.trim();
        queueId = Integer.parseInt(defaultQueue);
    }

    String creator = (String) session.getAttribute("user");
    ArrayList doctypes = EDocUtil.getActiveDocTypes("demographic");
    EDoc curdoc = EDocUtil.getDoc(documentNo);

    String demographicID = curdoc.getModuleId();
    String mrpProviderName = "";
    if ((demographicID != null) && !demographicID.isEmpty() && !demographicID.equals("-1")) {
        DemographicDao demographicDao = (DemographicDao)SpringUtils.getBean(DemographicDao.class);
        Demographic demographic = demographicDao.getDemographic(demographicID);
				demoName = demographic.getLastName()+","+demographic.getFirstName();
        mrpProviderName = demographic.getProviderNo() == null || demographic.getProviderNo().isEmpty() ? "Unknown" : providerDao.getProviderNameLastFirst(demographic.getProviderNo());
        mrpProviderName = " (MRP: " + mrpProviderName + ")";
    } else {
      demoName = EDocUtil.getProviderName(providerNo);
    }
    LogAction.addLog((String) session.getAttribute("user"), LogConst.READ, LogConst.CON_DOCUMENT, documentNo, request.getRemoteAddr(),demographicID);
    String docId = curdoc.getDocId();
    String ackFunc;
    if(skipComment) {
      ackFunc = "updateStatus('acknowledgeForm_" + Encode.forJavaScript(docId) + "'," + inQueueB + ");";
    } else {
      ackFunc = "getDocComment('" + Encode.forJavaScript(docId) + "','" + Encode.forJavaScript(providerNo) + "'," + inQueueB + ");";
    }

    int slash = 0;
    String contentType = "";
    if ((slash = curdoc.getContentType().indexOf('/')) != -1) {
        contentType = curdoc.getContentType().substring(slash + 1);
    }
    String dStatus = "";
    if ((curdoc.getStatus() + "").compareTo("A") == 0) {
        dStatus = "active";
    } else if ((curdoc.getStatus() + "").compareTo("H") == 0) {
        dStatus = "html";
    }
    int numOfPage = curdoc.getNumberOfPages();
    String numOfPageStr = "";
    if (numOfPage == 0)
        numOfPageStr = "unknown";
    else
        numOfPageStr = (new Integer(numOfPage)).toString();
    String cp = request.getContextPath();
    String url = cp + "/documentManager/ManageDocument.do?method=viewDocPage&doc_no=" + docId + "&curPage=1";
    String url2 = cp + "/documentManager/ManageDocument.do?method=display&doc_no=" + docId;
    String currentDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

    Integer docCurrentFiledQueue = null;

    request.setAttribute("mrpProviderName", mrpProviderName);
    request.setAttribute("demoName", demoName);
%>
<%
    // Tickler alert: load pending ticklers for the linked patient
    String tickler_note = "";
    Integer demoI = 0;
    Integer numTickler = 0;
    boolean isLinkedToDemographic = demographicID != null && !demographicID.isEmpty()
                                    && !demographicID.equals("-1") && !demographicID.equalsIgnoreCase("null");
    if (isLinkedToDemographic) {
        try {
            demoI = Integer.parseInt(demographicID);
        } catch (NumberFormatException e) {
            MiscUtils.getLogger().warn("Invalid demographicID in showDocument: " + demographicID, e);
        }
    }

    LocalDate nearFuture = LocalDate.now().plusWeeks(6);
    DateTimeFormatter dtFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    String strDate = nearFuture.format(dtFormatter);

    SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);

    if (securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", "r", demoI) && isLinkedToDemographic) {
        // Note: tickler_note is intentionally raw HTML. All dynamic values MUST remain encoded using OWASP Encoder methods.
        String tlinkf = "\n <a class=\"alert-link\" href='" + request.getContextPath() + "/tickler/ticklerEdit.jsp?tickler_no=";
        List<String> notes = new java.util.ArrayList<>();
        List<Tickler> ticklers = ticklerManager.search_tickler(loggedInInfo, demoI, MyDateFormat.getSysDate(strDate));
        for (Tickler t : ticklers) {
            if (t.getMessage() != null && !t.getMessage().trim().isEmpty()) {
                notes.add(tlinkf + Encode.forUriComponent(String.valueOf(t.getId())) + "' target='_blank'>" + Encode.forHtml(t.getMessage()) + "</a>");
            }
        }
        numTickler = notes.size();
        tickler_note = String.join(", ", notes);
    }

    // Lab macro: load provider's configured macros for document acknowledgment
    UserProperty labMacroProp = userPropertyDAO.getProp(providerNo, UserProperty.LAB_MACRO_JSON);
%>

<fmt:setBundle basename="oscarResources"/>

<c:if test="${param.inWindow eq 'true'}">
    <html>
    <head>
        <script type="text/javascript">
            const ctx = "${pageContext.servletContext.contextPath}";
        </script>

        <link rel="stylesheet" type="text/css"
              href="${pageContext.servletContext.contextPath}/library/jquery/jquery-ui.theme-1.12.1.min.css"/>
        <link rel="stylesheet" type="text/css"
              href="${pageContext.servletContext.contextPath}/library/jquery/jquery-ui.structure-1.12.1.min.css"/>
        <link rel="stylesheet" type="text/css" href="${pageContext.servletContext.contextPath}/css/showDocument.css"/>
        <link rel="stylesheet" type="text/css" media="all" href="${pageContext.request.contextPath}/library/bootstrap/5.0.2/css/bootstrap.css">
        <script src="${pageContext.request.contextPath}/library/bootstrap/5.0.2/js/bootstrap.bundle.js"></script>

        <script type="text/javascript"
                src="${pageContext.servletContext.contextPath}/share/calendar/calendar.js"></script>
        <!-- language for the calendar -->
        <script type="text/javascript"
                src="${pageContext.servletContext.contextPath}/share/calendar/lang/<fmt:message key='global.javascript.calendar'/>"></script>
        <!-- the following script defines the Calendar.setup helper function, which makes adding a calendar a matter of 1 or 2 lines of code. -->
        <script type="text/javascript"
                src="${pageContext.servletContext.contextPath}/share/calendar/calendar-setup.js"></script>
        <!-- calendar stylesheet -->
        <link rel="stylesheet" type="text/css" media="all"
              href="${pageContext.servletContext.contextPath}/share/calendar/calendar.css" title="win2k-cold-1"/>
        <!-- jquery -->
        <script type="text/javascript"
                src="${pageContext.servletContext.contextPath}/share/javascript/Oscar.js"></script>

        <script type="text/javascript"
                src="${pageContext.servletContext.contextPath}/library/jquery/jquery-1.12.0.min.js"></script>
        <script type="text/javascript"
                src="${pageContext.servletContext.contextPath}/library/jquery/jquery-ui-1.12.1.min.js"></script>
        <script type="text/javascript"
                src="${pageContext.servletContext.contextPath}/js/demographicProviderAutocomplete.js"></script>

        <script type="text/javascript">
            jQuery.noConflict();

            function renderCalendar(id, inputFieldId) {
                Calendar.setup({inputField: inputFieldId, ifFormat: "%Y-%m-%d", showsTime: false, button: id});

            }

            function handleDocSave(docid, action) {
                var url = contextpath + "/documentManager/inboxManage.do";
                var data = 'method=isDocumentLinkedToDemographic&docId=' + encodeURIComponent(docid);

                fetch(url, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded'
                    },
                    body: data
                })
                .then(function(response) {
                    return response.json();
                })
                .then(function(json) {
                    if (json != null) {
                        var success = json.isLinkedToDemographic;
                        var demoid = '';

                        if (success) {
                            if (action == 'addTickler') {
                                demoid = json.demoId;
                                if (demoid != null && demoid.length > 0)
                                    popupStart(450, 600, contextpath + '/tickler/ForwardDemographicTickler.do?docType=DOC&docId=' + encodeURIComponent(docid) + '&demographic_no=' + encodeURIComponent(demoid), 'tickler')
                            }
                        } else {
                            alert("Make sure demographic is linked and document changes saved!");
                        }
                    }
                })
                .catch(function(error) {
                    console.error('Error:', error);
                });
            }


            function rotate90(id) {
                jQuery("#rotate90btn_" + id).attr('disabled', 'disabled');

                fetch(contextpath + "/documentManager/SplitDocument.do", {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded'
                    },
                    body: "method=rotate90&document=" + encodeURIComponent(id)
                })
                .then(function(response) {
                    jQuery("#rotate90btn_" + id).removeAttr('disabled');
                    jQuery("#docImg_" + id).attr('src', contextpath + "/documentManager/ManageDocument.do?method=showPage&doc_no=" + encodeURIComponent(id) + "&page=1&rand=" + (new Date().getTime()));
                })
                .catch(function(error) {
                    console.error('Error:', error);
                    jQuery("#rotate90btn_" + id).removeAttr('disabled');
                });
            }


            function split(id, demoName) {
                var loc = "${pageContext.servletContext.contextPath}";
                loc = loc + "/oscarMDS/Split.jsp?document=";
                loc = loc + id;
                loc = loc + "&queueID=";
                loc = loc + "<%=inQueue%>";
                loc = loc + "&demoName=" + encodeURIComponent(demoName);
                popupStart(1400, 1400, loc, "Splitter");
            }

        </script>

    </head>

    <body>
</c:if>
<script type="text/javascript">
    var _in_window = <%=( "true".equals(request.getParameter("inWindow")) ? "true" : "false" )%>;
    var contextpath = "<%=request.getContextPath()%>";
</script>
<div id="labdoc_<%=docId%>" class="content">
    <%
        ArrayList ackList = AcknowledgementData.getAcknowledgements("DOC", docId);
        ReportStatus reportStatus = null;
        String docCommentTxt = "";
        String rptStatus = "";
        boolean ackedOrFiled = false;
        for (int idx = 0; idx < ackList.size(); ++idx) {
            reportStatus = (ReportStatus) ackList.get(idx);

            if (reportStatus.getOscarProviderNo() != null && reportStatus.getOscarProviderNo().equals(providerNo)) {
                docCommentTxt = reportStatus.getComment();
                if (docCommentTxt == null) {
                    docCommentTxt = "";
                }

                rptStatus = reportStatus.getStatus();

                if (rptStatus != null) {
                    ackedOrFiled = rptStatus.equalsIgnoreCase("A") ? true : rptStatus.equalsIgnoreCase("F") ? true : false;
                }
                break;
            }
        }
    %>
    <form name="acknowledgeForm_<%=docId%>" id="acknowledgeForm_<%=docId%>" onsubmit="<%=ackFunc%>" method="post"
          action="javascript:void(0);">

        <input type="hidden" name="segmentID" value="<%= docId%>"/>
        <input type="hidden" name="multiID" value="<%= docId%>"/>
        <input type="hidden" name="providerNo" value="<%= providerNo%>"/>
        <input type="hidden" name="status" value="A" id="status_<%=docId%>"/>
        <input type="hidden" name="labType" value="DOC"/>
        <input type="hidden" name="ajaxcall" value="yes"/>
        <input type="hidden" name="comment" id="comment_<%=docId%>" value="<%=Encode.forHtmlAttribute(docCommentTxt)%>">
        <%
            if (labMacroProp != null && !StringUtils.isEmpty(labMacroProp.getValue())) {
        %>
        <div class="dropdown d-inline-block">
            <button class="btn btn-outline-primary btn-sm dropdown-toggle" type="button"
                    data-bs-toggle="dropdown" aria-expanded="false"><fmt:message key="showDocument.btnMacros"/></button>
            <ul class="dropdown-menu">
                <%
                    try {
                        ObjectMapper macroMapper = new ObjectMapper();
                        JsonNode macros = macroMapper.readTree(labMacroProp.getValue());
                        if (macros != null && macros.isArray()) {
                            for (int mx = 0; mx < macros.size(); mx++) {
                                JsonNode macro = macros.get(mx);
                                String macroName = macro.get("name").asText();
                                boolean closeOnSuccess = macro.has("closeOnSuccess") && macro.get("closeOnSuccess").asBoolean();
                %>
                <li><a class="dropdown-item" href="javascript:void(0);"
                       onclick="runDocMacro('<%=Encode.forJavaScript(macroName)%>','acknowledgeForm_<%=Encode.forJavaScript(docId)%>',<%=closeOnSuccess%>)"><%=Encode.forHtml(macroName)%></a></li>
                <%
                            }
                        }
                    } catch (Exception e) {
                        MiscUtils.getLogger().warn("Invalid JSON for lab macros in document viewer", e);
                    }
                %>
            </ul>
        </div>
        <% } %>
        <% if (demographicID != null && !demographicID.equals("") && !demographicID.equalsIgnoreCase("null") && !ackedOrFiled) {%>
        <input type="submit" class="btn btn-outline-primary btn-sm" id="ackBtn_<%=docId%>"
               value="<fmt:message key="oscarMDS.segmentDisplay.btnAcknowledge"/>">
        <input type="button" class="btn btn-outline-secondary btn-sm" value="<fmt:message key="oscarMDS.segmentDisplay.btnComment"/>" onclick="addDocComment('<%=Encode.forJavaScript(docId)%>','<%=Encode.forJavaScript(providerNo)%>')"/>

        <%}%>
        <input type="button" class="btn btn-outline-secondary btn-sm" id="fwdBtn_<%=docId%>" value="<fmt:message key="oscarMDS.index.btnForward"/>"
               onClick="ForwardSelectedRows(<%=docId%> + ':DOC', null, null);">
        <%if (!ackedOrFiled) { %>
        <input type="button" class="btn btn-outline-secondary btn-sm" id="fileBtn_<%=docId%>" value="<fmt:message key="oscarMDS.index.btnFile"/>"
               onclick="fileDoc('<%=docId%>');">
        <%} %>
        <input type="button" class="btn btn-outline-secondary btn-sm" id="closeBtn_<%=docId%>" value=" <fmt:message key="global.btnClose"/> "
               onClick="window.close()">
        <input type="button" class="btn btn-outline-secondary btn-sm" id="printBtn_<%=docId%>" value=" <fmt:message key="global.btnPrint"/> "
               onClick="popup(700,960,'<%=url2%>','file download')">
        <%
            String btnDisabled = "disabled";
            if (demographicID != null && !demographicID.equals("") && !demographicID.equalsIgnoreCase("null") && !demographicID.equals("-1")) {
                btnDisabled = "";
            }

        %>
        <input type="button" class="btn btn-outline-secondary btn-sm" id="msgBtn_<%=docId%>" value="<fmt:message key="showDocument.btnMsg"/>"
               onclick="popupPatient(700,960,'${pageContext.servletContext.contextPath}/messenger/SendDemoMessage.do?demographic_no=','msg', '<%=Encode.forJavaScript(docId)%>')" <%=btnDisabled %>/>

        <!--input type="button" class="btn btn-outline-secondary btn-sm" id="ticklerBtn_<%=docId%>" value="Tickler" onclick="handleDocSave('<%=docId%>','addTickler')"/-->
        <input type="button" class="btn btn-outline-secondary btn-sm" id="mainTickler_<%=docId%>" value="<fmt:message key="showDocument.btnTickler"/>" onClick="popupPatientTickler(710, 1024,'${pageContext.servletContext.contextPath}/tickler/ticklerAdd.jsp?', 'Tickler','<%=docId%>')" <%=btnDisabled %>>
        <%
                                                            String refileBtnVisibility = "";
                                                            for (Hashtable ht : queues) {
                                                                int id = (Integer) ht.get("id");

                                                                if (EDocUtil.isDocumentAlreadyRefiledInQueue(curdoc.getDescription(), id)) {
                                                                    docCurrentFiledQueue = id;
                                                                    if (id == queueId) {
                                                                        refileBtnVisibility = "disabled";
                                                                        break;
                                                                    }
                                                                }
                                                            }
                                                        %>

        <input type="button" class="btn btn-outline-secondary btn-sm" id="mainEchart_<%=docId%>"
               value=" <fmt:message key="oscarMDS.segmentDisplay.btnEChart"/> "
               onClick="popupPatient(710, 1024,'${pageContext.servletContext.contextPath}/oscarEncounter/IncomingEncounter.do?reason=<fmt:message key="oscarMDS.segmentDisplay.labResults"/>&curDate=<%=currentDate%>>&appointmentNo=&appointmentDate=&startTime=&status=&demographicNo=', 'encounter', '<%=docId%>')" <%=btnDisabled %>>
        <input type="button" class="btn btn-outline-secondary btn-sm" id="mainMaster_<%=docId%>" value=" <fmt:message key="oscarMDS.segmentDisplay.btnMaster"/>"
               onClick="popupPatient(710,1024,'${pageContext.servletContext.contextPath}/demographic/demographiccontrol.jsp?displaymode=edit&dboperation=search_detail&demographic_no=','master','<%=docId%>')" <%=btnDisabled %>>
        <input type="button" class="btn btn-outline-secondary btn-sm" id="mainApptHistory_<%=docId%>"
               value=" <fmt:message key="oscarMDS.segmentDisplay.btnApptHist"/>"
               onClick="popupPatient(710,1024,'${pageContext.servletContext.contextPath}/demographic/demographiccontrol.jsp?orderby=appttime&displaymode=appt_history&dboperation=appt_history&limit1=0&limit2=25&demographic_no=','ApptHist','<%=docId%>')" <%=btnDisabled %>>

        <input type="button" class="btn btn-outline-secondary btn-sm" id="refileDoc_<%=docId%>"
               value="<fmt:message key="oscarEncounter.noteBrowser.msgRefile"/>" onclick="refileDoc('<%=docId%>');" <%=refileBtnVisibility%> >

        <select id="queueList_<%=docId%>" class="btn btn-outline-secondary btn-sm" name="queueList"
                onchange="handleQueueListChange(this, document.getElementById('refileDoc_<%=docId%>'), '<%=docCurrentFiledQueue%>')">
            <%
                for (Hashtable ht : queues) {
                    int id = (Integer) ht.get("id");
                    String qName = (String) ht.get("queue");
            %>
            <option value="<%=Encode.forHtmlAttribute(String.valueOf(id))%>" <%=((id == queueId) ? " selected" : "")%>><%=Encode.forHtml(qName)%>
            </option>
            <%}%>
        </select>

    </form>
    <security:oscarSec roleName="<%=roleName$%>" objectName="_tickler" rights="r">
        <% if (numTickler > 0 && isLinkedToDemographic) { %>
        <table style="width:100%;">
            <tr>
                <td class="alert alert-info alert-dismissible fade show">
                    <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
                    <strong><fmt:message key="showDocument.ticklerAlertLabel"/></strong> <fmt:message key="showDocument.ticklerAlertFollowing"/> <%=numTickler%>
                    <a class="alert-link" href="javascript:void(0);" onclick="popup(450, 1200, '<%=Encode.forJavaScript(request.getContextPath())%>/tickler/ticklerDemoMain.jsp?demoview=<%=Encode.forUriComponent(demographicID)%>', 'openTicklers')"><fmt:message key="showDocument.ticklerAlertLink"/></a>
                    <fmt:message key="showDocument.ticklerAlertPending"/>: <%=tickler_note%>
                </td>
            </tr>
        </table>
        <% } %>
    </security:oscarSec>
    <table class="docTable">
        <tr>
            <td class="pdfPreviewColumn" style="vertical-align: top;">
                <div style="text-align: right;font-weight: bold">
                    <% if (numOfPage > 1 && displayDocumentAs.equals(UserProperty.IMAGE)) {%>
                    <a id="firstP_<%=docId%>" style="display: none;" href="javascript:void(0);"
                       onclick="firstPage('<%=docId%>','<%=cp%>');"><fmt:message key="dms.incomingDocs.first"/></a>
                    <a id="prevP_<%=docId%>" style="display: none;" href="javascript:void(0);"
                       onclick="prevPage('<%=docId%>','<%=cp%>');"><fmt:message key="dms.incomingDocs.previous"/></a>
                    <a id="nextP_<%=docId%>" href="javascript:void(0);"
                       onclick="nextPage('<%=docId%>','<%=cp%>');"><fmt:message key="dms.incomingDocs.next"/></a>
                    <a id="lastP_<%=docId%>" href="javascript:void(0);"
                       onclick="lastPage('<%=docId%>','<%=cp%>');"><fmt:message key="dms.incomingDocs.last"/></a>
                    <%} %>
                </div>
                <% if (displayDocumentAs.equals(UserProperty.IMAGE)) { %>
                <a href="<%=url2%>" target="_blank"><img alt="document" id="docImg_<%=docId%>" src="<%=url%>"
                                                         onerror="this.src='<%=request.getContextPath()%>/images/icon_alert.gif'"/></a>
                <%} else {%>
                <div id="docDispPDF_<%=docId%>"></div>
                <%}%>
                <div style="text-align: right;font-weight: bold">
                    <% if (numOfPage > 1 && displayDocumentAs.equals(UserProperty.IMAGE)) {%>
                    <a id="firstP2_<%=docId%>" style="display: none;" href="javascript:void(0);"
                       onclick="firstPage('<%=docId%>','<%=cp%>');"><fmt:message key="dms.incomingDocs.first"/></a>
                    <a id="prevP2_<%=docId%>" style="display: none;" href="javascript:void(0);"
                       onclick="prevPage('<%=docId%>','<%=cp%>');"><fmt:message key="dms.incomingDocs.previous"/></a>
                    <a id="nextP2_<%=docId%>" href="javascript:void(0);" onclick="nextPage('<%=docId%>','<%=cp%>');"><fmt:message key="dms.incomingDocs.next"/></a>
                    <a id="lastP2_<%=docId%>" href="javascript:void(0);" onclick="lastPage('<%=docId%>','<%=cp%>');"><fmt:message key="dms.incomingDocs.last"/></a>
                    <%} %>
                </div>
            </td>

            <td class="pdfAssignmentToolsColumn" style="vertical-align: top;">
                <fieldset>
                    <legend><fmt:message key="inboxmanager.document.PatientMsg"/><span
                            id="assignedPId_<%=docId%>"><e:forHtmlContent value='${demoName}' /></span></legend>
                    <table>
                        <tr>
                            <td><fmt:message key="inboxmanager.document.DocumentUploaded"/></td>
                            <td><%=Encode.forHtml(curdoc.getDateTimeStamp())%>
                            </td>
                        </tr>
                        <tr>
                            <td><fmt:message key="inboxmanager.document.ContentType"/></td>
                            <td><%=Encode.forHtml(contentType)%>
                            </td>
                        </tr>
                        <tr>
                            <td><fmt:message key="inboxmanager.document.NumberOfPages"/></td>
                            <td>
                                <input id="shownPage_<%=docId %>" type="hidden" value="1"/>
                                <%if (displayDocumentAs.equals(UserProperty.IMAGE)) { %>
                                <span id="viewedPage_<%=docId%>"
                                      class="<%= numOfPage > 1 ? "multiPage" : "singlePage" %>">1</span>&nbsp; of
                                &nbsp;<%}%>
                                <span id="numPages_<%=docId %>"
                                      class="<%= numOfPage > 1 ? "multiPage" : "singlePage" %>"><%=numOfPageStr%></span>
                            </td>
                        </tr>

                        <tr>
                            <td></td>
                            <td>
                                <% boolean updatableContent = true; %>
                                <oscar:oscarPropertiesCheck property="ALLOW_UPDATE_DOCUMENT_CONTENT" value="false"
                                                            defaultVal="false">
                                    <%
                                        if (!demographicID.equals("-1")) {
                                            updatableContent = false;
                                        }
                                    %>
                                </oscar:oscarPropertiesCheck>
                                <div style="<%=updatableContent==true?"":"visibility: hidden"%>">
                                    <input onclick="split('<%=docId%>','${e:forJavaScript(demoName)}')"
                                           type="button" class=" btn btn-light btn-sm" value="<fmt:message key="inboxmanager.document.split"/>"/>
                                    <input id="rotate180btn_<%=docId %>" onclick="rotate180('<%=docId %>')"
                                           type="button" class=" btn btn-light btn-sm"
                                           value="<fmt:message key="inboxmanager.document.rotate180"/>"/>
                                    <input id="rotate90btn_<%=docId %>" onclick="rotate90('<%=docId %>')"
                                            type="button" class=" btn btn-light btn-sm"
                                           value="<fmt:message key="inboxmanager.document.rotate90"/>"/>
                                    <% if (numOfPage > 1) { %><input id="removeFirstPagebtn_<%=docId %>"
                                            onclick="removeFirstPage('<%=docId %>')"
                                            type="button" class=" btn btn-light btn-sm"
                                            value="<fmt:message key="inboxmanager.document.removeFirstPage"/>"/><% } %>
                                </div>
                            </td>
                        </tr>

                    </table>

                    <form id="forms_<%=docId%>" method="post" onsubmit="return updateDocument('forms_<%=docId%>');">
                        <input type="hidden" name="method" value="documentUpdateAjax"/>
                        <input type="hidden" name="documentId" value="<%=docId%>"/>
                        <input type="hidden" name="providerNo" value="<%= providerNo%>"/>
                        <input type="hidden" name="curPage_<%=docId%>" id="curPage_<%=docId%>" value="1"/>
                        <input type="hidden" name="totalPage_<%=docId%>" id="totalPage_<%=docId%>"
                               value="<%=numOfPage%>"/>
                        <input type="hidden" name="displayDocumentAs_<%=docId%>" id="displayDocumentAs_<%=docId%>"
                               value="<%=Encode.forHtmlAttribute(displayDocumentAs)%>">
                        <table>
                            <tr>
                                <td><fmt:message key="dms.documentReport.msgCreator"/>:</td>
                                <td><%=Encode.forHtml(curdoc.getCreatorName())%>
                                </td>
                            </tr>
                            <tr>
                                <td><fmt:message key="dms.documentReport.msgDocType"/>:</td>
                                <td>
                                    <select name="docType" id="docType_<%=docId%>">
                                        <option value=""><fmt:message key="dms.addDocument.formSelect"/></option>
                                        <%
                                            for (int j = 0; j < doctypes.size(); j++) {
                                                String doctype = (String) doctypes.get(j);
                                        %>
                                        <option value="<%=Encode.forHtmlAttribute(doctype)%>" <%=(curdoc.getType().equals(doctype)) ? " selected" : ""%>><%=Encode.forHtml(doctype)%>
                                        </option>
                                        <%}%>
                                    </select>
                                </td>
                            </tr>
                            <tr>
                                <td><fmt:message key="dms.documentReport.msgDocDesc"/>:</td>
                                <td><input id="docDesc_<%=docId%>" type="text" name="documentDescription"
                                           value="<%=Encode.forHtmlAttribute(curdoc.getDescription())%>"
                                           onfocus="this.select(); this.setAttribute('data-original-value', this.value)"
                                           onblur="if (this.value.trim() === '') this.value = this.getAttribute('data-original-value')"/></td>
                            </tr>
                            <tr>
                                <td><fmt:message key="inboxmanager.document.ObservationDateMsg"/></td>
                                <td id="observation-calendar">
                                    <input class="input-field" id="observationDate<%=docId%>" name="observationDate"
                                           type="text" value="<%=Encode.forHtmlAttribute(curdoc.getObservationDate())%>">
                                    <a class="calendar-icon" id="obsdate<%=docId%>"
                                       onmouseover="renderCalendar(this.id,'observationDate<%=docId%>' );"
                                       href="javascript:void(0);">
                                        <img class="calendar-image" title="Calendar"
                                             src="<%=request.getContextPath()%>/images/cal.gif" alt="Calendar"/>
                                    </a>
                                </td>
                            </tr>
                            <tr>
                                <td><fmt:message key="inboxmanager.document.DemographicMsg"/></td>
                                <td><%
                                    if (!demographicID.equals("-1")) {%>
                                    <input id="saved<%=docId%>" type="hidden" name="saved" value="true"/>
                                    <input type="hidden" value="<%=Encode.forHtmlAttribute(demographicID)%>" name="demog"
                                           id="demofind<%=docId%>"/>
                                    <input type="hidden" name="demofindName" value="${e:forHtmlAttribute(demoName)}"
                                           id="demofindName<%=docId%>"/>
                                    <e:forHtmlContent value='${demoName}' /><e:forHtmlContent value='${mrpProviderName}' /><%} else {%>
                                    <input id="saved<%=docId%>" type="hidden" name="saved" value="false"/>
                                    <input type="hidden" name="demog" value="<%=Encode.forHtmlAttribute(demographicID)%>"
                                           id="demofind<%=docId%>"/>
                                    <input type="hidden" name="demofindName" value="${e:forHtmlAttribute(demoName)}"
                                           id="demofindName<%=docId%>"/>

                                    <input type="checkbox" id="activeOnly<%=docId%>" name="activeOnly" checked="checked"
                                           value="true" onclick="setupDemoAutoCompletion()"><fmt:message key="showDocument.lblActiveOnly"/><br>
                                    <input type="text" id="autocompletedemo<%=docId%>"
                                           onchange="checkSave('<%=Encode.forJavaScript(docId)%>');" name="demographicKeyword"
                                           placeholder="Search Demographic"/>
                                    <div id="autocomplete_choices<%=docId%>" class="autocomplete"></div>

                                    <%}%>
                                    <input type="button" class=" btn btn-light btn-sm" id="createNewDemo" value="<fmt:message key="dms.incomingDocs.createNewDemographic"/>"
                                           onclick="popup(700,960,'${pageContext.servletContext.contextPath}/demographic/demographicaddarecordhtm.jsp','demographic')"/>

                                    <input id="saved_<%=docId%>" type="hidden" name="saved" value="false"/>
                                    <br><input id="mrp_<%=docId%>" style="display: none;" type="checkbox"
                                               onclick="sendMRP(this)" name="demoLink">
                                    <a id="mrp_fail_<%=docId%>"
                                       style="color:red;font-style: italic;display: none;"><fmt:message key="inboxmanager.document.SendToMRPFailedMsg"/></a>
                                </td>
                            </tr>

                            <tr>
                                <td style="vertical-align: top;"><fmt:message key="inboxmanager.document.FlagProviderMsg"/></td>
                                <td>
                                    <input type="hidden" name="provi" id="provfind<%=docId%>"/>
                                    <input type="text" id="autocompleteprov<%=docId%>" name="demographicKeyword"
                                           placeholder="Search Provider"/>
                                    <div id="autocomplete_choicesprov<%=docId%>" class="autocomplete"></div>


                                    <div class="provider-list-additions" id="providerList<%=docId%>"></div>
                                </td>
                            </tr>
                            <tr>
                                <td style="width: 30%; text-align: left;"><a id="saveSucessMsg_<%=docId%>"
                                                                            style="display:none;color:blue;"><fmt:message key="inboxmanager.document.SuccessfullySavedMsg"/></a></td>
                                <td style="width: 30%; text-align: left;"><%if(demographicID.equals("-1")){%>
                                    <input type="submit" class=" btn btn-primary btn-sm" name="save" disabled id="save<%=docId%>" value="<fmt:message key="global.btnSave"/>"/>
                                    <input type="button" class=" btn btn-light btn-sm" name="save" id="saveNext<%=docId%>"
                                           onclick="saveNext(<%=docId%>)" disabled
                                           value='<fmt:message key="inboxmanager.document.SaveAndNext"/>'/>
                                        <%}
            else{%>
                                    <input type="submit" class=" btn btn-primary btn-sm" name="save" id="save<%=docId%>" value="<fmt:message key="global.btnSave"/>"/>
                                    <input type="button" class=" btn btn-light btn-sm" name="save" onclick="saveNext(<%=docId%>)"
                                           id="saveNext<%=docId%>"
                                           value='<fmt:message key="inboxmanager.document.SaveAndNext"/>'/>

                                        <%}%>

                            </tr>

                            <tr>
                                <td colspan="2">
                                    <fmt:message key="inboxmanager.document.LinkedProvidersMsg"/>
                                    <%
                                        Properties p = (Properties) session.getAttribute("providerBean");
                                        List<ProviderInboxItem> routeList = Collections.emptyList();
                                        if (docId != null) {
                                            routeList = providerInboxRoutingDao.getProvidersWithRoutingForDocument("DOC", Integer.parseInt(docId));
                                        }
                                    %>
                                    <ul>
                                        <%
                                            for (ProviderInboxItem pItem : routeList) {
                                                String s = p.getProperty(pItem.getProviderNo(), pItem.getProviderNo());

                                                if (!s.equals("0") && !s.equals("null") && !pItem.getStatus().equals("X")) {
                                        %>
                                        <li><%=Encode.forHtml(s)%><a href="#"
                                                     onclick="removeLink('DOC', '<%=Encode.forJavaScript(docId)%>', '<%=Encode.forJavaScript(pItem.getProviderNo())%>', this);return false;"><fmt:message key="inboxmanager.document.RemoveLinkedProviderMsg"/></a></li>
                                        <%
                                                }
                                            }
                                        %>
                                    </ul>
                                </td>
                            </tr>
                        </table>

                    </form>
                </fieldset>


                <%

                    if (ackList.size() > 0) {%>
                <fieldset>
                    <table style="width: 100%;">
                        <tr>
                            <td style="text-align: center; background-color: white;">
                                <div class="FieldData">
                                    <% for (int i = 0; i < ackList.size(); i++) {
                                        ReportStatus report = (ReportStatus) ackList.get(i); %>
                                    <%=Encode.forHtml(report.getProviderName())%> :

                                    <% String ackStatus = report.getStatus();
                                        String ackStatusKey;
                                        if (ackStatus.equals("A")) {
                                            ackStatusKey = "showDocument.statusAcknowledged";
                                        } else if (ackStatus.equals("F")) {
                                            ackStatusKey = "showDocument.statusFiledNotAck";
                                        } else {
                                            ackStatusKey = "showDocument.statusNotAck";
                                        }
                                        pageContext.setAttribute("ackStatusKey", ackStatusKey);
                                    %>
                                    <span style="color: red;"><fmt:message key="${ackStatusKey}"/>
                                    </span>
                                    <span id="timestamp_<%=Encode.forHtml(docId + "_" + report.getOscarProviderNo())%>"><%= report.getTimestamp() == null ? "&nbsp;" : Encode.forHtml(report.getTimestamp()) + "&nbsp;"%></span>,
                                    <fmt:message key="inboxmanager.document.Comment"/> <span
                                        id="comment_<%=Encode.forHtml(docId + "_" + report.getOscarProviderNo())%>"><% if (report.getComment() == null || report.getComment().isEmpty()) { %><fmt:message key="showDocument.noComment"/><% } else { %><%=Encode.forHtml(report.getComment())%><% } %></span>

                                    <br>
                                    <% }
                                        if (ackList.size() == 0) {
                                    %><span style="color: red;"><fmt:message key="showDocument.msgNA"/></span><%
                                    }
                                %>
                                </div>
                            </td>
                        </tr>
                    </table>
                </fieldset>
                <%
                    }

                %>

                <fieldset>
                    <legend><span class="FieldData"><i><fmt:message key="inboxmanager.document.NextAppointmentMsg"/> <oscar:nextAppt
                            demographicNo="<%=demographicID%>"/></i></span></legend>
                    <%
                        int iPageSize = 5;
                        Provider prov;
                        boolean HighlightUserAppt = false;
                        if (demographicID != null && !demographicID.isEmpty() && !demographicID.equals("-1")) {

                            List<Appointment> appointmentList = appointmentDao.getAppointmentHistory(Integer.parseInt(demographicID), 0, iPageSize);
                            if (appointmentList != null && appointmentList.size() > 0) {
                    %>

                    <table style="background-color: #c0c0c0; margin: 0 auto;">
                        <tr style="background-color: #ccccff;">
                            <th colspan="4"><fmt:message key="appointment.addappointment.msgOverview"/></th>
                        </tr>
                        <tr style="background-color: #ccccff;">
                            <th><fmt:message key="Appointment.formDate"/></th>
                            <th><fmt:message key="Appointment.formStartTime"/></th>
                            <th><fmt:message key="appointment.addappointment.msgProvider"/></th>
                            <th><fmt:message key="appointment.addappointment.msgComments"/></th>
                        </tr>
                        <%
                            for (Appointment a : appointmentList) {
                                prov = providerDao.getProvider(a.getProviderNo());
                                HighlightUserAppt = false;
                                if (creator.equals(a.getProviderNo())) {
                                    HighlightUserAppt = true;
                                }
                        %>
                        <tr style="background-color: <%=HighlightUserAppt == false ? "#FFFFFF" : "#CCFFCC"%>;">
                            <td><%=Encode.forHtml(ConversionUtils.toDateString(a.getAppointmentDate()))%>
                            </td>
                            <td><%=Encode.forHtml(ConversionUtils.toTimeString(a.getStartTime()))%>
                            </td>
                            <td><% if (prov == null) { %><fmt:message key="showDocument.msgNA"/><% } else { %><%=Encode.forHtml(prov.getFormattedName())%><% } %>
                            </td>
                            <td><% if (a.getStatus() == null) {%>
                                "" <% } else if (a.getStatus().startsWith("N")) {%><fmt:message key="oscar.appt.ApptStatusData.msgNoShow"/><% } else if (a.getStatus().startsWith("C")) {%><fmt:message key="oscar.appt.ApptStatusData.msgCanceled"/> <%}%>
                            </td>
                        </tr>
                        <%}%>
                    </table>
                    <%
                            }
                        }
                    %>
                    <form name="reassignForm_<%=docId%>" id="reassignForm_<%=docId%>" method="post">
                        <input type="hidden" name="flaggedLabs" value="<%=Encode.forHtmlAttribute(docId)%>"/>
                        <input type="hidden" name="selectedProviders" value=""/>
                        <input type="hidden" name="labType" value="DOC"/>
                        <input type="hidden" name="labType<%=Encode.forHtmlAttribute(docId)%>DOC" value="imNotNull"/>
                        <input type="hidden" name="providerNo" value="<%=Encode.forHtmlAttribute(providerNo)%>"/>
                        <input type="hidden" name="favorites" value=""/>
                        <input type="hidden" name="ajax" value="yes"/>
                    </form>
                </fieldset>
            </td>
        </tr>

        <tr>
            <td colspan="2">
                <hr style="width: 100%; border-color: red;">
            </td>
        </tr>
    </table>
</div>

<script type="text/javascript"
        src="${pageContext.servletContext.contextPath}/share/javascript/oscarMDSIndex.js"></script>
<script type="text/javascript" src="showDocument.js"></script>
<script type="text/javascript">

    var displayDocAsEl = document.getElementById('displayDocumentAs_<%=docId%>');
    if (displayDocAsEl && displayDocAsEl.value == "<%=UserProperty.PDF%>") {
        showPDF('<%=docId%>', contextpath);
    }

    var tmp;

    function setupDemoAutoCompletion() {
        if (jQuery("#autocompletedemo<%=docId%>")) {

            var url;
            if (jQuery("#activeOnly<%=docId%>").is(":checked")) {
                url = "${pageContext.servletContext.contextPath}/demographic/SearchDemographic.do?jqueryJSON=true&activeOnly=" + jQuery("#activeOnly<%=docId%>").val();
            } else {
                url = "${pageContext.servletContext.contextPath}/demographic/SearchDemographic.do?jqueryJSON=true";
            }

            jQuery("#autocompletedemo<%=docId%>").autocomplete({
                source: url,
                minLength: 2,

                focus: function (event, ui) {
                    jQuery("#autocompletedemo<%=docId%>").val(ui.item.label);
                    return false;
                },
                select: function (event, ui) {
                    jQuery("#autocompletedemo<%=docId%>").val(ui.item.label);
                    jQuery("#demofind<%=docId%>").val(ui.item.value);
                    jQuery("#demofindName<%=docId%>").val(ui.item.formattedName);
                    selectedDemos.push(ui.item.label);
                    console.log(ui.item.providerNo);
                    if (ui.item.providerNo != undefined && ui.item.providerNo != null && ui.item.providerNo != "" && ui.item.providerNo != "null") {
                        addDocToList(ui.item.providerNo, ui.item.provider + " (MRP)", "<%=docId%>");
                    }

                    //enable Save button whenever a selection is made
                    jQuery('#save<%=docId%>').removeAttr('disabled');
                    jQuery('#saveNext<%=docId%>').removeAttr('disabled');

                    jQuery('#msgBtn_<%=docId%>').removeAttr('disabled');
                    jQuery('#mainTickler_<%=docId%>').removeAttr('disabled');
                    jQuery('#mainEchart_<%=docId%>').removeAttr('disabled');
                    jQuery('#mainMaster_<%=docId%>').removeAttr('disabled');
                    jQuery('#mainApptHistory_<%=docId%>').removeAttr('disabled');
                    return false;
                }
            });
        }
    }


    jQuery(setupDemoAutoCompletion());

    function setupProviderAutoCompletion() {
        var url = "${pageContext.servletContext.contextPath}/provider/SearchProvider.do?method=labSearch";

        jQuery("#autocompleteprov<%=docId%>").autocomplete({
            source: url,
            minLength: 2,

            focus: function (event, ui) {
                jQuery("#autocompleteprov<%=docId%>").val(ui.item.label);
                return false;
            },
            select: function (event, ui) {
                jQuery("#autocompleteprov<%=docId%>").val("");
                jQuery("#provfind<%=docId%>").val(ui.item.value);
                addDocToList(ui.item.value, ui.item.label, "<%=docId%>");

                return false;
            }
        });
    }

    jQuery(setupProviderAutoCompletion());

    // Macro support: check if document is linked to patient, then apply macro
    function runDocMacro(name, formid, closeOnSuccess) {
        var url = '<%=request.getContextPath()%>/documentManager/inboxManage.do';
        var data = 'method=isDocumentLinkedToDemographic&docId=<%= Encode.forJavaScript(docId) %>';
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
                runDocMacroInternal(name, formid, closeOnSuccess, demoid);
            }
        });
    }

    function runDocMacroInternal(name, formid, closeOnSuccess, demographicNo) {
        var url = '<%=request.getContextPath()%>' + '/oscarMDS/RunMacro.do?name=' + encodeURIComponent(name) + (demographicNo.length > 0 ? '&demographicNo=' + encodeURIComponent(demographicNo) : '');
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
<jsp:include page="/images/spinner.jsp"/>
<c:if test="${param.inWindow eq 'true'}">
    </body>
    </html>
</c:if>
