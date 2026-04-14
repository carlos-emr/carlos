<!DOCTYPE html>
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

<%@page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_demographic,_demographicExport" rights="w"
                   reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_demographic&type=_demographicExport");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page
        import="java.util.*,io.github.carlos_emr.carlos.demographic.data.*,io.github.carlos_emr.carlos.prevention.*,io.github.carlos_emr.carlos.providers.data.*,io.github.carlos_emr.carlos.util.*,io.github.carlos_emr.carlos.report.data.*,io.github.carlos_emr.carlos.prevention.pageUtil.*,io.github.carlos_emr.carlos.demographic.pageUtil.*" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.pageUtil.Util" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.pageUtil.DemographicExportAction42Action" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.pageUtil.PGPEncrypt" %>
<%@ page import="io.github.carlos_emr.carlos.report.data.DemographicSets" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>

<%

    CarlosProperties op = CarlosProperties.getInstance();
    String tmp_dir = op.getProperty("TMP_DIR");
    boolean tmp_dir_ready = Util.checkDir(tmp_dir);

    String pgp_ready = (String) session.getAttribute("pgp_ready");
    if (pgp_ready == null || pgp_ready.equals("No")) {
        PGPEncrypt pgp = new PGPEncrypt();
        if (pgp.check(tmp_dir)) pgp_ready = "Yes";
        else pgp_ready = "No";
    }
    session.setAttribute("pgp_ready", pgp_ready);

    String demographicNo = request.getParameter("demographicNo");
    DemographicSets ds = new DemographicSets();
    List<String> sets = ds.getDemographicSets();

//  io.github.carlos_emr.carlos.report.data.RptSearchData searchData  = new io.github.carlos_emr.carlos.report.data.RptSearchData();
//  ArrayList queryArray = searchData.getQueryTypes();

    String userRole = (String) session.getAttribute("userrole");

%>

<html>
    <head>
        <title><fmt:message key="demographic.demographicexport.title"/></title>

        <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">

        <SCRIPT LANGUAGE="JavaScript">

            function showHideItem(id) {
                if (document.getElementById(id).style.display == 'none')
                    document.getElementById(id).style.display = '';
                else
                    document.getElementById(id).style.display = 'none';
            }

            function showItem(id) {
                document.getElementById(id).style.display = '';
            }

            function hideItem(id) {
                document.getElementById(id).style.display = 'none';
            }

            function showHideNextDate(id, nextDate, neverWarn) {
                if (document.getElementById(id).style.display == 'none') {
                    showItem(id);
                } else {
                    hideItem(id);
                    document.getElementById(nextDate).value = "";
                    document.getElementById(neverWarn).checked = false;

                }
            }

            function disableifchecked(ele, nextDate) {
                if (ele.checked == true) {
                    document.getElementById(nextDate).disabled = true;
                } else {
                    document.getElementById(nextDate).disabled = false;
                }
            }

            function checkSelect(slct) {
                if (slct == -1) {
                    alert("Please select a Patient Set");
                    return false;
                } else return true;
            }

            function checkValidOptions() {
                var pt = document.getElementById("patientSet").value;
                var pn = document.getElementById("providerNo").value;

                if (pt != -1 && pn != -1) {
                    alert("Please choose either a Patient Set or a Provider");
                    return false;
                }

                if (pt == -1 && pn == -1) {
                    alert("Please choose either a Patient Set or a Provider");
                    return false;
                }

                return true;
            }

            function checkAll(all) {
                var frm = document.DemographicExportForm;
                if (all) {
                    frm.exPersonalHistory.checked = true;
                    frm.exFamilyHistory.checked = true;
                    frm.exPastHealth.checked = true;
                    frm.exProblemList.checked = true;
                    frm.exRiskFactors.checked = true;
                    frm.exAllergiesAndAdverseReactions.checked = true;
                    frm.exMedicationsAndTreatments.checked = true;
                    frm.exImmunizations.checked = true;
                    frm.exLaboratoryResults.checked = true;
                    frm.exAppointments.checked = true;
                    frm.exClinicalNotes.checked = true;
                    frm.exReportsReceived.checked = true;
                    frm.exAlertsAndSpecialNeeds.checked = true;
                    frm.exCareElements.checked = true;
                } else {
                    frm.exPersonalHistory.checked = false;
                    frm.exFamilyHistory.checked = false;
                    frm.exPastHealth.checked = false;
                    frm.exProblemList.checked = false;
                    frm.exRiskFactors.checked = false;
                    frm.exAllergiesAndAdverseReactions.checked = false;
                    frm.exMedicationsAndTreatments.checked = false;
                    frm.exImmunizations.checked = false;
                    frm.exLaboratoryResults.checked = false;
                    frm.exAppointments.checked = false;
                    frm.exClinicalNotes.checked = false;
                    frm.exReportsReceived.checked = false;
                    frm.exAlertsAndSpecialNeeds.checked = false;
                    frm.exCareElements.checked = false;
                }
            }

            function toggle(source) {
                var c = new Array();
                c = document.getElementsByTagName('input');
                for (var i = 0; i < c.length; i++) {
                    if (c[i].type == 'checkbox') {
                        c[i].checked = source.checked;
                    }
                }
            }

            /**
             * Returns the CSRF token value from the hidden input injected by CSRFGuard.
             * CSRFGuard 4.5 does NOT intercept fetch() — token must be included manually.
             */
            function getCsrfToken() {
                var el = document.querySelector('input[name="CSRF-TOKEN"]');
                return el ? el.value : '';
            }

            /**
             * Submits the export form via fetch(), reads the X-Export-Status response
             * header, and triggers a file download on success. Replaces the former
             * cookie-polling + hidden-iframe approach.
             */
            async function submitExport() {
                var form = document.getElementById('DemographicExportForm');
                var formData = new FormData(form);

                // Show loading overlay
                document.getElementById('exportSuccessMessage').style.display = 'none';
                document.getElementById('exportErrorMessage').style.display = 'none';
                document.getElementById('exportLoadingOverlay').style.display = 'block';

                try {
                    var response = await fetch(form.action, {
                        method: 'POST',
                        body: formData,
                        credentials: 'same-origin',
                        headers: { 'CSRF-TOKEN': getCsrfToken() }
                    });

                    var exportStatus = response.headers.get('X-Export-Status');

                    document.getElementById('exportLoadingOverlay').style.display = 'none';

                    if (exportStatus === 'success' && response.ok) {
                        var blob = await response.blob();
                        var contentDisposition = response.headers.get('Content-Disposition');
                        var filename = 'export.zip';
                        if (contentDisposition) {
                            var match = contentDisposition.match(/filename[^;=\n]*=["']?([^"';\n]+)/);
                            if (match) {
                                filename = match[1];
                            }
                        }
                        var url = URL.createObjectURL(blob);
                        var a = document.createElement('a');
                        a.href = url;
                        a.download = filename;
                        document.body.appendChild(a);
                        a.click();
                        document.body.removeChild(a);
                        URL.revokeObjectURL(url);

                        document.getElementById('exportSuccessMessage').style.display = 'block';
                    } else {
                        document.getElementById('exportErrorMessage').style.display = 'block';
                    }
                } catch (e) {
                    document.getElementById('exportLoadingOverlay').style.display = 'none';
                    document.getElementById('exportErrorMessage').style.display = 'block';
                }
            }

            /**
             * Handles the export form submission.
             * Validates options, then submits via fetch() to read status from response header.
             */
            function handleExportSubmit() {
                if (!checkValidOptions()) {
                    return false;
                }

                submitExport();
                return false;
            }

            /**
             * Allows user to retry the export by re-submitting the form.
             */
            function retryExport() {
                document.getElementById('exportErrorMessage').style.display = 'none';
                document.getElementById('exportSuccessMessage').style.display = 'none';

                submitExport();
            }
        </SCRIPT>

        <style type="text/css">
            input[type="checkbox"] {
                line-height: normal;
                margin: 4px 4px 4px;
            }

            #exportLoadingOverlay {
                display: none;
                position: absolute;
                top: 0;
                left: 0;
                right: 0;
                bottom: 0;
                background-color: rgba(0, 0, 0, 0.4);
                z-index: 9999;
            }

            #exportLoadingContent {
                position: absolute;
                top: 50%;
                left: 50%;
                transform: translate(-50%, -50%);
                background: white;
                padding: 20px 30px;
                border: 1px solid #ccc;
                border-radius: 4px;
                text-align: center;
            }

            .export-spinner {
                width: 40px;
                height: 40px;
                margin: 0 auto 10px;
                border: 4px solid #f3f3f3;
                border-top: 4px solid #3498db;
                border-radius: 50%;
                animation: spin 1s linear infinite;
            }

            @keyframes spin {
                0% { transform: rotate(0deg); }
                100% { transform: rotate(360deg); }
            }

            #exportSuccessMessage,
            #exportErrorMessage {
                display: none;
            }
        </style>

    </head>

    <body>

    <%
        if (!userRole.toLowerCase().contains("admin")) { %>
    <div class="alert alert-danger">
        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
        <fmt:message key="demographic.demographicexport.msgsorry"/>
    </div>
    <%
    } else if (!tmp_dir_ready) { %>
    <div class="alert alert-danger">
        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
        <fmt:message key="demographic.demographicexport.msgerror"/>
    </div>
    <%
    } else {
    %>

    <div class="container-fluid card card-body bg-body-tertiary" style="position: relative;">
        <!-- Loading overlay shown during export -->
        <div id="exportLoadingOverlay">
            <div id="exportLoadingContent">
                <div class="export-spinner"></div>
                <div><fmt:message key="demographic.demographicexport.preparingExport"/></div>
            </div>
        </div>
        <h3><fmt:message key="demographic.demographicexport.title"/> </h3>

        <div class="col-md-2">
            <% if (demographicNo == null) { %>
            <a href='<c:out value="${ctx}/demographic/eRourkeExport.do"></c:out>'><fmt:message key="demographic.demographicexport.rourke2009export"/></a>
            <%} %>
        </div><!--span2-->

        <div class="col-md-4">

            <!-- Success message shown after export completes -->
            <div id="exportSuccessMessage" class="alert alert-success">
                <fmt:message key="demographic.demographicexport.exportSuccess"/>
                <br/><br/>
                <fmt:message key="demographic.demographicexport.downloadNotStarted"/> <a href="javascript:void(0);" onclick="retryExport()"><fmt:message key="demographic.demographicexport.clickToDownload"/></a>
            </div>

            <!-- Error message shown if export fails -->
            <div id="exportErrorMessage" class="alert alert-danger">
                <fmt:message key="demographic.demographicexport.exportError"/>
                <br/><br/>
                <button type="button" class="btn btn-secondary" onclick="retryExport()"><fmt:message key="demographic.demographicexport.retry"/></button>
            </div>

            <form id="DemographicExportForm" name="DemographicExportForm" action="${pageContext.request.contextPath}/demographic/DemographicExport.do" method="post" onsubmit="return handleExportSubmit();">

                <% if (demographicNo != null) { %>
                <input type="hidden" name="demographicNo" id="demographicNo" value="<%= Encode.forHtmlAttribute(demographicNo) %>"/>
                <fmt:message key="demographic.demographicexport.exportingdemographicno"/><%=Encode.forHtml(demographicNo)%>
                <%} else {%>
                <fmt:message key="demographic.demographicexport.patientset"/><br>
                <select style="width: 189px" name="patientSet" id="patientSet">
                    <option value="-1"><fmt:message key="demographic.demographicexport.selectset"/></option>
                    <%
                        /*			    for (int i =0 ; i < queryArray.size(); i++){
                        RptSearchData.SearchCriteria sc = (RptSearchData.SearchCriteria) queryArray.get(i);
                        String qId = sc.id;
                        String qName = sc.queryName;
                        */
                        for (int i = 0; i < sets.size(); i++) {
                            String setName = sets.get(i);
                    %>
                    <option value="<%=setName%>"><%=setName%>
                    </option>
                    <%}%>
                </select>

                <br>

                <fmt:message key="demographic.demographicexport.providers"/><br>
                <select style="width: 189px" name="providerNo" id="providerNo">
                    <option value="-1"><fmt:message key="demographic.demographicexport.selectProvider"/></option>
                    <%
                        ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
                        List<Provider> providers = providerDao.getActiveProviders();
	/*			    for (int i =0 ; i < queryArray.size(); i++){
	RptSearchData.SearchCriteria sc = (RptSearchData.SearchCriteria) queryArray.get(i);
	String qId = sc.id;
	String qName = sc.queryName;
	*/
                        for (int i = 0; i < providers.size(); i++) {
                            Provider p = providers.get(i);
                    %>
                    <option value="<%=p.getProviderNo()%>"><%=p.getFormattedName()%>
                    </option>
                    <%}%>
                </select>

                <%}%>


                <br>


                <fmt:message key="demographic.demographicexport.exporttemplate"/><br>
                <select style="width: 189px" name="template">
                    <option
                            value="<%=(new Integer(DemographicExportAction42Action.CMS4)).toString() %>">EMR DM 5.0</option>
                    <option value="<%=(new Integer(DemographicExportAction42Action.E2E)).toString() %>">E2E</option>
                </select>

                <br>

                <fmt:message key="demographic.demographicexport.exportcategories"/><br>

                <input type="checkbox" onClick="toggle(this)"/>Select All<br/>

                <input type="checkbox" name="exPersonalHistory" value="true" /><fmt:message key="demographic.demographicexport.personalhistory"/><br>
                <input type="checkbox" name="exFamilyHistory" value="true" /><fmt:message key="demographic.demographicexport.familyhistory"/><br>
                <input type="checkbox" name="exPastHealth" value="true" /><fmt:message key="demographic.demographicexport.pasthealth"/><br>
                <input type="checkbox" name="exProblemList" value="true" /><fmt:message key="demographic.demographicexport.problemlist"/><br>
                <input type="checkbox" name="exRiskFactors" value="true" /><fmt:message key="demographic.demographicexport.riskfactors"/><br>
                <input type="checkbox" name="exAllergiesAndAdverseReactions" value="true" /><fmt:message key="demographic.demographicexport.allergiesadversereaction"/><br>
                <input type="checkbox" name="exMedicationsAndTreatments" value="true" /><fmt:message key="demographic.demographicexport.medicationstreatments"/><br>

                <input type="checkbox" name="exImmunizations" value="true" /><fmt:message key="demographic.demographicexport.immunization"/><br>
                <input type="checkbox" name="exLaboratoryResults" value="true" /><fmt:message key="demographic.demographicexport.laboratoryresults"/><br>
                <input type="checkbox" name="exAppointments" value="true" /><fmt:message key="demographic.demographicexport.appointments"/><br>
                <input type="checkbox" name="exClinicalNotes" value="true" /><fmt:message key="demographic.demographicexport.clinicalnotes"/><br>
                <input type="checkbox" name="exReportsReceived" value="true" /><fmt:message key="demographic.demographicexport.reportsreceived"/><br>
                <input type="checkbox" name="exCareElements" value="true" /><fmt:message key="demographic.demographicexport.careelements"/><br>
                <input type="checkbox" name="exAlertsAndSpecialNeeds" value="true" /><fmt:message key="demographic.demographicexport.alertsandspecialneeds"/>

                <br>
                <input type="hidden" name="pgpReady" id="pgpReady" value="<%=pgp_ready%>"/>

                <% boolean pgpReady = pgp_ready.equals("Yes") ? true : false;
//    pgpReady = true; //To be removed after CMS4
                    if (!pgpReady) { %>

                <div class="alert alert-danger">
                    <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
                    <fmt:message key="demographic.demographicexport.msgwarning"/>
                </div>

                <% } %>

                <input class="btn btn-primary" type="submit" value="<fmt:message key="export"/>"/>

            </form>

        </div><!--span4-->

    </div><!--container-->
    <%}%>

    <script type="text/javascript" src="<%=request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js"></script>
    <script src="<%=request.getContextPath() %>/library/jquery/jquery-compat.js"></script>
    <script src="<%=request.getContextPath() %>/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>

    </body>
</html>
