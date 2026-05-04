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

<%-- ========== PAGE IMPORTS ========== --%>
<%@page import="org.apache.commons.lang3.StringUtils" %>
<%@page import="io.github.carlos_emr.carlos.encounter.pageUtil.EctDisplayLabAction2" %>
<%@page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="java.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.on.LabResultData" %>
<%@ page import="io.github.carlos_emr.carlos.mds.data.*,io.github.carlos_emr.carlos.lab.ca.on.*" %>
<%@ page import="io.github.carlos_emr.carlos.util.DateUtils" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.all.Hl7textResultsData" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData" %>
<%@ page import="io.github.carlos_emr.carlos.mds.data.ProviderData" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>

<%-- ========== TAGLIB DECLARATIONS ========== --%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="carlos" prefix="carlos" %>

<%-- ========== SECURITY CHECK ========== --%>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_lab" rights="r" reverse="<%=true%>">
    <%authed = false;%>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_lab");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%-- ========== BUSINESS LOGIC ========== --%>
<%
    CommonLabResultData comLab = new CommonLabResultData();
    String providerNo = (String) session.getAttribute("user");
    String searchProviderNo = request.getParameter("searchProviderNo");
    String ackStatus = request.getParameter("status");
    String demographicNo = request.getParameter("demographicNo");

    if (ackStatus == null) ackStatus = "N";
    if (providerNo == null) providerNo = "";
    if (searchProviderNo == null) searchProviderNo = providerNo;

    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

    ArrayList<LabResultData> labs = comLab.populateLabResultsData(loggedInInfo, "", demographicNo, "", "", "", "U");
    labs = getLatestLabVersions(labs);
    Collections.sort(labs);

    pageContext.setAttribute("ctx", request.getContextPath());
    pageContext.setAttribute("labs", labs);
    pageContext.setAttribute("ackStatus", ackStatus);
    pageContext.setAttribute("providerNo", providerNo);
    pageContext.setAttribute("searchProviderNo", searchProviderNo);
    pageContext.setAttribute("demographicNo", demographicNo);
    pageContext.setAttribute("hasDemographicNo", demographicNo != null);
    pageContext.setAttribute("hasFname", request.getParameter("fname") != null);
    pageContext.setAttribute("hasLabsAndNoDemographicNo", demographicNo == null && labs.size() > 0);
%>

<fmt:setBundle basename="oscarResources"/>

<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head>
    <meta charset="UTF-8">
    <title><fmt:message key="oscarMDS.index.title"/></title>
    <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
    <link rel="stylesheet" type="text/css" href="${ctx}/library/DataTables/DataTables-1.13.4/css/dataTables.bootstrap5.min.css">
    <script type="text/javascript" src="${ctx}/library/DataTables/DataTables-1.13.4/js/jquery.dataTables.min.js"></script>
    <script type="text/javascript" src="${ctx}/library/DataTables/DataTables-1.13.4/js/dataTables.bootstrap5.min.js"></script>

    <script type="text/javascript">
        function popupStart(vheight, vwidth, varpage, windowname) {
            var windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes";
            window.open(varpage, windowname || "helpwindow", windowprops);
        }

        function reportWindow(page) {
            var windowprops = "height=660, width=960, location=no, scrollbars=yes, menubars=no, toolbars=no, resizable=yes, top=0, left=0";
            var popup = window.open(page, "labreport", windowprops);
            popup.focus();
        }

        function checkSelected() {
            var aBoxIsChecked = false;
            var boxes = document.reassignForm.flaggedLabs;
            if (boxes.length === undefined) {
                if (boxes.checked) aBoxIsChecked = true;
            } else {
                for (var i = 0; i < boxes.length; i++) {
                    if (boxes[i].checked) aBoxIsChecked = true;
                }
            }
            if (aBoxIsChecked) {
                popupStart(300, 400, '${ctx}/oscarMDS/ViewSelectProvider', 'providerselect');
            } else {
                alert('<fmt:message key="oscarMDS.index.msgSelectOneLab"/>');
            }
        }

        function submitFile() {
            var aBoxIsChecked = false;
            var boxes = document.reassignForm.flaggedLabs;
            if (boxes.length === undefined) {
                if (boxes.checked) aBoxIsChecked = true;
            } else {
                for (var i = 0; i < boxes.length; i++) {
                    if (boxes[i].checked) aBoxIsChecked = true;
                }
            }
            if (aBoxIsChecked) {
                document.reassignForm.action = '${ctx}/oscarMDS/FileLabs';
                document.reassignForm.submit();
            }
        }

        function checkAll(formId) {
            var f = document.getElementById(formId);
            var val = f.checkA.checked;
            var boxes = f.flaggedLabs;
            if (boxes.length === undefined) {
                boxes.checked = val;
            } else {
                for (var i = 0; i < boxes.length; i++) {
                    boxes[i].checked = val;
                }
            }
        }

        jQuery(document).ready(function () {
            jQuery('#labResultsTbl').DataTable({
                searching: true,
                pageLength: 25,
                language: {
                    url: '${ctx}/library/DataTables/i18n/<fmt:message key="global.i18n.datatablescode"/>.json'
                }
            });
        });
    </script>
</head>

<body>
<div class="container-fluid mt-2">

    <form name="reassignForm" method="post" action="${ctx}/oscarMDS/ReportReassign" id="lab_form">
        <input type="hidden" name="providerNo" value="<carlos:encode value='<%= providerNo %>' context='htmlAttribute'/>">
        <input type="hidden" name="searchProviderNo" value="<carlos:encode value='<%= searchProviderNo %>' context='htmlAttribute'/>">
        <c:if test="${not empty param.lname}">
            <input type="hidden" name="lname" value="<carlos:encode value='${param.lname}' context='htmlAttribute'/>">
        </c:if>
        <c:if test="${not empty param.fname}">
            <input type="hidden" name="fname" value="<carlos:encode value='${param.fname}' context='htmlAttribute'/>">
        </c:if>
        <c:if test="${not empty param.hnum}">
            <input type="hidden" name="hnum" value="<carlos:encode value='${param.hnum}' context='htmlAttribute'/>">
        </c:if>
        <input type="hidden" name="status" value="<carlos:encode value='<%= ackStatus %>' context='htmlAttribute'/>">
        <input type="hidden" name="selectedProviders">

        <%-- Page header --%>
        <div class="page-header-bar d-flex align-items-center justify-content-between py-2 mb-3 border-bottom" id="header">
            <div class="d-flex align-items-center gap-2">
                <i class="fa-solid fa-flask"></i>
                <span class="fw-semibold">
                    <c:choose>
                        <c:when test="${not hasDemographicNo}">
                            <c:choose>
                                <c:when test="${ackStatus eq 'N'}"><fmt:message key="oscarMDS.index.msgNewLabReportsFor"/></c:when>
                                <c:when test="${ackStatus eq 'A'}"><fmt:message key="oscarMDS.index.msgAcknowledgedLabReportsFor"/></c:when>
                                <c:otherwise><fmt:message key="oscarMDS.index.msgAllLabReportsFor"/></c:otherwise>
                            </c:choose>
                            <c:choose>
                                <c:when test="${empty searchProviderNo}"><fmt:message key="oscarMDS.index.msgAllPhysicians"/></c:when>
                                <c:when test="${searchProviderNo eq '0'}"><fmt:message key="oscarMDS.index.msgUnclaimed"/></c:when>
                                <c:otherwise><carlos:encode value='<%= ProviderData.getProviderName(searchProviderNo) %>' context="html"/></c:otherwise>
                            </c:choose>
                        </c:when>
                        <c:otherwise><fmt:message key="oscarMDS.index.title"/></c:otherwise>
                    </c:choose>
                </span>
            </div>
            <div class="d-flex align-items-center gap-2">
                <c:if test="${not hasDemographicNo}">
                    <a class="btn btn-outline-secondary btn-sm" href="${ctx}/oscarMDS/ViewSearch?providerNo=<carlos:encode value='<%= providerNo %>' context='uriComponent'/>">
                        <fmt:message key="oscarMDS.index.btnSearch"/>
                    </a>
                </c:if>
                <c:if test="${hasFname}">
                    <a class="btn btn-outline-secondary btn-sm" href="${ctx}/lab/ViewDemographicLab?providerNo=<carlos:encode value='<%= providerNo %>' context='uriComponent'/>">
                        <fmt:message key="oscarMDS.index.btnDefaultView"/>
                    </a>
                </c:if>
                <c:if test="${hasLabsAndNoDemographicNo}">
                    <button type="button" class="btn btn-outline-primary btn-sm" onclick="checkSelected()">
                        <fmt:message key="oscarMDS.index.btnForward"/>
                    </button>
                    <button type="button" class="btn btn-outline-primary btn-sm" onclick="submitFile()">
                        <fmt:message key="oscarMDS.index.btnFile"/>
                    </button>
                    <span title="<fmt:message key="global.uploadWarningBody"/>" style="cursor:pointer">
                        <img border="0" src="${ctx}/images/icon_alertsml.gif" alt="warning"/>
                    </span>
                </c:if>
                <button type="button" class="btn btn-secondary btn-sm" onclick="window.close()">
                    <fmt:message key="oscarMDS.index.btnClose"/>
                </button>
            </div>
        </div>

        <div class="bg-light border rounded p-3">
            <table id="labResultsTbl" class="table table-bordered table-hover table-sm">
                <thead>
                    <tr>
                        <c:if test="${hasLabsAndNoDemographicNo}">
                            <th><input type="checkbox" id="checkA" name="checkA" onclick="checkAll('lab_form')"/></th>
                        </c:if>
                        <th><fmt:message key="oscarMDS.index.msgDateTest"/></th>
                        <th><fmt:message key="oscarMDS.index.msgLabel"/></th>
                        <th><fmt:message key="oscarMDS.index.msgRequestingClient"/></th>
                        <th><fmt:message key="oscarMDS.index.msgResultStatus"/></th>
                        <th><fmt:message key="oscarMDS.index.msgReportStatus"/></th>
                        <th><fmt:message key="oscarMDS.index.msgDiscipline"/></th>
                    </tr>
                </thead>
                <tbody>
                    <%
                        int colCount = (demographicNo == null) ? 7 : 6;
                        if (labs.isEmpty()) {
                    %>
                    <tr>
                        <td colspan="<%=colCount%>" class="text-center text-muted fst-italic">
                            <fmt:message key="oscarMDS.index.msgNoReports"/>
                        </td>
                    </tr>
                    <%
                        } else {
                            for (int i = 0; i < labs.size(); i++) {
                                LabResultData result = (LabResultData) labs.get(i);
                                String segmentID = (String) result.segmentID;
                                String status = (String) result.acknowledgedStatus;

                                String rowClass = result.isMatchedToPatient() ? "" : "table-warning";
                                if (result.isAbnormal()) rowClass += " table-danger";

                                Date d1 = getServiceDate(loggedInInfo, result);
                                String formattedDate = DateUtils.getDate(d1);
                    %>
                    <tr class="<%=rowClass%>">
                        <%  if (demographicNo == null) { %>
                        <td>
                            <input type="checkbox" name="flaggedLabs" value="<carlos:encode value='<%= segmentID %>' context='htmlAttribute'/>"/>
                        </td>
                        <% } %>
                        <td><%=SafeEncode.forHtmlContent(formattedDate)%></td>
                        <td>
                            <%
                                String labUrl = null;
                                String labLabel = null;
                                if (result.isMDS()) {
                                    labUrl = request.getContextPath() + "/oscarMDS/ViewSegmentDisplay?demographicId=" + SafeEncode.forUriComponent(demographicNo) + "&segmentID=" + SafeEncode.forUriComponent(segmentID) + "&providerNo=" + SafeEncode.forUriComponent(providerNo) + "&searchProviderNo=" + SafeEncode.forUriComponent(searchProviderNo) + "&status=" + SafeEncode.forUriComponent(status);
                                    labLabel = StringUtils.trimToEmpty(result.getDiscipline());
                                } else if (result.isCML()) {
                                    labUrl = request.getContextPath() + "/lab/CA/ON/ViewCMLDisplay?demographicId=" + SafeEncode.forUriComponent(demographicNo) + "&segmentID=" + SafeEncode.forUriComponent(segmentID) + "&providerNo=" + SafeEncode.forUriComponent(providerNo) + "&searchProviderNo=" + SafeEncode.forUriComponent(searchProviderNo) + "&status=" + SafeEncode.forUriComponent(status);
                                    labLabel = StringUtils.trimToEmpty(result.getDiscipline());
                                } else if (result.isHL7TEXT()) {
                                    labUrl = request.getContextPath() + "/lab/CA/ALL/ViewLabDisplay?demographicId=" + SafeEncode.forUriComponent(demographicNo) + "&segmentID=" + SafeEncode.forUriComponent(segmentID) + "&providerNo=" + SafeEncode.forUriComponent(providerNo) + "&searchProviderNo=" + SafeEncode.forUriComponent(searchProviderNo) + "&status=" + SafeEncode.forUriComponent(status);
                                    labLabel = StringUtils.trimToEmpty(result.getLabel());
                                } else {
                                    labUrl = request.getContextPath() + "/lab/CA/BC/ViewLabDisplay?demographicId=" + SafeEncode.forUriComponent(demographicNo) + "&segmentID=" + SafeEncode.forUriComponent(segmentID) + "&providerNo=" + SafeEncode.forUriComponent(providerNo) + "&searchProviderNo=" + SafeEncode.forUriComponent(searchProviderNo) + "&status=" + SafeEncode.forUriComponent(status);
                                    labLabel = StringUtils.trimToEmpty(result.getLabel());
                                }
                                if (labLabel == null || labLabel.isEmpty()) labLabel = StringUtils.trimToEmpty(result.getDiscipline());
                            %>
                            <a href="javascript:reportWindow('<%=SafeEncode.forJavaScript(labUrl)%>')">
                                <%=SafeEncode.forHtmlContent(labLabel)%>
                            </a>
                        </td>
                        <td><%=SafeEncode.forHtmlContent(StringUtils.trimToEmpty(result.getRequestingClient()))%></td>
                        <td>
                            <% if (result.isAbnormal()) { %>
                            <span class="text-danger fw-semibold"><fmt:message key="oscarMDS.index.msgAbnormal"/></span>
                            <% } %>
                        </td>
                        <td>
                            <% if (result.isFinal()) { %>
                            <fmt:message key="oscarMDS.index.msgFinal"/>
                            <% } else { %>
                            <fmt:message key="oscarMDS.index.msgPartial"/>
                            <% } %>
                        </td>
                        <td><%=SafeEncode.forHtmlContent(StringUtils.trimToEmpty(result.getDiscipline()))%></td>
                    </tr>
                    <%
                            }
                        }
                    %>
                </tbody>
            </table>
        </div><%-- end bg-light --%>

    </form>

</div><%-- end container-fluid --%>
</body>
</html>

<%!
    public Date getServiceDate(LoggedInInfo loggedInInfo, LabResultData labData) {
        EctDisplayLabAction2.ServiceDateLoader loader = new EctDisplayLabAction2.ServiceDateLoader(labData);
        Date resultDate = loader.determineResultDate(loggedInInfo);
        if (resultDate != null) {
            return resultDate;
        }
        return labData.getDateObj();
    }

    public ArrayList<LabResultData> getLatestLabVersions(ArrayList<LabResultData> labs) {
        List<String> allLabIds = new ArrayList<>();
        ArrayList<LabResultData> latestLabVersions = new ArrayList<>();
        for (LabResultData lab : labs) {
            if (allLabIds.contains(lab.getSegmentID())) {
                continue;
            }
            String[] allLabVersionIdsOfLab = Hl7textResultsData.getMatchingLabs(lab.getSegmentID()).split(",");
            allLabIds.addAll(Arrays.asList(allLabVersionIdsOfLab));
            for (LabResultData labResultData : labs) {
                if (allLabVersionIdsOfLab[allLabVersionIdsOfLab.length - 1].equals(labResultData.getSegmentID())) {
                    latestLabVersions.add(labResultData);
                    break;
                }
            }
        }
        return latestLabVersions;
    }
%>
