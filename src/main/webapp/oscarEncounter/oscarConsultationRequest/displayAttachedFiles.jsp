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
<security:oscarSec roleName="<%=roleName$%>" objectName="_con" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_con");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@page
        import="java.util.ArrayList, ca.openosp.openo.lab.ca.on.*, ca.openosp.openo.util.StringUtils" %>
<%@page import="java.util.List" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.hospitalReportManager.dao.HRMDocumentDao" %>
<%@ page import="ca.openosp.openo.hospitalReportManager.dao.HRMDocumentToDemographicDao" %>
<%@ page import="ca.openosp.openo.hospitalReportManager.model.HRMDocument" %>
<%@ page import="ca.openosp.openo.hospitalReportManager.model.HRMDocumentToDemographic" %>
<%@ page import="ca.openosp.openo.commn.model.EFormData" %>
<%@ page import="ca.openosp.openo.eform.EFormUtil" %>
<%@ page import="ca.openosp.openo.documentManager.EDocUtil" %>
<%@ page import="ca.openosp.openo.documentManager.EDoc" %>
<%@ page import="ca.openosp.openo.lab.ca.on.CommonLabResultData" %>
<%@ page import="ca.openosp.openo.lab.ca.on.LabResultData" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    String demo = request.getParameter("demo");
    String requestId = request.getParameter("requestId");
%>
<ul id="attachedList"
    style="background-color: white; padding-left: 20px; list-style-position: outside; list-style-type: lower-roman;">
    <%
        ArrayList privatedocs = new ArrayList();
        privatedocs = EDocUtil.listDocs(loggedInInfo, demo, requestId, EDocUtil.ATTACHED);
        EDoc curDoc;
        for (int idx = 0; idx < privatedocs.size(); ++idx) {
            curDoc = (EDoc) privatedocs.get(idx);
    %>
    <li class="doc"><%=StringUtils.maxLenString(curDoc.getDescription(), 19, 16, "...")%>
    </li>
    <%
        }

        CommonLabResultData labData = new CommonLabResultData();
        ArrayList labs = labData.populateLabResultsData(loggedInInfo, demo, requestId, CommonLabResultData.ATTACHED);
        LabResultData resData;
        for (int idx = 0; idx < labs.size(); ++idx) {
            resData = (LabResultData) labs.get(idx);
    %>
    <li class="lab"><%=resData.getDiscipline() + " " + resData.getDateTime()%>
    </li>
    <%
        }
        //Gets the DAOs for HRMDocumentToDemographic and HRMDocument
        HRMDocumentToDemographicDao hrmDocumentToDemographicDao = (HRMDocumentToDemographicDao) SpringUtils.getBean(HRMDocumentToDemographicDao.class);
        HRMDocumentDao hrmDocumentDao = (HRMDocumentDao) SpringUtils.getBean(HRMDocumentDao.class);
        //Gets the list of attached HRM Documents with the consultation
        List<HRMDocumentToDemographic> hrmDocumentToDemographicList = hrmDocumentToDemographicDao.findHRMDocumentsAttachedToConsultation(requestId);
        //Declares an hrmDocument, a truncatedDisplayName, and a date for each attached HRM document
        HRMDocument hrmDocument;
        String truncatedDisplayName;
        String date;
        //For each HRMDocumentToDemographic in the list
        for (HRMDocumentToDemographic hrmDocumentToDemographic : hrmDocumentToDemographicList) {
            //Gets the corresponding HRMDocument
            hrmDocument = hrmDocumentDao.find(hrmDocumentToDemographic.getHrmDocumentId());
            //Checks if the hrmDescription has data, if it does then it becomes the displayName, if it doesn't then the reportType becomes the display name
            if (!hrmDocument.getDescription().equals("")) {
                truncatedDisplayName = StringUtils.maxLenString(hrmDocument.getDescription(), 14, 11, "");
            } else {
                truncatedDisplayName = StringUtils.maxLenString(hrmDocument.getReportType(), 14, 11, "");
            }

            //Outputs the list item
    %>
    <li class="hrm"><%=truncatedDisplayName%>
    </li>
    <%
        }

        //Get attached eForms
        List<EFormData> eForms = EFormUtil.listPatientEformsCurrentAttachedToConsult(requestId);
        for (EFormData eForm : eForms) { %>
    <li class="eForm"><%=(eForm.getFormName().length() > 14) ? eForm.getFormName().substring(0, 11) + "..." : eForm.getFormName()%>
    </li>
    <%
        }
    %>
</ul>
<%
    if (privatedocs.size() == 0 && labs.size() == 0 && hrmDocumentToDemographicList.size() == 0 && eForms.isEmpty()) {
%>
<p id="attachDefault"
   style="background-color: white; text-align: center;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.AttachDoc.Empty"/></p>
<%
    }
%>
