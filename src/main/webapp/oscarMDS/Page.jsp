<%--

    Copyright (c) 2008-2012 Indivica Inc.

    This software is made available under the terms of the
    GNU General Public License, Version 2, 1991 (GPLv2).
    License details are available via "indivica.ca/gplv2"
    and "gnu.org/licenses/gpl-2.0.html".

--%>
<%@ page import="java.util.*" %>
<%@ page import="ca.openosp.openo.lab.ca.on.*" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@page import="ca.openosp.openo.utility.MiscUtils,org.apache.commons.text.StringEscapeUtils" %>
<%@page import="org.apache.logging.log4j.Logger,ca.openosp.openo.commn.dao.OscarLogDao,ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.commn.dao.SystemPreferencesDao" %>
<%@ page import="ca.openosp.openo.commn.model.SystemPreferences" %>
<%@ page import="ca.openosp.openo.lab.ca.on.LabResultData" %>

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
    Logger logger = MiscUtils.getLogger();

    String view = (String) request.getAttribute("view");
    Integer pageSize = (Integer) request.getAttribute("pageSize");
    Integer pageNum = (Integer) request.getAttribute("pageNum");
    Integer pageCount = (Integer) request.getAttribute("pageCount");
    Map<String, String> docType = (Map<String, String>) request.getAttribute("docType");
    Map<String, List<String>> patientDocs = (Map<String, List<String>>) request.getAttribute("patientDocs");
    String providerNo = (String) request.getAttribute("providerNo");
    String searchProviderNo = (String) request.getAttribute("searchProviderNo");
    Map<String, String> patientIdNames = (Map<String, String>) request.getAttribute("patientIdNames");
    String patientIdNamesStr = (String) request.getAttribute("patientIdNamesStr");
    Map<String, String> docStatus = (Map<String, String>) request.getAttribute("docStatus");
    String patientIdStr = (String) request.getAttribute("patientIdStr");
    Map<String, List<String>> typeDocLab = (Map<String, List<String>>) request.getAttribute("typeDocLab");
    String demographicNo = (String) request.getAttribute("demographicNo");
    String ackStatus = (String) request.getAttribute("ackStatus");
    List labdocs = (List) request.getAttribute("labdocs");
    Map<String, Integer> patientNumDoc = (Map<String, Integer>) request.getAttribute("patientNumDoc");
    Integer totalDocs = (Integer) request.getAttribute("totalDocs");
    Integer totalHL7 = (Integer) request.getAttribute("totalHL7");
    List<String> normals = (List<String>) request.getAttribute("normals");
    List<String> abnormals = (List<String>) request.getAttribute("abnormals");
    Integer totalNumDocs = (Integer) request.getAttribute("totalNumDocs");
    String selectedCategory = request.getParameter("selectedCategory");
    String selectedCategoryPatient = request.getParameter("selectedCategoryPatient");
    String selectedCategoryType = request.getParameter("selectedCategoryType");
    boolean isListView = Boolean.valueOf(request.getParameter("isListView"));

    OscarLogDao oscarLogDao = (OscarLogDao) SpringUtils.getBean(OscarLogDao.class);
    String curUser_no = (String) session.getAttribute("user");

%>

<% if (isListView && pageNum == 0) { %>
<script type="text/javascript">
    function submitLabel(lblval) {
        var tdisForm = document.forms['TDISLabelForm'];
        var ackForm = document.forms['acknowledgeForm'];
        if (tdisForm && tdisForm.label && ackForm && ackForm.label) {
            tdisForm.label.value = ackForm.label.value;
        }
    }
</script>

<table id="scrollNumber1">
    <tr>
        <td class="MainTableTopRowRightColumn" colspan="10" align="left">
            <table width="100%">
                <tr>
                    <td align="left" valign="center">
                        <c:if test="${ labdocs.size() gt 0}">
                            <input id="topFBtn" type="button" class="smallButton"
                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.index.btnForward"/>"
                                   onClick="submitForward('${ searchProviderNo }', '${ param.status }')">
                            <c:if test="${ ackStatus eq 'N' or empty ackStatus }">
                                <input id="topFileBtn" type="button" class="smallButton" value="File"
                                       onclick="submitFile('${ searchProviderNo }', '${ param.status }')"/>
                            </c:if>
                        </c:if>
                        <input type="hidden" id="currentNumberOfPages" value="0"/>
                    </td>
                </tr>
            </table>
        </td>
    </tr>
    <tr>
        <td>
            <div id="listViewDocs" style="height:550px; overflow:scroll;" onscroll="handleScroll(this)">
                <%
                    SystemPreferencesDao systemPreferencesDao = SpringUtils.getBean(SystemPreferencesDao.class);
                    SystemPreferences systemPreferences = systemPreferencesDao.findPreferenceByName(SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS.inboxDateSearchType);
                    String dateType = "serviceObservation";

                    if (systemPreferences != null && systemPreferences.getValue() != null) {
                        dateType = systemPreferences.getValue();
                    }
                %>
                <table id="summaryView" class="tablesorter">
                    <thead>
                    <tr>
                        <th>
                            <input type="checkbox" onclick="checkAllLabs('lab_form');" name="checkA"/>
                            <%--                                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.index.msgHealthNumber"/>--%>
                        </th>
                        <th>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.index.msgPatientName"/>
                        </th>
                        <th>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.index.msgSex"/>
                        </th>

                        <th>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.index.msgResultStatus"/>
                        </th>
                        <th>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.index.msgLabel"/>
                        </th>
                        <th>
                            <% if (dateType.equals("receivedCreated")) { %>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.index.msgDateCreated"/>
                            <% } else { %>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.index.msgDateTest"/>
                            <% } %>
                        </th>
                        <%--                            <th>--%>
                        <%--                                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.index.msgOrderPriority"/>--%>
                        <%--                            </th>--%>
                        <th>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.index.msgRequestingClient"/>
                        </th>
                        <th>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.index.msgDiscipline"/>
                        </th>
                        <th>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.index.msgReportStatus"/>
                        </th>
                        <th>
                            Ack #
                        </th>
                    </tr>
                    </thead>
                    <tbody id="summaryBody">
                    <%
                        } // End if(pageNum == 1)
                        List<String> doclabid_seq = new ArrayList<String>();
                        Integer number_of_rows_per_page = pageSize;
                        Integer totalNoPages = pageCount;
                        Integer total_row_index = labdocs.size() - 1;
                        if (total_row_index < 0 || (totalNoPages != null && totalNoPages.intValue() == (pageNum + 1))) {
                    %>
                    <%
                        if (isListView) { %>
                    <tr>
                        <td colspan="10" align="center">
                            <input type="hidden" name="NoMoreItems" value="true"/>
                            <i><% if (pageNum == 1) { %>
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.index.msgNoReports"/>
                                <% } else { %>
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.index.msgNoMoreReports"/>
                                <% } %>
                            </i>

                        </td>
                    </tr>
                    <% } else {
                    %>

                    <div>
                        <input type="hidden" name="NoMoreItems" value="true"/>
                        <% if (pageNum == 1) { %>
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.index.msgNoReports"/>
                        <% } else { %>
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.index.msgNoMoreReports"/>
                        <% } %>
                    </div>

                    <%
                            }

                        }
                        for (int i = 0; i < labdocs.size(); i++) {

                            LabResultData result = (LabResultData) labdocs.get(i);
                            String segmentID = result.getSegmentID();
                            String status = result.getAcknowledgedStatus();

                            String bgcolor = i % 2 == 0 ? "#e0e0ff" : "#ccccff";
                            if (!result.isMatchedToPatient()) {
                                bgcolor = "#FFCC00";
                            }

                            String labRead = "";

                            if (result.isHRM() && !oscarLogDao.hasRead(curUser_no, "hrm", segmentID)) {
                                labRead = "*";
                            }

                            if (!result.isHRM() && result.isDocument() && !oscarLogDao.hasRead(curUser_no, "document", segmentID)) {
                                labRead = "*";
                            }

                            if (!result.isHRM() && !result.isDocument() && !oscarLogDao.hasRead(curUser_no, "lab", segmentID)) {
                                labRead = "*";
                            }


                            String discipline = result.getDiscipline();
                            if (discipline == null || discipline.equalsIgnoreCase("null"))
                                discipline = "";
                            MiscUtils.getLogger().debug("result.isAbnormal()=" + result.isAbnormal());
                            doclabid_seq.add(segmentID);
                            request.setAttribute("segmentID", segmentID);
                            String demoName = StringEscapeUtils.escapeEcmaScript(result.getPatientName());

                            if (!isListView) {
                                try {
                                    if (result.isDocument()) { %>
                    <!-- segment ID <%= segmentID %>  -->
                    <!-- demographic name <%=StringEscapeUtils.escapeEcmaScript(result.getPatientName()) %>  -->
                    <form id="frmDocumentDisplay_<%=segmentID%>">
                        <input type="hidden" name="segmentID" value="<%=segmentID%>"/>
                        <input type="hidden" name="demoName" value="<%=demoName%>"/>
                    </form>
                    <div id="document_<%=segmentID%>">
                        <jsp:include page="/documentManager/showDocument.jsp" flush="true">
                            <jsp:param name="segmentID" value="<%=segmentID%>"/>
                            <jsp:param name="demoName" value="<%=demoName%>"/>
                            <jsp:param name="providerNo" value="<%=providerNo%>"/>
                            <jsp:param name="searchProviderNo" value="<%=searchProviderNo%>"/>
                            <jsp:param name="status" value="<%=status%>"/>
                        </jsp:include>
                    </div>
                    <%
                    } else if (result.isHRM()) {

                        StringBuilder duplicateLabIds = new StringBuilder();
                        for (Integer duplicateLabId : result.getDuplicateLabIds()) {
                            if (duplicateLabIds.length() > 0) duplicateLabIds.append(',');
                            duplicateLabIds.append(duplicateLabId);
                        }
                    %>

                    <jsp:include page="/hospitalReportManager/displayHRMReport.jsp" flush="true">
                        <jsp:param name="id" value="<%=segmentID %>"/>
                        <jsp:param name="segmentID" value="<%=segmentID %>"/>
                        <jsp:param name="providerNo" value="<%=providerNo %>"/>
                        <jsp:param name="searchProviderNo" value="<%=searchProviderNo %>"/>
                        <jsp:param name="status" value="<%=status %>"/>
                        <jsp:param name="demoName" value="<%=result.getPatientName() %>"/>
                        <jsp:param name="duplicateLabIds" value="<%=duplicateLabIds.toString() %>"/>
                    </jsp:include>
                    <% } else {

                    %>

                    <jsp:include page="/lab/CA/ALL/labDisplayAjax.jsp" flush="true">
                        <jsp:param name="segmentID" value="<%=segmentID%>"/>
                        <jsp:param name="demoName" value="<%=demoName%>"/>
                        <jsp:param name="providerNo" value="<%=providerNo%>"/>
                        <jsp:param name="searchProviderNo" value="<%=searchProviderNo%>"/>
                        <jsp:param name="status" value="<%=status%>"/>
                        <jsp:param name="showLatest" value="true"/>
                    </jsp:include>

                    <%
                            }
                        } catch (Exception e) {
                            logger.error(e.toString());
                        }
                    } else {
                    %>
                    <tr id="labdoc_<%=segmentID%>" bgcolor="<%=bgcolor%>" <%if (result.isDocument()) {%>
                        name="scannedDoc" <%} else {%> name="HL7lab" <%}%>
                        class="<%= (result.isAbnormal() ? "AbnormalRes" : "NormalRes" ) + " " + (result.isMatchedToPatient() ? "AssignedRes" : "UnassignedRes") %>">
                        <td>
                            <input type="hidden" id="totalNumberRow" value="<%=total_row_index+1%>">
                            <%--
                                // used to disable the checkboxes for any reason an action should be blocked for
                                // unmatched labs
                                String disabled = "";
                                if(! result.isMatchedToPatient() && result.labType != "DOC")
                                {
                                    disabled = "disabled";
                                };
                            --%>
                            <input type="checkbox" name="flaggedLabs" value="<%=segmentID + ":" + result.labType%>">
                            <input type="hidden" name="labType<%=segmentID+result.labType%>"
                                   value="<%=result.labType%>"/>
                            <input type="hidden" name="ackStatus" value="<%= result.isMatchedToPatient() %>"/>
                            <input type="hidden" name="patientName"
                                   value="<%=StringEscapeUtils.escapeHtml4(result.patientName) %>"/>
                            <%--                                    <%=result.getHealthNumber() %>--%>
                        </td>

                        <td>
                            <% if (result.isMDS()) { %>
                            <a href="javascript:parent.reportWindow('SegmentDisplay.jsp?segmentID=<%=segmentID%>&providerNo=<%=providerNo%>&searchProviderNo=<%=searchProviderNo%>&status=<%=status%>')"><%=labRead%><%= StringEscapeUtils.escapeHtml4(result.getPatientName())%>
                            </a>
                            <% } else if (result.isCML()) { %>
                            <a href="javascript:parent.reportWindow('<%=request.getContextPath()%>/lab/CA/ON/CMLDisplay.jsp?segmentID=<%=segmentID%>&providerNo=<%=providerNo%>&searchProviderNo=<%=searchProviderNo%>&status=<%=status%>')"><%=labRead%><%=StringEscapeUtils.escapeHtml4(result.getPatientName())%>
                            </a>
                            <% } else if (result.isHL7TEXT()) {
                                String categoryType = result.getDiscipline();

                                if ("REF_I12".equals(categoryType)) {
                            %>
                            <a href="javascript:parent.popupConsultation('<%=segmentID%>')"><%=labRead%><%=StringEscapeUtils.escapeHtml4(result.getPatientName())%>
                            </a>
                            <%
                            } else if (categoryType != null && categoryType.startsWith("ORU_R01:")) {
                            %>
                            <a href="<%=request.getContextPath()%>/lab/CA/ALL/viewOruR01.jsp?segmentId=<%=segmentID%>"><%=labRead%><%=StringEscapeUtils.escapeHtml4(result.getPatientName())%>
                            </a>
                            <%
                            } else {
                            %>
                            <a href="javascript:parent.reportWindow('<%=request.getContextPath()%>/lab/CA/ALL/labDisplay.jsp?inWindow=true&segmentID=<%=segmentID%>&providerNo=<%=providerNo%>&searchProviderNo=<%=searchProviderNo%>&status=<%=status%>&showLatest=true')"><%=labRead%><%=StringEscapeUtils.escapeHtml4(result.getPatientName())%>
                            </a>
                            <%
                                }
                            } else if (result.isDocument()) {
                                String patientName = result.getPatientName();
                                StringBuilder url = new StringBuilder(request.getContextPath());
                                url.append("/documentManager/showDocument.jsp?inWindow=true&segmentID=");
                                url.append(segmentID);
                                url.append("&demoName=");

                                //the browser html parser does not understand javascript so we need to account for the opening
                                //and closing quotes used in the onclick event handler
                                patientName = StringEscapeUtils.escapeHtml4(patientName);

                                //now that the html parser will pass the correct characters to the javascript engine we need to
                                //escape chars for javascript that are not transformed by the html escape.
                                url.append(StringEscapeUtils.escapeEcmaScript(patientName));
                            %>

                            <a href="javascript:void(0);"
                               onclick="reportWindow('<%=url.toString()%>',screen.availHeight, screen.availWidth); return false;"><%=labRead + StringEscapeUtils.escapeHtml4(result.getPatientName())%>
                            </a>

                            <% } else if (result.isHRM()) {
                                StringBuilder duplicateLabIds = new StringBuilder();
                                for (Integer duplicateLabId : result.getDuplicateLabIds()) {
                                    if (duplicateLabIds.length() > 0) duplicateLabIds.append(',');
                                    duplicateLabIds.append(duplicateLabId);
                                }
                            %>
                            <a href="javascript:reportWindow('<%=request.getContextPath()%>/hospitalReportManager/Display.do?id=<%=segmentID%>&segmentID=<%=segmentID%>&providerNo=<%=providerNo%>&searchProviderNo=<%=searchProviderNo%>&status=<%=status%>&demoName=<%=demoName%>&duplicateLabIds=<%=duplicateLabIds.toString()%>&isListView=<%=isListView%>',850,1020)"><%=labRead%><%=result.getPatientName()%>
                            </a>
                            <% } else {%>
                            <a href="javascript:parent.reportWindow('<%=request.getContextPath()%>/lab/CA/BC/labDisplay.jsp?segmentID=<%=segmentID%>&providerNo=<%=providerNo%>&searchProviderNo=<%=searchProviderNo%>&status=<%=status%>')"><%=labRead%><%=StringEscapeUtils.escapeEcmaScript(result.getPatientName())%>
                            </a>
                            <% }%>
                        </td>
                        <td>
                            <%=result.getSex() %>
                        </td>
                        <td>
                            <%= (result.isAbnormal() ? "Abnormal" : "") %>
                        </td>
                        <td class="lab-label">
                            <c:out value="<%= result.getLabel() %>"/>
                        </td>
                        <td>
                            <%=result.getDateTime() + (result.isDocument() ? " / " + result.lastUpdateDate : "")%>
                        </td>
                        <%--                                <td>--%>
                        <%--                                    <%=result.getPriority()%>--%>
                        <%--                                </td>--%>
                        <td>
                            <c:out value="<%=result.getRequestingClient()%>"/>
                        </td>
                        <td>
                            <c:out value='<%=result.isDocument() ? result.description == null ? "" : result.description : result.getDisciplineDisplayString()%>'/>
                        </td>
                        <td>
                            <c:out value="<%= result.getReportStatus() %>"/>
                        </td>
                        <td>
                            <% int multiLabCount = result.getMultipleAckCount(); %>
                            <%= result.getAckCount() %>&#160<% if (multiLabCount >= 0) { %>
                            (<%= result.getMultipleAckCount() %>)<%}%>
                        </td>
                    </tr>
                    <% }


                    } // End else from if(isListView)
                        if (isListView && pageNum == 0) { %>
                    </tbody>
                </table>

                <table>
                    <tr>
                        <td bgcolor="E0E1FF">
                            <div id='loader' style="display:none"><img
                                    src='<%=request.getContextPath()%>/images/DMSLoader.gif'> Loading reports...
                            </div>
                        </td>
                    </tr>
                </table>
            </div>

        </td>
    </tr>
</table>
<script>
    jQuery.tablesorter.addParser({
        id: 'dateOfTest',
        is: function (s) {
            return false;
        },
        format: function (s) {
            //this code assumes the following:
            //in this scenario (2023-09-21 / 2023-09-21), use the 10 characters following the /
            //in this scenario (2023-09-19 12:53:00 ), use the first 10 characters
            //in this scenario (2023-09-21 17:39:05.0), use the first 10 characters
            //in any other scenario, just use the original string
            return s.indexOf("/") != -1 ? s.substr(s.indexOf("/") + 2, 10) : (s.indexOf(" ") != -1 ? s.substr(0, 10) : s);
        },
        type: 'text'
    });

    jQuery("#summaryView").tablesorter({
        sortList: [5, 1],
        headers: {
            5: {
                sorter: 'dateOfTest'
            }
        }
    });
</script>
<% } // End if (pageNum == 1) %>

