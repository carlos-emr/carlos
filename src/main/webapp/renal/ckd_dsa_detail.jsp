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

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
"http://www.w3.org/TR/html4/loose.dtd">

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_measurement" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_measurement");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<%@ page import="java.util.*, java.text.*" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.commn.model.Measurement" %>
<%@page import="ca.openosp.openo.commn.dao.MeasurementDao" %>
<%@page import="ca.openosp.openo.commn.dao.DxresearchDAO" %>
<%@page import="ca.openosp.openo.commn.model.Dxresearch" %>
<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%
    MeasurementDao measurementDao = SpringUtils.getBean(MeasurementDao.class);
    DxresearchDAO dxResearchDao = (DxresearchDAO) SpringUtils.getBean(DxresearchDAO.class);
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

    pageContext.setAttribute("demographic_no", request.getParameter("demographic_no"));
    String labReqVer = ca.openosp.OscarProperties.getInstance().getProperty("onare_labreqver", "07");
    if (labReqVer.equals("")) {
        labReqVer = "07";
    }

%>

<html>
<head>
    <title>Chronic Kidney Disease DSA</title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/oscarEncounter/decisionSupport/decisionSupport.css"
          type="text/css"></link>
    <script src="<%=request.getContextPath() %>/js/jquery-1.7.1.min.js" type="text/javascript"></script>

    <script>
        function popupPage(vheight, vwidth, varpage) { //open a new popup window
            var page = "" + varpage;
            windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes";
            var popup = window.open(page, "attachment", windowprops);
            if (popup != null) {
                if (popup.opener == null) {
                    popup.opener = self;
                }
            }
        }

        function generateLabReq(demographicNo) {
            var url = '<%=request.getContextPath()%>/form/formlabreq<%=labReqVer%>.jsp?demographic_no=' + demographicNo + '&formId=0&provNo=<%=session.getAttribute("user")%>&fromSession=true';
            jQuery.ajax({
                url: '<%=request.getContextPath()%>/renal/Renal.do?method=createLabReq&demographicNo=' + demographicNo,
                async: false,
                success: function (data) {
                    popupPage(900, 850, url);
                }
            });
        }

    </script>
</head>
<body>
<div style="font-size: 16px; font-weight: bold;">Guideline assessment: <c:out value="${patientName}"/></div>
<br>
<c:if test="${not empty reasons}">
    <c:forEach items="${reasons}" var="reason">
                    <span class="good" style="font-size: 12px; font-weight: bold;">
                    <c:out value="${reason}"/><br/>
                    </span>
    </c:forEach>
</c:if>
<table style="font-size: 10px;  border-top: 1px solid black; border-collapse: collapse; margin-top: 15px;">
    <tr>
        <th>Title:</th>
        <td>CKD Pilot</td>
    </tr>
    <tr>
        <th>Author:</th>
        <td>Ontario Renal Network</td>
    </tr>
    <tr>
        <th>Start Date:</th>
        <td>N/A</td>
    </tr>
</table>

<br/>
<%
    String demographicNo = request.getParameter("demographic_no");
    String message = "";

    List<Measurement> egfrs = measurementDao.findByType(Integer.parseInt(demographicNo), "EGFR");
    List<Measurement> acrs = measurementDao.findByType(Integer.parseInt(demographicNo), "ACR");

    if (egfrs.size() > 0) {
        message += "EGFR: " + egfrs.get(0).getDataField() + " observed on " + formatter.format(egfrs.get(0).getDateObserved()) + "<br/>";
    } else {
        message += "EGFR: None" + "<br/>";
    }

    if (acrs.size() > 0) {
        message += "ACR: " + acrs.get(0).getDataField() + " observed on " + formatter.format(acrs.get(0).getDateObserved()) + "<br/>";
    } else {
        message += "ACR: None" + "<br/>";
    }
%>
<%=message %>

<%
    message = "";
    Dxresearch screeningDx = null;
    List<Dxresearch> dxs = dxResearchDao
        .findByDemographicNoResearchCodeAndCodingSystem(
            Integer.parseInt(demographicNo),
            "CKDSCREEN",
            "OscarCode"
        );
    for (Dxresearch dx : dxs) {
        if (dx.getStatus() == 'A') {
            screeningDx = dx;
            break;
        }
    }

    if (screeningDx != null) {
        String baseUrl = request.getContextPath() + "/oscarResearch/dxresearch/dxResearchUpdate.do";
        String did = screeningDx.getId().toString();
        String demo = Integer.toString(Integer.parseInt(demographicNo));
        String provider = loggedInInfo.getLoggedInProviderNo();

        String urlC = baseUrl + "?status=C&did=" + did + "&demographicNo=" + demo + "&providerNo=" + provider;
        String urlD = baseUrl + "?status=D&did=" + did + "&demographicNo=" + demo + "&providerNo=" + provider;

        message += "Screening complete - <a href=\"javascript:void(0)\" onclick=\"popupPage(580,900,'" + urlC + "');\">Click Here</a>.<br/>";
        message += "Screening not appropriate - <a href=\"javascript:void(0)\" onclick=\"popupPage(580,900,'" + urlD + "');\">Click Here</a>.<br/>";
    }

    dxs = dxResearchDao.findByDemographicNoResearchCodeAndCodingSystem(Integer.parseInt(demographicNo), "585", "icd9");
    if (dxs.size() == 0) {
        String popupUrl = request.getContextPath() + "/oscarResearch/oscarDxResearch/dxResearch.do"
                        + "?selectedCodingSystem=icd9"
                        + "&xml_research1=585"
                        + "&xml_research2="
                        + "&xml_research3="
                        + "&xml_research4="
                        + "&xml_research5="
                        + "&demographicNo=" + demographicNo
                        + "&quickList=default"
                        + "&forward=";

        message += "<br/>Add 'Chronic Renal Failure' to Dx Registry, and prevent subsequent notifications - "
                + "<a href=\"javascript:void(0);\" onclick=\"popupPage(580,900,'" + popupUrl + "');\">Click Here</a><br/>";
    }
%>
<%= message %>


<br/>
Order Labs - <a title="Create Lab Requisition" href="javascript:void(0);"
                onclick="generateLabReq(<%=demographicNo %>);return false;">Lab Requisition</a>
<br/><br/>
<%
    String flowsheet = ca.openosp.OscarProperties.getInstance().getProperty("ckd_flowsheet", "indicators");
    if (flowsheet.equals("indicators")) {
%>
Go to CDM Indicators <a href="javascript:void(0);"
                        onclick="popupPage(700,1000,'<%=request.getContextPath() %>/oscarEncounter/oscarMeasurements/TemplateFlowSheet.jsp?demographic_no=<%=demographicNo %>&amp;template=diab3');return false;">here</a><br/>
<% } else if (flowsheet.equals("diabetes")) { %>

Go to Diabetes Flowsheet <a href="javascript:void(0)"
                            onclick="popupPage(700,1000,'<%=request.getContextPath() %>/oscarEncounter/oscarMeasurements/TemplateFlowSheet.jsp?demographic_no=<%=demographicNo %>&amp;template=diab2');return false;">here</a><br/>
<% } %>

Go to Disease Registry to mark CKD Screening as resolved/deleted <a href="javascript:void(0);"
                                                                    onclick="popupPage(580,900,'<%=request.getContextPath() %>/oscarResearch/oscarDxResearch/setupDxResearch.do?demographicNo=<%=demographicNo %>&amp;quickList='); return false;">here</a><br/>


</body>
</html>
