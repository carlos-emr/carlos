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

<%@page import="java.text.SimpleDateFormat" %>
<%@ page
        import="ca.openosp.openo.utility.WebUtils" %>
<%@page import="org.apache.commons.text.StringEscapeUtils" %>
<%@ page import="java.util.*" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@page import="org.owasp.encoder.Encode"%>
<%@ taglib uri="/WEB-INF/rewrite-tag.tld" prefix="rewrite" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="https://www.owasp.org/index.php/OWASP_Java_Encoder_Project" prefix="e" %>
<%@ page import="ca.openosp.openo.log.*" %>
<%@ page import="ca.openosp.openo.util.ConversionUtils" %>
<%@page import="ca.openosp.openo.PMmodule.dao.ProviderDao" %>
<%@page import="ca.openosp.openo.lab.ca.all.*,ca.openosp.openo.mds.data.*" %>
<%@page import="ca.openosp.openo.commn.dao.*,ca.openosp.openo.commn.model.*,ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.documentManager.EDocUtil" %>
<%@ page import="ca.openosp.openo.documentManager.EDoc" %>
<%@ page import="ca.openosp.openo.documentManager.IncomingDocUtil" %>
<%@ page import="ca.openosp.openo.log.LogAction" %>
<%@ page import="ca.openosp.openo.log.LogConst" %>
<%@ page import="ca.openosp.openo.lab.ca.all.AcknowledgementData" %>
<%@ page import="ca.openosp.openo.mds.data.ReportStatus" %>
<%@ page import="ca.openosp.openo.commn.dao.*" %>
<%@ page import="ca.openosp.openo.commn.model.*" %>
<%@ page import="ca.openosp.openo.utility.LoggedInInfo" %>
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
      ackFunc = "updateStatus('acknowledgeForm_" + docId + "'," + inQueueB + ");";
    } else {
      ackFunc = "getDocComment('" + docId + "','" + providerNo + "'," + inQueueB + ");";
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
        <script language="javascript" type="text/javascript"
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
        <input type="hidden" name="comment" id="comment_<%=docId%>" value="<%=docCommentTxt%>">
        <% if (demographicID != null && !demographicID.equals("") && !demographicID.equalsIgnoreCase("null") && !ackedOrFiled) {%>
        <input type="submit" id="ackBtn_<%=docId%>"
               value="<fmt:message key="oscarMDS.segmentDisplay.btnAcknowledge"/>">
        <input type="button" value="Comment" onclick="addDocComment('<%=docId%>','<%=providerNo%>')"/>
        <%}%>
        <input type="button" id="fwdBtn_<%=docId%>" value="<fmt:message key="oscarMDS.index.btnForward"/>"
               onClick="ForwardSelectedRows(<%=docId%> + ':DOC', null, null);">
        <%if (!ackedOrFiled) { %>
        <input type="button" id="fileBtn_<%=docId%>" value="<fmt:message key="oscarMDS.index.btnFile"/>"
               onclick="fileDoc('<%=docId%>');">
        <%} %>
        <input type="button" id="closeBtn_<%=docId%>" value=" <fmt:message key="global.btnClose"/> "
               onClick="window.close()">
        <input type="button" id="printBtn_<%=docId%>" value=" <fmt:message key="global.btnPrint"/> "
               onClick="popup(700,960,'<%=url2%>','file download')">
        <%
            String btnDisabled = "disabled";
            if (demographicID != null && !demographicID.equals("") && !demographicID.equalsIgnoreCase("null") && !demographicID.equals("-1")) {
                btnDisabled = "";
            }

        %>
        <input type="button" id="msgBtn_<%=docId%>" value="Msg"
               onclick="popupPatient(700,960,'${pageContext.servletContext.contextPath}/messenger/SendDemoMessage.do?demographic_no=','msg', '<%=docId%>')" <%=btnDisabled %>/>

        <!--input type="button" id="ticklerBtn_<%=docId%>" value="Tickler" onclick="handleDocSave('<%=docId%>','addTickler')"/-->
        <input type="button" id="mainTickler_<%=docId%>" value="Tickler" onClick="popupPatientTickler(710, 1024,'${pageContext.servletContext.contextPath}/tickler/ticklerAdd.jsp?', 'Tickler','<%=docId%>')" <%=btnDisabled %>>
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

        <input type="button" id="mainEchart_<%=docId%>"
               value=" <fmt:message key="oscarMDS.segmentDisplay.btnEChart"/> "
               onClick="popupPatient(710, 1024,'${pageContext.servletContext.contextPath}/oscarEncounter/IncomingEncounter.do?reason=<fmt:message key="oscarMDS.segmentDisplay.labResults"/>&curDate=<%=currentDate%>>&appointmentNo=&appointmentDate=&startTime=&status=&demographicNo=', 'encounter', '<%=docId%>')" <%=btnDisabled %>>
        <input type="button" id="mainMaster_<%=docId%>" value=" <fmt:message key="oscarMDS.segmentDisplay.btnMaster"/>"
               onClick="popupPatient(710,1024,'${pageContext.servletContext.contextPath}/demographic/demographiccontrol.jsp?displaymode=edit&dboperation=search_detail&demographic_no=','master','<%=docId%>')" <%=btnDisabled %>>
        <input type="button" id="mainApptHistory_<%=docId%>"
               value=" <fmt:message key="oscarMDS.segmentDisplay.btnApptHist"/>"
               onClick="popupPatient(710,1024,'${pageContext.servletContext.contextPath}/demographic/demographiccontrol.jsp?orderby=appttime&displaymode=appt_history&dboperation=appt_history&limit1=0&limit2=25&demographic_no=','ApptHist','<%=docId%>')" <%=btnDisabled %>>

        <input type="button" id="refileDoc_<%=docId%>"
               value="<fmt:message key="oscarEncounter.noteBrowser.msgRefile"/>" onclick="refileDoc('<%=docId%>');" <%=refileBtnVisibility%> >
        <select id="queueList_<%=docId%>" name="queueList"
                onchange="handleQueueListChange(this, document.getElementById('refileDoc_<%=docId%>'), '<%=docCurrentFiledQueue%>')">
            <%
                for (Hashtable ht : queues) {
                    int id = (Integer) ht.get("id");
                    String qName = (String) ht.get("queue");
            %>
            <option value="<%=id%>" <%=((id == queueId) ? " selected" : "")%>><%= qName%>
            </option>
            <%}%>
        </select>
    </form>
    <table class="docTable">
        <tr>
            <td valign="top" class="pdfPreviewColumn">
                <div style="text-align: right;font-weight: bold">
                    <% if (numOfPage > 1 && displayDocumentAs.equals(UserProperty.IMAGE)) {%>
                    <a id="firstP_<%=docId%>" style="display: none;" href="javascript:void(0);"
                       onclick="firstPage('<%=docId%>','<%=cp%>');">First</a>
                    <a id="prevP_<%=docId%>" style="display: none;" href="javascript:void(0);"
                       onclick="prevPage('<%=docId%>','<%=cp%>');">Prev</a>
                    <a id="nextP_<%=docId%>" href="javascript:void(0);"
                       onclick="nextPage('<%=docId%>','<%=cp%>');">Next</a>
                    <a id="lastP_<%=docId%>" href="javascript:void(0);"
                       onclick="lastPage('<%=docId%>','<%=cp%>');">Last</a>
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
                       onclick="firstPage('<%=docId%>','<%=cp%>');">First</a>
                    <a id="prevP2_<%=docId%>" style="display: none;" href="javascript:void(0);"
                       onclick="prevPage('<%=docId%>','<%=cp%>');">Prev</a>
                    <a id="nextP2_<%=docId%>" href="javascript:void(0);" onclick="nextPage('<%=docId%>','<%=cp%>');">Next</a>
                    <a id="lastP2_<%=docId%>" href="javascript:void(0);" onclick="lastPage('<%=docId%>','<%=cp%>');">Last</a>
                    <%} %>
                </div>
            </td>

            <td valign="top" class="pdfAssignmentToolsColumn">
                <fieldset>
                    <legend><fmt:message key="inboxmanager.document.PatientMsg"/><span
                            id="assignedPId_<%=docId%>"><e:forHtmlContent value='${demoName}' /></span></legend>
                    <table>
                        <tr>
                            <td><fmt:message key="inboxmanager.document.DocumentUploaded"/></td>
                            <td><%=curdoc.getDateTimeStamp()%>
                            </td>
                        </tr>
                        <tr>
                            <td><fmt:message key="inboxmanager.document.ContentType"/></td>
                            <td><%=contentType%>
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
                                           type="button" value="<fmt:message key="inboxmanager.document.split"/>"/>
                                    <input id="rotate180btn_<%=docId %>" onclick="rotate180('<%=docId %>')"
                                           type="button"
                                           value="<fmt:message key="inboxmanager.document.rotate180"/>"/>
                                    <input id="rotate90btn_<%=docId %>" onclick="rotate90('<%=docId %>')" type="button"
                                           value="<fmt:message key="inboxmanager.document.rotate90"/>"/>
                                    <% if (numOfPage > 1) { %><input id="removeFirstPagebtn_<%=docId %>"
                                                                     onclick="removeFirstPage('<%=docId %>')"
                                                                     type="button"
                                                                     value="<fmt:message key="inboxmanager.document.removeFirstPage"/>"/><% } %>
                                </div>
                            </td>
                        </tr>

                    </table>

                    <form id="forms_<%=docId%>" onsubmit="return updateDocument('forms_<%=docId%>');">
                        <input type="hidden" name="method" value="documentUpdateAjax"/>
                        <input type="hidden" name="documentId" value="<%=docId%>"/>
                        <input type="hidden" name="providerNo" value="<%= providerNo%>"/>
                        <input type="hidden" name="curPage_<%=docId%>" id="curPage_<%=docId%>" value="1"/>
                        <input type="hidden" name="totalPage_<%=docId%>" id="totalPage_<%=docId%>"
                               value="<%=numOfPage%>"/>
                        <input type="hidden" name="displayDocumentAs_<%=docId%>" id="displayDocumentAs_<%=docId%>"
                               value="<%=displayDocumentAs%>">
                        <table border="0">
                            <tr>
                                <td><fmt:message key="dms.documentReport.msgCreator"/>:</td>
                                <td><%=curdoc.getCreatorName()%>
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
                                        <option value="<%= doctype%>" <%=(curdoc.getType().equals(doctype)) ? " selected" : ""%>><%= doctype%>
                                        </option>
                                        <%}%>
                                    </select>
                                </td>
                            </tr>
                            <tr>
                                <td><fmt:message key="dms.documentReport.msgDocDesc"/>:</td>
                                <td><input id="docDesc_<%=docId%>" type="text" name="documentDescription"
                                           value="<%=curdoc.getDescription()%>"
                                           onfocus="this.select(); this.setAttribute('data-original-value', this.value)"
                                           onblur="if (this.value.trim() === '') this.value = this.getAttribute('data-original-value')"/></td>
                            </tr>
                            <tr>
                                <td><fmt:message key="inboxmanager.document.ObservationDateMsg"/></td>
                                <td id="observation-calendar">
                                    <input class="input-field" id="observationDate<%=docId%>" name="observationDate"
                                           type="text" value="<%=curdoc.getObservationDate()%>">
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
                                    <input type="hidden" value="<%=demographicID%>" name="demog"
                                           id="demofind<%=docId%>"/>
                                    <input type="hidden" name="demofindName" value="${e:forHtmlAttribute(demoName)}"
                                           id="demofindName<%=docId%>"/>
                                    <e:forHtmlContent value='${demoName}' /><e:forHtmlContent value='${mrpProviderName}' /><%} else {%>
                                    <input id="saved<%=docId%>" type="hidden" name="saved" value="false"/>
                                    <input type="hidden" name="demog" value="<%=demographicID%>"
                                           id="demofind<%=docId%>"/>
                                    <input type="hidden" name="demofindName" value="${e:forHtmlAttribute(demoName)}"
                                           id="demofindName<%=docId%>"/>

                                    <input type="checkbox" id="activeOnly<%=docId%>" name="activeOnly" checked="checked"
                                           value="true" onclick="setupDemoAutoCompletion()">Active Only<br>
                                    <input type="text" id="autocompletedemo<%=docId%>"
                                           onchange="checkSave('<%=docId%>');" name="demographicKeyword"
                                           placeholder="Search Demographic"/>
                                    <div id="autocomplete_choices<%=docId%>" class="autocomplete"></div>

                                    <%}%>
                                    <input type="button" id="createNewDemo" value="Create New Demographic"
                                           onclick="popup(700,960,'${pageContext.servletContext.contextPath}/demographic/demographicaddarecordhtm.jsp','demographic')"/>

                                    <input id="saved_<%=docId%>" type="hidden" name="saved" value="false"/>
                                    <br><input id="mrp_<%=docId%>" style="display: none;" type="checkbox"
                                               onclick="sendMRP(this)" name="demoLink">
                                    <a id="mrp_fail_<%=docId%>"
                                       style="color:red;font-style: italic;display: none;"><fmt:message key="inboxmanager.document.SendToMRPFailedMsg"/></a>
                                </td>
                            </tr>

                            <tr>
                                <td valign="top"><fmt:message key="inboxmanager.document.FlagProviderMsg"/></td>
                                <td>
                                    <input type="hidden" name="provi" id="provfind<%=docId%>"/>
                                    <input type="text" id="autocompleteprov<%=docId%>" name="demographicKeyword"
                                           placeholder="Search Provider"/>
                                    <div id="autocomplete_choicesprov<%=docId%>" class="autocomplete"></div>


                                    <div class="provider-list-additions" id="providerList<%=docId%>"></div>
                                </td>
                            </tr>
                            <tr>
                                <td width="30%" colspan="1" align="left"><a id="saveSucessMsg_<%=docId%>"
                                                                            style="display:none;color:blue;"><fmt:message key="inboxmanager.document.SuccessfullySavedMsg"/></a></td>
                                <td width="30%" colspan="1" align="left"><%if(demographicID.equals("-1")){%>
                                    <input type="submit" name="save" disabled id="save<%=docId%>" value="Save"/>
                                    <input type="button" name="save" id="saveNext<%=docId%>"
                                           onclick="saveNext(<%=docId%>)" disabled
                                           value='<fmt:message key="inboxmanager.document.SaveAndNext"/>'/>
                                        <%}
            else{%>
                                    <input type="submit" name="save" id="save<%=docId%>" value="Save"/>
                                    <input type="button" name="save" onclick="saveNext(<%=docId%>)"
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
                                        <li><%=s%><a href="#"
                                                     onclick="removeLink('DOC', '<%=docId %>', '<%=pItem.getProviderNo() %>', this);return false;"><fmt:message key="inboxmanager.document.RemoveLinkedProviderMsg"/></a></li>
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
                    <table width="100%" height="20" cellpadding="2" cellspacing="2">
                        <tr>
                            <td align="center" bgcolor="white">
                                <div class="FieldData">
                                    <!--center-->
                                    <% for (int i = 0; i < ackList.size(); i++) {
                                        ReportStatus report = (ReportStatus) ackList.get(i); %>
                                    <%= report.getProviderName() %> :

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
                                    <span id="timestamp_<%=docId + "_" + report.getOscarProviderNo()%>"><%= report.getTimestamp() == null ? "&nbsp;" : report.getTimestamp() + "&nbsp;"%></span>,
                                    comment: <span
                                        id="comment_<%=docId + "_" + report.getOscarProviderNo()%>"><%=report.getComment() == null || report.getComment().equals("") ? "no comment" : report.getComment()%></span>

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

                    <table bgcolor="#c0c0c0" align="center" valign="top">
                        <tr bgcolor="#ccccff">
                            <th colspan="4"><fmt:message key="appointment.addappointment.msgOverview"/></th>
                        </tr>
                        <tr bgcolor="#ccccff">
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
                        <tr bgcolor="<%=HighlightUserAppt == false ? "#FFFFFF" : "#CCFFCC"%>">
                            <td><%=ConversionUtils.toDateString(a.getAppointmentDate())%>
                            </td>
                            <td><%=ConversionUtils.toTimeString(a.getStartTime())%>
                            </td>
                            <td><%=prov == null ? "N/A" : prov.getFormattedName()%>
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
                    <form name="reassignForm_<%=docId%>" id="reassignForm_<%=docId%>">
                        <input type="hidden" name="flaggedLabs" value="<%= docId%>"/>
                        <input type="hidden" name="selectedProviders" value=""/>
                        <input type="hidden" name="labType" value="DOC"/>
                        <input type="hidden" name="labType<%= docId%>DOC" value="imNotNull"/>
                        <input type="hidden" name="providerNo" value="<%= providerNo%>"/>
                        <input type="hidden" name="favorites" value=""/>
                        <input type="hidden" name="ajax" value="yes"/>
                    </form>
                </fieldset>
            </td>
        </tr>

        <tr>
            <td colspan="2">
                <hr width="100%" color="red">
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


</script>
<jsp:include page="/images/spinner.jsp"/>
<c:if test="${param.inWindow eq 'true'}">
    </body>
    </html>
</c:if>
