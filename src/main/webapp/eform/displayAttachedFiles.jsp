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

<%@page import="io.github.carlos_emr.carlos.commn.dao.EFormDataDao" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_eform" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_eform");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@page
        import="java.util.ArrayList, io.github.carlos_emr.carlos.lab.ca.on.*, io.github.carlos_emr.carlos.util.StringUtils" %>
<%@page import="io.github.carlos_emr.carlos.utility.SessionConstants" %>
<%@page import="java.util.List" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.util.DateUtils" %>
<%@ page import="io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao" %>
<%@ page import="io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao" %>
<%@ page import="io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument" %>
<%@ page import="io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.EFormData" %>
<%@ page import="io.github.carlos_emr.carlos.eform.EFormUtil" %>
<%@ page import="io.github.carlos_emr.carlos.documentManager.EDocUtil" %>
<%@ page import="io.github.carlos_emr.carlos.documentManager.EDoc" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.on.LabResultData" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    String demo = request.getParameter("demo");
    String requestId = request.getParameter("requestId");
    String val = "";

    if (!StringUtils.isNullOrEmpty(requestId) && demo == null) {
        EFormDataDao eformDataDao = SpringUtils.getBean(EFormDataDao.class);
        EFormData efmData = eformDataDao.find(Integer.parseInt(requestId));
        demo = String.valueOf(efmData.getDemographicId());
    }
%>
<ul id="attachedList"
    style="background-color: white; padding-left: 20px; list-style-position: outside; list-style-type: lower-roman;">
    <%
        ArrayList<EDoc> privatedocs = new ArrayList<EDoc>();
        privatedocs = EDocUtil.listDocsAttachedToEForm(loggedInInfo, demo, requestId, EDocUtil.ATTACHED);
        EDoc curDoc;
        for (int idx = 0; idx < privatedocs.size(); ++idx) {
            curDoc = privatedocs.get(idx);
            val += "D" + curDoc.getDocId() + "|";
    %>
    <li class="doc"><%=StringUtils.maxLenString(curDoc.getDescription(), 19, 16, "...")%>
    </li>
    <%
        }

        CommonLabResultData labData = new CommonLabResultData();
        ArrayList labs = labData.populateLabResultsDataEForm(loggedInInfo, demo, requestId, CommonLabResultData.ATTACHED);
        LabResultData resData;
        for (int idx = 0; idx < labs.size(); ++idx) {
            resData = (LabResultData) labs.get(idx);
            val += "L" + resData.segmentID + "|";
    %>
    <li class="lab"><%=resData.getDiscipline() + " " + resData.getDateTime()%>
    </li>
    <%
        }
        //Gets the DAOs for HRMDocumentToDemographic and HRMDocument
        HRMDocumentToDemographicDao hrmDocumentToDemographicDao = (HRMDocumentToDemographicDao) SpringUtils.getBean(HRMDocumentToDemographicDao.class);
        HRMDocumentDao hrmDocumentDao = (HRMDocumentDao) SpringUtils.getBean(HRMDocumentDao.class);
        //Gets the list of attached HRM Documents with the eform
        List<HRMDocumentToDemographic> hrmDocumentToDemographicList = hrmDocumentToDemographicDao.findHRMDocumentsAttachedToEForm(requestId);
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

            val += "H" + hrmDocumentToDemographic.getHrmDocumentId() + "|";
            //Outputs the list item
    %>
    <li class="hrm"><%=truncatedDisplayName%>
    </li>
    <%
        }

        //Get attached eForms
        List<EFormData> eForms = EFormUtil.listPatientEformsCurrentAttachedToEForm(requestId);
        for (EFormData eForm : eForms) {
            val += "E" + eForm.getId() + "|";

    %>
    <li class="eForm"><%=(eForm.getFormName().length() > 14) ? eForm.getFormName().substring(0, 11) + "..." : eForm.getFormName()%>
    </li>
    <%
        }
    %>
</ul>
<input type="text" name="selectDocs" value="<%=val %>" style="display:none">
<%
    if (privatedocs.size() == 0 && labs.size() == 0 && hrmDocumentToDemographicList.size() == 0 && eForms.isEmpty()) {
%>
<p id="attachDefault"
   style="background-color: white; text-align: center;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.AttachDoc.Empty"/></p>
<%
    }
%>
