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

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ page import="io.github.carlos_emr.carlos.provider.web.CppPreferencesUIBean" %>
<%
    String curUser_no = (String) session.getAttribute("user");
    boolean bFirstLoad = request.getAttribute("status") == null;
    CppPreferencesUIBean bean = (CppPreferencesUIBean) request.getAttribute("bean");
%>
<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>

<!DOCTYPE html>
<html lang="en">
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.cppPrefs"/></title>

        <script>
            function validate() {
                var positions = ["R1I1", "R1I2", "R2I1", "R2I2"];
                var labels = ["Row 1, Column 1", "Row 1, Column 2", "Row 2, Column 1", "Row 2, Column 2"];
                for (var i = 0; i < positions.length; i++) {
                    if (getTotalPos(positions[i]) > 1) {
                        alert("You have a duplicate for " + labels[i] + "..Please fix.");
                        return false;
                    }
                }
                return true;
            }

            function getTotalPos(value) {
                var total = 0;
                $("select").each(function () {
                    if ($(this).val() == value) {
                        total++;
                    }
                });
                return total;
            }
        </script>
    </head>

    <body>
    <div class="container">

        <div class="page-header-bar">
            <h4 class="page-header-title">
                <i class="fas fa-sliders-h page-header-icon"></i>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.cppPrefs"/>
            </h4>
        </div>

        <form action="<c:out value="${ctx}"/>/provider/CppPreferences.do?method=save" method="post"
              onsubmit="return validate();" class="mt-3">

            <table class="table table-sm table-bordered" style="max-width:700px;">
                <tbody>
                    <tr class="table-light">
                        <td colspan="2">
                            <%=CppPreferencesUIBean.getCheckbox("Enable Custom EChart", CppPreferencesUIBean.ENABLE, bean.getEnable()) %>
                        </td>
                    </tr>
                    <tr>
                        <td style="width:180px;">Social History</td>
                        <td>
                            <select name="<%=CppPreferencesUIBean.SOCIAL_HISTORY_POS%>" class="form-select form-select-sm d-inline-block" style="width:auto;">
                                <%=CppPreferencesUIBean.getPositionSelect(bean.getSocialHxPosition()) %>
                            </select>
                            <br>
                            <%=CppPreferencesUIBean.getCheckbox("Show Start Date", CppPreferencesUIBean.SOC_HX_START_DATE, bean.getSocialHxStartDate()) %>
                            <%=CppPreferencesUIBean.getCheckbox("Show Resolution Date", CppPreferencesUIBean.SOC_HX_RES_DATE, bean.getSocialHxResDate()) %>
                        </td>
                    </tr>
                    <tr>
                        <td>Medical History</td>
                        <td>
                            <select name="<%=CppPreferencesUIBean.MEDICAL_HISTORY_POS%>" class="form-select form-select-sm d-inline-block" style="width:auto;">
                                <%=CppPreferencesUIBean.getPositionSelect(bean.getMedicalHxPosition()) %>
                            </select>
                            <br>
                            <%=CppPreferencesUIBean.getCheckbox("Show Start Date", CppPreferencesUIBean.MED_HX_START_DATE, bean.getMedHxStartDate()) %>
                            <%=CppPreferencesUIBean.getCheckbox("Show Resolution Date", CppPreferencesUIBean.MED_HX_RES_DATE, bean.getMedHxResDate()) %>
                            <%=CppPreferencesUIBean.getCheckbox("Show Treatment", CppPreferencesUIBean.MED_HX_TREATMENT, bean.getMedHxTreatment()) %>
                            <%=CppPreferencesUIBean.getCheckbox("Show Procedure Date", CppPreferencesUIBean.MED_HX_PROCEDURE_DATE, bean.getMedHxProcedureDate()) %>
                        </td>
                    </tr>
                    <tr>
                        <td>Ongoing Concerns</td>
                        <td>
                            <select name="<%=CppPreferencesUIBean.ONGOING_CONCERNS_POS%>" class="form-select form-select-sm d-inline-block" style="width:auto;">
                                <%=CppPreferencesUIBean.getPositionSelect(bean.getOngoingConcernsPosition()) %>
                            </select>
                            <br>
                            <%=CppPreferencesUIBean.getCheckbox("Show Start Date", CppPreferencesUIBean.ONGOING_START_DATE, bean.getOngoingConcernsStartDate()) %>
                            <%=CppPreferencesUIBean.getCheckbox("Show Resolution Date", CppPreferencesUIBean.ONGOING_RES_DATE, bean.getOngoingConcernsResDate()) %>
                            <%=CppPreferencesUIBean.getCheckbox("Show Problem Status", CppPreferencesUIBean.ONGOING_PROBLEM_STATUS, bean.getOngoingConcernsProblemStatus()) %>
                        </td>
                    </tr>
                    <tr>
                        <td>Reminders</td>
                        <td>
                            <select name="<%=CppPreferencesUIBean.REMINDERS_POS%>" class="form-select form-select-sm d-inline-block" style="width:auto;">
                                <%=CppPreferencesUIBean.getPositionSelect(bean.getRemindersPosition()) %>
                            </select>
                            <br>
                            <%=CppPreferencesUIBean.getCheckbox("Show Start Date", CppPreferencesUIBean.REMINDERS_START_DATE, bean.getRemindersStartDate()) %>
                            <%=CppPreferencesUIBean.getCheckbox("Show Resolution Date", CppPreferencesUIBean.REMINDERS_RES_DATE, bean.getRemindersResDate()) %>
                        </td>
                    </tr>
                    <tr>
                        <td>Preventions</td>
                        <td>
                            <select name="<%=CppPreferencesUIBean.PREVENTIONS_DSP%>" class="form-select form-select-sm d-inline-block" style="width:auto;">
                                <%=CppPreferencesUIBean.getDisplaySelect(bean.getPreventionsDisplay()) %>
                            </select>
                        </td>
                    </tr>
                    <tr>
                        <td>Disease Registry</td>
                        <td>
                            <select name="<%=CppPreferencesUIBean.DX_REGISTRY_DSP%>" class="form-select form-select-sm d-inline-block" style="width:auto;">
                                <%=CppPreferencesUIBean.getDisplaySelect(bean.getDxRegistryDisplay()) %>
                            </select>
                        </td>
                    </tr>
                    <tr>
                        <td>Forms</td>
                        <td>
                            <select name="<%=CppPreferencesUIBean.FORMS_DSP%>" class="form-select form-select-sm d-inline-block" style="width:auto;">
                                <%=CppPreferencesUIBean.getDisplaySelect(bean.getFormsDisplay()) %>
                            </select>
                        </td>
                    </tr>
                    <tr>
                        <td>eForms</td>
                        <td>
                            <select name="<%=CppPreferencesUIBean.EFORMS_DSP%>" class="form-select form-select-sm d-inline-block" style="width:auto;">
                                <%=CppPreferencesUIBean.getDisplaySelect(bean.getEformsDisplay()) %>
                            </select>
                        </td>
                    </tr>
                    <tr>
                        <td>Documents</td>
                        <td>
                            <select name="<%=CppPreferencesUIBean.DOCUMENTS_DSP%>" class="form-select form-select-sm d-inline-block" style="width:auto;">
                                <%=CppPreferencesUIBean.getDisplaySelect(bean.getDocumentsDisplay()) %>
                            </select>
                        </td>
                    </tr>
                    <tr>
                        <td>Lab Result</td>
                        <td>
                            <select name="<%=CppPreferencesUIBean.LABS_DSP%>" class="form-select form-select-sm d-inline-block" style="width:auto;">
                                <%=CppPreferencesUIBean.getDisplaySelect(bean.getLabsDisplay()) %>
                            </select>
                        </td>
                    </tr>
                    <tr>
                        <td>Measurements</td>
                        <td>
                            <select name="<%=CppPreferencesUIBean.MEASUREMENTS_DSP%>" class="form-select form-select-sm d-inline-block" style="width:auto;">
                                <%=CppPreferencesUIBean.getDisplaySelect(bean.getMeasurementsDisplay()) %>
                            </select>
                        </td>
                    </tr>
                    <tr>
                        <td>Consultations</td>
                        <td>
                            <select name="<%=CppPreferencesUIBean.CONSULTATIONS_DSP%>" class="form-select form-select-sm d-inline-block" style="width:auto;">
                                <%=CppPreferencesUIBean.getDisplaySelect(bean.getConsultationsDisplay()) %>
                            </select>
                        </td>
                    </tr>
                    <tr>
                        <td>HRM Documents</td>
                        <td>
                            <select name="<%=CppPreferencesUIBean.HRM_DSP%>" class="form-select form-select-sm d-inline-block" style="width:auto;">
                                <%=CppPreferencesUIBean.getDisplaySelect(bean.getHrmDisplay()) %>
                            </select>
                        </td>
                    </tr>
                    <tr>
                        <td>Allergies</td>
                        <td>
                            <select name="<%=CppPreferencesUIBean.ALLERGIES_DSP%>" class="form-select form-select-sm d-inline-block" style="width:auto;">
                                <%=CppPreferencesUIBean.getDisplaySelect(bean.getAllergiesDisplay()) %>
                            </select>
                            <br>
                            <%=CppPreferencesUIBean.getCheckbox("Show Start Date", CppPreferencesUIBean.ALLERGY_START_DATE, bean.getAllergyStartDate()) %>
                            <%=CppPreferencesUIBean.getCheckbox("Show Severity", CppPreferencesUIBean.ALLERGY_SEVERITY, bean.getAllergySeverity()) %>
                        </td>
                    </tr>
                    <tr>
                        <td>Medications</td>
                        <td>
                            <select name="<%=CppPreferencesUIBean.MEDICATIONS_DSP%>" class="form-select form-select-sm d-inline-block" style="width:auto;">
                                <%=CppPreferencesUIBean.getDisplaySelect(bean.getMedicationsDisplay()) %>
                            </select>
                            <br>
                            <%=CppPreferencesUIBean.getCheckbox("Show Start Date", CppPreferencesUIBean.MEDICATION_START_DATE, bean.getMedicationStartDate()) %>
                            <%=CppPreferencesUIBean.getCheckbox("Show End Date", CppPreferencesUIBean.MEDICATION_END_DATE, bean.getMedicationEndDate()) %>
                            <%=CppPreferencesUIBean.getCheckbox("Show Qty", CppPreferencesUIBean.MEDICATION_QTY, bean.getMedicationQty()) %>
                            <%=CppPreferencesUIBean.getCheckbox("Show Repeats", CppPreferencesUIBean.MEDICATION_REPEATS, bean.getMedicationRepeats()) %>
                        </td>
                    </tr>
                    <tr>
                        <td>Other Meds</td>
                        <td>
                            <select name="<%=CppPreferencesUIBean.OTHER_MEDS_DSP%>" class="form-select form-select-sm d-inline-block" style="width:auto;">
                                <%=CppPreferencesUIBean.getDisplaySelect(bean.getOtherMedsDisplay()) %>
                            </select>
                            <br>
                            <%=CppPreferencesUIBean.getCheckbox("Show Start Date", CppPreferencesUIBean.OTHER_MEDS_START_DATE, bean.getOtherMedsStartDate()) %>
                            <%=CppPreferencesUIBean.getCheckbox("Show Resolution Date", CppPreferencesUIBean.OTHER_MEDS_RES_DATE, bean.getOtherMedsResDate()) %>
                        </td>
                    </tr>
                    <tr>
                        <td>Risk Factors</td>
                        <td>
                            <select name="<%=CppPreferencesUIBean.RISK_FACTORS_DSP%>" class="form-select form-select-sm d-inline-block" style="width:auto;">
                                <%=CppPreferencesUIBean.getDisplaySelect(bean.getRiskFactorsDisplay()) %>
                            </select>
                            <br>
                            <%=CppPreferencesUIBean.getCheckbox("Show Start Date", CppPreferencesUIBean.RISK_FACTORS_START_DATE, bean.getRiskFactorsStartDate()) %>
                            <%=CppPreferencesUIBean.getCheckbox("Show Resolution Date", CppPreferencesUIBean.RISK_FACTORS_RES_DATE, bean.getRiskFactorsResDate()) %>
                        </td>
                    </tr>
                    <tr>
                        <td>Family History</td>
                        <td>
                            <select name="<%=CppPreferencesUIBean.FAMILY_HISTORY_DSP%>" class="form-select form-select-sm d-inline-block" style="width:auto;">
                                <%=CppPreferencesUIBean.getDisplaySelect(bean.getFamilyHxDisplay()) %>
                            </select>
                            <br>
                            <%=CppPreferencesUIBean.getCheckbox("Show Start Date", CppPreferencesUIBean.FAMILY_HISTORY_START_DATE, bean.getFamilyHistoryStartDate()) %>
                            <%=CppPreferencesUIBean.getCheckbox("Show Resolution Date", CppPreferencesUIBean.FAMILY_HISTORY_RES_DATE, bean.getFamilyHistoryResDate()) %>
                            <%=CppPreferencesUIBean.getCheckbox("Show Treatment", CppPreferencesUIBean.FAMILY_HISTORY_TREATMENT, bean.getFamilyHistoryTreatment()) %>
                            <%=CppPreferencesUIBean.getCheckbox("Show Relationship", CppPreferencesUIBean.FAMILY_HISTORY_RELATIONSHIP, bean.getFamilyHistoryRelationship()) %>
                        </td>
                    </tr>
                    <tr>
                        <td>Unresolved Issues</td>
                        <td>
                            <select name="<%=CppPreferencesUIBean.UNRESOLVED_ISSUES_DSP%>" class="form-select form-select-sm d-inline-block" style="width:auto;">
                                <%=CppPreferencesUIBean.getDisplaySelect(bean.getUnresolvedIssuesDisplay()) %>
                            </select>
                        </td>
                    </tr>
                    <tr>
                        <td>Resolved Issues</td>
                        <td>
                            <select name="<%=CppPreferencesUIBean.RESOLVED_ISSUES_DSP%>" class="form-select form-select-sm d-inline-block" style="width:auto;">
                                <%=CppPreferencesUIBean.getDisplaySelect(bean.getResolvedIssuesDisplay()) %>
                            </select>
                        </td>
                    </tr>
                    <tr>
                        <td>Episodes</td>
                        <td>
                            <select name="<%=CppPreferencesUIBean.EPISODES_DSP%>" class="form-select form-select-sm d-inline-block" style="width:auto;">
                                <%=CppPreferencesUIBean.getDisplaySelect(bean.getEpisodesDisplay()) %>
                            </select>
                        </td>
                    </tr>
                </tbody>
            </table>

            <div class="d-flex gap-2">
                <input type="submit" class="btn btn-primary btn-sm" value="Save Changes">
            </div>
        </form>

    </div>
    </body>
</html>
