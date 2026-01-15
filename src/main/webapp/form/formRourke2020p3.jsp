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
    String roleName2$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName2$%>" objectName="_form" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_form");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="ca.openosp.openo.util.*, ca.openosp.openo.form.*, ca.openosp.openo.form.data.*" %>
<%@ page import="ca.openosp.openo.encounter.data.EctFormData" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>

<%@ page import="ca.openosp.openo.commn.model.Demographic" %>
<%@ page import="ca.openosp.openo.managers.DemographicManager" %>
<%@ page import="ca.openosp.openo.util.UtilMisc" %>
<%@ page import="ca.openosp.openo.form.FrmRecord" %>
<%@ page import="ca.openosp.openo.form.FrmRourke2020Record" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ taglib uri="/WEB-INF/rourke-tag.tld" prefix="rourke" %>

<%
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


    String formTable = "formGrowth0_36";
    String formName = "Growth 0-36m";
    String growthChartURL = "";
    EctFormData.PatientForm[] pforms = EctFormData.getPatientFormsFromLocalAndRemote(loggedInInfo, String.valueOf(demoNo), formTable);
    if (pforms.length > 0) {
        EctFormData.PatientForm pfrm = pforms[0];
        growthChartURL = request.getContextPath() + "/form/forwardshortcutname.jsp?formname=" + formName + "&demographic_no=" + demoNo + (pfrm.getRemoteFacilityId() != null ? "&remoteFacilityId=" + pfrm.getRemoteFacilityId() + "&formId=" + pfrm.getFormId() : "");
    }
%>
<%
    boolean bView = false;
    if (request.getParameter("view") != null && request.getParameter("view").equals("1")) bView = true;
%>


<div style="width:100%;">
    <img alt="copyright" width="80%" src="graphics/Rourke2020Banner.png"
         onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formCopyRight"/>')"
         onMouseOut="hideLayer()"/>
</div>

<div id="object1"
     style="position: absolute; background-color: #FFFFDD; color: black; border-color: black; border-width: 20px; left: 25px; top: -100px; z-index: +1"
     onmouseover="overdiv=1;" onmouseout="overdiv=0; setTimeout('hideLayer()',1000)">pop up description layer
</div>

<table cellpadding="0" cellspacing="0" class="Header" class="hidePrint">
    <tr>
        <td nowrap="true">
            <input type="submit" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btnSave"/>"
                   onclick="javascript:return onSave();"/>
            <input type="submit" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btnSaveExit"/>"
                   onclick="javascript:return onSaveExit();"/>
            <input type="submit" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btnExit"/>"
                   onclick="javascript:return onExit();"><br/>
            <input type="submit" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btnPrint"/>"
                   onclick="javascript:return onPrint();"/>
            <input type="button" value="About"
                   onclick="javascript:return popPage('http://rourkebabyrecord.ca','About Rourke');"/>
        </td>
        <td width="100%">
            <div name="saveMessageDiv"></div>
        </td>
        <td align="center" nowrap="true" width="100%">
            <% if (formId > 0) { %> <a name="length" href="#"
                                       onclick="onGraph('<%=request.getContextPath()%>/form/formname.do?submit=graph&form_class=Rourke2020&__title=Baby+Growth+Graph1&__cfgfile=<%=growthCharts[0]%>&demographic_no=<%=demoNo%>&formId=<%=formId%>&provNo=<%=provNo%>','<%= "growth1" + demoNo %>'); return false;">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btnGraphLenghtWeight"/></a><br>
            <a name="headCirc" href="#"
               onclick="onGraph('<%=request.getContextPath()%>/form/formname.do?submit=graph&form_class=Rourke2020&__title=Baby+Head+Circumference&__cfgfile=<%=growthCharts[1]%>&demographic_no=<%=demoNo%>&formId=<%=formId%>&provNo=<%=provNo%>','<%= "growth2" + demoNo %>'); return false;">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btnGraphHead"/></a> <% } else { %>
            &nbsp; <% } %>
        </td>
    </tr>
</table>

<table cellpadding="0" cellspacing="0" border="0" width="100%">
    <tr class="titleBar">
        <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_3.msgRourkeBabyRecord"/></th>
    </tr>
</table>

<div id="patientInfop3">
    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgName"/>:
    <input type="text" maxlength="60" size="30" value="<%= Encode.forHtmlAttribute(props.getProperty("c_pName", "")) %>"
           readonly="true"/>
    &nbsp;&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgBirthDate"/> (d/m/yyyy):
    <input type="text" id="c_birthDate3" size="10" maxlength="10" value="<%= props.getProperty("c_birthDate", "") %>"
           readonly="true">
    &nbsp;&nbsp;Age: <input type="text" id="currentAge3" size="10" maxlength="10" readonly="true"
                            ondblclick="calcAge();">
    <% if (!((FrmRourke2020Record) rec).isFemale(loggedInInfo, demoNo)) {%>(<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgMale"/>)<%
} else { %>(<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgFemale"/>)<% } %>
</div>
<table cellpadding="0" cellspacing="0" width="100%" border="1">
    <tr align="center">
        <td class="column"><a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.visitDate"/></a></td>
        <td colspan="3" class="row"><a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_3.msg9mos"/></a></td>
        <td colspan="3" class="row"><a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_3.msg12mos"/></a></td>
        <td colspan="3" class="row"><a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_3.msg15mos"/></a></td>
    </tr>
    <tr align="center">
        <td class="column"><a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgDate"/></a></td>
        <td colspan="3">
            <img src="<%= request.getContextPath() %>/images/cal.gif" id="p3_date9m_cal" style="vertical-align: middle;">
            <input readonly type="text" id="p3_date9m" name="p3_date9m" ondblclick="resetDate(this)" size="10"
                   value="<%=UtilMisc.htmlEscape(props.getProperty("p3_date9m", ""))%>"/>
            <img src="<%= request.getContextPath() %>/images/clear.png" id="p3_date9m_delete" style="vertical-align: middle; cursor: pointer;"
                 onClick="resetDateUsingID('p3_date9m')">
        </td>
        <td colspan="3">
            <img src="<%= request.getContextPath() %>/images/cal.gif" id="p3_date12m_cal" style="vertical-align: middle;">
            <input readonly type="text" id="p3_date12m" name="p3_date12m" ondblclick="resetDate(this)" size="10"
                   value="<%=UtilMisc.htmlEscape(props.getProperty("p3_date12m", ""))%>"/>
            <img src="<%= request.getContextPath() %>/images/clear.png" id="p3_date12m_delete" style="vertical-align: middle; cursor: pointer;"
                 onClick="resetDateUsingID('p3_date12m')">
        </td>
        <td colspan="3">
            <img src="<%= request.getContextPath() %>/images/cal.gif" id="p3_date15m_cal" style="vertical-align: middle;">
            <input readonly type="text" id="p3_date15m" name="p3_date15m" ondblclick="resetDate(this)" size="10"
                   value="<%=UtilMisc.htmlEscape(props.getProperty("p3_date15m", ""))%>"/>
            <img src="<%= request.getContextPath() %>/images/clear.png" id="p3_date15m_delete" style="vertical-align: middle; cursor: pointer;"
                 onClick="resetDateUsingID('p3_date15m')">
        </td>
    </tr>
    <tr id="growthAp3" align="center">
        <td class="column" rowspan="2">
            <a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020_1.btnGrowth"/></a><br>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_1.btnGrowthmsg"/>
        </td>
        <td>
            <a href="javascript:void(0)"
               onclick="displayDemographicMeasurements('p3_ht9m', 'HT', '<%=demographic.getDemographicNo()%>',
                       '<%=demographic.getFormattedDob()%>', '<%=appointmentNo%>')">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formHt"/>
            </a>
        </td>
        <td>
            <a href="javascript:void(0)"
               onclick="displayDemographicMeasurements('p3_wt9m', 'WT', '<%=demographic.getDemographicNo()%>',
                       '<%=demographic.getFormattedDob()%>', '<%=appointmentNo%>')">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formWt"/>
            </a>
        </td>
        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_3.formHdCirc"/></td>
        <td>
            <a href="javascript:void(0)"
               onclick="displayDemographicMeasurements('p3_ht12m', 'HT', '<%=demographic.getDemographicNo()%>',
                       '<%=demographic.getFormattedDob()%>', '<%=appointmentNo%>')">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formHt"/>
            </a>
        </td>
        <td>
            <a href="javascript:void(0)"
               onclick="displayDemographicMeasurements('p3_wt12m', 'WT', '<%=demographic.getDemographicNo()%>',
                       '<%=demographic.getFormattedDob()%>', '<%=appointmentNo%>')">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_3.formWt12m"/>
            </a>
        </td>
        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_3.formHdCirc"/></td>
        <td>
            <a href="javascript:void(0)"
               onclick="displayDemographicMeasurements('p3_ht15m', 'HT', '<%=demographic.getDemographicNo()%>',
                       '<%=demographic.getFormattedDob()%>', '<%=appointmentNo%>')">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formHt"/>
            </a>
        </td>
        <td>
            <a href="javascript:void(0)"
               onclick="displayDemographicMeasurements('p3_wt15m', 'WT', '<%=demographic.getDemographicNo()%>',
                       '<%=demographic.getFormattedDob()%>', '<%=appointmentNo%>')">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formWt"/>
            </a>
        </td>
        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_3.formHdCirc"/></td>
    </tr>
    <tr id="growthBp3" align="center">
        <td><input type="text" class="wide" ondblclick="htEnglish2Metric(this);" name="p3_ht9m" id="p3_ht9m" size="4"
                   maxlength="5" value="<%= props.getProperty("p3_ht9m", "") %>"></td>
        <td><input type="text" class="wide" ondblclick="wtEnglish2Metric(this);" name="p3_wt9m" id="p3_wt9m" size="4"
                   maxlength="5" value="<%= props.getProperty("p3_wt9m", "") %>"></td>
        <td><input type="text" class="wide" ondblclick="htEnglish2Metric(this);" name="p3_hc9m" size="4" maxlength="5"
                   value="<%= props.getProperty("p3_hc9m", "") %>"></td>
        <td><input type="text" class="wide" ondblclick="htEnglish2Metric(this);" name="p3_ht12m" id="p3_ht12m" size="4"
                   maxlength="5" value="<%= props.getProperty("p3_ht12m", "") %>"></td>
        <td><input type="text" class="wide" ondblclick="wtEnglish2Metric(this);" name="p3_wt12m" id="p3_wt12m" size="4"
                   maxlength="5" value="<%= props.getProperty("p3_wt12m", "") %>"></td>
        <td><input type="text" class="wide" ondblclick="htEnglish2Metric(this);" name="p3_hc12m" size="4" maxlength="5"
                   value="<%= props.getProperty("p3_hc12m", "") %>"></td>
        <td><input type="text" class="wide" ondblclick="htEnglish2Metric(this);" name="p3_ht15m" id="p3_ht15m" size="4"
                   maxlength="5" value="<%= props.getProperty("p3_ht15m", "") %>"></td>
        <td><input type="text" class="wide" ondblclick="wtEnglish2Metric(this);" name="p3_wt15m" id="p3_wt15m" size="4"
                   maxlength="5" value="<%= props.getProperty("p3_wt15m", "") %>"></td>
        <td><input type="text" class="wide" ondblclick="htEnglish2Metric(this);" name="p3_hc15m" size="4" maxlength="5"
                   value="<%= props.getProperty("p3_hc15m", "") %>"></td>
    </tr>
    <tr align="center">
        <td class="column"><a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.msgParentConcerns"/></a></td>
        <td colspan="3"><textarea id="p3_pConcern9m" name="p3_pConcern9m" class="wide limit-rows" cols="10" rows="5"
                                  maxlength="400"><%= props.getProperty("p3_pConcern9m", "") %></textarea></td>
        <td colspan="3"><textarea id="p3_pConcern12m" name="p3_pConcern12m" class="wide limit-rows" cols="10" rows="5"
                                  maxlength="400"><%= props.getProperty("p3_pConcern12m", "") %></textarea></td>
        <td colspan="3"><textarea id="p3_pConcern15m" name="p3_pConcern15m" class="wide limit-rows" cols="10" rows="5"
                                  maxlength="400"><%= props.getProperty("p3_pConcern15m", "") %></textarea></td>
    </tr>
    <tr id="nutritionp3" align="center">
        <td class="column"><a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgNutrition"/>*</a><br/>
            <img height="15" width="20" src="graphics/Checkmark_Lwhite.gif">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.msgNutritionLegend"/>
        </td>
        <td colspan="3">
            <table id="ntp31" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif">
                        </div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNo"/></td>
                    <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <rourke:discussionRadioSelect sectionName="p3_breastFeeding9m" formProperties="<%=props%>"
                                              showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                <td><b><a href="javascript:showNotes()"
                          onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                          onMouseOut="hideLayer()">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formBreastfeedingVitaminD"/>
                </a></b></td>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p3_formulaFeeding9m" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td><i><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_3.msgFormulaFeeding"/></a></i></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p3_ironFruitVeg9m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formIronFruitsAndVegetables"/>
                    </a></b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p3_AvoidJuiceAndBeverages9m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formAvoidJuiceAndBeverages"/>*
                    </a></b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p3_introCowMilk9m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formCowsMilkProducts"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p3_cup9m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formEncourageChangeToCup"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p3_eatsVarietyOfTextures9m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formEatsVarietyOfTexture"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p3_honey9m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formNoHoney"/>*
                    </a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p3_bottle9m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_2.msgBottle"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p3_independentSelfFeed9m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formIndependentSelfFeeding"/>*
                    </a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p3_choking9m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_2.msgChoking"/>*
                    </a></td>
                </tr>
                <tr align="center" style="vertical-align:bottom;">
                    <td colspan="5"><textarea id="p3_nutrition9m" name="p3_nutrition9m" class="wide" rows="5"
                                              cols="25"><%= props.getProperty("p3_nutrition9m", "") %></textarea></td>
                </tr>
            </table>
        </td>
        <td colspan="3">
            <table id="ntp32" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif">
                        </div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNo"/></td>
                    <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p3_breastFeeding12m" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td><b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formBreastfeedingVitaminD"/>
                    </a></b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p3_homoMilk12m" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td><a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formHomogenizedMilk"/>
                    </a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p3_AvoidJuiceAndBeverages12m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formAvoidJuiceAndBeverages"/>*
                    </a></b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p3_appetite12m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2.formAppetiteReduced"/></td>
                </tr>
    </tr>
    <tr>
        <rourke:discussionRadioSelect sectionName="p3_NoBottles12m" formProperties="<%=props%>"
                                      showNotDiscussedOption="<%=true%>"/>
        <td><a href="javascript:showNotes()"
               onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
               onMouseOut="hideLayer()">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formNoBottlesInBed"/>
        </a></td>
    </tr>
    <tr>
        <rourke:discussionRadioSelect sectionName="p3_cup12m" formProperties="<%=props%>"
                                      showNotDiscussedOption="<%=true%>"/>
        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formPromoteOpenCup"/></td>
    </tr>
    <tr>
        <rourke:discussionRadioSelect sectionName="p3_inquireVegetarian12m" formProperties="<%=props%>"
                                      showNotDiscussedOption="<%=true%>"/>
        <td><a href="javascript:showNotes()"
               onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
               onMouseOut="hideLayer()">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formInquireVegetarianDiets"/>*
        </a></td>
    </tr>
    <tr>
        <rourke:discussionRadioSelect sectionName="p3_eatsVarietyOfTextures12m" formProperties="<%=props%>"
                                      showNotDiscussedOption="<%=true%>"/>
        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formEatsFamilyFoodTextures"/>*</td>
    </tr>
    <tr>
        <rourke:discussionRadioSelect sectionName="p3_independentSelfFeed12m" formProperties="<%=props%>"
                                      showNotDiscussedOption="<%=true%>"/>
        <td><a href="javascript:showNotes()"
               onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
               onMouseOut="hideLayer()">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formIndependentSelfFeeding"/>*
        </a></td>
    </tr>
    <tr>
        <rourke:discussionRadioSelect sectionName="p3_choking12m" formProperties="<%=props%>"
                                      showNotDiscussedOption="<%=true%>"/>
        <td><a href="javascript:showNotes()"
               onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
               onMouseOut="hideLayer()">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_2.msgChoking"/>*
        </a></td>
    </tr>
    <tr align="center">
        <td colspan="5" style="vertical-align:bottom;"><textarea id="p3_nutrition12m" name="p3_nutrition12m"
                                                                 class="wide" rows="5"
                                                                 cols="25"><%= props.getProperty("p3_nutrition12m", "") %></textarea>
        </td>
    </tr>
</table>
</td>
<td colspan="3">
    <table id="ntp33" cellpadding="0" cellspacing="0" width="100%">
        <tr>
            <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif"></div>
            </td>
            <td class="edcol" valign="top">X</td>
            <td class="edcol" valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNo"/></td>
            <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
        </tr>
        <tr>
            <rourke:discussionRadioSelect sectionName="p3_breastFeeding15m" formProperties="<%=props%>"
                                          showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
            <td><b><a href="javascript:showNotes()"
                      onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                      onMouseOut="hideLayer()">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formBreastfeedingVitaminD"/>
            </a></b></td>
        </tr>
        <tr>
            <rourke:discussionRadioSelect sectionName="p3_homoMilk15m" formProperties="<%=props%>"
                                          showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
            <td><a href="javascript:showNotes()"
                   onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                   onMouseOut="hideLayer()">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formHomogenizedMilk"/>
            </a></td>
        </tr>
        <tr>
            <rourke:discussionRadioSelect sectionName="p3_AvoidJuiceAndBeverages15m" formProperties="<%=props%>"
                                          showNotDiscussedOption="<%=true%>"/>
            <td><b><a href="javascript:showNotes()"
                      onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                      onMouseOut="hideLayer()">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formAvoidJuiceAndBeverages"/>*
            </a></b></td>
        </tr>
        <tr>
            <rourke:discussionRadioSelect sectionName="p3_cup15m" formProperties="<%=props%>"
                                          showNotDiscussedOption="<%=true%>"/>
            <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formPromoteOpenCup"/></td>
        </tr>
        <tr>
            <rourke:discussionRadioSelect sectionName="p3_NoBottles15m" formProperties="<%=props%>"
                                          showNotDiscussedOption="<%=true%>"/>
            <td><a href="javascript:showNotes()"
                   onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                   onMouseOut="hideLayer()">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formNoBottlesInBed"/>
            </a></td>
        </tr>
        <tr>
            <rourke:discussionRadioSelect sectionName="p3_inquireVegetarian15m" formProperties="<%=props%>"
                                          showNotDiscussedOption="<%=true%>"/>
            <td><a href="javascript:showNotes()"
                   onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                   onMouseOut="hideLayer()">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formInquireVegetarianDiets"/>*
            </a></td>
        </tr>
        <tr>
            <rourke:discussionRadioSelect sectionName="p3_independentSelfFeed15m" formProperties="<%=props%>"
                                          showNotDiscussedOption="<%=true%>"/>
            <td><a href="javascript:showNotes()"
                   onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                   onMouseOut="hideLayer()">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formIndependentSelfFeeding"/>*
            </a></td>
        </tr>
        <tr>
            <rourke:discussionRadioSelect sectionName="p3_choking15m" formProperties="<%=props%>"
                                          showNotDiscussedOption="<%=true%>"/>
            <td><a href="javascript:showNotes()"
                   onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                   onMouseOut="hideLayer()">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_2.msgChoking"/>*
            </a></td>
        </tr>
        <tr align="center">
            <td colspan="5" style="vertical-align:bottom;"><textarea id="p3_nutrition15m" name="p3_nutrition15m"
                                                                     class="wide" rows="5"
                                                                     cols="25"><%= props.getProperty("p3_nutrition15m", "") %></textarea>
            </td>
        </tr>
    </table>
</td>
</tr>
<tr id="educationp3">
    <td class="column">
        <a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgEducational"/></a><br/>
        <small><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgEducationalSubtitle"/></small><br/>
        <img height="15" width="20" src="graphics/Checkmark_Lwhite.gif"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.msgEducationalLegend"/>
    </td>
    <td colspan="9" valign="top">
        <table id="edt3" style="font-size: 9pt; height: 466px;" cellpadding="0" cellspacing="0" width="100%">
            <tr>
                <td colspan="16">&nbsp;</td>
            </tr>
            <tr>
                <td valign="top" colspan="20"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formInjuryPrev"/></td>
            </tr>
            <tr>
                <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                    <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif"></div>
                </td>
                <td class="edcol" valign="top">X</td>
                <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                    <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif"></div>
                </td>
                <td class="edcol" valign="top">X</td>
                <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                    <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif"></div>
                </td>
                <td class="edcol" valign="top">X</td>
                <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                    <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif"></div>
                </td>
                <td class="edcol" valign="top">X</td>
                <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
            </tr>
            <tr>

                <rourke:discussionRadioSelect sectionName="p3_carSeat" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top">
                    <b><a onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                          onMouseOut="hideLayer()" href="javascript:showNotes()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020_1.formMotorizedvehicles"/>/<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020_1.formCarSeat"/>
                    </a>*</b>
                </td>
                <rourke:discussionRadioSelect sectionName="p3_SafeSleep" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top">
                    <b><a onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                          onMouseOut="hideLayer()" href="javascript:showNotes()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formSafeSleep"/>*
                    </a></b>
                </td>
                <rourke:discussionRadioSelect sectionName="p3_poisons" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top">
                    <b><a onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                          onMouseOut="hideLayer()" href="javascript:showNotes()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_2.formPoisons"/>
                    </a></b>
                </td>
                <rourke:discussionRadioSelect sectionName="p3_smokeSafety" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top">
                    <a href="javascript:showNotes()"
                       onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                       onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formSmokeSafety"/>*
                    </a>
                </td>
                <rourke:discussionRadioSelect sectionName="p3_firearmSafety" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top">
                    <b><a href="javascript:showNotes()"
                          onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                          onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formFireArm"/>*
                    </a></b>
                </td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_hotWater" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top">
                    <i><a href="javascript:showNotes()"
                          onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                          onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formBathSafety"/>*/<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formBurns"/>*
                    </a></i>
                </td>
                <rourke:discussionRadioSelect sectionName="p3_safeToys" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top">
                    <a href="javascript:showNotes()"
                       onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                       onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formSafeToys"/>*
                    </a>
                </td>
                <rourke:discussionRadioSelect sectionName="p3_pacifier" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top">
                    <i><a href="javascript:showNotes()"
                          onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                          onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_2.formPacifierUse"/>*
                    </a></i>
                </td>
                <rourke:discussionRadioSelect sectionName="p3_electric" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top">
                    <i><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formElectricPlugs"/></i>
                </td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_falls" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td colspan="5" valign="top">
                    <i><a href="javascript:showNotes()"
                          onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                          onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_2.formFalls"/>*
                    </a></i>
                </td>
            </tr>
            <tr>
                <td colspan="16">&nbsp;</td>
            </tr>
            <tr>
                <td valign="top" colspan="16"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formBehaviour"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_crying" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top">
                    <a href="javascript:showNotes()"
                       onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                       onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formBehaviourCrying"/>**</a>
                </td>
                <rourke:discussionRadioSelect sectionName="p3_healthySleep" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top">
                    <b><a href="javascript:showNotes()"
                          onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                          onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formHealthySleep"/>**
                    </a></b>
                </td>
                <rourke:discussionRadioSelect sectionName="p3_nightWaking" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top">
                    <b><a href="javascript:showNotes()"
                          onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                          onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formNightWaking"/>**
                    </a></b>
                </td>
                <rourke:discussionRadioSelect sectionName="p3_soothability" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formSoothability"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_parenting" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top">
                    <b><a href="javascript:showNotes()"
                          onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                          onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formParenting"/>**
                    </a></b>
                </td>
                <rourke:discussionRadioSelect sectionName="p3_famConflict" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formFamConflict"/></td>

                <rourke:discussionRadioSelect sectionName="p3_siblings" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formSiblings"/></td>

                <rourke:discussionRadioSelect sectionName="p3_childCare" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top">
                    <i><a href="javascript:showNotes()"
                          onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                          onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formChildCare"/>
                    </a></i>
                </td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_pFatigue" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top">
                    <b><a href="javascript:showNotes()"
                          onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                          onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formParentFatigueDepression"/>**
                    </a></b>
                </td>
                <rourke:discussionRadioSelect sectionName="p3_reading" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top">
                    <i><a href="javascript:showNotes()"
                          onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                          onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_3.formEncourageReading"/>**
                    </a></i>
                </td>
                <rourke:discussionRadioSelect sectionName="p3_homeVisit" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td colspan="5" valign="top">
                    <b><a href="javascript:showNotes()"
                          onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                          onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formHighRiskOrHomeVisit"/>**
                    </a></b>
                </td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_makingEndsMeetInquiry" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td colspan="5" valign="top">
                    <b><a href="javascript:showNotes()"
                          onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                          onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formMakingEndsMeetInquiry"/>**
                    </a></b>
                </td>
                <rourke:discussionRadioSelect sectionName="p3_active" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td colspan="5" valign="top">
                    <b><a href="javascript:showNotes()"
                          onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                          onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formHealthyActiveLiving"/>**
                    </a></b>
                </td>
            </tr>
            <tr>
                <td colspan="16">&nbsp;</td>
            </tr>
            <tr>
                <td valign="top" colspan="16">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formEnvironmentalHealth"/> &amp; <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formOtherIssues"/>
                </td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_2ndSmoke" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top">
                    <b><a href="javascript:showNotes()"
                          onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                          onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020_1.formSecondHandSmoke"/>*
                    </a></b>
                </td>
                <rourke:discussionRadioSelect sectionName="p3_pesticides" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top">
                    <i><a href="javascript:showNotes()"
                          onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                          onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_2.formPesticides"/>*
                    </a></i>
                </td>
                <rourke:discussionRadioSelect sectionName="p3_fever" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top">
                    <a href="javascript:showNotes()"
                       onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                       onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formFever"/>*
                    </a>
                </td>
                <rourke:discussionRadioSelect sectionName="p3_footwear" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top">
                    <a href="javascript:showNotes()"
                       onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                       onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formFootwear"/>*
                    </a>
                </td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_sunExposure" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td colspan="5" valign="top">
                    <a href="javascript:showNotes()"
                       onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                       onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formSunExposure"/>*
                    </a>
                </td>
                <rourke:discussionRadioSelect sectionName="p3_altMed" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td colspan="5" valign="top">
                    <i><a href="javascript:showNotes()"
                          onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                          onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formAltMed"/>*
                    </a></i>
                </td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_noCoughMed" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td colspan="5" valign="top">
                    <b><a href="javascript:showNotes()"
                          onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                          onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formNoOtcColdMed"/>*
                    </a></b>
                </td>
                <rourke:discussionRadioSelect sectionName="p3_teething" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td colspan="5" valign="top">
                    <a href="javascript:showNotes()"
                       onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                       onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formDental"/>*
                    </a>
                </td>
            </tr>
            <tr>
                <td colspan="16">&nbsp;</td>
            </tr>
            <tr>
                <td style="vertical-align:bottom;" colspan="16">
                    <table cellpadding="0" cellspacing="0" width="100%">
                        <tr>
                            <td style="vertical-align:bottom;">
                                <textarea id="p3_education9m" name="p3_education9m" style="width: 100%"
                                          rows="5"><%= props.getProperty("p3_education9m", "") %></textarea>
                            </td>
                            <td style="vertical-align:bottom;">
                                <textarea id="p3_education12m" name="p3_education12m" style="width: 100%"
                                          rows="5"><%= props.getProperty("p3_education12m", "") %></textarea>
                            </td>
                            <td style="vertical-align:bottom;">
                                <textarea id="p3_education15m" name="p3_education15m" style="width: 100%"
                                          rows="5"><%= props.getProperty("p3_education15m", "") %></textarea>
                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
        </table>
    </td>
</tr>
<tr id="developmentp3">
    <td class="column">
        <a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgDevelopment"/>**</a><br>
        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.msgDevelopmentDesc"/><br>
        <img height="15" width="20" src="graphics/Checkmark_Lwhite.gif"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.msgDevelopmentLegend"/>
    </td>
    <td colspan="3" align="center">
        <table id="dt31" cellpadding="0" cellspacing="0" width="100%">
            <tr>
                <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                    <div class="checkmark-img-wrapper"><img height="15"
                                                            width="20" src="graphics/Checkmark_L.gif"></div>
                </td>
                <td class="edcol" valign="top">X</td>
                <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_hiddenToy" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_3.formhiddenToy"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_sounds" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_3.formSounds"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_responds2people" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_3.formResponds2people"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_makeSounds" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_3.formMakeSounds"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_sits" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_3.formSits"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_UseBothHand9m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formUseBothHand"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_stands" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_3.formStands"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_thumb" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formThumbAndFingersGrasp"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_playGames" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_3.formplayGames"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_attention9m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_3.formAttention"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_noParentsConcerns9m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNoparentConcerns"/>**</td>
            </tr>
            <tr align="center">
                <td colspan="4" style="vertical-align:bottom;">
                    <textarea id="p3_development9m" name="p3_development9m" rows="5" cols="25"
                              class="wide"><%= props.getProperty("p3_development9m", "") %></textarea>
                </td>
            </tr>
        </table>
    </td>
    <td colspan="3" align="center">
        <table id="dt32" cellpadding="0" cellspacing="0" width="100%">
            <tr>
                <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                    <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif"></div>
                </td>
                <td class="edcol" valign="top">X</td>
                <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_responds2name" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_3.formResponds"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_simpleRequests" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_3.formSimpleReq"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_consonant" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_3.formConsonant"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_says3words" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_3.formsays3words"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_shuffles" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formCrawls"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_pull2stand" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_3.formPulltoStand"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_hasPincerGrasp" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formHasPincerGrasp"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_UseBothHand12m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formUseBothHand"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_showDistress" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_3.formShowDistress"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_followGaze" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_3.formfollowGaze"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_noParentsConcerns12m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNoparentConcerns"/>**</td>
            </tr>
            <tr align="center">
                <td colspan="4" style="vertical-align:bottom;">
                    <textarea id="p3_development12m" name="p3_development12m" rows="5" cols="25"
                              class="wide"><%= props.getProperty("p3_development12m", "") %></textarea>
                </td>
            </tr>
        </table>
    </td>
    <td colspan="3">
        <table id="dt33" cellpadding="0" cellspacing="0" width="100%">
            <tr>
                <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                    <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif"></div>
                </td>
                <td class="edcol" valign="top">X</td>
                <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_says5words" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_3.formSays5words"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_walksSideways" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_3.formwalksSideways"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_showsFearStrangers" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_3.formshowsFearStrangers"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_crawlsStairs" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_3.formCrawlsStairs"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_squats" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_3.formSquats"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_noParentsConcerns15m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNoparentConcerns"/>**</td>
            </tr>
            <tr>
                <td colspan="4" style="vertical-align:bottom;">
                    <textarea id="p3_development15m" name="p3_development15m" rows="5" cols="25"
                              class="wide"><%= props.getProperty("p3_development15m", "") %></textarea>
                </td>
            </tr>
        </table>
    </td>
</tr>
<tr id="physicalExamp3">
    <td class="column">
        <a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgPhysicalExamination"/></a><br>
        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgPhysicalExaminationDesc"/><br>
        <img height="15" width="20" src="graphics/Checkmark_Lwhite.gif">
        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.msgPhysicalExaminationLegend"/>
    </td>
    <td colspan="3">
        <table id="pt31" cellpadding="0" cellspacing="0" width="100%">
            <tr>
                <td colspan="4">&nbsp;</td>
            </tr>
            <tr>
                <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                    <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif"></div>
                </td>
                <td class="edcol" valign="top">X</td>
                <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_fontanelles9m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formAnteriorFontanelle"/>**
                </a></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_eyes9m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><b><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formRedReflex"/>**
                </a></b></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_corneal9m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top">
                    <b><a href="javascript:showNotes()"
                          onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                          onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formCornealReflex"/>/<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formCoverUncoverTest"/>**
                    </a></b>
                </td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_hearing9m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><i><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formHearingInquiry"/>**
                </a></i></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_hips9m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formHipsLimitedHipAdbn"/>**
                </a></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_HeartLungsAbdomen9m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formHeartLungsAbdomen"/>
                </a></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_teeth9m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formTeethRiskAssesment"/>**
                </a></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_MuscleTone9m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formMuscleTone"/>**
                </a></td>
            </tr>
            <tr>
                <td colspan="4" style="vertical-align:bottom">
                    <textarea id="p3_physical9m" name="p3_physical9m" class="wide" rows="5"
                              cols="25"><%= props.getProperty("p3_physical9m", "") %></textarea>
                </td>
            </tr>
        </table>
    </td>
    <td colspan="3">
        <table id="pt32" cellpadding="0" cellspacing="0" width="100%">
            <tr>
                <td colspan="3">&nbsp;</td>
            </tr>
            <tr>
                <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                    <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif"></div>
                </td>
                <td class="edcol" valign="top">X</td>
                <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_fontanelles12m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formAnteriorFontanelle"/>**
                </a></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_eyes12m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><b><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formRedReflex"/>**
                </a></b></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_corneal12m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top">
                    <b><a href="javascript:showNotes()"
                          onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                          onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formCornealReflex"/>/<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formCoverUncoverTest"/>**
                    </a></b>
                </td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_hearing12m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><i><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formHearingInquiry"/>**
                </a></i></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_tonsilSize12m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><b><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formTonsilSize"/>**
                </a></b></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_HeartLungsAbdomen12m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formHeartLungsAbdomen"/>
                </a></td>
            </tr>

            <tr>
                <rourke:discussionRadioSelect sectionName="p3_teeth12m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formTeethRiskAssesment"/>**
                </a></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_hips12m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formHipsLimitedHipAdbn"/>**
                </a></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_MuscleTone12m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formMuscleTone"/>**
                </a></td>
            </tr>
            <tr>
                <td colspan="4" style="vertical-align:bottom;">
                    <textarea id="p3_physical12m" name="p3_physical12m" class="wide" rows="5"
                              cols="25"><%= props.getProperty("p3_physical12m", "") %></textarea>
                </td>
            </tr>
        </table>
    </td>
    <td colspan="3">
        <table id="pt33" cellpadding="0" cellspacing="0" width="100%">
            <tr>
                <td colspan="3">&nbsp;</td>
            </tr>
            <tr>
                <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                    <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif"></div>
                </td>
                <td class="edcol" valign="top">X</td>
                <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_fontanelles15m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formAnteriorFontanelle"/>**
                </a></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_eyes15m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><b><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formRedReflex"/>**
                </a></b></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_corneal15m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top">
                    <b><a href="javascript:showNotes()"
                          onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                          onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formCornealReflex"/>/<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formCoverUncoverTest"/>**
                    </a></b>
                </td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_hearing15m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><i><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formHearingInquiry"/>**
                </a></i></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_tonsilSize15m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><b><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formTonsilSize"/>**
                </a></b></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_hips15m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formHipsLimitedHipAdbn"/>**
                </a></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_HeartLungsAbdomen15m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formHeartLungsAbdomen"/>
                </a></td>
            </tr>
            <tr>
                <rourke:discussionRadioSelect sectionName="p3_teeth15m" formProperties="<%=props%>"
                                              showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formTeethRiskAssesment"/>**
                </a></td>
            </tr>
            <tr>
                <td colspan="4" style="vertical-align:bottom;">
                    <textarea id="p3_physical15m" name="p3_physical15m" class="wide" rows="5"
                              cols="25"><%= props.getProperty("p3_physical15m", "") %></textarea>
                </td>
            </tr>
        </table>
    </td>
</tr>
<tr id="problemsPlansp3">
    <td class="column">
        <a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.msgProblemsAndPlans"/>/<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.msgCurrentAndNewReferrals"/></a><br/>
        <small><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.msgPlansAndReferralsDescription"/></small><br/>
        <img height="15" width="20" src="graphics/Checkmark_Lwhite.gif"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.msgProblemsLegend"/>
    </td>
    <td colspan="3">
        <table id="prbt31" cellpadding="0" cellspacing="0" width="100%">
            <tr align="center">
                <td colspan="4" style="vertical-align:bottom;">
                    <textarea id="p3_problems9m" name="p3_problems9m" rows="5" cols="25" class="wide limit-rows"
                              maxlength="400"><%= props.getProperty("p3_problems9m", "") %></textarea>
                </td>
            </tr>
        </table>
    </td>
    <td colspan="3">
        <table id="prbt32" cellpadding="0" cellspacing="0" width="100%">
            <tr align="center">
                <td colspan="4" style="vertical-align:bottom;">
                    <textarea id="p3_problems12m" name="p3_problems12m" rows="5" cols="25" class="wide limit-rows"
                              maxlength="400"><%= props.getProperty("p3_problems12m", "") %></textarea>
                </td>
            </tr>
        </table>
    </td>
    <td colspan="3" height="100%" style="vertical-align:bottom;">
        <textarea id="p3_problems15m" name="p3_problems15m" rows="5" cols="25" class="wide limit-rows"
                  maxlength="400"><%= props.getProperty("p3_problems15m", "") %></textarea>
    </td>
</tr>
<tr id="immunizationp3">
    <td class="column">
        <a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.msgInvestigationsScreeningAndImmunization"/></a><br>
        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.msgInvestigationsScreeningAndImmunizationDesc"/><br/>
        <img height="15" width="20" src="graphics/Checkmark_Lwhite.gif">
        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.msgProblemsLegend"/>
    </td>
    <td colspan="9" valign="top">
        <table id="immt31" style="font-size: 9pt;" cellpadding="0" cellspacing="0" width="100%">
            <tr>
                <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                    <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif"></div>
                </td>
                <td class="edcol" valign="top">X</td>
                <td class="edcol" valign="top" colspan="3"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>

            </tr>

            <rourke:discussionRadioSelect sectionName="p3_BloodLead" formProperties="<%=props%>"
                                          showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
            <td colspan="3"><i><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formBloodLead"/>*
            </a></i></td>
</tr>
<tr>
    <rourke:discussionRadioSelect sectionName="p3_AnemiaScreening" formProperties="<%=props%>" showNoOption="<%=false%>"
                                  showNotDiscussedOption="<%=true%>"/>
    <td valign="top"><i><a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formAnemiaScreening"/>**
    </a></i></td>
</tr>
<tr>
    <rourke:discussionRadioSelect sectionName="p3_HbvAntibodies" formProperties="<%=props%>" showNoOption="<%=false%>"
                                  showNotDiscussedOption="<%=true%>"/>
    <td colspan="7"><b><a href="javascript:showNotes()"
                          onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                          onMouseOut="hideLayer()">
        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.form9Or12MonthsHbvAntibodies"/>***
    </a></b></td>
</tr>
<tr>
    <td colspan="9" style="vertical-align:bottom;">
        <textarea id="p3_immunization" name="p3_immunization" rows="5" cols="25" class="wide"></textarea>
    </td>
</tr>
</table>
</td>
</tr>
<tr>
    <td class="column"><a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formSignature"/></a></td>
    <td colspan="3"><input type="text" class="wide" style="width: 100%" name="p3_signature9m"
                           value="<%= props.getProperty("p3_signature9m", "") %>"/></td>
    <td colspan="3"><input type="text" class="wide" maxlength="42" style="width: 100%" name="p3_signature12m"
                           value="<%= props.getProperty("p3_signature12m", "") %>"/></td>
    <td colspan="3"><input type="text" class="wide" style="width: 100%" name="p3_signature15m"
                           value="<%= props.getProperty("p3_signature15m", "") %>"/></td>
</tr>
</table>
<table cellpadding="0" cellspacing="0" class="Header" class="hidePrint">
    <tr>
        <td nowrap="true">
            <input type="submit" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btnSave"/>"
                   onclick="javascript:return onSave();"/>
            <input type="submit" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btnSaveExit"/>"
                   onclick="javascript:return onSaveExit();"/>
            <input type="submit" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btnExit"/>"
                   onclick="javascript:return onExit();"><br/>
            <input type="submit" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btnPrint"/>"
                   onclick="javascript:return onPrint();"/>
            <input type="button" value="About"
                   onclick="javascript:return popPage('http://rourkebabyrecord.ca','About Rourke');"/>
        </td>
        <td width="100%">
            <div name="saveMessageDiv"></div>
        </td>
        <td align="center" nowrap="true" width="100%">
            <% if (formId > 0) { %> <a name="length" href="#"
                                       onclick="onGraph('<%=request.getContextPath()%>/form/formname.do?submit=graph&form_class=Rourke2020&__title=Baby+Growth+Graph1&__cfgfile=<%=growthCharts[0]%>&demographic_no=<%=demoNo%>&formId=<%=formId%>&provNo=<%=provNo%>','<%= "growth1" + demoNo %>');return false;">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btnGraphLenghtWeight"/></a><br>
            <a name="headCirc" href="#"
               onclick="onGraph('<%=request.getContextPath()%>/form/formname.do?submit=graph&form_class=Rourke2020&__title=Baby+Head+Circumference&__cfgfile=<%=growthCharts[1]%>&demographic_no=<%=demoNo%>&formId=<%=formId%>&provNo=<%=provNo%>','<%= "growth2" + demoNo %>');return false;">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btnGraphHead"/></a> <% } else { %>
            &nbsp; <% } %>
        </td>

    </tr>
</table>
<p style="font-size: 8pt;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.footer"/><br/>
</p>

<script type="text/javascript">
    Calendar.setup({
        inputField: "p3_date9m",
        ifFormat: "%d/%m/%Y",
        showsTime: false,
        button: "p3_date9m_cal",
        singleClick: true,
        step: 1
    });
    Calendar.setup({
        inputField: "p3_date12m",
        ifFormat: "%d/%m/%Y",
        showsTime: false,
        button: "p3_date12m_cal",
        singleClick: true,
        step: 1
    });
    Calendar.setup({
        inputField: "p3_date15m",
        ifFormat: "%d/%m/%Y",
        showsTime: false,
        button: "p3_date15m_cal",
        singleClick: true,
        step: 1
    });
</script>
