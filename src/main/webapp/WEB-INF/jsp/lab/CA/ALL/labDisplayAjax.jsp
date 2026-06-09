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

<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.PatientLabRouting" %>
<%@ page import="io.github.carlos_emr.carlos.util.ConversionUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao" %>
<%@ page import="java.util.*,
                 java.sql.*,
                 io.github.carlos_emr.carlos.db.*,
                 io.github.carlos_emr.carlos.lab.ca.all.*,
                 io.github.carlos_emr.carlos.lab.ca.all.util.*,
                 io.github.carlos_emr.carlos.utility.SpringUtils,
                 io.github.carlos_emr.carlos.lab.ca.all.parsers.*,
                 io.github.carlos_emr.carlos.lab.LabRequestReportLink,
                 io.github.carlos_emr.carlos.mds.data.ReportStatus,
                 io.github.carlos_emr.carlos.log.*,
                 io.github.carlos_emr.CarlosProperties,
                 org.apache.commons.codec.binary.Base64,
                 io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao,
                 io.github.carlos_emr.carlos.commn.model.Hl7TextInfo,
                 io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO,
                 io.github.carlos_emr.carlos.commn.model.UserProperty,
                 javax.swing.text.rtf.RTFEditorKit,
                 java.io.ByteArrayInputStream" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Tickler" %>
<%@ page import="io.github.carlos_emr.carlos.managers.TicklerManager" %>
<%@ page import="io.github.carlos_emr.carlos.log.LogAction" %>
<%@ page import="io.github.carlos_emr.carlos.log.LogConst" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.all.parsers.PATHL7Handler" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.all.parsers.ExcellerisOntarioHandler" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.all.Hl7textResultsData" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.all.AcknowledgementData" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/oscarProperties-tag.tld" prefix="oscarProperties" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_lab" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_lab");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    CarlosProperties props = CarlosProperties.getInstance();
    String segmentID = request.getParameter("segmentID");
    if (segmentID == null || segmentID.trim().isEmpty() || !segmentID.trim().matches("\\d+")) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid segmentID");
        return;
    }
    segmentID = segmentID.trim();
    String providerNo = request.getParameter("providerNo");
    String searchProviderNo = request.getParameter("searchProviderNo");
    String patientMatched = request.getParameter("patientMatched");

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

    String ackLabFunc;
    if (skipComment) {
        ackLabFunc = "handleLab('acknowledgeForm_" + SafeEncode.forJavaScriptAttribute(segmentID) + "','" + SafeEncode.forJavaScriptAttribute(segmentID) + "','ackLab');";
    } else {
        ackLabFunc = "getComment('" + SafeEncode.forJavaScriptAttribute(segmentID) + "','ackLab');";
    }

    Long reqIDL = LabRequestReportLink.getIdByReport("hl7TextMessage", Long.valueOf(segmentID.trim()));
    String reqID = reqIDL == null ? "" : reqIDL.toString();
    reqIDL = LabRequestReportLink.getRequestTableIdByReport("hl7TextMessage", Long.valueOf(segmentID.trim()));
    String reqTableID = reqIDL == null ? "" : reqIDL.toString();

    PatientLabRoutingDao dao = SpringUtils.getBean(PatientLabRoutingDao.class);
    String demographicID = "";
    for (PatientLabRouting r : dao.findByLabNoAndLabType(ConversionUtils.fromIntString(segmentID), "HL7")) {
        demographicID = "" + r.getDemographicNo();
    }

    boolean isLinkedToDemographic = false;
    if (demographicID != null && !demographicID.equals("") && !demographicID.equals("0")) {
        isLinkedToDemographic = true;
        LogAction.addLog((String) session.getAttribute("user"), LogConst.READ, LogConst.CON_HL7_LAB, segmentID, request.getRemoteAddr(), demographicID);
    } else {
        LogAction.addLog((String) session.getAttribute("user"), LogConst.READ, LogConst.CON_HL7_LAB, segmentID, request.getRemoteAddr());
    }

    boolean ackFlag = false;
    ArrayList ackList = AcknowledgementData.getAcknowledgements(segmentID);
    String labStatus = "";
    if (ackList != null) {
        for (int i = 0; i < ackList.size(); i++) {
            ReportStatus reportStatus = (ReportStatus) ackList.get(i);
            if (reportStatus.getProviderNo().equals(providerNo)) {
                labStatus = reportStatus.getStatus();
                if (labStatus.equals("A")) {
                    ackFlag = true;
                    break;
                }
            }
        }
    }

    String multiLabId = Hl7textResultsData.getMatchingLabs(segmentID);

    MessageHandler handler = Factory.getHandler(segmentID);
    String hl7 = Factory.getHL7Body(segmentID);
    Hl7TextInfoDao hl7TextInfoDao = (Hl7TextInfoDao) SpringUtils.getBean(Hl7TextInfoDao.class);
    int lab_no = Integer.parseInt(segmentID);
    String label = "";
    Hl7TextInfo hl7Lab = hl7TextInfoDao.findLabId(lab_no);
    if (hl7Lab.getLabel() != null) label = hl7Lab.getLabel();
// check for errors printing
    if (request.getAttribute("printError") != null && (Boolean) request.getAttribute("printError")) {
%>
<script language="JavaScript">
    alert("The lab could not be printed due to an error. Please see the server logs for more detail.");
</script>
<%
    }
%>
<script type="text/javascript" src="<%= request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js"></script>
<script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/carlos-ajax.js"></script>

<script language="JavaScript">
    popupStart = function (vheight, vwidth, varpage, windowname) {
        var page = varpage;
        windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes";
        var popup = window.open(varpage, windowname, windowprops);
    }


    <%
        int version = 0;
        if( multiLabId != null ) {
        String[] multiID = multiLabId.split(",");
            if( multiID.length > 1 ) {
                for( int k = 0; k < multiID.length; ++k ) {
                    if( multiID[k].equals(segmentID)) {
                        version = k;
                    }
                }
            }
        }
    %>

    getComment = function (labid, action) {
        var ret = true;
        var text = "V" + <%=version%> +"commentText" + labid + document.getElementById("providerNo").value;

        var commentVal = "";

        var textEl = document.getElementById(text);
        if (textEl != null) {
            // Use textContent (not innerHTML) to get decoded text, preventing
            // progressive HTML-entity double-encoding on each comment edit cycle.
            commentVal = textEl.textContent;
            if (commentVal == null) {
                commentVal = "";
            }
        }
        var commentID = "comment_" + labid;

        var comment = prompt('<fmt:message key="oscarMDS.segmentDisplay.msgComment"/>', commentVal);

        if (comment == null)
            ret = false;
        else if (comment != null && comment.length > 0) {
            document.getElementById(commentID).value = comment;
        } else {
            document.getElementById(commentID).value = commentVal;
        }
        if (ret)
            handleLab('acknowledgeForm_' + labid, labid, action);

        return false;
    }

    printPDF = function (doclabid) {
        document.forms['acknowledgeForm_' + doclabid].action = "<%=request.getContextPath()%>/lab/CA/ALL/PrintPDF";
        document.forms['acknowledgeForm_' + doclabid].submit();
    }

    linkreq = function (rptId, reqId) {
        var link = "<%= request.getContextPath() %>/lab/ViewLinkReq?table=hl7TextMessage&rptid=" + rptId + "&reqid=" + reqId + "<%=demographicID != null ? "&demographicNo=" + SafeEncode.forJavaScript(demographicID) : ""%>";
        window.open(link, "linkwin", "width=500, height=200");
    }

    popupStart = function (vheight, vwidth, varpage, windowname) {
        var windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes";
        var popup = window.open(varpage, windowname, windowprops);
    }
    handleLab = function (formid, labid, action) {
        var contextPath = '${pageContext.request.contextPath}';
        var url = contextPath + '/documentManager/inboxManage';
        var data = 'method=isLabLinkedToDemographic&labid=' + labid;
        CarlosAjax.request(url, {
            method: 'POST', parameters: data, onSuccess: function (transport) {
                var json = JSON.parse(transport.responseText);
                if (json != null) {
                    var success = json.isLinkedToDemographic;
                    var demoid = '';
                    //check if lab is linked to a providers
                    if (success) {
                        if (action == 'ackLab') {
                            if (confirmAck()) {
                                document.getElementById("status_" + labid).value = "A";
                                updateStatus(formid);
                            }
                        } else if (action == 'msgLab') {
                            demoid = json.demoId;
                            if (demoid != null && demoid.length > 0)
                                window.popup(700, 960, contextPath + '/messenger/SendDemoMessage?demographic_no=' + demoid, 'msg');
                        } else if (action == 'msgLabRecall') {
                            demoid = json.demoId;
                            if (demoid != null && demoid.length > 0)
                                window.popup(700, 980, contextPath + '/messenger/SendDemoMessage?demographic_no=' + demoid + "&recall", 'msgRecall');
                            window.popup(450, 600, contextPath + '/tickler/ForwardDemographicTickler?docType=HL7&docId=' + labid + '&demographic_no=' + demoid + '<carlos:encode value='<%= ticklerAssignee %>' context="javaScript"/>&priority=<carlos:encode value='<%= recallTicklerPriority %>' context="javaScript"/>&recall', 'ticklerRecall');
                        } else if (action == 'ticklerLab') {
                            demoid = json.demoId;
                            if (demoid != null && demoid.length > 0)
                                window.popup(450, 600, contextPath + '/tickler/ForwardDemographicTickler?docType=HL7&docId=' + labid + '&demographic_no=' + demoid, 'tickler')
                        } else if (action == 'addComment') {
                            addComment(formid, labid);
                        }

                    } else {
                        if (action == 'ackLab') {
                            if (confirmAckUnmatched()) {
                                document.getElementById("status_" + labid).value = "A";
                                updateStatus(formid);
                            } else {
                                var pn = document.getElementById("demoName" + labid).value;
                                if (pn) popupStart(360, 680, contextPath + '/oscarMDS/SearchPatient?labType=HL7&segmentID=' + labid + '&name=' + pn, 'searchPatientWindow');
                            }
                        } else {
                            alert("Please relate lab to a demographic.");
                            //pop up relate demo window
                            var pn = document.getElementById("demoName" + labid).value;
                            if (pn) popupStart(360, 680, contextPath + '/oscarMDS/SearchPatient?labType=HL7&segmentID=' + labid + '&name=' + pn, 'searchPatientWindow');
                        }
                    }
                }
            }
        });
    }

    function addComment(formid, labid) {
        var url = '<%=request.getContextPath()%>' + "/oscarMDS/UpdateStatus?method=addComment";
        var status = "status_" + labid;

        var statusEl = document.getElementById(status);
        if (statusEl.value == "") {
            statusEl.value = "N";
        }
        var data = new URLSearchParams(new FormData(document.getElementById(formid))).toString();

        var label = "V" + <%=version%> +"commentLabel" + labid + document.getElementById("providerNo").value;
        var text = "V" + <%=version%> +"commentText" + labid + document.getElementById("providerNo").value;
        var commentID = "comment_" + labid;
        var newComment;

        CarlosAjax.request(url, {
            method: 'POST', parameters: data, onSuccess: function (transport) {
                var labelEl = document.getElementById(label);
                var textEl = document.getElementById(text);
                if (labelEl != null && textEl != null) {
                    newComment = document.getElementById(commentID).value;
                    labelEl.textContent = "comment: ";
                    textEl.textContent = newComment;
                } else {
                    alert("Comment '" + document.getElementById(commentID).value + "' added!\nThis lab has been forwarded to you.");
                }
            }
        });
    }

    function confirmAck() {
        <% if (props.getProperty("confirmAck", "").equals("yes")) { %>
        return confirm('<fmt:message key="oscarMDS.index.msgConfirmAcknowledge"/>');
        <% } else { %>
        return true;
        <% } %>
    }

    confirmAckUnmatched = function () {
        return confirm('<fmt:message key="oscarMDS.index.msgConfirmAcknowledgeUnmatched"/>');
    }
    updateStatus = function (formid) {
        var url = '<%=request.getContextPath()%>' + "/oscarMDS/UpdateStatus";
        var data = new URLSearchParams(new FormData(document.getElementById(formid))).toString();

        CarlosAjax.request(url, {
            method: 'POST', parameters: data, onSuccess: function (transport) {
                var num = formid.split("_");
                if (num[1]) {
                    var labEl = document.getElementById('labdoc_' + num[1]);
                    if (labEl) { labEl.classList.add('carlos-collapsed'); }
                    //updateDocLabData(num[1]);
                    refreshCategoryList();

                }
            }
        });

    }

    createLabLabel = function (labFormId, ackformid, labelspanid, labelid) {
        var labForm = document.forms[labFormId];
        var ackForm = document.forms[ackformid];
        if (labForm && ackForm && labForm.label && ackForm.label) {
            labForm.label.value = ackForm.label.value;
        }
        var url = '<%=request.getContextPath()%>' + "/lab/CA/ALL/createLabLabel";
        var data = new URLSearchParams(new FormData(document.getElementById(labFormId))).toString();
        CarlosAjax.request(url, {
            method: 'POST', parameters: data

        });
        var labelSpanEl = document.getElementById(labelspanid);
        var labelEl = document.getElementById(labelid);
        if (labelSpanEl && labelEl) {
            labelSpanEl.textContent = "";
            var italicEl = document.createElement("i");
            italicEl.textContent = " Label: " + labelEl.value;
            labelSpanEl.appendChild(italicEl);
            labelEl.value = "";
        }
    };
</script>
<div id="labdoc_<carlos:encode value='<%= segmentID %>' context="htmlAttribute"/>">
    <!-- form forwarding of the lab -->
    <form name="reassignForm_<carlos:encode value='<%= segmentID %>' context="htmlAttribute"/>" method="post">
        <input type="hidden" name="flaggedLabs" value="<carlos:encode value='<%= segmentID %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="selectedProviders" value=""/>
        <input type="hidden" name="labType" value="HL7"/>
        <input type="hidden" name="labType<carlos:encode value='<%= segmentID %>' context="htmlAttribute"/>HL7" value="imNotNull"/> <%-- segmentID is validated as numeric at the top of this JSP --%>
        <input type="hidden" name="providerNo" id="providerNo" value="<carlos:encode value='<%= providerNo %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="ajax" value="yes"/>
    </form>
    <form name="labLabelForm" id="labLabelForm<carlos:encode value='<%= segmentID %>' context="htmlAttribute"/>" method='POST'
          onsubmit="createLabLabel('labLabelForm<carlos:encode value='<%= segmentID %>' context="javaScriptAttribute"/>');" action="javascript:void(0);">
        <input type="hidden" id="labNum" name="lab_no" value="<carlos:encode value='<%= String.valueOf(lab_no) %>' context="htmlAttribute"/>">
        <input type="hidden" id="label" name="label" value="<carlos:encode value='<%= label %>' context="htmlAttribute"/>">
    </form>
    <form name="acknowledgeForm" id="acknowledgeForm_<carlos:encode value='<%= segmentID %>' context="htmlAttribute"/>" onsubmit="javascript:void(0);" method="post"
          action="javascript:void(0);">

        <table width="100%" border="0" cellspacing="0" cellpadding="0">
            <tr>
                <td valign="top">
                    <table width="100%" border="0" cellspacing="0" cellpadding="3">
                        <tr>
                            <td align="left" class="MainTableTopRowRightColumn" width="100%">
                                <input type="hidden" name="segmentID" value="<carlos:encode value='<%= segmentID %>' context="htmlAttribute"/>"/>
                                <input type="hidden" name="multiID" value="<carlos:encode value='<%= multiLabId %>' context="htmlAttribute"/>"/>
                                <input type="hidden" name="providerNo" value="<carlos:encode value='<%= providerNo %>' context="htmlAttribute"/>"/>
                                <input type="hidden" name="status" value="<carlos:encode value='<%= labStatus %>' context="htmlAttribute"/>" id="status_<carlos:encode value='<%= segmentID %>' context="htmlAttribute"/>"/>
                                <input type="hidden" name="comment" value="" id="comment_<carlos:encode value='<%= segmentID %>' context="htmlAttribute"/>"/>
                                <input type="hidden" name="labType" value="HL7"/>
                                <input type="hidden" name="ajaxcall" value="yes"/>
                                <input type="hidden" id="demoName<carlos:encode value='<%= segmentID %>' context="htmlAttribute"/>"
                                       value="<%=java.net.URLEncoder.encode(handler.getLastName()+", "+handler.getFirstName(), StandardCharsets.UTF_8)%>"/>
                                <% if (!ackFlag) { %>
                                <input type="button"
                                       value="<fmt:message key="oscarMDS.segmentDisplay.btnAcknowledge"/>"
                                       onclick="<carlos:encode value='<%= ackLabFunc %>' context="htmlAttribute"/>">
                                <input type="button" value="<fmt:message key="oscarMDS.segmentDisplay.btnComment"/>"
                                       onclick="return getComment('<carlos:encode value='<%= segmentID %>' context="javaScriptAttribute"/>','addComment');">
                                <% } %>
                                                    <c:set var="__enc_1"><carlos:encode value='<%= segmentID %>' context="uriComponent"/></c:set>
                                       <c:set var="__enc_2"><carlos:encode value='<%= providerNo %>' context="uriComponent"/></c:set>
                                       <c:set var="__enc_3"><carlos:encode value='<%= searchProviderNo %>' context="uriComponent"/></c:set>
                   <input type="button" class="smallButton"
                                       value="<fmt:message key="oscarMDS.index.btnForward"/>"
                                       onClick="popupStart(300, 400, '<%= request.getContextPath() %>/oscarMDS/ViewSelectProviderAltView?doc_no=<carlos:encode value='${__enc_1}' context="javaScriptAttribute"/>&providerNo=<carlos:encode value='${__enc_2}' context="javaScriptAttribute"/>&searchProviderNo=<carlos:encode value='${__enc_3}' context="javaScriptAttribute"/>', 'providerselect')">
                                <input type="button" value=" <fmt:message key="global.btnPrint"/> "
                                       onClick="printPDF('<carlos:encode value='<%= segmentID %>' context="javaScriptAttribute"/>')">

                                <input type="button" value="Msg" onclick="handleLab('','<carlos:encode value='<%= segmentID %>' context="javaScriptAttribute"/>','msgLab');"/>
                                <input type="button" value="Tickler"
                                       onclick="handleLab('','<carlos:encode value='<%= segmentID %>' context="javaScriptAttribute"/>','ticklerLab');"/>

                                <% if (searchProviderNo != null) { // null if we were called from e-chart%>
                                                     <c:set var="__enc_4"><carlos:encode value='<%= segmentID %>' context="uriComponent"/></c:set>
                  <input type="button" value=" <fmt:message key="oscarMDS.segmentDisplay.btnEChart"/> "
                                       onClick="popupStart(360, 680, '${pageContext.request.contextPath}/oscarMDS/SearchPatient?labType=HL7&segmentID=<carlos:encode value='${__enc_4}' context="javaScriptAttribute"/>&name=<%=java.net.URLEncoder.encode(handler.getLastName()+", "+handler.getFirstName(), StandardCharsets.UTF_8)%>', 'searchPatientWindow')">
                                <% } %>
                                <input type="button" value="Req# <carlos:encode value='<%= reqTableID %>' context="htmlAttribute"/>" title="Link to Requisition"
                                       onclick="linkreq('<carlos:encode value='<%= segmentID %>' context="javaScriptAttribute"/>','<carlos:encode value='<%= reqID %>' context="javaScriptAttribute"/>');"/>

                                <% if (recall) {%>
                                <input type="button" value="Recall"
                                       onclick="handleLab('','<carlos:encode value='<%= segmentID %>' context="javaScriptAttribute"/>','msgLabRecall');">
                                <%}%>

                                <% if (!label.equals(null) && !label.equals("")) { %>
                                <button type="button" id="createLabel" value="Label"
                                        onClick="createLabLabel('labLabelForm<carlos:encode value='<%= segmentID %>' context="javaScriptAttribute"/>','acknowledgeForm_<carlos:encode value='<%= segmentID %>' context="javaScriptAttribute"/>','labelspan_<carlos:encode value='<%= segmentID %>' context="javaScriptAttribute"/>','label_<carlos:encode value='<%= segmentID %>' context="javaScriptAttribute"/>')">
                                    Label
                                </button>
                                <%} else { %>
                                <button type="button" id="createLabel" style="background-color:#6699FF" value="Label"
                                        onClick="createLabLabel('labLabelForm<carlos:encode value='<%= segmentID %>' context="javaScriptAttribute"/>','acknowledgeForm_<carlos:encode value='<%= segmentID %>' context="javaScriptAttribute"/>','labelspan_<carlos:encode value='<%= segmentID %>' context="javaScriptAttribute"/>','label_<carlos:encode value='<%= segmentID %>' context="javaScriptAttribute"/>')">
                                    Label
                                </button>
                                <%} %>


                                <input type="text" id="label_<carlos:encode value='<%= segmentID %>' context="htmlAttribute"/>" name="label" value=""/>
                                <% String labelval = "";
                                    if (label != "" && label != null) {
                                        labelval = label;
                                    } else {
                                        labelval = "(not set)";

                                    } %>
                                <span id="labelspan_<carlos:encode value='<%= segmentID %>' context="htmlAttribute"/>" class="Field2"><i>Label: <carlos:encode value='<%= labelval %>' context="html"/> </i></span>
                                <span class="Field2"><i>Next Appointment: <oscar:nextAppt
                                        demographicNo="<%=demographicID%>"/></i></span>
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
                                %><a href="javascript:void(0);"
                                     onclick="popup(850, 950, '${pageContext.request.contextPath}/lab/CA/ALL/ViewLabDisplay?segmentID=<%=SafeEncode.forJavaScriptAttribute(SafeEncode.forUriComponent(multiID[i].trim()))%>&multiID=<%=SafeEncode.forJavaScriptAttribute(SafeEncode.forUriComponent(multiLabId))%>&providerNo=<%= SafeEncode.forJavaScriptAttribute(SafeEncode.forUriComponent(providerNo)) %>&searchProviderNo=<%= SafeEncode.forJavaScriptAttribute(SafeEncode.forUriComponent(searchProviderNo)) %>', 'labVersion');">v<%= i + 1 %>
                                </a>&#160;<%
                                } else {
                                %><a href="javascript:void(0);"
                                     onclick="popup(850, 950, '${pageContext.request.contextPath}/lab/CA/ALL/ViewLabDisplay?segmentID=<%=SafeEncode.forJavaScriptAttribute(SafeEncode.forUriComponent(multiID[i].trim()))%>&multiID=<%=SafeEncode.forJavaScriptAttribute(SafeEncode.forUriComponent(multiLabId))%>&providerNo=<%= SafeEncode.forJavaScriptAttribute(SafeEncode.forUriComponent(providerNo)) %>', 'labVersion');">v<%= i + 1 %>
                                </a>&#160;<%
                                            }
                                        }
                                    }
                                    if (multiID.length > 1) {
                                        if (searchProviderNo != null) { // null if we were called from e-chart
                                %><a href="javascript:void(0);"
                                     onclick="popup(850, 950, '${pageContext.request.contextPath}/lab/CA/ALL/ViewLabDisplay?segmentID=<%=SafeEncode.forJavaScriptAttribute(SafeEncode.forUriComponent(segmentID))%>&multiID=<%=SafeEncode.forJavaScriptAttribute(SafeEncode.forUriComponent(multiLabId))%>&providerNo=<%= SafeEncode.forJavaScriptAttribute(SafeEncode.forUriComponent(providerNo)) %>&searchProviderNo=<%= SafeEncode.forJavaScriptAttribute(SafeEncode.forUriComponent(searchProviderNo)) %>&all=true', 'labVersion');">All</a>&#160;<%
                                } else {
                                %><a href="javascript:void(0);"
                                     onclick="popup(850, 950, '${pageContext.request.contextPath}/lab/CA/ALL/ViewLabDisplay?segmentID=<%=SafeEncode.forJavaScriptAttribute(SafeEncode.forUriComponent(segmentID))%>&multiID=<%=SafeEncode.forJavaScriptAttribute(SafeEncode.forUriComponent(multiLabId))%>&providerNo=<%= SafeEncode.forJavaScriptAttribute(SafeEncode.forUriComponent(providerNo)) %>&all=true', 'labVersion');">All</a>&#160;<%
                                        }
                                    }
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
                                    <fmt:message key="oscarMDS.segmentDisplay.formDetailResults"/>
                                </div>
                            </td>
                            <td width="33%" align="middle" class="Cell">
                                <div class="Field2">
                                    <fmt:message key="oscarMDS.segmentDisplay.formResultsInfo"/>
                                </div>
                            </td>
                        </tr>
                        <tr>
                            <td bgcolor="white" valign="top">
                                <table valign="top" border="0" cellpadding="2" cellspacing="0" width="100%">
                                    <tr valign="top">
                                        <td valign="top" width="33%" align="left">
                                            <table width="100%" border="0" cellpadding="2" cellspacing="0" valign="top">
                                                <tr>
                                                    <td valign="top" align="left">
                                                        <table width="100%" border="0" cellpadding="2" cellspacing="0"
                                                               valign="top"  <% if (demographicID.equals("") || demographicID.equals("0")) { %>
                                                               bgcolor="orange" <% } %> id="DemoTable<carlos:encode value='<%= segmentID %>' context="htmlAttribute"/>">
                                                            <tr>
                                                                <td nowrap>
                                                                    <div class="FieldData">
                                                                        <strong><fmt:message key="oscarMDS.segmentDisplay.formPatientName"/>: </strong>
                                                                    </div>
                                                                </td>
                                                                <td nowrap>
                                                                    <div class="FieldData" nowrap="nowrap">
                                                                        <% if (searchProviderNo == null) { // we were called from e-chart
                                                                        %>
                                                                        <a href="javascript:window.close()"><% } else { // we were called from lab module
                                                                        %></a>
                                                                        <c:set var="__enc_19"><carlos:encode value='<%= segmentID %>' context="uriComponent"/></c:set>
                                                                        <a href="javascript:popupStart(360, 680, '${pageContext.request.contextPath}/oscarMDS/SearchPatient?labType=HL7&segmentID=<carlos:encode value='${__enc_19}' context="javaScriptAttribute"/>&name=<%=java.net.URLEncoder.encode(handler.getLastName()+", "+handler.getFirstName(), StandardCharsets.UTF_8)%>', 'searchPatientWindow')">
                                                                            <% } %>
                                                                            <carlos:encode value='<%= handler.getPatientName() %>' context="html"/>
                                                                        </a>
                                                                    </div>
                                                                </td>
                                                                <td colspan="2"></td>
                                                            </tr>
                                                            <tr>
                                                                <td nowrap>
                                                                    <div class="FieldData">
                                                                        <strong><fmt:message key="oscarMDS.segmentDisplay.formDateBirth"/>: </strong>
                                                                    </div>
                                                                </td>
                                                                <td nowrap>
                                                                    <div class="FieldData" nowrap="nowrap">
                                                                        <carlos:encode value='<%= handler.getDOB() %>' context="html"/>
                                                                    </div>
                                                                </td>
                                                                <td colspan="2"></td>
                                                            </tr>
                                                            <tr>
                                                                <td nowrap>
                                                                    <div class="FieldData">
                                                                        <strong><fmt:message key="oscarMDS.segmentDisplay.formAge"/>: </strong>
                                                                    </div>
                                                                </td>
                                                                <td nowrap>
                                                                    <div class="FieldData">
                                                                        <carlos:encode value='<%= handler.getAge() %>' context="html"/>
                                                                    </div>
                                                                </td>
                                                                <td nowrap>
                                                                    <div class="FieldData">
                                                                        <strong><fmt:message key="oscarMDS.segmentDisplay.formSex"/>: </strong>
                                                                    </div>
                                                                </td>
                                                                <td align="left" nowrap>
                                                                    <div class="FieldData">
                                                                        <carlos:encode value='<%= handler.getSex() %>' context="html"/>
                                                                    </div>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td nowrap>
                                                                    <div class="FieldData">
                                                                        <strong>
                                                                            <fmt:message key="oscarMDS.segmentDisplay.formHealthNumber"/>
                                                                        </strong>
                                                                    </div>
                                                                </td>
                                                                <td nowrap>
                                                                    <div class="FieldData" nowrap="nowrap">
                                                                        <carlos:encode value='<%= handler.getHealthNum() %>' context="html"/>
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
                                                                        <strong><fmt:message key="oscarMDS.segmentDisplay.formHomePhone"/>: </strong>
                                                                    </div>
                                                                </td>
                                                                <td nowrap>
                                                                    <div align="left" class="FieldData" nowrap="nowrap">
                                                                        <carlos:encode value='<%= handler.getHomePhone() %>' context="html"/>
                                                                    </div>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td nowrap>
                                                                    <div align="left" class="FieldData">
                                                                        <strong><fmt:message key="oscarMDS.segmentDisplay.formWorkPhone"/>: </strong>
                                                                    </div>
                                                                </td>
                                                                <td nowrap>
                                                                    <div align="left" class="FieldData" nowrap="nowrap">
                                                                        <carlos:encode value='<%= handler.getWorkPhone() %>' context="html"/>
                                                                    </div>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td nowrap>
                                                                    <div align="left" class="FieldData" nowrap="nowrap">
                                                                    </div>
                                                                </td>
                                                                <td nowrap>
                                                                    <div align="left" class="FieldData" nowrap="nowrap">
                                                                    </div>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td nowrap>
                                                                    <div align="left" class="FieldData">
                                                                        <strong><fmt:message key="oscarMDS.segmentDisplay.formPatientLocation"/>: </strong>
                                                                    </div>
                                                                </td>
                                                                <td nowrap>
                                                                    <div align="left" class="FieldData" nowrap="nowrap">
                                                                        <carlos:encode value='<%= handler.getPatientLocation() %>' context="html"/>
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
                                                <strong><fmt:message key="oscarMDS.segmentDisplay.formDateService"/>:</strong>
                                            </div>
                                        </td>
                                        <td>
                                            <div class="FieldData" nowrap="nowrap">
                                                <carlos:encode value='<%= handler.getServiceDate() %>' context="html"/>
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
                                                        <carlos:encode value='<%= ((ExcellerisOntarioHandler) handler).getReportStatusChangeDate() %>' context="html"/>
                                                    </div>
                                                </td>
                                            </tr>
                                        <% } %>
                                    <tr>
                                        <td>
                                            <div class="FieldData">
                                                <strong><fmt:message key="oscarMDS.segmentDisplay.formReportStatus"/>:</strong>
                                            </div>
                                        </td>
                                        <td>
                                            <div class="FieldData" nowrap="nowrap">
                                                <%= (handler.getOrderStatus().equals("F") ? "Final" : handler.getOrderStatus().equals("C") ? "Corrected" : (handler.getMsgType().equals("PATHL7") && handler.getOrderStatus().equals("P")) ? "Preliminary" : handler.getOrderStatus().equals("X") ? "DELETED" : SafeEncode.forHtml(handler.getOrderStatus())) %>
                                            </div>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td></td>
                                    </tr>
                                    <tr>
                                        <td nowrap>
                                            <div class="FieldData">
                                                <strong><fmt:message key="oscarMDS.segmentDisplay.formClientRefer"/>:</strong>
                                            </div>
                                        </td>
                                        <td nowrap>
                                            <div class="FieldData" nowrap="nowrap">
                                                <carlos:encode value='<%= handler.getClientRef() %>' context="html"/>
                                            </div>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td>
                                            <div class="FieldData">
                                                <strong><fmt:message key="oscarMDS.segmentDisplay.formAccession"/>:</strong>
                                            </div>
                                        </td>
                                        <td>
                                            <div class="FieldData" nowrap="nowrap">
                                                <carlos:encode value='<%= handler.getAccessionNum() %>' context="html"/>
                                            </div>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td bgcolor="white" colspan="2">
                                <table width="100%" border="0" cellpadding="0" cellspacing="0" bordercolor="#CCCCCC">
                                    <tr>
                                        <td bgcolor="white">
                                            <div class="FieldData">
                                                <strong><fmt:message key="oscarMDS.segmentDisplay.formRequestingClient"/>: </strong>
                                                <carlos:encode value='<%= handler.getDocName() %>' context="html"/>
                                            </div>
                                        </td>

                                        <td bgcolor="white" align="right">
                                            <div class="FieldData">
                                                <strong><fmt:message key="oscarMDS.segmentDisplay.formCCClient"/>: </strong>
                                                <carlos:encode value='<%= handler.getCCDocs() %>' context="html"/>

                                            </div>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td align="center" bgcolor="white" colspan="2">
                                <% if (demographicID != null && !demographicID.equals("")) {

                                    TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);
                                    List<Tickler> LabTicklers = ticklerManager.getTicklerByLabId(loggedInInfo, Integer.valueOf(segmentID), Integer.valueOf(demographicID));

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
                                                    <td><b>Priority:</b> <%=flag%> <carlos:encode value='<%= tickler.getPriority().toString() %>' context="html"/>
                                                    </td>
                                                    <td><b>Service Date:</b> <carlos:encode value='<%= String.valueOf(tickler.getServiceDate()) %>' context="html"/>
                                                    </td>
                                                    <td><b>Assigned
                                                        To:</b> <%=tickler.getAssignee() != null ? SafeEncode.forHtml(tickler.getAssignee().getLastName() + ", " + tickler.getAssignee().getFirstName()) : "N/A"%>
                                                    </td>
                                                    <td width="90px">
                                                        <b>Status:</b> <%=ticklerStatus.equals("C") ? "Completed" : "Active" %>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td colspan="4"><carlos:encode value='<%= tickler.getMessage() %>' context="html"/>
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
                                %>

                                <%
                                    String[] multiID = multiLabId.split(",");
                                    ReportStatus report;
                                    boolean startFlag = false;
                                    for (int j = multiID.length - 1; j >= 0; j--) {
                                        ackList = AcknowledgementData.getAcknowledgements(multiID[j]);
                                        if (multiID[j].equals(segmentID))
                                            startFlag = true;
                                        if (startFlag)
                                            if (ackList.size() > 0) {
                                                {
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
                                                    report = (ReportStatus) ackList.get(i); %>
                                                <carlos:encode value='<%= report.getProviderName() %>' context="html"/> :

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

                                                <carlos:encode value='<%= String.valueOf(report.getTimestamp()) %>' context="html"/>,
                                                <% String commentTitle = null;
                                                    if (report.getComment() == null || report.getComment().equals("")) {
                                                        commentTitle = "no comment";
                                                    } else {
                                                        commentTitle = "comment: ";
                                                    }
                                                %>
                                                <span id="<%="V" + j + "commentLabel" + SafeEncode.forHtmlAttribute(segmentID) + SafeEncode.forHtmlAttribute(report.getProviderNo())%>"><%=commentTitle%></span><span
                                                    id="<%="V" + j + "commentText" + SafeEncode.forHtmlAttribute(segmentID) + SafeEncode.forHtmlAttribute(report.getProviderNo())%>"> <%=report.getComment() == null ? "" : SafeEncode.forHtml(report.getComment())%></span>

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
                                                }
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
                }
        %>
        <table style="page-break-inside:avoid;" bgcolor="#003399" border="0" cellpadding="0" cellspacing="0"
               width="100%">
            <tr>
                <td colspan="4" height="7">&nbsp;</td>
            </tr>
            <tr>
                <td bgcolor="#FFCC00" width="300" valign="bottom">
                    <div class="Title2">
                        <carlos:encode value='<%= headers.get(i) %>' context="html"/>
                    </div>
                </td>
                <%--<td align="right" bgcolor="#FFCC00" width="100">&nbsp;</td>--%>
                <td width="9">&nbsp;</td>
                <td width="9">&nbsp;</td>
                <td width="*">&nbsp;</td>
            </tr>
        </table>
        <%if (isUnstructuredDoc) {%>
        <table width="100%" border="0" cellspacing="0" cellpadding="2" bgcolor="#CCCCFF" bordercolor="#9966FF"
               bordercolordark="#bfcbe3" name="tblDiscs" id="tblDiscs">
            <tr class="Field2">
                <td width="20%" align="middle" valign="bottom" class="Cell"><fmt:message key="oscarMDS.segmentDisplay.formTestName"/></td>
                <td width="60%" align="middle" valign="bottom" class="Cell"><fmt:message key="oscarMDS.segmentDisplay.formResult"/></td>
                <td width="20%" align="middle" valign="bottom" class="Cell"><fmt:message key="oscarMDS.segmentDisplay.formDateTimeCompleted"/></td>
            </tr>
            <%
            } else {%>
            <table width="100%" border="0" cellspacing="0" cellpadding="2" bgcolor="#CCCCFF" bordercolor="#9966FF"
                   bordercolordark="#bfcbe3" name="tblDiscs" id="tblDiscs">
                <tr class="Field2">
                    <td width="25%" align="middle" valign="bottom" class="Cell"><fmt:message key="oscarMDS.segmentDisplay.formTestName"/></td>
                    <td width="15%" align="middle" valign="bottom" class="Cell"><fmt:message key="oscarMDS.segmentDisplay.formResult"/></td>
                    <td width="5%" align="middle" valign="bottom" class="Cell"><fmt:message key="oscarMDS.segmentDisplay.formAbn"/></td>
                    <td width="15%" align="middle" valign="bottom" class="Cell"><fmt:message key="oscarMDS.segmentDisplay.formReferenceRange"/></td>
                    <td width="10%" align="middle" valign="bottom" class="Cell"><fmt:message key="oscarMDS.segmentDisplay.formUnits"/></td>
                    <td width="15%" align="middle" valign="bottom" class="Cell"><fmt:message key="oscarMDS.segmentDisplay.formDateTimeCompleted"/></td>
                    <td width="6%" align="middle" valign="bottom" class="Cell"><fmt:message key="oscarMDS.segmentDisplay.formNew"/></td>
                </tr>

                <%
                    }

                    for (j = 0; j < OBRCount; j++) {

                        boolean obrFlag = false;
                        int obxCount = handler.getOBXCount(j);

                               if (handler.getMsgType().equals("ExcellerisON") && handler.getObservationHeader(j, 0).equals(headers.get(i))) {
                                    String orderRequestStatus = ((ExcellerisOntarioHandler) handler).getOrderStatus(j);
                                    int obrCommentCount = handler.getOBRCommentCount(j);
                                    if (orderRequestStatus.equals(ExcellerisOntarioHandler.OrderStatus.DELETED.getDescription())) { continue; }

                                    if (obxCount > 0 || !orderRequestStatus.isEmpty() || obrCommentCount > 0) {
                                        obrFlag = true;
                                    %>
                                    <tr style="<%=(linenum % 2 == 1 ? "background-color:"+highlight : "")%>" >
                                        <td style="text-align:left; vertical-align:top"><span style="font-size:16px;font-weight: bold;"><carlos:encode value='<%= handler.getOBRName(j) %>' context="html"/></span></td>
                                        <td colspan="1"><carlos:encode value='<%= orderRequestStatus %>' context="html"/></td>
                                    </tr>
                                    <%
                                    }
                               }


                        for (k = 0; k < obxCount; k++) {
                            String obxName = handler.getOBXName(j, k);
                            boolean isAllowedDuplicate = false;
                            if (handler.getMsgType().equals("PATHL7")) {
                                //if the obxidentifier and result name are any of the following, they must be displayed (they are the Excepetion to Excelleris TX/FT duplicate result name display rules)
                                if ((handler.getOBXName(j, k).equals("Culture") && handler.getOBXIdentifier(j, k).equals("6463-4")) ||
                                        (handler.getOBXName(j, k).equals("Organism") && (handler.getOBXIdentifier(j, k).equals("X433") || handler.getOBXIdentifier(j, k).equals("X30011")))) {
                                    isAllowedDuplicate = true;
                                }
                            }
                            boolean b2 = !obxName.equals(""), b3 = handler.getObservationHeader(j, k).equals(headers.get(i));
                            if (handler.getMsgType().equals("EPSILON")) {
                                b2 = true;
                                b3 = true;
                            } else if (handler.getMsgType().equals("HHSEMR")) {
                                b2 = true;
                            }


                            if (!handler.getOBXResultStatus(j, k).equals("DNS") && b2 && b3) { // <<--  DNS only needed for MDS messages
                                String obrName = handler.getOBRName(j);
                                        if(!obrFlag && !obrName.equals("") && !(obxName.contains(obrName) && obxCount < 2) && !handler.getMsgType().equals("ExcellerisON")){%>
                <%--  <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>" >
                     <td valign="top" align="left"><%=obrName%></td>
                     <td colspan="6">&nbsp;</td>
                 </tr> --%>
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

                    boolean isEmbeddedDocumentResult = (handler.getMsgType().equals("ExcellerisON") || handler.getMsgType().equals("PATHL7")) && handler.getOBXValueType(j, k).equals("ED");
                    String embeddedDocumentLegacy = "";
                    if (isEmbeddedDocumentResult && handler.getMsgType().equals("PATHL7") && ((PATHL7Handler) handler).isLegacy(j, k)) {
                        embeddedDocumentLegacy = "&legacy=true";
                    }
                    String embeddedDocumentHref = request.getContextPath() + "/lab/DownloadEmbeddedDocumentFromLab?labNo="
                            + URLEncoder.encode(segmentID == null ? "" : segmentID, StandardCharsets.UTF_8)
                            + "&segment=" + j
                            + "&group=" + k
                            + embeddedDocumentLegacy;
                    String labValuesHref = "javascript:popupStart('660','900','" + request.getContextPath()
                            + "/lab/CA/ON/ViewLabValues?testName=" + URLEncoder.encode(obxName, StandardCharsets.UTF_8)
                            + "&demo=" + (demographicID != null ? URLEncoder.encode(demographicID, StandardCharsets.UTF_8) : "")
                            + "&labType=HL7&identifier=" + URLEncoder.encode(handler.getOBXIdentifier(j, k), StandardCharsets.UTF_8)
                            + "')";
                    String observationHref = isEmbeddedDocumentResult ? embeddedDocumentHref : labValuesHref;
                %>
                <%
                    if (handler.getMsgType().equals("EPSILON")) {
                        if (handler.getOBXIdentifier(j, k).equals(headers.get(i)) && !obxName.equals("")) {
                %>

                <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>" class="<%=lineClass%>">
                    <td valign="top" align="left"><%= obrFlag ? "&nbsp; &nbsp; &nbsp;" : "&nbsp;" %><a
                            href="javascript:popupStart('660','900','${pageContext.request.contextPath}/lab/CA/ON/ViewLabValues?testName=<%=URLEncoder.encode(obxName, StandardCharsets.UTF_8)%>&demo=<carlos:encode value='<%= demographicID %>' context="javaScript"/>&labType=HL7&identifier=<%=URLEncoder.encode(handler.getOBXIdentifier(j, k), StandardCharsets.UTF_8)%>')"><carlos:encode value='<%= obxName %>' context="html"/>
                    </a></td>
                    <td align="right"><carlos:encode value='<%= handler.getOBXResult(j, k) %>' context="html"/>
                    </td>

                    <td align="center">
                        <carlos:encode value='<%= handler.getOBXAbnormalFlag(j, k) %>' context="html"/>
                    </td>
                    <td align="left"><carlos:encode value='<%= handler.getOBXReferenceRange(j, k) %>' context="html"/>
                    </td>
                    <td align="left"><carlos:encode value='<%= handler.getOBXUnits(j, k) %>' context="html"/>
                    </td>
                    <td align="center"><carlos:encode value='<%= handler.getTimeStamp(j, k) %>' context="html"/>
                    </td>
                    <td align="center"><carlos:encode value='<%= handler.getOBXResultStatus(j, k) %>' context="html"/>
                    </td>
                </tr>
                <% } else if (handler.getOBXIdentifier(j, k).equals(headers.get(i)) && obxName.equals("")) { %>
                <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>" class="NormalRes">
                    <td valign="top" align="left" colspan="8">
                        <pre style="margin:0px 0px 0px 100px;"><carlos:encode value='<%= handler.getOBXResult(j, k) %>' context="html"/></pre>
                    </td>
                </tr>
                <% }
                } else if (handler.getMsgType().equals("HHSEMR")) {
                    if (!obxName.equals("")) { %>
                <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>" class="<%=lineClass%>">
                    <td valign="top" align="left"><%= obrFlag ? "&nbsp; &nbsp; &nbsp;" : "&nbsp;" %><a
                            href="javascript:popupStart('660','900','${pageContext.request.contextPath}/lab/CA/ON/ViewLabValues?testName=<%=URLEncoder.encode(obxName, StandardCharsets.UTF_8)%>&demo=<carlos:encode value='<%= demographicID %>' context="javaScript"/>&labType=HL7&identifier=<%=URLEncoder.encode(handler.getOBXIdentifier(j, k), StandardCharsets.UTF_8)%>')"><carlos:encode value='<%= obxName %>' context="html"/>
                    </a></td>
                    <td align="right"><carlos:encode value='<%= handler.getOBXResult(j, k) %>' context="html"/>
                    </td>

                    <td align="center">
                        <carlos:encode value='<%= handler.getOBXAbnormalFlag(j, k) %>' context="html"/>
                    </td>
                    <td align="left"><carlos:encode value='<%= handler.getOBXReferenceRange(j, k) %>' context="html"/>
                    </td>
                    <td align="left"><carlos:encode value='<%= handler.getOBXUnits(j, k) %>' context="html"/>
                    </td>
                    <td align="center"><carlos:encode value='<%= handler.getTimeStamp(j, k) %>' context="html"/>
                    </td>
                    <td align="center"><carlos:encode value='<%= handler.getOBXResultStatus(j, k) %>' context="html"/>
                    </td>
                </tr>

                <%} else { %>
                <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>" class="NormalRes">
                    <td valign="top" align="left" colspan="8">
                        <pre style="margin:0px 0px 0px 100px;"><carlos:encode value='<%= handler.getOBXResult(j, k) %>' context="html"/></pre>
                    </td>
                </tr>
                <%
                    }
                    if (!handler.getNteForOBX(j, k).equals("") && handler.getNteForOBX(j, k) != null) {
                %>
                <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>" class="NormalRes">
                    <td valign="top" align="left" colspan="8">
                        <pre style="margin:0px 0px 0px 100px;"><carlos:encode value='<%= handler.getNteForOBX(j, k) %>' context="html"/></pre>
                    </td>
                </tr>
                <% }
                    for (l = 0; l < handler.getOBXCommentCount(j, k); l++) {%>
                <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>" class="NormalRes">
                    <td valign="top" align="left" colspan="8">
                        <pre style="margin:0px 0px 0px 100px;"><carlos:encode value='<%= handler.getOBXComment(j, k, l) %>' context="html"/></pre>
                    </td>
                </tr>
                <%
                    }


                } else if (!handler.getMsgType().equals("EPSILON")) {
                %>
                <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>" class="<%=lineClass%>"><%
                    if (isUnstructuredDoc) {
                        if (handler.getOBXIdentifier(j, k).equalsIgnoreCase(handler.getOBXIdentifier(j, k - 1)) && (obxCount > 1)) {%>
                    <td valign="top" align="left"><%= obrFlag ? "&nbsp; &nbsp; &nbsp;" : "&nbsp;" %><a
                            href="javascript:popupStart('660','900','${pageContext.request.contextPath}/lab/CA/ON/ViewLabValues?testName=<%=URLEncoder.encode(obxName, StandardCharsets.UTF_8)%>&demo=<carlos:encode value='<%= demographicID %>' context="javaScript"/>&labType=HL7&identifier=<%=URLEncoder.encode(handler.getOBXIdentifier(j, k), StandardCharsets.UTF_8)%>')"></a><%
	                                   				}
	                                   			else{%>
                    <td valign="top" align="left"><%= obrFlag ? "&nbsp; &nbsp; &nbsp;" : "&nbsp;" %><a
                            href="javascript:popupStart('660','900','${pageContext.request.contextPath}/lab/CA/ON/ViewLabValues?testName=<%=URLEncoder.encode(obxName, StandardCharsets.UTF_8)%>&demo=<carlos:encode value='<%= demographicID %>' context="javaScript"/>&labType=HL7&identifier=<%=URLEncoder.encode(handler.getOBXIdentifier(j, k), StandardCharsets.UTF_8)%>')"><carlos:encode value='<%= obxName %>' context="html"/>
                    </a><%}%>
                            <%if(isVIHARtf){
												    //create bytes from the rtf string
											    	byte[] rtfBytes = handler.getOBXResult(j, k).getBytes();
											    	ByteArrayInputStream rtfStream = new ByteArrayInputStream(rtfBytes);

											    	//Use RTFEditor Kit to get plaintext from RTF
											    	RTFEditorKit rtfParser = new RTFEditorKit();
											    	javax.swing.text.Document doc = rtfParser.createDefaultDocument();
											    	rtfParser.read(rtfStream, doc, 0);
											    	// IMPORTANT: HTML-encode FIRST (XSS prevention), then convert newlines to <br>.
											    	// Reversing this order would encode the <br> tags themselves.
											    	String rtfText = SafeEncode.forHtml(doc.getText(0, doc.getLength())).replaceAll("\n", "<br>");
											    	String disclaimer = "<br>IMPORTANT DISCLAIMER: You are viewing a PREVIEW of the original report. The rich text formatting contained in the original report may convey critical information that must be considered for clinical decision making. Please refer to the ORIGINAL report, by clicking 'Print', prior to making any decision on diagnosis or treatment.";%>
                    <td align="left"><%= rtfText + disclaimer %>
                    </td>
                    <%} %><%
                        else{%>
                    <td align="left"><carlos:encode value='<%= handler.getOBXResult(j, k) %>' context="html"/>
                    </td>
                    <%} %>
                    <%
                        if (handler.getTimeStamp(j, k).equals(handler.getTimeStamp(j, k - 1)) && (obxCount > 1)) {
                    %>
                    <td align="center"></td>
                    <%} else {%>
                    <td align="center"><carlos:encode value='<%= handler.getTimeStamp(j, k) %>' context="html"/>
                    </td>
                    <%
                        }
                    }//end of isUnstructuredDoc

                    else {//if it isn't a PATHL7 doc
                        //if there are duplicate FT/TX obxNames, only display the first (only if handler is PATHL7)
                        if (handler.getMsgType().equals("PATHL7") && !isAllowedDuplicate && (obxCount > 1) && handler.getOBXIdentifier(j, k).equalsIgnoreCase(handler.getOBXIdentifier(j, k - 1)) && (handler.getOBXValueType(j, k).equals("TX") || handler.getOBXValueType(j, k).equals("FT"))) {
                    %>
                    <td valign="top" align="left"><%= obrFlag ? "&nbsp; &nbsp; &nbsp;" : "&nbsp;" %><a
                            href="javascript:popupStart('660','900','${pageContext.request.contextPath}/lab/CA/ON/ViewLabValues?testName=<%=URLEncoder.encode(obxName, StandardCharsets.UTF_8)%>&demo=<carlos:encode value='<%= demographicID %>' context="javaScript"/>&labType=HL7&identifier=<%=URLEncoder.encode(handler.getOBXIdentifier(j, k), StandardCharsets.UTF_8)%>')"></a><%
	                                   				}
	                               				else{%>
                    <td valign="top" align="left"><%= obrFlag ? "&nbsp; &nbsp; &nbsp;" : "&nbsp;" %><a
                            href="<%= SafeEncode.forHtmlAttribute(observationHref) %>"><carlos:encode value='<%= obxName %>' context="html"/>
                    </a></td>
                    <%}%>
                    <%
                        //for pathl7, if it is an SG/CDC result greater than 100 characters, left justify it
                        if ((handler.getOBXResult(j, k) != null && handler.getOBXResult(j, k).length() > 100) && isSGorCDC) {%>
                    <td align="left"><carlos:encode value='<%= handler.getOBXResult(j, k) %>' context="html"/>
                    </td>
                    <%
                    } else {%>
                    <%
                        if (isEmbeddedDocumentResult) {
                    %>
                    <td align="right"><a
                            href="<%= SafeEncode.forHtmlAttribute(embeddedDocumentHref) %>">PDF
                        Report</a></td>
                    <%
                    } else {
                    %>
	                                            <td align="right">
                                                   <% if (handler.getMsgType().equals("ExcellerisON") && !((ExcellerisOntarioHandler) handler).getOBXSubId(j, k).isEmpty()) { %>
                                                    <em><carlos:encode value='<%= ((ExcellerisOntarioHandler) handler).getOBXSubIdWithObservationValue( j, k) %>' context="html"/></em>
                                                    <% } else { %>
                                                    <carlos:encode value='<%= handler.getOBXResult( j, k) %>' context="html"/>
                                                    <% } %>
                                                </td><%}%>
                    <% } %>
                    <td align="center">
                        <carlos:encode value='<%= handler.getOBXAbnormalFlag(j, k) %>' context="html"/>
                    </td>
                    <td align="left"><carlos:encode value='<%= handler.getOBXReferenceRange(j, k) %>' context="html"/>
                    </td>
                    <td align="left"><carlos:encode value='<%= handler.getOBXUnits(j, k) %>' context="html"/>
                    </td>
                    <td align="center"><carlos:encode value='<%= handler.getTimeStamp(j, k) %>' context="html"/>
                    </td>
                    <td align="center"><carlos:encode value='<%= handler.getOBXResultStatus(j, k) %>' context="html"/>
                    </td>
                    <%
                        }//end of PATHL7 else %>
                </tr>

                <%for (l = 0; l < handler.getOBXCommentCount(j, k); l++) {%>
                <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>" class="NormalRes">
                    <td valign="top" align="left" colspan="8">
                        <pre style="margin:0px 0px 0px 100px;"><carlos:encode value='<%= handler.getOBXComment(j, k, l) %>' context="html"/></pre>
                    </td>
                </tr>
                <%
                    }
                } else {
                %>
                <%for (l = 0; l < handler.getOBXCommentCount(j, k); l++) {%>
                <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>">
                    <td valign="top" align="left" colspan="8">
                        <pre style="margin:0px 0px 0px 100px;"><carlos:encode value='<%= handler.getOBXComment(j, k, l) %>' context="html"/></pre>
                    </td>
                </tr>
                <%
                                }
                            }
                        }
                    }
                    //}

                    //for ( j=0; j< OBRCount; j++){
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
                    <td valign="top" align="left"><carlos:encode value='<%= handler.getOBRName(j) %>' context="html"/>
                    </td>
                    <td colspan="6">&nbsp;</td>
                </tr>
                <%
                        obrFlag = true;
                    }
                %>
                <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>" class="NormalRes">
                    <td valign="top" align="left" colspan="8">
                        <pre style="margin:0px 0px 0px 100px;"><carlos:encode value='<%= handler.getOBRComment(j, k) %>' context="html"/></pre>
                    </td>
                </tr>
                <% if (!handler.getMsgType().equals("HHSEMR")) {
                    if (handler.getOBXName(j, k).equals("")) {
                        String result = handler.getOBXResult(j, k);%>
                <tr bgcolor="<%=(linenum % 2 == 1 ? highlight : "")%>">
                    <td colspan="7" valign="top" align="left"><carlos:encode value='<%= result %>' context="html"/>
                    </td>
                </tr>
                <%
                                            }
                                        }


                                    }
                                }
                            }
                %>
            </table>
            <% // end for headers
            }  // for i=0... (headers)
            %>

            <table width="100%" border="0" cellspacing="0" cellpadding="3" class="MainTableBottomRowRightColumn"
                                                  <c:set var="__enc_20"><carlos:encode value='<%= segmentID %>' context="uriComponent"/></c:set>
                               <c:set var="__enc_21"><carlos:encode value='<%= providerNo %>' context="uriComponent"/></c:set>
                               <c:set var="__enc_22"><carlos:encode value='<%= searchProviderNo %>' context="uriComponent"/></c:set>
bgcolor="#003399">
                <tr>
                    <td align="left" width="50%">
                        <% if (!ackFlag) { %>
                        <input type="button" value="<fmt:message key="oscarMDS.segmentDisplay.btnAcknowledge"/>"
                               onclick="<carlos:encode value='<%= ackLabFunc %>' context="htmlAttribute"/>">
                        <input type="button" value="<fmt:message key="oscarMDS.segmentDisplay.btnComment"/>"
                               onclick="getComment('<carlos:encode value='<%= segmentID %>' context="javaScriptAttribute"/>','addComment')">
                        <% } %>
                        <input type="button" class="smallButton" value="<fmt:message key="oscarMDS.index.btnForward"/>"
                               onClick="popupStart(300, 400, '${pageContext.request.contextPath}/oscarMDS/ViewSelectProviderAltView?doc_no=<carlos:encode value='${__enc_20}' context="javaScriptAttribute"/>&providerNo=<carlos:encode value='${__enc_21}' context="javaScriptAttribute"/>&searchProviderNo=<carlos:encode value='${__enc_22}' context="javaScriptAttribute"/>', 'providerselect')">

                        <input type="button" value=" <fmt:message key="global.btnPrint"/> "
                               onClick="printPDF('<carlos:encode value='<%= segmentID %>' context="javaScriptAttribute"/>')">
                        <% if (searchProviderNo != null) { // we were called from e-chart %>
                        <input type="button" value=" <fmt:message key="oscarMDS.segmentDisplay.btnEChart"/> "
                               onClick="popupStart(360, 680, '${pageContext.request.contextPath}/oscarMDS/SearchPatient?labType=HL7&segmentID=<carlos:encode value='<%= segmentID %>' context="javaScriptAttribute"/>&name=<%=java.net.URLEncoder.encode(handler.getLastName()+", "+handler.getFirstName(), StandardCharsets.UTF_8)%>', 'searchPatientWindow')">

                        <% } %>
                    </td>
                    <td width="50%" valign="center" align="left">
                        <span class="Field2"><i><fmt:message key="oscarMDS.segmentDisplay.msgReportEnd"/></i></span>
                    </td>
                </tr>
            </table>
            </td>
            </tr>
            <tr>
                <td colspan="1"><a style="color:white;" href="javascript:void(0);"
                                   onclick="showHideItem('rawhl7_<carlos:encode value='<%= segmentID %>' context="javaScriptAttribute"/>');">show/hide</a>
                    <pre id="rawhl7_<carlos:encode value='<%= segmentID %>' context="htmlAttribute"/>" style="display:none;"><carlos:encode value='<%= hl7 %>' context="html"/></pre>
                </td>
            </tr>
            <tr>
                <td colspan="1">
                    <hr width="100%" color="red">
                </td>
            </tr>
        </table>
    </form>

</div>
