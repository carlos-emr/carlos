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

<%@ include file="/WEB-INF/jsp/casemgmt/taglibs.jsp" %>
<fmt:setBundle basename="oscarResources"/>
<%@ page import="java.util.ResourceBundle" %>
<%@ page import="io.github.carlos_emr.carlos.provider.web.CppPreferencesUIBean" %>
<%
    String curUser_no;
    curUser_no = (String) session.getAttribute("user");
    ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", request.getLocale());

    boolean bFirstLoad = request.getAttribute("status") == null;

    CppPreferencesUIBean bean = (CppPreferencesUIBean) request.getAttribute("bean");
%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
"http://www.w3.org/TR/html4/loose.dtd">
<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>
<html>
    <head>
        <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <title><fmt:message key="provider.cppPrefs"/></title>

        <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/encounter/encounterStyles.css">
        <link rel="stylesheet" type="text/css" media="all" href="${e:forHtmlAttribute(ctx)}/share/calendar/calendar.css"
              title="win2k-cold-1">
        <script type="text/javascript">

            function validate() {
                //make sure none of the positions are duplicates
                if (getTotalPos("R1I1") > 1) {
                    alert("You have a duplicate for Row 1, Column 1..Please fix.");
                    return false;
                }
                if (getTotalPos("R1I2") > 1) {
                    alert("You have a duplicate for Row 1, Column 2..Please fix.");
                    return false;
                }
                if (getTotalPos("R2I1") > 1) {
                    alert("You have a duplicate for Row 2, Column 1..Please fix.");
                    return false;
                }
                if (getTotalPos("R2I2") > 1) {
                    alert("You have a duplicate for Row 2, Column 2..Please fix.");
                    return false;
                }
                return true;
            }

            function getTotalPos(value) {
                var total = 0;
                jQuery("select").each(function () {
                    if (jQuery(this).val() == value) {
                        total++;
                    }
                });
                return total;
            }
        </script>

    </head>

    <body class="BodyStyle" vlink="#0000FF">

    <table class="MainTable" id="scrollNumber1" name="encounterTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn"><fmt:message key="provider.setNoteStaleDate.msgPrefs"/></td>
            <td style="color: white" class="MainTableTopRowRightColumn"><fmt:message key="provider.cppPrefs"/></td>
        </tr>
        <tr>
            <td class="MainTableLeftColumn">&nbsp;</td>
            <td class="MainTableRightColumn">
                <!-- form starts here -->
                <form action="${e:forHtmlAttribute(ctx)}/provider/CppPreferences?method=save" method="post"
                      onSubmit="return validate();">
                    <table width="100%" border="1">
                        <tr>
                            <td colspan="2">
                                <%=CppPreferencesUIBean.getCheckbox(bundle.getString("provider.cppPrefs.enableCustomEChart"), CppPreferencesUIBean.ENABLE, bean.getEnable()) %>
                            </td>

                        </tr>
                        <tr>
                            <td><fmt:message key="encounter.socHistory.title"/></td>
                            <td>
                                <select name="<%=CppPreferencesUIBean.SOCIAL_HISTORY_POS%>">
                                    <%=CppPreferencesUIBean.getPositionSelect(bean.getSocialHxPosition()) %>
                                </select>
                                <br/>
                                <%=CppPreferencesUIBean.getCheckbox(bundle.getString("provider.cppPrefs.showStartDate"), CppPreferencesUIBean.SOC_HX_START_DATE, bean.getSocialHxStartDate()) %>
                                <%=CppPreferencesUIBean.getCheckbox(bundle.getString("provider.cppPrefs.showResolutionDate"), CppPreferencesUIBean.SOC_HX_RES_DATE, bean.getSocialHxResDate()) %>
                            </td>
                        </tr>

                        <tr>
                            <td><fmt:message key="encounter.medHistory.title"/></td>
                            <td>
                                <select name="<%=CppPreferencesUIBean.MEDICAL_HISTORY_POS%>">
                                    <%=CppPreferencesUIBean.getPositionSelect(bean.getMedicalHxPosition()) %>
                                </select>
                                <br/>
                                <%=CppPreferencesUIBean.getCheckbox(bundle.getString("provider.cppPrefs.showStartDate"), CppPreferencesUIBean.MED_HX_START_DATE, bean.getMedHxStartDate()) %>
                                <%=CppPreferencesUIBean.getCheckbox(bundle.getString("provider.cppPrefs.showResolutionDate"), CppPreferencesUIBean.MED_HX_RES_DATE, bean.getMedHxResDate()) %>
                                <%=CppPreferencesUIBean.getCheckbox(bundle.getString("provider.cppPrefs.showTreatment"), CppPreferencesUIBean.MED_HX_TREATMENT, bean.getMedHxTreatment()) %>
                                <%=CppPreferencesUIBean.getCheckbox(bundle.getString("provider.cppPrefs.showProcedureDate"), CppPreferencesUIBean.MED_HX_PROCEDURE_DATE, bean.getMedHxProcedureDate()) %>
                            </td>
                        </tr>

                        <tr>
                            <td><fmt:message key="encounter.onGoing.title"/></td>
                            <td>
                                <select name="<%=CppPreferencesUIBean.ONGOING_CONCERNS_POS%>">
                                    <%=CppPreferencesUIBean.getPositionSelect(bean.getOngoingConcernsPosition()) %>
                                </select>
                                <br/>
                                <%=CppPreferencesUIBean.getCheckbox(bundle.getString("provider.cppPrefs.showStartDate"), CppPreferencesUIBean.ONGOING_START_DATE, bean.getOngoingConcernsStartDate()) %>
                                <%=CppPreferencesUIBean.getCheckbox(bundle.getString("provider.cppPrefs.showResolutionDate"), CppPreferencesUIBean.ONGOING_RES_DATE, bean.getOngoingConcernsResDate()) %>
                                <%=CppPreferencesUIBean.getCheckbox(bundle.getString("provider.cppPrefs.showProblemStatus"), CppPreferencesUIBean.ONGOING_PROBLEM_STATUS, bean.getOngoingConcernsProblemStatus()) %>
                            </td>
                        </tr>

                        <tr>
                            <td><fmt:message key="encounter.reminders.title"/></td>
                            <td>
                                <select name="<%=CppPreferencesUIBean.REMINDERS_POS%>">
                                    <%=CppPreferencesUIBean.getPositionSelect(bean.getRemindersPosition()) %>
                                </select>
                                <br/>
                                <%=CppPreferencesUIBean.getCheckbox(bundle.getString("provider.cppPrefs.showStartDate"), CppPreferencesUIBean.REMINDERS_START_DATE, bean.getRemindersStartDate()) %>
                                <%=CppPreferencesUIBean.getCheckbox(bundle.getString("provider.cppPrefs.showResolutionDate"), CppPreferencesUIBean.REMINDERS_RES_DATE, bean.getRemindersResDate()) %>
                            </td>
                        </tr>

                        <tr>
                            <td><fmt:message key="encounter.Preventions.title"/></td>
                            <td>
                                <select name="<%=CppPreferencesUIBean.PREVENTIONS_DSP%>">
                                    <%=CppPreferencesUIBean.getDisplaySelect(bean.getPreventionsDisplay()) %>
                                </select>
                            </td>
                        </tr>

                        <tr>
                            <td><fmt:message key="provider.cppPrefs.diseaseRegistry"/></td>
                            <td>
                                <select name="<%=CppPreferencesUIBean.DX_REGISTRY_DSP%>">
                                    <%=CppPreferencesUIBean.getDisplaySelect(bean.getDxRegistryDisplay()) %>
                                </select>
                            </td>
                        </tr>

                        <tr>
                            <td><fmt:message key="encounter.Index.msgForms"/></td>
                            <td>
                                <select name="<%=CppPreferencesUIBean.FORMS_DSP%>">
                                    <%=CppPreferencesUIBean.getDisplaySelect(bean.getFormsDisplay()) %>
                                </select>
                            </td>
                        </tr>

                        <tr>
                            <td><fmt:message key="global.eForms"/></td>
                            <td>
                                <select name="<%=CppPreferencesUIBean.EFORMS_DSP%>">
                                    <%=CppPreferencesUIBean.getDisplaySelect(bean.getEformsDisplay()) %>
                                </select>
                            </td>
                        </tr>

                        <tr>
                            <td><fmt:message key="encounter.Index.msgDocuments"/></td>
                            <td>
                                <select name="<%=CppPreferencesUIBean.DOCUMENTS_DSP%>">
                                    <%=CppPreferencesUIBean.getDisplaySelect(bean.getDocumentsDisplay()) %>
                                </select>
                            </td>
                        </tr>

                        <tr>
                            <td><fmt:message key="encounter.LeftNavBar.Labs"/></td>
                            <td>
                                <select name="<%=CppPreferencesUIBean.LABS_DSP%>">
                                    <%=CppPreferencesUIBean.getDisplaySelect(bean.getLabsDisplay()) %>
                                </select>
                            </td>
                        </tr>

                        <tr>
                            <td><fmt:message key="encounter.Index.measurements"/></td>
                            <td>
                                <select name="<%=CppPreferencesUIBean.MEASUREMENTS_DSP%>">
                                    <%=CppPreferencesUIBean.getDisplaySelect(bean.getMeasurementsDisplay()) %>
                                </select>
                            </td>
                        </tr>

                        <tr>
                            <td><fmt:message key="encounter.oscarConsultationRequest.ConsultChoice.msgTitle"/></td>
                            <td>
                                <select name="<%=CppPreferencesUIBean.CONSULTATIONS_DSP%>">
                                    <%=CppPreferencesUIBean.getDisplaySelect(bean.getConsultationsDisplay()) %>
                                </select>
                            </td>
                        </tr>

                        <tr>
                            <td><fmt:message key="encounter.Index.msgHRMDocuments"/></td>
                            <td>
                                <select name="<%=CppPreferencesUIBean.HRM_DSP%>">
                                    <%=CppPreferencesUIBean.getDisplaySelect(bean.getHrmDisplay()) %>
                                </select>
                            </td>
                        </tr>

                        <tr>
                            <td><fmt:message key="encounter.NavBar.Allergy"/></td>
                            <td>
                                <select name="<%=CppPreferencesUIBean.ALLERGIES_DSP%>">
                                    <%=CppPreferencesUIBean.getDisplaySelect(bean.getAllergiesDisplay()) %>
                                </select>
                                <br/>
                                <%=CppPreferencesUIBean.getCheckbox(bundle.getString("provider.cppPrefs.showStartDate"), CppPreferencesUIBean.ALLERGY_START_DATE, bean.getAllergyStartDate()) %>
                                <%=CppPreferencesUIBean.getCheckbox(bundle.getString("provider.cppPrefs.showSeverity"), CppPreferencesUIBean.ALLERGY_SEVERITY, bean.getAllergySeverity()) %>

                            </td>
                        </tr>

                        <tr>
                            <td><fmt:message key="encounter.NavBar.Medications"/></td>
                            <td>
                                <select name="<%=CppPreferencesUIBean.MEDICATIONS_DSP%>">
                                    <%=CppPreferencesUIBean.getDisplaySelect(bean.getMedicationsDisplay()) %>
                                </select>
                                <br/>
                                <%=CppPreferencesUIBean.getCheckbox(bundle.getString("provider.cppPrefs.showStartDate"), CppPreferencesUIBean.MEDICATION_START_DATE, bean.getMedicationStartDate()) %>
                                <%=CppPreferencesUIBean.getCheckbox(bundle.getString("provider.cppPrefs.showEndDate"), CppPreferencesUIBean.MEDICATION_END_DATE, bean.getMedicationEndDate()) %>
                                <%=CppPreferencesUIBean.getCheckbox(bundle.getString("provider.cppPrefs.showQty"), CppPreferencesUIBean.MEDICATION_QTY, bean.getMedicationQty()) %>
                                <%=CppPreferencesUIBean.getCheckbox(bundle.getString("provider.cppPrefs.showRepeats"), CppPreferencesUIBean.MEDICATION_REPEATS, bean.getMedicationRepeats()) %>

                            </td>
                        </tr>

                        <tr>
                            <td><fmt:message key="encounter.NavBar.OtherMeds"/></td>
                            <td>
                                <select name="<%=CppPreferencesUIBean.OTHER_MEDS_DSP%>">
                                    <%=CppPreferencesUIBean.getDisplaySelect(bean.getOtherMedsDisplay()) %>
                                </select>
                                <br/>
                                <%=CppPreferencesUIBean.getCheckbox(bundle.getString("provider.cppPrefs.showStartDate"), CppPreferencesUIBean.OTHER_MEDS_START_DATE, bean.getOtherMedsStartDate()) %>
                                <%=CppPreferencesUIBean.getCheckbox(bundle.getString("provider.cppPrefs.showResolutionDate"), CppPreferencesUIBean.OTHER_MEDS_RES_DATE, bean.getOtherMedsResDate()) %>
                            </td>
                        </tr>

                        <tr>
                            <td><fmt:message key="encounter.riskFactors.title"/></td>
                            <td>
                                <select name="<%=CppPreferencesUIBean.RISK_FACTORS_DSP%>">
                                    <%=CppPreferencesUIBean.getDisplaySelect(bean.getRiskFactorsDisplay()) %>
                                </select>
                                <br/>
                                <%=CppPreferencesUIBean.getCheckbox(bundle.getString("provider.cppPrefs.showStartDate"), CppPreferencesUIBean.RISK_FACTORS_START_DATE, bean.getRiskFactorsStartDate()) %>
                                <%=CppPreferencesUIBean.getCheckbox(bundle.getString("provider.cppPrefs.showResolutionDate"), CppPreferencesUIBean.RISK_FACTORS_RES_DATE, bean.getRiskFactorsResDate()) %>
                            </td>

                        </tr>

                        <tr>
                            <td><fmt:message key="encounter.famHistory.title"/></td>
                            <td>
                                <select name="<%=CppPreferencesUIBean.FAMILY_HISTORY_DSP%>">
                                    <%=CppPreferencesUIBean.getDisplaySelect(bean.getFamilyHxDisplay()) %>
                                </select>
                                <br/>
                                <%=CppPreferencesUIBean.getCheckbox(bundle.getString("provider.cppPrefs.showStartDate"), CppPreferencesUIBean.FAMILY_HISTORY_START_DATE, bean.getFamilyHistoryStartDate()) %>
                                <%=CppPreferencesUIBean.getCheckbox(bundle.getString("provider.cppPrefs.showResolutionDate"), CppPreferencesUIBean.FAMILY_HISTORY_RES_DATE, bean.getFamilyHistoryResDate()) %>
                                <%=CppPreferencesUIBean.getCheckbox(bundle.getString("provider.cppPrefs.showTreatment"), CppPreferencesUIBean.FAMILY_HISTORY_TREATMENT, bean.getFamilyHistoryTreatment()) %>
                                <%=CppPreferencesUIBean.getCheckbox(bundle.getString("provider.cppPrefs.showRelationship"), CppPreferencesUIBean.FAMILY_HISTORY_RELATIONSHIP, bean.getFamilyHistoryRelationship()) %>

                            </td>
                        </tr>

                        <tr>
                            <td><fmt:message key="encounter.NavBar.unresolvedIssues"/></td>
                            <td>
                                <select name="<%=CppPreferencesUIBean.UNRESOLVED_ISSUES_DSP%>">
                                    <%=CppPreferencesUIBean.getDisplaySelect(bean.getUnresolvedIssuesDisplay()) %>
                                </select>
                            </td>
                        </tr>

                        <tr>
                            <td><fmt:message key="encounter.NavBar.resolvedIssues"/></td>
                            <td>
                                <select name="<%=CppPreferencesUIBean.RESOLVED_ISSUES_DSP%>">
                                    <%=CppPreferencesUIBean.getDisplaySelect(bean.getResolvedIssuesDisplay()) %>
                                </select>
                            </td>
                        </tr>

                        <tr>
                            <td><fmt:message key="global.episode"/></td>
                            <td>
                                <select name="<%=CppPreferencesUIBean.EPISODES_DSP%>">
                                    <%=CppPreferencesUIBean.getDisplaySelect(bean.getEpisodesDisplay()) %>
                                </select>
                            </td>
                        </tr>
                    </table>
                    <input type="submit" value="<fmt:message key='provider.pref.btnSave'/>"/>
                </form>
                <!-- end of form -->
            </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn"></td>
            <td class="MainTableBottomRowRightColumn"></td>
        </tr>
    </table>
    </body>
</html>
