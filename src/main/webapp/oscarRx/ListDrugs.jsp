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

<%@page import="ca.openosp.openo.commn.model.PartialDate" %>
<%@page import="org.apache.commons.text.StringEscapeUtils" %>
<%@page import="ca.openosp.openo.casemgmt.web.PrescriptDrug" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ page
        import="ca.openosp.openo.rx.data.*,ca.openosp.openo.demographic.data.DemographicData,ca.openosp.OscarProperties,ca.openosp.openo.log.*" %>
<%@page import="ca.openosp.openo.casemgmt.service.CaseManagementManager,
                org.springframework.web.context.WebApplicationContext,
                org.springframework.web.context.support.WebApplicationContextUtils,
                ca.openosp.openo.casemgmt.model.CaseManagementNoteLink,
                ca.openosp.openo.casemgmt.model.CaseManagementNote" %>
<%@page import="java.text.SimpleDateFormat" %>
<%@page import="java.util.Calendar" %>
<%@page import="java.util.Enumeration" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.utility.SessionConstants" %>
<%@page import="java.util.List,ca.openosp.openo.util.StringUtils" %>
<%@page import="ca.openosp.openo.PMmodule.caisi_integrator.CaisiIntegratorManager" %>
<%@page import="ca.openosp.openo.utility.LoggedInInfo,ca.openosp.openo.commn.dao.DrugReasonDao,ca.openosp.openo.commn.model.DrugReason" %>
<%@page import="java.util.ArrayList,ca.openosp.openo.util.*,java.util.*,ca.openosp.openo.commn.model.Drug,ca.openosp.openo.commn.dao.*" %>
<%@page import="ca.openosp.openo.managers.DrugDispensingManager" %>
<%@page import="ca.openosp.openo.managers.CodingSystemManager" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.services.security.SecurityManager" %>
<%@ page import="ca.openosp.openo.prescript.pageUtil.RxSessionBean" %>
<%@ page import="ca.openosp.openo.prescript.data.RxPatientData" %>
<%@ page import="ca.openosp.openo.prescript.data.RxPrescriptionData" %>
<%@ page import="ca.openosp.openo.util.UtilDateUtilities" %>
<%@ page import="ca.openosp.openo.commn.dao.PartialDateDao" %>

<%
    RxPatientData.Patient patient = null;
    RxSessionBean bean = null;
%>
<c:if test="${empty sessionScope.RxSessionBean}">
    <c:redirect url="error.html"/>
</c:if>
<c:if test="${not empty sessionScope.RxSessionBean}">
    <%
        // Directly access the RxSessionBean from the session
        bean = (RxSessionBean) session.getAttribute("RxSessionBean");
        if (bean != null && !bean.isValid()) {
            response.sendRedirect("error.html");
            return; // Ensure no further JSP processing
        }
        patient = (RxPatientData.Patient) request.getSession().getAttribute("Patient");
    %>
</c:if>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>

<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_rx" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_rx");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>



<style>
    /* Container for the drug-maintenance-switch */
    .drug-maintenance-switch {
        top: -2px;
        position: relative;
        width: 34px;
        height: 16px;
    }

    /* Hide the default checkbox */
    .drug-maintenance-switch-input {
        display: none;
    }

    /* Style the switch track */
    .drug-maintenance-switch-label {
        position: absolute;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background-color: #dfdfdf;
        cursor: pointer;
        transition: background-color 0.3s;
        border-radius: 5%;
    }

    /* Style the toggle knob, default label to blank if unchecked */
    .drug-maintenance-switch-label::after {
        text-align: center;
        content: '';
        font-size: xx-small;
        font-stretch: extra-expanded;
        display: flex;
        justify-content: center;
        align-items: center;
        position: absolute;
        top: 2px;
        left: 2px;
        width: 12px;
        height: 12px;
        background-color: #FFFFFFAF;
        border-radius: 50%;
        transition: transform 0.3s, content 0.3s, width 0.3s, height 0.3s, border-radius 0.3s;
        box-shadow: 0 1px 6px rgba(0, 0, 0, 0.4);
    }

    /* Change Label to LT (long term) when checked */
    .drug-maintenance-switch-input:checked + .drug-maintenance-switch-label::after {
        content: 'LT'; /* Change label when checked */
        transform: translateX(14px);
        border-radius: 5%;
        width: 16px;
        height: 12px;
        color: white;
        background-color: #1e7e34AF;
    }

    /* Disabled State Styling */
    .drug-maintenance-switch-input:disabled + .drug-maintenance-switch-label {
        background-color: #e0e0e0;
        cursor: not-allowed;
    }

    /* Disabled State: Handle appearance */
    .drug-maintenance-switch-input:disabled:checked + .drug-maintenance-switch-label::after {
        background-color: #1e7e349F;
        transform: translateX(14px);
        content: 'LT';
        width: 16px;
        height: 12px;
        border-radius: 5%;
    }

    .list-drugs td:nth-child(5) {
        word-break: break-word;
        white-space: normal;
    }


</style>

<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    SecurityManager securityManager = new SecurityManager();
    PartialDateDao partialDateDao = SpringUtils.getBean(PartialDateDao.class);

    boolean showall = false;
    if (request.getParameter("show") != null) {
        if (request.getParameter("show").equals("all")) {
            showall = true;
        }
    }

    CodingSystemManager codingSystemManager = SpringUtils.getBean(CodingSystemManager.class);

    boolean integratorEnabled = loggedInInfo.getCurrentFacility().isIntegratorEnabled();
    String annotation_display = CaseManagementNoteLink.DISP_PRESCRIP;
    String heading = request.getParameter("heading");

    if (heading != null) {
%>
<h4 style="margin-bottom:1px;margin-top:3px;"><%=Encode.forHtmlContent(heading)%></h4>
<%}%>
<div class="drugProfileText" style="">
    <table width="100%" cellpadding="3" border="0" class="list-drugs sortable" id="Drug_table<%=Encode.forHtmlContent(heading)%>">
        <tr>
            <th align="left"><b>Entered Date</b></th>
            <th align="left"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgRxDate"/></b></th>
            <th align="left"><b>Days to Exp</b></th>
            <th align="left"><b>LT Med</b></th>
            <th align="left"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgPrescription"/></b></th>
            <%if (securityManager.hasWriteAccess("_rx", roleName$, true)) {%>
            <th align="center" width="35px"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgReprescribe"/></b></th>
            <%if (!OscarProperties.getInstance().getProperty("prescript.delete_drug.hide", "false").equals("true")) {%>
            <th align="center" width="35px"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgDelete"/></b></th>
            <% }
            }
            %>
            <th align="center" width="35px"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgDiscontinue"/></b></th>
            <th align="center" width="35px" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgReason_help"/>"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgReason"/></b></th>
            <th align="center" width="35px"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgPastMed"/></b></th>
            <%if (securityManager.hasWriteAccess("_rx", roleName$, true)) {%>
            <th align="center" width="15px">&nbsp;</th>
            <% } %>
            <th align="center"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgLocationPrescribed"/></th>
            <th align="center" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgHideCPP_help"/>"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgHideCPP"/></th>
            <%if (OscarProperties.getInstance().getProperty("rx.enable_internal_dispensing", "false").equals("true")) {%>
            <th align="center"><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.msgDispense"/></th>
            <%} %>
            <th align="center"></th>
        </tr>

        <%
            List<Drug> prescriptDrugs = null;
            CaseManagementManager caseManagementManager = (CaseManagementManager) SpringUtils.getBean(CaseManagementManager.class);

            if (showall) {
                prescriptDrugs = caseManagementManager.getPrescriptions(loggedInInfo, patient.getDemographicNo(), showall);
            } else {
                prescriptDrugs = caseManagementManager.getCurrentPrescriptions(patient.getDemographicNo());
            }

            DrugReasonDao drugReasonDao = (DrugReasonDao) SpringUtils.getBean(DrugReasonDao.class);

            DrugDispensingManager drugDispensingManager = SpringUtils.getBean(DrugDispensingManager.class);
            List<String> reRxDrugList = bean.getReRxDrugIdList();
            Collections.sort(prescriptDrugs, Drug.START_DATE_COMPARATOR);

            long now = System.currentTimeMillis();
            long month = 1000L * 60L * 60L * 24L * 30L;
            for (int x = 0; x < prescriptDrugs.size(); x++) {
                Drug prescriptDrug = prescriptDrugs.get(x);
                boolean isPrevAnnotation = false;
                String styleColor = "";
                //test for previous note
                HttpSession se = request.getSession();
                Integer tableName = caseManagementManager.getTableNameByDisplay(annotation_display);

                CaseManagementNoteLink cml = null;
                CaseManagementNote p_cmn = null;

                if (prescriptDrug.getRemoteFacilityId() != null) {
                    cml = caseManagementManager.getLatestLinkByTableId(tableName, Long.parseLong(prescriptDrug.getId().toString()));
                }

                if (cml != null) {
                    p_cmn = caseManagementManager.getNote(cml.getNoteId().toString());
                }
                if (p_cmn != null) {
                    isPrevAnnotation = true;
                }

                if (request.getParameter("status") != null) { //TODO: Redo this in a better way
                    String stat = request.getParameter("status");
                    if (stat.equals("active") && !prescriptDrug.isLongTerm() && !prescriptDrug.isCurrent()) {
                        continue;
                    } else if (stat.equals("inactive") && prescriptDrug.isCurrent()) {
                        continue;
                    }
                }
                if (request.getParameter("longTermOnly") != null && request.getParameter("longTermOnly").equals("true")) {
                    if (!prescriptDrug.isLongTerm()) {
                        continue;
                    }
                }

                if (request.getParameter("longTermOnly") != null && request.getParameter("longTermOnly").equals("acute")) {
                    if (prescriptDrug.isLongTerm()) {
                        continue;
                    }
                }
                if (request.getParameter("drugLocation") != null && request.getParameter("drugLocation").equals("external")) {
                    if (!prescriptDrug.isExternal())
                        continue;
                }
//add all long term med drugIds to an array.
                styleColor = getClassColour(prescriptDrug, now, month);
                String specialText = prescriptDrug.getSpecial();
                specialText = specialText == null ? "" : specialText.replace("\n", " ");
                Integer prescriptIdInt = prescriptDrug.getId();
                String bn = prescriptDrug.getBrandName();

                boolean startDateUnknown = prescriptDrug.getStartDateUnknown();
        %>
        <tr>
            <td valign="top"><a id="createDate_<%=prescriptIdInt%>"   <%=styleColor%>
                                href="oscarRx/StaticScript2.jsp?regionalIdentifier=<%=prescriptDrug.getRegionalIdentifier()%>&amp;cn=<%=response.encodeURL(prescriptDrug.getCustomName())%>&amp;bn=<%=response.encodeURL(bn)%>&amp;atc=<%=prescriptDrug.getAtc()%>"><%=UtilDateUtilities.DateToString(prescriptDrug.getCreateDate())%>
            </a></td>
            <td valign="top">
                <% if (startDateUnknown) { %>

                <% } else {
                    String startDate = UtilDateUtilities.DateToString(prescriptDrug.getRxDate());
                    startDate = partialDateDao.getDatePartial(startDate, PartialDate.DRUGS, prescriptDrug.getId(), PartialDate.DRUGS_STARTDATE);
                %>
                <a id="rxDate_<%=prescriptIdInt%>"   <%=styleColor%>
                   href="oscarRx/StaticScript2.jsp?regionalIdentifier=<%=prescriptDrug.getRegionalIdentifier()%>&amp;cn=<%=response.encodeURL(prescriptDrug.getCustomName())%>&amp;bn=<%=response.encodeURL(bn)%>"><%=startDate%>
                </a>
                <% } %>
            </td>
            <td valign="top">
                <% if (startDateUnknown) { %>

                <% } else { %>
                <%=prescriptDrug.daysToExpire()%>
                <% } %>
            </td>
            <td valign="top">
                <%
                    if (prescriptDrug.isLongTerm()) {
                %>
                *
                <%
                    } else {
                        if (prescriptDrug.getRemoteFacilityId() == null) {
                            if (securityManager.hasWriteAccess("_rx", roleName$, true)) {
                %>
                <a id="notLongTermDrug_<%=prescriptIdInt%>"
                   title="<fmt:setBundle basename='oscarResources'/><fmt:message key='oscarRx.Prescription.changeDrugLongTerm'/>"
                   onclick="changeLt('<%=prescriptIdInt%>');" href="javascript:void(0);">
                    L
                </a>
                <% 
                            } else { 
                %>
                <span style="color:blue">L</span>
                <% 
                            }
                        }
                    } 
                %>
                <div class="drug-maintenance-switch" style="display: flex; align-items: baseline;">
                    <% String drugMaintenanceSwitch = "drugMaintenanceSwitch_" + prescriptIdInt + Math.abs(new Random().nextInt(10001)); %>
                    <input id="<%=drugMaintenanceSwitch%>" type="checkbox" name="checkBox_<%=prescriptIdInt%>"
                           class="drug-maintenance-switch-input"
                           onclick="changeLt(this, '<%=prescriptIdInt%>');"
                            <% if (!securityManager.hasWriteAccess("_rx", roleName$, true)) {%> disabled <%}%>
                            <% if (prescriptDrug.isLongTerm()) {%> checked <%}%> />
                    <label id="drugMaintenanceSwitchLbl_<%=prescriptIdInt%>" for="<%=drugMaintenanceSwitch%>" class="drug-maintenance-switch-label">

                    </label>
                </div>
            </td>
            <%
                //display comment as tooltip if not null - simply using the TITLE attr
                String xComment = prescriptDrug.getComment();
                String tComment = "";
                if (xComment != null) {
                    tComment = "TITLE='" + xComment + " '";
                }

            %>
            <td valign="top"><a id="prescrip_<%=prescriptIdInt%>" <%=styleColor%>
                                href="oscarRx/StaticScript2.jsp?regionalIdentifier=<%=prescriptDrug.getRegionalIdentifier()%>&amp;cn=<%=response.encodeURL(prescriptDrug.getCustomName())%>&amp;bn=<%=response.encodeURL(bn)%>&amp;atc=<%=prescriptDrug.getAtc()%>"   <%=tComment%>   ><%=RxPrescriptionData.getFullOutLine(prescriptDrug.getSpecial()).replaceAll(";", " ")%>
            </a></td>
            <%
                if (securityManager.hasWriteAccess("_rx", roleName$, true)) {
            %>
            <td width="20px" align="center" valign="top">
                <%if (prescriptDrug.getRemoteFacilityName() == null) {%>
                <div style="display: flex; align-items: center;">
                    <% String cbxId = "reRxCheckBox_" + prescriptIdInt; %>
                    <input id="<%=cbxId%>" type=CHECKBOX
                           onclick="updateReRxStatusForPrescribedDrug(this, <%=prescriptIdInt%>)"
                           <%if(reRxDrugList.contains(prescriptIdInt.toString())){%>checked<%}%>
                           name="checkBox_<%=prescriptIdInt%>">
                    <label id="reRx_<%=prescriptIdInt%>" for="<%=cbxId%>">ReRx</label>
                </div>
                <%} else {%>
                <form action="<%=request.getContextPath()%>/oscarRx/searchDrug.do" method="post">
                    <input type="hidden" name="demographicNo" value="<%=patient.getDemographicNo()%>"/>
                    <input type="hidden" name="searchString" value="<%=getName(prescriptDrug)%>"/>
                    <input type="submit" class="ControlPushButton" value="Search to Re-prescribe"/>
                </form>
                <%}%>
            </td>
            <%if (!OscarProperties.getInstance().getProperty("prescript.delete_drug.hide", "false").equals("true")) { %>
            <td width="20px" align="center" valign="top">
                <%if (prescriptDrug.getRemoteFacilityName() == null) {%>
                <a id="del_<%=prescriptIdInt%>" name="delete" <%=styleColor%> href="javascript:void(0);"
                   onclick="Delete2(this);">Del</a>
                <%}%>
            </td>

            <% }
            }
            %>
            <td width="20px" align="center" valign="top">
                <%
                    if (!prescriptDrug.isDiscontinued()) {
                        if (prescriptDrug.getRemoteFacilityId() == null) {

                            if (securityManager.hasWriteAccess("_rx", roleName$, true)) {

                %>
                <a id="discont_<%=prescriptIdInt%>" href="javascript:void(0);"
                   onclick="Discontinue(event,this);" <%=styleColor%> >Discon</a>
                <% }
                }
                } else {%>
                <%=prescriptDrug.getArchivedReason()%>
                <%}%>
            </td>
            <%-- DRUG REASON --%>
            <td style="vertical-align:top;">
                <%
                    List<DrugReason> drugReasons = drugReasonDao.getReasonsForDrugID(prescriptDrug.getId(), true);

                    if (prescriptDrug.getRemoteFacilityId() == null && securityManager.hasWriteAccess("_rx", roleName$, true)) {
                %>
                <a href="javascript:void(0);"
                   onclick="popupRxReasonWindow(<%=patient.getDemographicNo()%>,<%=prescriptIdInt%>);"
                   title="<%=displayDrugReason(codingSystemManager,drugReasons,true) %>">
                    <%
                        }
                    %>
                    <%=StringUtils.maxLenString(displayDrugReason(codingSystemManager, drugReasons, false), 4, 3, StringUtils.ELLIPSIS)%>
                    <%
                        if (prescriptDrug.getRemoteFacilityId() == null && securityManager.hasWriteAccess("_rx", roleName$, true)) {
                    %>
                </a>
                <%
                    }
                %>
            </td>
            <%-- END DRUG REASON --%>
            <%
                Boolean past_med = prescriptDrug.getPastMed();
            %>

            <td align="center" valign="top">
                <% if (past_med == null) { %>
                unk
                <% } else if (past_med) { %>
                yes
                <% } else { %>
                no
                <% } %>
            </td>

            <%if (securityManager.hasWriteAccess("_rx", roleName$, true)) {%>
            <td width="10px" align="center" valign="top">
                <%
                    if (prescriptDrug.getRemoteFacilityId() == null) {
                %>
                <a href="javascript:void(0);" title="Annotation"
                   onclick="window.open('<%= request.getContextPath() %>/annotation/annotation.jsp?display=<%=annotation_display%>&amp;table_id=<%=prescriptIdInt%>&amp;demo=<%=bean.getDemographicNo()%>&amp;drugSpecial=<%=StringEscapeUtils.escapeEcmaScript(specialText)%>','anwin','width=400,height=500');">
                    <%if (!isPrevAnnotation) {%> <img src="<%= request.getContextPath() %>/images/notes.gif" alt="rxAnnotation" height="16"
                                                      width="13" border="0"><%} else {%><img
                        src="<%= request.getContextPath() %>/images/filledNotes.gif" height="16" width="13" alt="rxFilledNotes" border="0"> <%}%></a>
                <%
                    }
                %>
            </td>
            <% } %>

            <td width="10px" align="center" valign="top">
                <%
                    if (prescriptDrug.getRemoteFacilityName() != null) { %>
                <span class="external"><%=prescriptDrug.getRemoteFacilityName()%></span>
                <%} else if (prescriptDrug.getOutsideProviderName() != null && !prescriptDrug.getOutsideProviderName().equals("")) {%>
                <span class="external"><%=prescriptDrug.getOutsideProviderName()%></span>
                <%} else {%>
                local
                <%}%>


            </td>

            <td align="center" valign="top">
                <%
                    boolean hideCpp = prescriptDrug.getHideFromCpp();
                    String checked = "";
                    if (hideCpp) {
                        checked = "checked=\"checked\"";
                    }
                %>
                <input type="checkbox" id="hidecpp_<%=prescriptIdInt%>" <%=checked%>/>
            </td>

            <%if (OscarProperties.getInstance().getProperty("rx.enable_internal_dispensing", "false").equals("true")) {%>
            <td align="center" valign="top">
                <%
                    if (prescriptDrug.getDispenseInternal() != null && prescriptDrug.getDispenseInternal() == true) {
                        if (securityManager.hasWriteAccess("_dispensing", roleName$, true)) {
                            String dispensingStatus = drugDispensingManager.getStatus(prescriptDrug.getId());

                %>
                <a href="javascript:void(0)"
                   onclick="javascript:popupWindow(720,700,'<%=request.getContextPath()%>/oscarRx/Dispense.do?method=view&id=<%=prescriptDrug.getId()%>','Dispense<%=prescriptIdInt %>'); return false;">Dispense
                    (<%=dispensingStatus%>)</a>
                <%
                        }
                    }
                %>
            </td>
            <% } %>

            <td nowrap="nowrap" align="center" valign="top">
                <%if (!(prescriptDrugs.get(prescriptDrugs.size() - 1) == prescriptDrug)) {%>
                <img border="0" src="<%=request.getContextPath()%>/images/icon_down_sort_arrow.png"
                     onclick="moveDrugDown(<%=prescriptDrug.getId() %>,<%=prescriptDrugs.get(x+1).getId() %>,<%=prescriptDrug.getDemographicId()%>);return false;"/>
                <% } %>
                <%if (!(prescriptDrugs.get(0) == prescriptDrug)) {%>
                <img border="0" src="<%=request.getContextPath()%>/images/icon_up_sort_arrow.png"
                     onclick="moveDrugUp(<%=prescriptDrug.getId() %>,<%=prescriptDrugs.get(x-1).getId() %>,<%=prescriptDrug.getDemographicId()%>);return false;"/>
                <%} %>
            </td>
        </tr>
        <script>
            Event.observe('hidecpp_<%=prescriptIdInt%>', 'change', function (event) {
                var val = $('hidecpp_<%=prescriptIdInt%>').checked;
                new Ajax.Request('<c:out value="${ctx}"/>/oscarRx/hideCpp.do?method=update&prescriptId=<%=prescriptIdInt%>&value=' + val, {
                    method: 'get',
                    onSuccess: function (transport) {
                    }
                });

            });

        </script>
        <%}%>
    </table>

</div>
<br>


<script type="text/javascript">
    sortables_init();
</script>
<%!

    String getName(Drug prescriptDrug) {
        String searchString = prescriptDrug.getBrandName();
        if (searchString == null) {
            searchString = prescriptDrug.getCustomName();
        }
        if (searchString == null) {
            searchString = prescriptDrug.getRegionalIdentifier();
        }
        if (searchString == null) {
            searchString = prescriptDrug.getSpecial();
        }
        return searchString;
    }

    String getClassColour(Drug drug, long referenceTime, long durationToSoon) {
        StringBuilder sb = new StringBuilder("class=\"");

        if (!drug.isLongTerm() && (drug.isCurrent() && drug.getEndDate() != null && (drug.getEndDate().getTime() - referenceTime <= durationToSoon))) {
            sb.append("expireInReference ");
        }

        if ((drug.isCurrent() && !drug.isArchived()) || drug.isLongTerm()) {
            sb.append("currentDrug ");
        }

        if (drug.isArchived()) {
            sb.append("archivedDrug ");
        }

        if (!drug.isLongTerm() && !drug.isCurrent()) {
            sb.append("expiredDrug ");
        }

        if (drug.isLongTerm()) {
            sb.append("longTermMed ");
        }

        if (drug.isDiscontinued()) {
            sb.append("discontinued ");
        }

        if (drug.isDeleted()) {
            sb.append("deleted ");

        }

        if (drug.getOutsideProviderName() != null && !drug.getOutsideProviderName().equals("")) {
            sb = new StringBuilder("class=\"");
            sb.append("external ");
        }
        if (drug.getRemoteFacilityName() != null) {
            sb = new StringBuilder("class=\"");
            sb.append("external ");
        }
        String retval = sb.toString();

        if (retval.equals("class=\"")) {
            return "";
        }

        return retval.substring(0, retval.length()) + "\"";

    }

%><%!

    String displayDrugReason(CodingSystemManager codingSystemManager, List<DrugReason> drugReasons, boolean title) {
        StringBuilder sb = new StringBuilder();
        boolean multiLoop = false;

        for (DrugReason drugReason : drugReasons) {
            if (multiLoop) {
                sb.append(", ");
            }
            String codeDescr = null;
            if (drugReason.getCodingSystem() != null && !drugReason.getCodingSystem().isEmpty()) {
                codeDescr = codingSystemManager.getCodeDescription(drugReason.getCodingSystem(), drugReason.getCode());
            }
            if (codeDescr != null) {
                sb.append(StringEscapeUtils.escapeHtml4(codeDescr));
            } else {
                sb.append(drugReason.getCode());
            }
            multiLoop = true;
        }
        if (sb.toString().equals("")) {
            if (title) {
                return "No diseases are associated with this medication";
            }
            return "+";
        }

        return sb.toString();
    }

%>
