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
<%-- i18n scope: UI-chrome-only (regulated clinical instrument) --%>


<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName2$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName2$%>" objectName="_form" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_form");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.data.EctFormData" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="io.github.carlos_emr.carlos.managers.DemographicManager" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilMisc" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecord" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRourke2020Record" %>
<%@ page import="io.github.carlos_emr.carlos.form.data.FrmData" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="/WEB-INF/rourke-tag.tld" prefix="rourke" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>

<%
    String formClass = "Rourke2009";
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    String appointmentNo = request.getParameter("appointmentNo");

    int demoNo = Integer.parseInt(request.getParameter("demographic_no"));
    Demographic demographic = demographicManager.getDemographic(loggedInInfo, demoNo);
    int formId = Integer.parseInt(request.getParameter("formId"));
    int provNo = Integer.parseInt((String) session.getAttribute("user"));
    java.util.Properties props = (java.util.Properties) request.getAttribute("frmProperties"); //rec.getFormRecord(demoNo, formId);
    FrmRecord rec = (FrmRecord) request.getAttribute("frmRecord");
    String[] growthCharts = new String[2];

    if (((FrmRourke2020Record) rec).isFemale(loggedInInfo, demoNo)) {
        growthCharts[0] = new String("GrowthChartRourke2009Girls&__cfgfile=GrowthChartRourke2009Girls3&__cfgGraphicFile=GrowthChartRourke2009GirlGraphic&__cfgGraphicFile=GrowthChartRourke2009GirlGraphic2&__cfgGraphicFile1=GrowthChartRourke2009GirlGraphic5&__cfgGraphicFile1=GrowthChartRourke2009GirlGraphic6&__numPages=2&__graphType=LENGTH&__template=GrowthChartRourke2009Girls");
        growthCharts[1] = new String("GrowthChartRourke2009Girls2&__cfgGraphicFile=GrowthChartRourke2009GirlGraphic3&__cfgGraphicFile=GrowthChartRourke2009GirlGraphic4&__graphType=HEAD_CIRC&__template=GrowthChartRourke2009Girlspg2");
    } else {
        growthCharts[0] = new String("GrowthChartRourke2009Boys&__cfgfile=GrowthChartRourke2009Boys3&__cfgGraphicFile=GrowthChartRourke2009BoyGraphic&__cfgGraphicFile=GrowthChartRourke2009BoyGraphic2&__cfgGraphicFile1=GrowthChartRourke2009BoyGraphic5&__cfgGraphicFile1=GrowthChartRourke2009BoyGraphic6&__numPages=2&__graphType=LENGTH&__template=GrowthChartRourke2009Boys");
        growthCharts[1] = new String("GrowthChartRourke2009Boys2&__cfgGraphicFile=GrowthChartRourke2009BoyGraphic3&__cfgGraphicFile=GrowthChartRourke2009BoyGraphic4&__graphType=HEAD_CIRC&__template=GrowthChartRourke2009Boyspg2");
    }

    FrmData fd = new FrmData();
    String resource = fd.getResource();

    String formTable = "formGrowth0_36";
    String formName = "Growth 0-36m";
    String growthChartURL = "";
    EctFormData.PatientForm[] pforms = EctFormData.getPatientFormsFromLocalAndRemote(loggedInInfo, String.valueOf(demoNo), formTable);
    if (pforms.length > 0) {
        EctFormData.PatientForm pfrm = pforms[0];
        growthChartURL = request.getContextPath() + "/form/forwardshortcutname?formname=" + formName + "&demographic_no=" + demoNo;
    }

%>

<div style="display:block; width:100%;">
    <img alt="copyright" width="80%" src="graphics/Rourke2020Banner.png"
         onMouseOver="popLayer('<fmt:message key="encounter.formRourke2009.formCopyRight"/>')"
         onMouseOut="hideLayer()">
</div>

<div id="object1"
     style="position: absolute; background-color: #FFFFDD; color: black; border-color: black; border-width: 20px; left: 25px; top: -100px; z-index: +1"
     onmouseover="overdiv=1;"
     onmouseout="overdiv=0; setTimeout('hideLayer()',1000)">pop up
    description layer
</div>

<table cellpadding="0" cellspacing="0" class="Header" class="hidePrint">
    <tr>
        <td nowrap="true">
            <input type="submit" value="<fmt:message key="encounter.formRourke1.btnSave"/>"
                   onclick="javascript:return onSave();"/>
            <input type="submit" value="<fmt:message key="encounter.formRourke1.btnSaveExit"/>"
                   onclick="javascript:return onSaveExit();"/>
            <input type="submit" value="<fmt:message key="encounter.formRourke1.btnExit"/>"
                   onclick="javascript:return onExit();"><br/>
            <input type="submit" value="<fmt:message key="encounter.formRourke1.btnPrint"/>"
                   onclick="javascript:return onPrint();"/>
            <input type="button" value="<fmt:message key='encounter.formRourke1.btnAbout'/>"
                   onclick="javascript:return popPage('http://rourkebabyrecord.ca','<fmt:message key='encounter.formRourke1.aboutRourkeTitle'/>');"/>
        </td>
        <td width="100%">
            <div name="saveMessageDiv"></div>
        </td>
        <td align="center" nowrap="true" width="100%">
            <% if (formId > 0) { %> <a name="length" href="#"
                                       onclick="onGraph('<%=request.getContextPath()%>/form/formname?submit=graph&form_class=Rourke2020&__title=Baby+Growth+Graph1&__cfgfile=<%=growthCharts[0]%>&demographic_no=<carlos:encode value='<%= String.valueOf(demoNo) %>' context="uriComponent"/>&formId=<carlos:encode value='<%= String.valueOf(formId) %>' context="uriComponent"/>&provNo=<carlos:encode value='<%= String.valueOf(provNo) %>' context="uriComponent"/>','<%= "growth1" + demoNo %>');return false;">
            <fmt:message key="encounter.formRourke1.btnGraphLenghtWeight"/></a><br>
            <a name="headCirc" href="#"
               onclick="onGraph('<%=request.getContextPath()%>/form/formname?submit=graph&form_class=Rourke2020&__title=Baby+Head+Circumference&__cfgfile=<%=growthCharts[1]%>&demographic_no=<carlos:encode value='<%= String.valueOf(demoNo) %>' context="uriComponent"/>&formId=<carlos:encode value='<%= String.valueOf(formId) %>' context="uriComponent"/>&provNo=<carlos:encode value='<%= String.valueOf(provNo) %>' context="uriComponent"/>','<%= "growth2" + demoNo %>');return false;">
                <fmt:message key="encounter.formRourke1.btnGraphHead"/></a> <% } else { %>
            &nbsp; <% } %>
        </td>

    </tr>
</table>

<table cellpadding="0" cellspacing="0" border="0" width="100%">
    <tr class="titleBar">
        <th><fmt:message key="encounter.formRourke2006_2.msgRourkeBabyRecord"/></th>
    </tr>
</table>
<div id="patientInfop2">
    <fmt:message key="encounter.formRourke1.msgName"/>:
    <input type="text" maxlength="60" size="30" value="<carlos:encode value='<%= props.getProperty("c_pName", "") %>' context="htmlAttribute"/>"
           readonly="true"/>
    &nbsp;&nbsp; <fmt:message key="encounter.formRourke1.msgBirthDate"/> (d/m/yyyy):
    <input type="text" id="c_birthDate2" size="10" maxlength="10" value="<carlos:encode value='<%= props.getProperty("c_birthDate", "") %>' context="htmlAttribute"/>"
           readonly="true">
    &nbsp;&nbsp;
    Age: <input type="text" id="currentAge2" size="10" maxlength="10" readonly="true" ondblclick="calcAge();">
    <% if (!((FrmRourke2020Record) rec).isFemale(loggedInInfo, demoNo)) {
    %>(<fmt:message key="encounter.formRourke1.msgMale"/>)
    <%
    } else {
    %>(<fmt:message key="encounter.formRourke1.msgFemale"/>)
    <%
        }
    %>

</div>
<table cellpadding="0" cellspacing="0" width="100%" border="1">
    <tr align="center">
        <td class="column"><a><fmt:message key="encounter.formRourke2006_1.visitDate"/></a></td>
        <td colspan="4" class="row"><a><fmt:message key="encounter.formRourke2006_2.msg2mos"/></a></td>
        <td colspan="4" class="row"><a><fmt:message key="encounter.formRourke2006_2.msg4mos"/></a></td>
        <td colspan="4" class="row"><a><fmt:message key="encounter.formRourke2006_2.msg6mos"/></a></td>
    </tr>
    <tr align="center">
        <td class="column"><a><fmt:message key="encounter.formRourke1.msgDate"/></a></td>
        <td colspan="4">
            <img src="<%= request.getContextPath() %>/images/cal.gif" id="p2_date2m_cal" style="vertical-align: middle;">
            <input readonly type="text" id="p2_date2m"
                   ondblclick="resetDate(this)" name="p2_date2m" size="10"
                   value="<carlos:encode value='<%= props.getProperty("p2_date2m", "") %>' context="htmlAttribute"/>"/>
            <img src="<%= request.getContextPath() %>/images/clear.png" id="p2_date2m_delete" style="vertical-align: middle; cursor: pointer;"
                 onClick="resetDateUsingID('p2_date2m')">
        </td>
        <td colspan="4">
            <img src="<%= request.getContextPath() %>/images/cal.gif" id="p2_date4m_cal" style="vertical-align: middle;">
            <input readonly type="text" id="p2_date4m"
                   ondblclick="resetDate(this)" name="p2_date4m" size="10"
                   value="<carlos:encode value='<%= props.getProperty("p2_date4m", "") %>' context="htmlAttribute"/>"/>
            <img src="<%= request.getContextPath() %>/images/clear.png" id="p2_date4m_delete" style="vertical-align: middle; cursor: pointer;"
                 onClick="resetDateUsingID('p2_date4m')">
        </td>
        <td colspan="4">
            <img src="<%= request.getContextPath() %>/images/cal.gif" id="p2_date6m_cal" style="vertical-align: middle;">
            <input readonly type="text" id="p2_date6m"
                   ondblclick="resetDate(this)" name="p2_date6m" size="10"
                   value="<carlos:encode value='<%= props.getProperty("p2_date6m", "") %>' context="htmlAttribute"/>"/>
            <img src="<%= request.getContextPath() %>/images/clear.png" id="p2_date6m_delete" style="vertical-align: middle; cursor: pointer;"
                 onClick="resetDateUsingID('p2_date6m')">
        </td>
    </tr>
    <tr align="center" id="growthAp2">
        <td class="column" rowspan="2">
            <a><fmt:message key="encounter.formRourke2020_1.btnGrowth"/><br><fmt:message key="encounter.formRourke2009_1.btnGrowthmsg"/></a>
        </td>
        <td>
            <a href="javascript:void(0)"
               onclick="displayDemographicMeasurements('p2_ht2m', 'HT', '<carlos:encode value='<%= String.valueOf(demographic.getDemographicNo()) %>' context="javaScriptAttribute"/>',
                       '<carlos:encode value='<%= demographic.getFormattedDob() %>' context="javaScriptAttribute"/>', '<carlos:encode value='<%= appointmentNo %>' context="javaScriptAttribute"/>')">
                <fmt:message key="encounter.formRourke1.formHt"/>
            </a>
        </td>
        <td><a href="javascript:void(0)"
               onclick="displayDemographicMeasurements('p2_wt2m', 'WT', '<carlos:encode value='<%= String.valueOf(demographic.getDemographicNo()) %>' context="javaScriptAttribute"/>',
                       '<carlos:encode value='<%= demographic.getFormattedDob() %>' context="javaScriptAttribute"/>', '<carlos:encode value='<%= appointmentNo %>' context="javaScriptAttribute"/>')">
            <fmt:message key="encounter.formRourke1.formWt"/>
        </a>
        </td>
        <td colspan="2"><fmt:message key="encounter.formRourke2006_3.formHdCirc"/></td>
        <td>
            <a href="javascript:void(0)"
               onclick="displayDemographicMeasurements('p2_ht4m', 'HT', '<carlos:encode value='<%= String.valueOf(demographic.getDemographicNo()) %>' context="javaScriptAttribute"/>',
                       '<carlos:encode value='<%= demographic.getFormattedDob() %>' context="javaScriptAttribute"/>', '<carlos:encode value='<%= appointmentNo %>' context="javaScriptAttribute"/>')">
                <fmt:message key="encounter.formRourke1.formHt"/>
            </a>
        </td>
        <td>
            <a href="javascript:void(0)"
               onclick="displayDemographicMeasurements('p2_wt4m', 'WT', '<carlos:encode value='<%= String.valueOf(demographic.getDemographicNo()) %>' context="javaScriptAttribute"/>',
                       '<carlos:encode value='<%= demographic.getFormattedDob() %>' context="javaScriptAttribute"/>', '<carlos:encode value='<%= appointmentNo %>' context="javaScriptAttribute"/>')">
                <fmt:message key="encounter.formRourke1.formWt"/>
            </a>
        </td>
        <td colspan="2"><fmt:message key="encounter.formRourke2006_3.formHdCirc"/></td>
        <td>
            <a href="javascript:void(0)"
               onclick="displayDemographicMeasurements('p2_ht6m', 'HT', '<carlos:encode value='<%= String.valueOf(demographic.getDemographicNo()) %>' context="javaScriptAttribute"/>',
                       '<carlos:encode value='<%= demographic.getFormattedDob() %>' context="javaScriptAttribute"/>', '<carlos:encode value='<%= appointmentNo %>' context="javaScriptAttribute"/>')">
                <fmt:message key="encounter.formRourke1.formHt"/>
            </a>
        </td>
        <td>
            <a href="javascript:void(0)"
               onclick="displayDemographicMeasurements('p2_wt6m', 'WT', '<carlos:encode value='<%= String.valueOf(demographic.getDemographicNo()) %>' context="javaScriptAttribute"/>',
                       '<carlos:encode value='<%= demographic.getFormattedDob() %>' context="javaScriptAttribute"/>', '<carlos:encode value='<%= appointmentNo %>' context="javaScriptAttribute"/>')">
                <fmt:message key="encounter.formRourke2006_2.formWt6m"/>
            </a>
        </td>
        <td colspan="2"><fmt:message key="encounter.formRourke2006_3.formHdCirc"/></td>
    </tr>
    <tr align="center" id="growthBp2">

        <td><input type="text" class="wide"
                   ondblclick="htEnglish2Metric(this);" name="p2_ht2m" id="p2_ht2m" size="4"
                   maxlength="5" value="<carlos:encode value='<%= props.getProperty("p2_ht2m", "") %>' context="htmlAttribute"/>"></td>
        <td><input type="text" class="wide"
                   ondblclick="wtEnglish2Metric(this);" name="p2_wt2m" id="p2_wt2m" size="4"
                   maxlength="5" value="<carlos:encode value='<%= props.getProperty("p2_wt2m", "") %>' context="htmlAttribute"/>"></td>
        <td colspan="2"><input type="text" class="wide"
                               ondblclick="htEnglish2Metric(this);" name="p2_hc2m" size="4"
                               maxlength="5" value="<carlos:encode value='<%= props.getProperty("p2_hc2m", "") %>' context="htmlAttribute"/>"></td>
        <td><input type="text" class="wide"
                   ondblclick="htEnglish2Metric(this);" name="p2_ht4m" id="p2_ht4m" size="4"
                   maxlength="5" value="<carlos:encode value='<%= props.getProperty("p2_ht4m", "") %>' context="htmlAttribute"/>"></td>
        <td><input type="text" class="wide"
                   ondblclick="wtEnglish2Metric(this);" name="p2_wt4m" id="p2_wt4m" size="4"
                   maxlength="5" value="<carlos:encode value='<%= props.getProperty("p2_wt4m", "") %>' context="htmlAttribute"/>"></td>
        <td colspan="2"><input type="text" class="wide"
                               ondblclick="htEnglish2Metric(this);" name="p2_hc4m" size="4"
                               maxlength="5" value="<carlos:encode value='<%= props.getProperty("p2_hc4m", "") %>' context="htmlAttribute"/>"></td>
        <td><input type="text" class="wide"
                   ondblclick="htEnglish2Metric(this);" name="p2_ht6m" id="p2_ht6m" size="4"
                   maxlength="5" value="<carlos:encode value='<%= props.getProperty("p2_ht6m", "") %>' context="htmlAttribute"/>"></td>
        <td><input type="text" class="wide"
                   ondblclick="wtEnglish2Metric(this);" name="p2_wt6m" id="p2_wt6m" size="4"
                   maxlength="5" value="<carlos:encode value='<%= props.getProperty("p2_wt6m", "") %>' context="htmlAttribute"/>"></td>
        <td colspan="2"><input type="text" class="wide"
                               ondblclick="htEnglish2Metric(this);" name="p2_hc6m" size="4"
                               maxlength="5" value="<carlos:encode value='<%= props.getProperty("p2_hc6m", "") %>' context="htmlAttribute"/>"></td>
    </tr>
    <tr align="center">
        <td class="column"><a><fmt:message key="encounter.formRourke2020.msgParentConcerns"/></a></td>
        <td colspan="4"><textarea id="p2_pConcern2m"
                                  name="p2_pConcern2m" class="wide limit-rows" cols="10" rows="5"
                                  maxlength="400"><carlos:encode value='<%= props.getProperty("p2_pConcern2m", "") %>' context="html"/></textarea>
        </td>
        <td colspan="4"><textarea id="p2_pConcern4m"
                                  name="p2_pConcern4m" class="wide limit-rows" cols="10" rows="5"
                                  maxlength="400"><carlos:encode value='<%= props.getProperty("p2_pConcern4m", "") %>' context="html"/></textarea>
        </td>
        <td colspan="4"><textarea id="p2_pConcern6m"
                                  name="p2_pConcern6m" class="wide limit-rows" cols="10" rows="5"
                                  maxlength="400"><carlos:encode value='<%= props.getProperty("p2_pConcern6m", "") %>' context="html"/></textarea>
        </td>
    </tr>
    <tr align="center" id="nutritionp2">
        <td class="column"><a><fmt:message key="encounter.formRourke1.msgNutrition"/>*</a><br/>
            <img height="15" width="20" src="graphics/Checkmark_Lwhite.gif">
            <fmt:message key="encounter.formRourke2020.msgNutritionLegend"/>
        </td>
        <td colspan="4">
            <table id="ntp21" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif">
                        </div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top"><fmt:message key="encounter.formRourke2009.formNo"/></td>
                    <td class="edcol" valign="top" colspan="2"><fmt:message key="encounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_breastFeeding2m" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td><b>
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()" onclick="popPage('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>n_breastFeeding');return false">
                            <fmt:message key="encounter.formRourke2006_1.btnBreastFeeding"/>
                        </a>
                    </b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_vitaminD2m" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td><b>
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2020.formVitaminD200"/>*
                        </a>
                    </b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_formulaFeeding2m" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td>
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()" href="javascript:showNotes()">
                            <i><fmt:message key="encounter.formRourke2009_2.msgFormulaFeeding2m"/></i>
                        </a>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_supplementationWater2m" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:message key="encounter.formRourke1.formSupplementationWater"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_supplementationOtherFluids2m"
                                                  formProperties="<%=props%>" showNoOption="<%=true%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:message key="encounter.formRourke1.formSupplementationOtherFluids"/></td>
                </tr>
                <tr>
                    <td style="vertical-align: bottom;" colspan="5">
                        <textarea id="p2_nutrition2m" name="p2_nutrition2m" class="wide" rows="5"
                                  cols="25"><carlos:encode value='<%= props.getProperty("p2_nutrition2m", "") %>' context="html"/></textarea>
                    </td>
                </tr>
            </table>
        </td>
        <td colspan="4">
            <table id="ntp22" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif">
                        </div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top"><fmt:message key="encounter.formRourke2009.formNo"/></td>
                    <td class="edcol" valign="top" colspan="2"><fmt:message key="encounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_breastFeeding4m" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td><b>
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()" onclick="popPage('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>n_breastFeeding');return false">
                            <fmt:message key="encounter.formRourke2006_1.btnBreastFeeding"/>
                        </a>
                    </b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_vitaminD4m" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td><b>
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2020.formVitaminD200"/>*
                        </a>
                    </b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_formulaFeeding4m" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td>
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()" href="javascript:showNotes()">
                            <i><fmt:message key="encounter.formRourke2009_2.msgFormulaFeeding4m"/></i>
                        </a>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_futureIntroductionSolids4m"
                                                  formProperties="<%=props%>" showNoOption="<%=true%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td>
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()" href="javascript:showNotes()">
                            <fmt:message key="encounter.formRourke2020.formFutureIntroductionSolids"/>*</a>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_supplementationWater4m" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:message key="encounter.formRourke1.formSupplementationWater"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_supplementationOtherFluids4m"
                                                  formProperties="<%=props%>" showNoOption="<%=true%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:message key="encounter.formRourke1.formSupplementationOtherFluids"/></td>
                </tr>
                <tr>
                    <td style="vertical-align: bottom;" colspan="5"><textarea id="p2_nutrition4m"
                                                                              name="p2_nutrition4m" class="wide"
                                                                              rows="5"
                                                                              cols="25"><carlos:encode value='<%= props.getProperty("p2_nutrition4m", "") %>' context="html"/></textarea>
                    </td>
                </tr>
            </table>
        </td>
        <td colspan="4">
            <table id="ntp23" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif">
                        </div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top"><fmt:message key="encounter.formRourke2009.formNo"/></td>
                    <td class="edcol" valign="top" colspan="2"><fmt:message key="encounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_breastFeeding6m" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td><b>
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()" onclick="popPage('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>n_breastFeeding');return false">
                            <fmt:message key="encounter.formRourke2006_1.btnBreastFeeding"/>
                        </a>
                    </b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_vitaminD6m" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td><b>
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2020.formVitaminD200"/>*
                        </a>
                    </b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_formulaFeeding6m" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td>
                        <i><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()" href="javascript:showNotes()">
                            <fmt:message key="encounter.formRourke2009_2.msgFormulaFeedingLong6m"/>*</a></i>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_iron6m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><b><fmt:message key="encounter.formRourke2020.formIronContainingFoods"/></b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_allergenic6m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><b><fmt:message key="encounter.formRourke2020.formAllergenicFoods"/>*</b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_vegFruit6m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:message key="encounter.formRourke2020.formFruitsVegetablesAndMilk"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_avoidJuiceAndBeverages6m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:message key="encounter.formRourke2020.formAvoidJuiceAndBeverages"/>*</td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_honey6m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()" href="javascript:showNotes()">
                        <fmt:message key="encounter.formRourke2020.formNoHoney"/>*</a>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_choking6m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                        <fmt:message key="encounter.formRourke2006_2.msgChoking"/>*
                    </a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_bottle6m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:message key="encounter.formRourke2006_2.msgBottle"/></td>
                </tr>
                <tr align="center" style="vertical-align:bottom;">
                    <td colspan="5"><textarea id="p2_nutrition6m" name="p2_nutrition6m" class="wide" rows="5"
                                              cols="25"><carlos:encode value='<%= props.getProperty("p2_nutrition6m", "") %>' context="html"/></textarea></td>
                </tr>
            </table>
        </td>
    </tr>
    <tr id="educationp2">

        <td class="column">
            <a><fmt:message key="encounter.formRourke1.msgEducational"/></a><br/>
            <small><fmt:message key="encounter.formRourke1.msgEducationalSubtitle"/></small><br/>
            <img height="15" width="20" src="graphics/Checkmark_Lwhite.gif"><fmt:message key="encounter.formRourke2006.msgEducationalLegend"/>
        </td>
        <td colspan="12">
            <table id="edt2" style="font-size: 9pt;" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td colspan="16">&nbsp;</td>
                </tr>
                <tr>
                    <td valign="top" colspan="16"><fmt:message key="encounter.formRourke2006_1.formInjuryPrev"/></td>
                </tr>
                <tr>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif">
                        </div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:message key="encounter.formRourke2009.formNotDiscussed"/></td>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif">
                        </div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:message key="encounter.formRourke2009.formNotDiscussed"/></td>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif">
                        </div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:message key="encounter.formRourke2009.formNotDiscussed"/></td>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif">
                        </div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:message key="encounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_poisons" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()" href="javascript:showNotes()">
                            <fmt:message key="encounter.formRourke2006_2.formPoisons"/>
                        </a></b>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p2_carSeat" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()" href="javascript:showNotes()">
                            <fmt:message key="encounter.formRourke2020_1.formMotorizedvehicles"/>/<fmt:message key="encounter.formRourke2020_1.formCarSeat"/>
                        </a>*</b>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p2_smokeSafety" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2006_1.formSmokeSafety"/>*
                        </a>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p2_firearmSafety" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2006_1.formFireArm"/>*
                        </a></b>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_hotWater" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <i><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2006_1.formHotWater"/>/<fmt:message key="encounter.formRourke2006_1.formBathSafety"/>*
                        </a></i>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p2_safeToys" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2006_1.formSafeToys"/>*
                        </a>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p2_pacifier" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <i><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2006_2.formPacifierUse"/>*
                        </a></i>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p2_electric" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <i><fmt:message key="encounter.formRourke2020.formElectricPlugs"/></i>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_sleepPos" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td colspan="5" valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2009_1.formSleepPos"/>/<fmt:message key="encounter.formRourke1.formCribSafety"/>*
                        </a></b>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p2_falls" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td colspan="5" valign="top">
                        <i><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2006_2.formFalls"/>*
                        </a></i>
                    </td>
                </tr>
                <tr>
                    <td colspan="16">&nbsp;</td>
                </tr>
                <tr>
                    <td valign="top" colspan="16"><fmt:message key="encounter.formRourke2006_1.formBehaviour"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_crying" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote2"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2020.formBehaviourCrying"/>**</a>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p2_healthySleep" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2020.formHealthySleep"/>**</a></b>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p2_nightWaking" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2020.formNightWaking"/>**</a></b>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p2_soothability" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:message key="encounter.formRourke2006_1.formSoothability"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_bonding" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2006_1.formBonding"/>**</a></b>
                    </td>

                    <rourke:discussionRadioSelect sectionName="p2_famConflict" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:message key="encounter.formRourke2006_1.formFamConflict"/></td>

                    <rourke:discussionRadioSelect sectionName="p2_siblings" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:message key="encounter.formRourke2006_1.formSiblings"/></td>

                    <rourke:discussionRadioSelect sectionName="p2_childCare" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote2"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2020.formChildCare"/></a>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_reading" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <i><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2006_3.formEncourageReading"/>**
                        </a></i>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p2_pFatigue" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2006_1.formParentFatigue"/>**
                        </a></b>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p2_homeVisit" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td colspan="5" valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2020.formHighRiskOrHomeVisit"/>**
                        </a></b>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_makingEndsMeetInquiry" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td colspan="5" valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2020.formMakingEndsMeetInquiry"/>**
                        </a></b>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p2_active" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td colspan="5" valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2020.formHealthyActiveLiving"/>**
                        </a></b>
                    </td>
                </tr>
                <tr>
                    <td colspan="16">&nbsp;</td>
                </tr>
                <tr>
                    <td valign="top" colspan="16">
                        <fmt:message key="encounter.formRourke2020.formEnvironmentalHealth"/> &amp; <fmt:message key="encounter.formRourke2006_1.formOtherIssues"/>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_2ndSmoke" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2020_1.formSecondHandSmoke"/>*
                        </a></b>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p2_pesticides" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <i><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2006_2.formPesticides"/>*
                        </a></i>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p2_sunExposure" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td colspan="5" valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2006_1.formSunExposure"/>*
                        </a>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_altMed" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <i><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2009_2.formAltMed"/>*
                        </a></i>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p2_noCoughMed" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2020.formNoOtcColdMed"/>*
                        </a></b>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p2_tmpControl" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><i><fmt:message key="encounter.formRourke2006_1.formTempCtrl"/></i></td>
                    <rourke:discussionRadioSelect sectionName="p2_fever" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2006_1.formFever"/>*
                        </a>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_teething" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td colspan="5" valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2020_2.formTeething"/>*
                        </a>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p2_tummyTime" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td colspan="5" valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2020.formTummyTime"/>*
                        </a></b>
                    </td>
                </tr>
                <tr>
                    <td colspan="16">&nbsp;</td>
                </tr>
                <tr>
                    <td colspan="16" style="vertical-align:bottom;">
                        <table cellpadding="0" cellspacing="0" width="100%">
                            <tr>
                                <td style="vertical-align:bottom;">
                                    <textarea id="p2_education2m" name="p2_education2m" style="width: 100%"
                                              rows="5"><carlos:encode value='<%= props.getProperty("p2_education2m", "") %>' context="html"/></textarea>
                                </td>
                                <td style="vertical-align:bottom;">
                                    <textarea id="p2_education4m" name="p2_education4m" style="width: 100%"
                                              rows="5"><carlos:encode value='<%= props.getProperty("p2_education4m", "") %>' context="html"/></textarea>
                                </td>
                                <td style="vertical-align:bottom;">
                                    <textarea id="p2_education6m" name="p2_education6m" style="width: 100%"
                                              rows="5"><carlos:encode value='<%= props.getProperty("p2_education6m", "") %>' context="html"/></textarea>
                                </td>
                            </tr>
                        </table>
                    </td>
                </tr>
            </table>
        </td>
    </tr>
    <tr id="developmentp2">
        <td class="column">
            <a><fmt:message key="encounter.formRourke1.msgDevelopment"/>**</a><br>
            <fmt:message key="encounter.formRourke2020.msgDevelopmentDesc"/><br>
            <img height="15" width="20" src="graphics/Checkmark_Lwhite.gif"><fmt:message key="encounter.formRourke2006_1.msgDevelopmentLegend"/>
        </td>
        <td colspan="4" align="center">
            <table id="dt21" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif">
                        </div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:message key="encounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <td valign="top"><input type="radio" id="p2_eyesMoveOk"
                                            name="p2_eyesOk" onclick="onCheck(this,'p2_eyesMove')"
                        <carlos:encode value='<%= props.getProperty("p2_eyesOk", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_eyesMoveOkConcerns"
                                            name="p2_eyesOkConcerns" onclick="onCheck(this,'p2_eyesMove')"
                        <carlos:encode value='<%= props.getProperty("p2_eyesOkConcerns", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_eyesMoveNotDiscussed"
                                            name="p2_eyesNotDiscussed" onclick="onCheck(this,'p2_eyesMove')"
                        <carlos:encode value='<%= props.getProperty("p2_eyesNotDiscussed", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><fmt:message key="encounter.formRourke2006_2.formEyesMove"/></td>
                </tr>

                <tr>
                    <td valign="top"><input type="radio" id="p2_coosOk"
                                            name="p2_coosOk" onclick="onCheck(this,'p2_coos')"
                        <carlos:encode value='<%= props.getProperty("p2_coosOk", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_coosOkConcerns"
                                            name="p2_coosOkConcerns" onclick="onCheck(this,'p2_coos')"
                        <carlos:encode value='<%= props.getProperty("p2_coosOkConcerns", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_coosNotDiscussed"
                                            name="p2_coosNotDiscussed" onclick="onCheck(this,'p2_coos')"
                        <carlos:encode value='<%= props.getProperty("p2_coosNotDiscussed", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><fmt:message key="encounter.formRourke2009_2.formCoos"/></td>
                </tr>

                <tr>
                    <td valign="top"><input type="radio" id="p2_headUpTummyOk"
                                            name="p2_headUpTummyOk" onclick="onCheck(this,'p2_headUpTummy')"
                        <carlos:encode value='<%= props.getProperty("p2_headUpTummyOk", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_headUpTummyOkConcerns"
                                            name="p2_headUpTummyOkConcerns" onclick="onCheck(this,'p2_headUpTummy')"
                        <carlos:encode value='<%= props.getProperty("p2_headUpTummyOkConcerns", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_headUpTummyNotDiscussed"
                                            name="p2_headUpTummyNotDiscussed" onclick="onCheck(this,'p2_headUpTummy')"
                        <carlos:encode value='<%= props.getProperty("p2_headUpTummyNotDiscussed", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><fmt:message key="encounter.formRourke2009_2.formHeadUp"/></td>
                </tr>

                <tr>
                    <td valign="top"><input type="radio" id="p2_cuddledOk"
                                            name="p2_cuddledOk" onclick="onCheck(this,'p2_cuddled')"
                        <carlos:encode value='<%= props.getProperty("p2_cuddledOk", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_cuddledOkConcerns"
                                            name="p2_cuddledOkConcerns" onclick="onCheck(this,'p2_cuddled')"
                        <carlos:encode value='<%= props.getProperty("p2_cuddledOkConcerns", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_cuddledNotDiscussed"
                                            name="p2_cuddledNotDiscussed" onclick="onCheck(this,'p2_cuddled')"
                        <carlos:encode value='<%= props.getProperty("p2_cuddledNotDiscussed", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><fmt:message key="encounter.formRourke2009_2.formCuddled"/></td>
                </tr>

                <tr>
                    <td valign="top"><input type="radio" id="p2_2sucksOk"
                                            name="p2_2sucksOk" onclick="onCheck(this,'p2_2sucks')"
                        <carlos:encode value='<%= props.getProperty("p2_2sucksOk", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_2sucksOkConcerns"
                                            name="p2_2sucksOkConcerns" onclick="onCheck(this,'p2_2sucks')"
                        <carlos:encode value='<%= props.getProperty("p2_2sucksOkConcerns", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_2sucksNotDiscussed"
                                            name="p2_2sucksNotDiscussed" onclick="onCheck(this,'p2_2sucks')"
                        <carlos:encode value='<%= props.getProperty("p2_2sucksNotDiscussed", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><fmt:message key="encounter.formRourke2009_2.form2sucks"/></td>
                </tr>

                <tr>
                    <td valign="top"><input type="radio" id="p2_smilesOk"
                                            name="p2_smilesOk" onclick="onCheck(this,'p2_smiles')"
                        <carlos:encode value='<%= props.getProperty("p2_smilesOk", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_smilesOkConcerns"
                                            name="p2_smilesOkConcerns" onclick="onCheck(this,'p2_smiles')"
                        <carlos:encode value='<%= props.getProperty("p2_smilesOkConcerns", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_smilesNotDiscussed"
                                            name="p2_smilesNotDiscussed" onclick="onCheck(this,'p2_smiles')"
                        <carlos:encode value='<%= props.getProperty("p2_smilesNotDiscussed", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><fmt:message key="encounter.formRourke2006_2.formSmiles"/></td>
                </tr>

                <tr>
                    <td valign="top"><input type="radio"
                                            id="p2_noParentsConcerns2mOk" name="p2_noParentsConcerns2mOk"
                                            onclick="onCheck(this,'p2_noParentsConcerns2m')"
                        <carlos:encode value='<%= props.getProperty("p2_noParentsConcerns2mOk", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio"
                                            id="p2_noParentsConcerns2mOkConcerns"
                                            name="p2_noParentsConcerns2mOkConcerns"
                                            onclick="onCheck(this,'p2_noParentsConcerns2m')"
                        <carlos:encode value='<%= props.getProperty("p2_noParentsConcerns2mOkConcerns", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio"
                                            id="p2_noParentsConcerns2mNotDiscussed"
                                            name="p2_noParentsConcerns2mNotDiscussed"
                                            onclick="onCheck(this,'p2_noParentsConcerns2m')"
                        <carlos:encode value='<%= props.getProperty("p2_noParentsConcerns2mNotDiscussed", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><fmt:message key="encounter.formRourke2009.formNoparentConcerns"/>**
                    </td>
                </tr>

                <tr align="center">
                    <td colspan="4" style="vertical-align:bottom;"><textarea id="p2_development2m"
                                                                             name="p2_development2m" rows="5" cols="25"
                                                                             class="wide"><carlos:encode value='<%= props.getProperty("p2_development2m", "") %>' context="html"/></textarea>
                    </td>
                </tr>
            </table>
        </td>
        <td colspan="4" align="center">
            <table id="dt22" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif">
                        </div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:message key="encounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <td valign="top"><input type="radio" id="p2_movingObjOk"
                                            name="p2_movingObjOk" onclick="onCheck(this,'p2_movingObj')"
                        <carlos:encode value='<%= props.getProperty("p2_movingObjOk", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_movingObjOkConcerns"
                                            name="p2_movingObjOkConcerns" onclick="onCheck(this,'p2_movingObj')"
                        <carlos:encode value='<%= props.getProperty("p2_movingObjOkConcerns", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_movingObjNotDiscussed"
                                            name="p2_movingObjNotDiscussed" onclick="onCheck(this,'p2_movingObj')"
                        <carlos:encode value='<%= props.getProperty("p2_movingObjNotDiscussed", "") %>' context="htmlAttribute"/>></td>
                    <td><fmt:message key="encounter.formRourke2009_2.formMovingObj"/></td>
                </tr>

                <tr>
                    <td valign="top"><input type="radio" id="p2_respondsOk"
                                            name="p2_respondsOk" onclick="onCheck(this,'p2_responds')"
                        <carlos:encode value='<%= props.getProperty("p2_respondsOk", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_respondsOkConcerns"
                                            name="p2_respondsOkConcerns" onclick="onCheck(this,'p2_responds')"
                        <carlos:encode value='<%= props.getProperty("p2_respondsOkConcerns", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_respondsNotDiscussed"
                                            name="p2_respondsNotDiscussed" onclick="onCheck(this,'p2_responds')"
                        <carlos:encode value='<%= props.getProperty("p2_respondsNotDiscussed", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><fmt:message key="encounter.formRourke2009_2.formResponds"/></td>
                </tr>

                <tr>
                    <td valign="top"><input type="radio" id="p2_headSteadyOk"
                                            name="p2_headSteadyOk" onclick="onCheck(this,'p2_headSteady')"
                        <carlos:encode value='<%= props.getProperty("p2_headSteadyOk", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_headSteadyOkConcerns"
                                            name="p2_headSteadyOkConcerns" onclick="onCheck(this,'p2_headSteady')"
                        <carlos:encode value='<%= props.getProperty("p2_headSteadyOkConcerns", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_headSteadyNotDiscussed"
                                            name="p2_headSteadyNotDiscussed" onclick="onCheck(this,'p2_headSteady')"
                        <carlos:encode value='<%= props.getProperty("p2_headSteadyNotDiscussed", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><fmt:message key="encounter.formRourke2009_2.formHeadSteady"/></td>
                </tr>

                <tr>
                    <td valign="top"><input type="radio" id="p2_holdsObjOk"
                                            name="p2_holdsObjOk" onclick="onCheck(this,'p2_holdsObj')"
                        <carlos:encode value='<%= props.getProperty("p2_holdsObjOk", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_holdsObjOkConcerns"
                                            name="p2_holdsObjOkConcerns" onclick="onCheck(this,'p2_holdsObj')"
                        <carlos:encode value='<%= props.getProperty("p2_holdsObjOkConcerns", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_holdsObjNotDiscussed"
                                            name="p2_holdsObjNotDiscussed" onclick="onCheck(this,'p2_holdsObj')"
                        <carlos:encode value='<%= props.getProperty("p2_holdsObjNotDiscussed", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><fmt:message key="encounter.formRourke2009_2.formholdsObj"/></td>
                </tr>

                <tr>
                    <td valign="top"><input type="radio" id="p2_laughsOk"
                                            name="p2_laughsOk" onclick="onCheck(this,'p2_laughs')"
                        <carlos:encode value='<%= props.getProperty("p2_laughsOk", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_laughsOkConcerns"
                                            name="p2_laughsOkConcerns" onclick="onCheck(this,'p2_laughs')"
                        <carlos:encode value='<%= props.getProperty("p2_laughsOkConcerns", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_laughsNotDiscussed"
                                            name="p2_laughsNotDiscussed" onclick="onCheck(this,'p2_laughs')"
                        <carlos:encode value='<%= props.getProperty("p2_laughsNotDiscussed", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><fmt:message key="encounter.formRourke2009_2.formLaughs"/></td>
                </tr>

                <tr>
                    <td valign="top"><input type="radio"
                                            id="p2_noParentsConcerns4mOk" name="p2_noParentsConcerns4mOk"
                                            onclick="onCheck(this,'p2_noParentsConcerns4m')"
                        <carlos:encode value='<%= props.getProperty("p2_noParentsConcerns4mOk", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio"
                                            id="p2_noParentsConcerns4mOkConcerns"
                                            name="p2_noParentsConcerns4mOkConcerns"
                                            onclick="onCheck(this,'p2_noParentsConcerns4m')"
                        <carlos:encode value='<%= props.getProperty("p2_noParentsConcerns4mOkConcerns", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio"
                                            id="p2_noParentsConcerns4mNotDiscussed"
                                            name="p2_noParentsConcerns4mNotDiscussed"
                                            onclick="onCheck(this,'p2_noParentsConcerns4m')"
                        <carlos:encode value='<%= props.getProperty("p2_noParentsConcerns4mNotDiscussed", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><fmt:message key="encounter.formRourke2009.formNoparentConcerns"/>**
                    </td>
                </tr>

                <tr align="center">
                    <td colspan="4" style="vertical-align:bottom;"><textarea id="p2_development4m"
                                                                             name="p2_development4m" rows="5" cols="25"
                                                                             class="wide"><carlos:encode value='<%= props.getProperty("p2_development4m", "") %>' context="html"/></textarea>
                    </td>
                </tr>
            </table>
        </td>
        <td colspan="4">
            <table id="dt23" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15"
                                                                width="20" src="graphics/Checkmark_L.gif"></div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:message key="encounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <td valign="top"><input type="radio" id="p2_turnsHeadOk"
                                            name="p2_turnsHeadOk" onclick="onCheck(this,'p2_turnsHead')"
                        <carlos:encode value='<%= props.getProperty("p2_turnsHeadOk", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_turnsHeadOkConcerns"
                                            name="p2_turnsHeadOkConcerns" onclick="onCheck(this,'p2_turnsHead')"
                        <carlos:encode value='<%= props.getProperty("p2_turnsHeadOkConcerns", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_turnsHeadNotDiscussed"
                                            name="p2_turnsHeadNotDiscussed" onclick="onCheck(this,'p2_turnsHead')"
                        <carlos:encode value='<%= props.getProperty("p2_turnsHeadNotDiscussed", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><fmt:message key="encounter.formRourke2006_2.formTurnsHead"/></td>
                </tr>

                <tr>
                    <td valign="top"><input type="radio" id="p2_makesSoundOk"
                                            name="p2_makesSoundOk" onclick="onCheck(this,'p2_makesSound')"
                        <carlos:encode value='<%= props.getProperty("p2_makesSoundOk", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_makesSoundOkConcerns"
                                            name="p2_makesSoundOkConcerns" onclick="onCheck(this,'p2_makesSound')"
                        <carlos:encode value='<%= props.getProperty("p2_makesSoundOkConcerns", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_makesSoundNotDiscussed"
                                            name="p2_makesSoundNotDiscussed" onclick="onCheck(this,'p2_makesSound')"
                        <carlos:encode value='<%= props.getProperty("p2_makesSoundNotDiscussed", "") %>' context="htmlAttribute"/>></td>
                    <td><fmt:message key="encounter.formRourke2009_2.formmakesSound"/></td>
                </tr>

                <tr>
                    <td valign="top"><input type="radio" id="p2_vocalizesOk"
                                            name="p2_vocalizesOk" onclick="onCheck(this,'p2_vocalizes')"
                        <carlos:encode value='<%= props.getProperty("p2_vocalizesOk", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_vocalizesOkConcerns"
                                            name="p2_vocalizesOkConcerns" onclick="onCheck(this,'p2_vocalizes')"
                        <carlos:encode value='<%= props.getProperty("p2_vocalizesOkConcerns", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_vocalizesNotDiscussed"
                                            name="p2_vocalizesNotDiscussed" onclick="onCheck(this,'p2_vocalizes')"
                        <carlos:encode value='<%= props.getProperty("p2_vocalizesNotDiscussed", "") %>' context="htmlAttribute"/>></td>
                    <td><fmt:message key="encounter.formRourke2009_2.formVocalizes"/></td>
                </tr>

                <tr>
                    <td valign="top"><input type="radio" id="p2_rollsOk"
                                            name="p2_rollsOk" onclick="onCheck(this,'p2_rolls')"
                        <carlos:encode value='<%= props.getProperty("p2_rollsOk", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_rollsOkConcerns"
                                            name="p2_rollsOkConcerns" onclick="onCheck(this,'p2_rolls')"
                        <carlos:encode value='<%= props.getProperty("p2_rollsOkConcerns", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_rollsNotDiscussed"
                                            name="p2_rollsNotDiscussed" onclick="onCheck(this,'p2_rolls')"
                        <carlos:encode value='<%= props.getProperty("p2_rollsNotDiscussed", "") %>' context="htmlAttribute"/>></td>
                    <td><fmt:message key="encounter.formRourke2009_2.formRolls"/></td>
                </tr>

                <tr>
                    <td valign="top"><input type="radio" id="p2_sitsOk"
                                            name="p2_sitsOk" onclick="onCheck(this,'p2_sits')"
                        <carlos:encode value='<%= props.getProperty("p2_sitsOk", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_sitsOkConcerns"
                                            name="p2_sitsOkConcerns" onclick="onCheck(this,'p2_sits')"
                        <carlos:encode value='<%= props.getProperty("p2_sitsOkConcerns", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_sitsNotDiscussed"
                                            name="p2_sitsNotDiscussed" onclick="onCheck(this,'p2_sits')"
                        <carlos:encode value='<%= props.getProperty("p2_sitsNotDiscussed", "") %>' context="htmlAttribute"/>></td>
                    <td><fmt:message key="encounter.formRourke2009_2.formSits"/></td>
                </tr>

                <tr>
                    <td valign="top"><input type="radio" id="p2_reachesGraspsOk"
                                            name="p2_reachesGraspsOk" onclick="onCheck(this,'p2_reachesGrasps')"
                        <carlos:encode value='<%= props.getProperty("p2_reachesGraspsOk", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_reachesGraspsOkConcerns"
                                            name="p2_reachesGraspsOkConcerns" onclick="onCheck(this,'p2_reachesGrasps')"
                        <carlos:encode value='<%= props.getProperty("p2_reachesGraspsOkConcerns", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_reachesGraspsNotDiscussed"
                                            name="p2_reachesGraspsNotDiscussed"
                                            onclick="onCheck(this,'p2_reachesGrasps')"
                        <carlos:encode value='<%= props.getProperty("p2_reachesGraspsNotDiscussed", "") %>' context="htmlAttribute"/>></td>
                    <td><fmt:message key="encounter.formRourke2020_2.formreachesGrasps"/></td>
                </tr>
                <tr>
                    <td valign="top"><input type="radio" id="p2_NoPersistentClosed6mOk"
                                            name="p2_NoPersistentClosed6mOk"
                                            onclick="onCheck(this,'p2_NoPersistentClosed6m')"
                        <carlos:encode value='<%= props.getProperty("p2_NoPersistentClosed6mOk", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_NoPersistentClosed6mOkConcerns"
                                            name="p2_NoPersistentClosed6mOkConcerns"
                                            onclick="onCheck(this,'p2_NoPersistentClosed6m')"
                        <carlos:encode value='<%= props.getProperty("p2_NoPersistentClosed6mOkConcerns", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio" id="p2_NoPersistentClosed6mNotDiscussed"
                                            name="p2_NoPersistentClosed6mNotDiscussed"
                                            onclick="onCheck(this,'p2_NoPersistentClosed6m')"
                        <carlos:encode value='<%= props.getProperty("p2_NoPersistentClosed6mNotDiscussed", "") %>' context="htmlAttribute"/>></td>
                    <td><fmt:message key="encounter.formRourke2020.formNoPersistentClosed"/></td>
                </tr>

                <tr>
                    <td valign="top"><input type="radio"
                                            id="p2_noParentsConcerns6mOk" name="p2_noParentsConcerns6mOk"
                                            onclick="onCheck(this,'p2_noParentsConcerns6m')"
                        <carlos:encode value='<%= props.getProperty("p2_noParentsConcerns6mOk", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio"
                                            id="p2_noParentsConcerns6mOkConcerns"
                                            name="p2_noParentsConcerns6mOkConcerns"
                                            onclick="onCheck(this,'p2_noParentsConcerns6m')"
                        <carlos:encode value='<%= props.getProperty("p2_noParentsConcerns6mOkConcerns", "") %>' context="htmlAttribute"/>></td>
                    <td valign="top"><input type="radio"
                                            id="p2_noParentsConcerns6mNotDiscussed"
                                            name="p2_noParentsConcerns6mNotDiscussed"
                                            onclick="onCheck(this,'p2_noParentsConcerns6m')"
                        <carlos:encode value='<%= props.getProperty("p2_noParentsConcerns6mNotDiscussed", "") %>' context="htmlAttribute"/>></td>
                    <td><fmt:message key="encounter.formRourke2009.formNoparentConcerns"/>**
                    </td>
                </tr>

                <tr align="center">
                    <td colspan="4" style="vertical-align:bottom;"><textarea id="p2_development6m"
                                                                             name="p2_development6m" rows="5" cols="25"
                                                                             class="wide"><carlos:encode value='<%= props.getProperty("p2_development6m", "") %>' context="html"/></textarea>
                    </td>
                </tr>
            </table>
        </td>
    </tr>
    <tr id="physicalExamp2">
        <td class="column">
            <a><fmt:message key="encounter.formRourke1.msgPhysicalExamination"/></a><br>
            <fmt:message key="encounter.formRourke1.msgPhysicalExaminationDesc"/><br>
            <img height="15" width="20" src="graphics/Checkmark_Lwhite.gif">
            <fmt:message key="encounter.formRourke2009.msgPhysicalExaminationLegend"/>
        </td>
        <td colspan="4">
            <table id="pt21" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td colspan="3">&nbsp;</td>
                </tr>
                <tr>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif">
                        </div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:message key="encounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_fontanelles2m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke1.formFontanelles"/>**
                        </a>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_eyes2m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><b><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:message key="encounter.formRourke2020.formRedReflex"/>**
                    </a></b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_hearing2m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><i><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:message key="encounter.formRourke2006_1.formHearingInquiry"/>**
                    </a></i></td>
                </tr>

                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_heart2m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:message key="encounter.formRourke2020.formHeartAbdomen"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_neckTorticollis2m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:message key="encounter.formRourke2020.formNeckTorticollis"/>**
                    </a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_muscleTone2m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:message key="encounter.formRourke2006_1.formMuscleTone"/>**
                    </a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_hips2m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:message key="encounter.formRourke2020.formHips"/>**
                    </a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_skin2m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:message key="encounter.formRourke2020.formDrySkin"/>
                    </a></td>
                </tr>

                <tr>
                    <td colspan="4" style="vertical-align:bottom;">
						<textarea id="p2_physical2m"
                                  name="p2_physical2m" rows="5" cols="25"
                                  class="wide"><carlos:encode value='<%= props.getProperty("p2_physical2m", "") %>' context="html"/></textarea>

                    </td>
                </tr>
            </table>
        </td>
        <td colspan="4">
            <table id="pt22" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td colspan="4">&nbsp;</td>
                </tr>
                <tr>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15"
                                                                width="20" src="graphics/Checkmark_L.gif"></div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:message key="encounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_fontanelles4m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:message key="encounter.formRourke2020.formAnteriorFontanelle"/>**
                    </a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_eyes4m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><b><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:message key="encounter.formRourke2020.formRedReflex"/>**
                    </a></b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_hearing4m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><i><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:message key="encounter.formRourke2006_1.formHearingInquiry"/>**
                    </a></i></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_neckTorticollis4m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:message key="encounter.formRourke2020.formNeckTorticollis"/>**
                    </a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_HeartLungsAbdomen4m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:message key="encounter.formRourke2020.formHeartLungsAbdomen"/>
                    </a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_hips4m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:message key="encounter.formRourke2020.formHipsLimitedHipAdbn"/>**
                    </a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_muscleTone4m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:message key="encounter.formRourke2006_1.formMuscleTone"/>**
                    </a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_bruising4m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:message key="encounter.formRourke2020.formBruising"/>**
                    </a></td>
                </tr>
                <tr>
                    <td colspan="4" style="vertical-align:bottom;">
						<textarea id="p2_physical4m"
                                  name="p2_physical4m" rows="5" cols="25"
                                  class="wide"><carlos:encode value='<%= props.getProperty("p2_physical4m", "") %>' context="html"/></textarea>
                    </td>
                </tr>
            </table>
        </td>
        <td colspan="4">
            <table id="pt23" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td colspan="4">&nbsp;</td>
                </tr>
                <tr>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15"
                                                                width="20" src="graphics/Checkmark_L.gif"></div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:message key="encounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_fontanelles6m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:message key="encounter.formRourke2020.formAnteriorFontanelle"/>**
                    </a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_TeethRiskAssesment6m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:message key="encounter.formRourke2020.formTeethRiskAssesment"/>**
                    </a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_HeartLungsAbdomen6m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:message key="encounter.formRourke2020.formHeartLungsAbdomen"/>
                    </a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_eyes6m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><b><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:message key="encounter.formRourke2020.formRedReflex"/>**
                    </a></b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_corneal6m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:message key="encounter.formRourke2006_1.formCornealReflex"/>/<fmt:message key="encounter.formRourke2020.formCoverUncoverTest"/>**
                        </a></b>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_hearing6m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><i><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:message key="encounter.formRourke2006_1.formHearingInquiry"/>**
                    </a></i></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_hips6m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:message key="encounter.formRourke2020.formHipsLimitedHipAdbn"/>**
                    </a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_muscleTone6m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:message key="encounter.formRourke2006_1.formMuscleTone"/>**/<fmt:message key="encounter.formRourke2020_2.formHeadLag"/>
                    </a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_bruising6m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:message key="encounter.formRourke2020.formBruising"/>**
                    </a></td>
                </tr>
                <tr>
                    <td colspan="4" style="vertical-align:bottom;">
						<textarea id="p2_physical6m"
                                  name="p2_physical6m" rows="5" cols="25"
                                  class="wide"><carlos:encode value='<%= props.getProperty("p2_physical6m", "") %>' context="html"/></textarea>
                    </td>
                </tr>
            </table>
        </td>
    </tr>
    <tr id="problemsPlansp2">
        <td class="column">
            <a><fmt:message key="encounter.formRourke2020.msgProblemsAndPlans"/>/<fmt:message key="encounter.formRourke2020.msgCurrentAndNewReferrals"/></a><br/>
            <small><fmt:message key="encounter.formRourke2020.msgPlansAndReferralsDescription"/></small><br/>
            <img height="15" width="20" src="graphics/Checkmark_Lwhite.gif"><fmt:message key="encounter.formRourke2009.msgProblemsLegend"/>
        </td>
        <td colspan="4" style="vertical-align:bottom;"><textarea id="p2_problems2m" name="p2_problems2m" rows="5"
                                                                 cols="25" class="wide limit-rows"
                                                                 maxlength="400"><carlos:encode value='<%= props.getProperty("p2_problems2m", "") %>' context="html"/></textarea>
        </td>
        <td colspan="4" style="vertical-align:bottom;"><textarea id="p2_problems4m" name="p2_problems4m" rows="5"
                                                                 cols="25" class="wide limit-rows"
                                                                 maxlength="400"><carlos:encode value='<%= props.getProperty("p2_problems4m", "") %>' context="html"/></textarea>
        </td>
        <td colspan="4">
            <table id="prbt23" cellpadding="0" cellspacing="0" width="100%">
                <tr align="center">
                    <td colspan="4" style="vertical-align:bottom;"><textarea id="p2_problems6m" name="p2_problems6m"
                                                                             rows="5" cols="25" class="wide limit-rows"
                                                                             maxlength="400"><carlos:encode value='<%= props.getProperty("p2_problems6m", "") %>' context="html"/></textarea>
                    </td>
                </tr>
            </table>
        </td>
    </tr>
    <tr id="immunizationp2">
        <td class="column">
            <a><fmt:message key="encounter.formRourke2020.msgInvestigationsScreeningAndImmunization"/></a><br>
            <fmt:message key="encounter.formRourke2020.msgInvestigationsScreeningAndImmunizationDesc"/><br/>
            <img height="15" width="20" src="graphics/Checkmark_Lwhite.gif">
            <fmt:message key="encounter.formRourke2009.msgProblemsLegend"/>
        </td>
        <td colspan="4"></td>
        <td colspan="4"></td>
        <td colspan="4">
            <table id="immt23" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif">
                        </div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:message key="encounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_AnemiaScreening6m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><i><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:message key="encounter.formRourke2020.formAnemiaScreening"/>**
                    </a></i></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_tb6m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:message key="encounter.formRourke2006_2.formTB"/>**
                    </a></td>
                </tr>
                <tr>
                    <td colspan="4" style="padding-left: 4px;"><fmt:message key="encounter.formRourke2020.formImmunizationIfHepatitis"/>:
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p2_hepatitisVaccine6m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td><b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:message key="encounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                        <fmt:message key="encounter.formRourke2020.formImmunizationHepatitisVaccine3"/>***</a></b>
                    </td>
                </tr>
                <tr>
                    <td colspan="4" style="vertical-align:bottom;">
                        <textarea id="p2_immunization6m" name="p2_immunization6m" rows="5" cols="25"
                                  class="wide"><carlos:encode value='<%= props.getProperty("p2_immunization6m", "") %>' context="html"/></textarea>
                    </td>
                </tr>
            </table>
        </td>
    </tr>
    <tr>
        <td class="column"><a><fmt:message key="encounter.formRourke1.formSignature"/></a></td>
        <td colspan="4"><input type="text" class="wide"
                               style="width: 100%" name="p2_signature2m"
                               value="<carlos:encode value='<%= props.getProperty("p2_signature2m", "") %>' context="htmlAttribute"/>"/></td>
        <td colspan="4"><input type="text" class="wide" maxlength="42"
                               style="width: 100%" name="p2_signature4m"
                               value="<carlos:encode value='<%= props.getProperty("p2_signature4m", "") %>' context="htmlAttribute"/>"/></td>
        <td colspan="4"><input type="text" class="wide"
                               style="width: 100%" name="p2_signature6m"
                               value="<carlos:encode value='<%= props.getProperty("p2_signature6m", "") %>' context="htmlAttribute"/>"/></td>
    </tr>

</table>

<table cellpadding="0" cellspacing="0" class="Header" class="hidePrint">
    <tr>
        <td nowrap="true">
            <input type="submit" value="<fmt:message key="encounter.formRourke1.btnSave"/>"
                   onclick="javascript:return onSave();"/>
            <input type="submit" value="<fmt:message key="encounter.formRourke1.btnSaveExit"/>"
                   onclick="javascript:return onSaveExit();"/>
            <input type="submit" value="<fmt:message key="encounter.formRourke1.btnExit"/>"
                   onclick="javascript:return onExit();"> <br/>
            <input type="submit" value="<fmt:message key="encounter.formRourke1.btnPrint"/>"
                   onclick="javascript:return onPrint();"/>
            <input type="button" value="<fmt:message key='encounter.formRourke1.btnAbout'/>"
                   onclick="javascript:return popPage('http://rourkebabyrecord.ca','<fmt:message key='encounter.formRourke1.aboutRourkeTitle'/>');"/>
        </td>
        <td width="100%">
            <div name="saveMessageDiv"></div>
        </td>
        <td align="center" nowrap="true" width="100%">
            <% if (formId > 0) { %> <a name="length" href="#"
                                       onclick="onGraph('<%=request.getContextPath()%>/form/formname?submit=graph&form_class=Rourke2020&__title=Baby+Growth+Graph1&__cfgfile=<%=growthCharts[0]%>&demographic_no=<carlos:encode value='<%= String.valueOf(demoNo) %>' context="uriComponent"/>&formId=<carlos:encode value='<%= String.valueOf(formId) %>' context="uriComponent"/>&provNo=<carlos:encode value='<%= String.valueOf(provNo) %>' context="uriComponent"/>','<%= "growth1" + demoNo %>');return false;">
            <fmt:message key="encounter.formRourke1.btnGraphLenghtWeight"/></a><br>
            <a name="headCirc" href="#"
               onclick="onGraph('<%=request.getContextPath()%>/form/formname?submit=graph&form_class=Rourke2020&__title=Baby+Head+Circumference&__cfgfile=<%=growthCharts[1]%>&demographic_no=<carlos:encode value='<%= String.valueOf(demoNo) %>' context="uriComponent"/>&formId=<carlos:encode value='<%= String.valueOf(formId) %>' context="uriComponent"/>&provNo=<carlos:encode value='<%= String.valueOf(provNo) %>' context="uriComponent"/>','<%= "growth2" + demoNo %>');return false;">
                <fmt:message key="encounter.formRourke1.btnGraphHead"/></a> <% } else { %>
            &nbsp; <% } %>
        </td>

    </tr>
</table>
<p style="font-size: 8pt;"><fmt:message key="encounter.formRourke2009.footer"/><br/>
</p>


<script type="text/javascript">
    Calendar.setup({
        inputField: "p2_date2m",
        ifFormat: "%d/%m/%Y",
        showsTime: false,
        button: "p2_date2m_cal",
        singleClick: true,
        step: 1
    });
    Calendar.setup({
        inputField: "p2_date4m",
        ifFormat: "%d/%m/%Y",
        showsTime: false,
        button: "p2_date4m_cal",
        singleClick: true,
        step: 1
    });
    Calendar.setup({
        inputField: "p2_date6m",
        ifFormat: "%d/%m/%Y",
        showsTime: false,
        button: "p2_date6m_cal",
        singleClick: true,
        step: 1
    });
</script>
