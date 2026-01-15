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
    String roleName2$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
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
<%@ page import="ca.openosp.openo.commn.dao.DemographicDao" %>
<%@ page import="ca.openosp.openo.commn.model.Demographic" %>
<%@ page import="ca.openosp.openo.managers.DemographicManager" %>
<%@ page import="ca.openosp.openo.util.UtilMisc" %>
<%@ page import="ca.openosp.openo.form.FrmRecord" %>
<%@ page import="ca.openosp.openo.form.FrmRourke2017Record" %>
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

    if (((FrmRourke2017Record) rec).isFemale(loggedInInfo, demoNo)) {
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

<body bgproperties="fixed" topmargin="0" leftmargin="0" rightmargin="0">
<div style="width:100%;">
    <img alt="copyright" src="graphics/rourke2017Banner.png"
         onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formCopyRight"/>')"
         onMouseOut="hideLayer()">
</div>
<div id="object1"
     style="position: absolute; background-color: #FFFFDD; color: black; border-color: black; border-width: 20px; left: 25px; top: -100px; z-index: +1"
     onmouseover="overdiv=1;" onmouseout="overdiv=0; setTimeout('hideLayer()',1000)">pop up description layer
</div>
<table cellpadding="0" cellspacing="0" class="Header" class="hidePrint">
    <tr>
        <td nowrap="true">
            <input type="button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btnSave"/>"
                   onclick="javascript:onSave(); return false;"/>
            <input type="button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btnSaveExit"/>"
                   onclick="javascript:onSaveExit(); return false;"/>
            <input type="button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btnExit"/>"
                   onclick="javascript:onExit(); return false;"/><br/>
            <input type="submit" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btnPrint"/>"
                   onclick="javascript:return onPrint();"/>
            <input type="button" value="About"
                   onclick="javascript:return popPage('http://rourkebabyrecord.ca','About Rourke');"/>
        </td>
        <td width="100%">
            <div name="saveMessageDiv"></div>
        </td>
        <td align="center" nowrap="true" width="100%">
            <% if (formId > 0) { %>
            <a name="length" href="#"
               onclick="onGraph('<%=request.getContextPath()%>/form/formname.do?submit=graph&form_class=Rourke2017&__title=Baby+Growth+Graph1&__cfgfile=<%=growthCharts[0]%>&demographic_no=<%=demoNo%>&formId=<%=formId%>&provNo=<%=provNo%>','<%= "growth1" + demoNo %>');return false;">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btnGraphLenghtWeight"/>
            </a><br>
            <a name="headCirc" href="#"
               onclick="onGraph('<%=request.getContextPath()%>/form/formname.do?submit=graph&form_class=Rourke2017&__title=Baby+Head+Circumference&__cfgfile=<%=growthCharts[1]%>&demographic_no=<%=demoNo%>&formId=<%=formId%>&provNo=<%=provNo%>','<%= "growth2" + demoNo %>');return false;">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btnGraphHead"/>
            </a> <% } else { %>
            &nbsp; <% } %>
        </td>
    </tr>
</table>
<table cellpadding="0" cellspacing="0" border="0" width="100%">
    <tr class="titleBar">
        <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.msgRourkeBabyRecord"/></th>
    </tr>
</table>
<div id="patientInfop4">
    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgName"/>:
    <input type="text" maxlength="60" size="30" value="<%= Encode.forHtmlAttribute(props.getProperty("c_pName", "")) %>"
           readonly="true"/>
    &nbsp;&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgBirthDate"/> (d/m/yyyy):
    <input type="text" id="c_birthDate4" size="10" maxlength="10" value="<%= props.getProperty("c_birthDate", "") %>"
           readonly="true"/>
    &nbsp;&nbsp;
    Age: <input type="text" id="currentAge4" size="10" maxlength="10" readonly="true" ondblclick="calcAge();"/>
    <% if (!((FrmRourke2017Record) rec).isFemale(loggedInInfo, demoNo)) { %>(<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgMale"/>)
    <% } else { %>(<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgFemale"/>)
    <% } %>
</div>
<table cellpadding="0" cellspacing="0" width="100%" border="1">
    <tr align="center">
        <td colspan="3" class="row"><a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.msg18mos"/></a></td>
        <td colspan="4" class="row"><a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.msg2yrs"/></a></td>
        <td colspan="3" class="row"><a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.msg4yrs"/></a></td>
        <td class="column"><a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.visitDate"/></a></td>
    </tr>
    <tr align="center">
        <td colspan="3">
            <input readonly type="text" id="p4_date18m" name="p4_date18m" ondblclick="resetDate(this)" size="10"
                   value="<%=UtilMisc.htmlEscape(props.getProperty("p4_date18m", ""))%>"/>
            <img src="<%= request.getContextPath() %>/images/cal.gif" id="p4_date18m_cal">
        </td>
        <td colspan="4">
            <input readonly type="text" id="p4_date24m" name="p4_date24m" ondblclick="resetDate(this)" size="10"
                   value="<%=UtilMisc.htmlEscape(props.getProperty("p4_date24m", ""))%>"/>
            <img src="<%= request.getContextPath() %>/images/cal.gif" id="p4_date24m_cal">
        </td>
        <td colspan="3">
            <input readonly type="text" id="p4_date48m" name="p4_date48m" ondblclick="resetDate(this)" size="10"
                   value="<%=UtilMisc.htmlEscape(props.getProperty("p4_date48m", ""))%>"/>
            <img src="<%= request.getContextPath() %>/images/cal.gif" id="p4_date48m_cal">
        </td>
        <td class="column"><a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgDate"/></a></td>
    </tr>
    <tr align="center" id="growthAp4">
        <td>
            <a href="javascript:void(0)"
               onclick="displayDemographicMeasurements('p4_ht18m', 'HT', '<%=demographic.getDemographicNo()%>',
                       '<%=demographic.getFormattedDob()%>', '<%=appointmentNo%>')">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formHt"/>
            </a>
        </td>
        <td>
            <a href="javascript:void(0)"
               onclick="displayDemographicMeasurements('p4_wt18m', 'WT', '<%=demographic.getDemographicNo()%>',
                       '<%=demographic.getFormattedDob()%>', '<%=appointmentNo%>')">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formWt"/>
            </a>
        </td>
        <td>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_3.formHdCirc"/>
        </td>
        <td>
            <a href="javascript:void(0)"
               onclick="displayDemographicMeasurements('p4_ht24m', 'HT', '<%=demographic.getDemographicNo()%>',
                       '<%=demographic.getFormattedDob()%>', '<%=appointmentNo%>')">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formHt"/>
            </a>
        </td>
        <td>
            <a href="javascript:void(0)"
               onclick="displayDemographicMeasurements('p4_wt24m', 'WT', '<%=demographic.getDemographicNo()%>',
                       '<%=demographic.getFormattedDob()%>', '<%=appointmentNo%>')">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formWt"/>
            </a>
        </td>
        <td>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_3.formHdCirc"/>
        </td>
        <td>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formBmi"/>
        </td>
        <td>
            <a href="javascript:void(0)"
               onclick="displayDemographicMeasurements('p4_ht48m', 'HT', '<%=demographic.getDemographicNo()%>',
                       '<%=demographic.getFormattedDob()%>', '<%=appointmentNo%>')">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formHt"/>
            </a>
        </td>
        <td>
            <a href="javascript:void(0)"
               onclick="displayDemographicMeasurements('p4_wt48m', 'WT', '<%=demographic.getDemographicNo()%>',
                       '<%=demographic.getFormattedDob()%>', '<%=appointmentNo%>')">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formWt"/>
            </a>
        </td>
        <td>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formBmi"/>
        </td>
        <td class="column" rowspan="2">
            <a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btnGrowth"/>*<br><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_1.btnGrowthmsg"/></a>
        </td>
    </tr>
    <tr align="center" id="growthBp4">
        <td>
            <input type="text" class="wide" ondblclick="htEnglish2Metric(this)" name="p4_ht18m" id="p4_ht18m" size="4"
                   maxlength="5" value="<%= props.getProperty("p4_ht18m", "") %>"/>
        </td>
        <td>
            <input type="text" class="wide" ondblclick="wtEnglish2Metric(this)" name="p4_wt18m" id="p4_wt18m" size="4"
                   maxlength="5" value="<%= props.getProperty("p4_wt18m", "") %>"/>
        </td>
        <td>
            <input type="text" class="wide" ondblclick="htEnglish2Metric(this)" name="p4_hc18m" size="4" maxlength="5"
                   value="<%= props.getProperty("p4_hc18m", "") %>"/>
        </td>
        <td>
            <input type="text" class="wide" ondblclick="htEnglish2Metric(this)" name="p4_ht24m" id="p4_ht24m" size="4"
                   maxlength="5" value="<%= props.getProperty("p4_ht24m", "") %>"/>
        </td>
        <td>
            <input type="text" class="wide" ondblclick="wtEnglish2Metric(this);" name="p4_wt24m" id="p4_wt24m" size="4"
                   maxlength="5" value="<%= props.getProperty("p4_wt24m", "") %>"/>
        </td>
        <td>
            <input type="text" class="wide" ondblclick="htEnglish2Metric(this)" name="p4_hc24m" size="4" maxlength="5"
                   value="<%= props.getProperty("p4_hc24m", "") %>"/>
        </td>
        <td>
            <input type="text" class="wide" ondblclick="htEnglish2Metric(this)" name="p4_bmi24m" size="4" maxlength="5"
                   value="<%= props.getProperty("p4_bmi24m", "") %>"/>
        </td>
        <td>
            <input type="text" class="wide" ondblclick="htEnglish2Metric(this);" name="p4_ht48m" id="p4_ht48m" size="4"
                   maxlength="5" value="<%= props.getProperty("p4_ht48m", "") %>"/>
        </td>
        <td>
            <input type="text" class="wide" ondblclick="wtEnglish2Metric(this);" name="p4_wt48m" id="p4_wt48m" size="4"
                   maxlength="5" value="<%= props.getProperty("p4_wt48m", "") %>"/>
        </td>
        <td>
            <input type="text" class="wide" ondblclick="htEnglish2Metric(this);" name="p4_bmi48m" size="4" maxlength="5"
                   value="<%= props.getProperty("p4_bmi48m", "") %>"/>
        </td>
    </tr>
    <tr align="center">
        <td colspan="3"><textarea id="p4_pConcern18m" name="p4_pConcern18m" class="wide limit-rows" cols="10" rows="5"
                                  maxlength="450"><%= props.getProperty("p4_pConcern18m", "") %></textarea></td>
        <td colspan="4"><textarea id="p4_pConcern24m" name="p4_pConcern24m" class="wide limit-rows" cols="10" rows="5"
                                  maxlength="450"><%= props.getProperty("p4_pConcern24m", "") %></textarea></td>
        <td colspan="3"><textarea id="p4_pConcern48m" name="p4_pConcern48m" class="wide limit-rows" cols="10" rows="5"
                                  maxlength="450"><%= props.getProperty("p4_pConcern48m", "") %></textarea></td>
        <td class="column"><a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.msgParentConcerns"/></a></td>
    </tr>
    <tr align="center" id="nutritionp4">
        <td colspan="3">
            <table id="ntp41" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td style="padding-right: 5pt" valign="top"><img height="15" width="20"
                                                                     src="graphics/Checkmark_L.gif"></td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNo"/></td>
                    <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_breastFeeding18m" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td><b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formBreastfeedingVitaminD"/>
                    </a></b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_homoMilk18m" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td><a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formHomogenizedMilk"/>
                    </a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_liquids18m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formSweetenedLiquids"/>*
                    </a></b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_noBottle18m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formNoBottle"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_inquireVegetarian18m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formInquireVegetarianDiets"/>*
                    </a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_independentSelfFeed18m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formIndependentSelfFeeding"/>*
                    </a></td>
                </tr>
                <tr>
                    <td colspan="5" style="vertical-align:bottom;">
                        <textarea id="p4_nutrition18m" name="p4_nutrition18m" class="wide" cols="10"
                                  rows="5"><%= props.getProperty("p4_nutrition18m", "") %></textarea>
                    </td>
                </tr>
            </table>
        </td>
        <td colspan="4">
            <table id="ntp42" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td style="padding-right: 5pt" valign="top"><img height="15" width="20"
                                                                     src="graphics/Checkmark_L.gif"></td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNo"/></td>
                    <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_breastFeeding24m" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td><b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formBreastfeedingVitaminD"/>
                    </a></b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_liquids24m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formSweetenedLiquids"/>*
                    </a></b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_inquireVegetarian24m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formInquireVegetarianDiets"/>*
                    </a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_lowerfatdiet24m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><i><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formLowerFatDiet"/>*
                    </a></i></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_homo2percent24m" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td><a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formMilk"/>
                    </a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_foodguide24m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td>
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formCanadasFoodGuide"/>*</a>
                    </td>
                </tr>
                <tr>
                    <td colspan="5" style="vertical-align:bottom;">
                        <textarea id="p4_nutrition24m" name="p4_nutrition24m" class="wide" cols="10"
                                  rows="5"><%= props.getProperty("p4_nutrition24m", "") %></textarea>
                    </td>
                </tr>
            </table>
        </td>
        <td colspan="3">
            <table id="ntp43" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td style="padding-right: 5pt" valign="top"><img height="15" width="20"
                                                                     src="graphics/Checkmark_L.gif"></td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNo"/></td>
                    <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_liquids48m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formSweetenedLiquids"/>*
                    </a></b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_inquireVegetarian48m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formInquireVegetarianDiets"/>*
                    </a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_homo2percent48m" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td><a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formMilk"/>
                    </a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_foodguide48m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td>
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formCanadasFoodGuide"/>*
                        </a>
                    </td>
                </tr>
                <tr>
                    <td colspan="5" style="vertical-align:bottom;">
                        <textarea id="p4_nutrition48m" name="p4_nutrition48m" class="wide" cols="10"
                                  rows="5"><%= props.getProperty("p4_nutrition48m", "") %></textarea>
                    </td>
                </tr>
            </table>
        </td>
        <td class="column"><a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgNutrition"/>*</a></td>
    </tr>
    <tr id="educationp4">
        <td colspan="3">
            <table id="edt41" style="font-size: 8pt;" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td valign="top" colspan="4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formInjuryPrev"/></td>
                </tr>
                <tr>
                    <td style="padding-right: 5pt" valign="top"><img height="15" width="20"
                                                                     src="graphics/Checkmark_L.gif"></td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_poisons18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()" href="javascript:showNotes()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_2.formPoisons"/>*
                        </a></b>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_carSeat18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()" href="javascript:showNotes()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formCarBoosterSeat"/>*
                        </a></b>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_bathSafety18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <i><a onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()" href="javascript:showNotes()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formBathSafety"/>*
                        </a></i>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_safeToys18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formSafeToys"/>*
                        </a>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_falls18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <i><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formFallsAndFurniture"/>*
                        </a></i>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_weanPacifier18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <i><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formWeanFromPacifier"/>*
                        </a></i>
                    </td>
                </tr>
                <tr>
                    <td valign="top" colspan="4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formBehaviourAndFamily"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_parentChild18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formParentChild"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_discipline18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.formDiscipline"/>**
                        </a></b>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_healthySleep18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formHealthySleep"/>**</a></b>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_highRisk18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formHighRisk"/>**</a></b>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_encourageReading18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <i><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_3.formEncourageReading"/>**
                        </a></i>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_pFatigue18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formParentFatigueStressDepression"/>**
                        </a></b>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_socializing18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formSocPeerPlay"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_activeLiving18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td colspan="5" valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formHealthyActiveLiving"/>**
                        </a></b>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_makingEndsMeetInquiry18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td colspan="5" valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formMakingEndsMeetInquiry"/>**
                        </a></b>
                    </td>
                </tr>
                <tr>
                    <td colspan="4" valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formEnvironmentalHealth"/> &amp; <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formOtherIssues"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_2ndSmoke18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formSecondHandSmoke"/>*
                        </a></b>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_pesticides18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <i><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_2.formPesticides"/>*
                        </a></i>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_sunExposure18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formSunExposure"/>*
                        </a>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_dentalCareOk18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.formDentalCleaning"/>*</a></b>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_toiletLearning18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formToiletLearning"/>**
                        </a>
                    </td>
                </tr>
                <tr>
                    <td colspan="4" style="vertical-align:bottom">
                        <textarea id="p4_education18m" name="p4_education18m" class="wide" cols="10"
                                  rows="5"><%= props.getProperty("p4_education18m", "") %></textarea>
                    </td>
                </tr>
            </table>
        </td>
        <td colspan="7">
            <table id="edt42" style="font-size: 8pt;" cellpadding="0" cellspacing="0" width="100%">

                <tr>
                    <td valign="top" colspan="12"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formInjuryPrev"/></td>
                </tr>
                <tr>
                    <td style="padding-right: 5pt" valign="top"><img height="15" width="20"
                                                                     src="graphics/Checkmark_L.gif"></td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                    <td style="padding-right: 5pt" valign="top"><img height="15" width="20"
                                                                     src="graphics/Checkmark_L.gif"></td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                    <td style="padding-right: 5pt" valign="top"><img height="15" width="20"
                                                                     src="graphics/Checkmark_L.gif"></td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_bikeHelmets24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()" href="javascript:showNotes()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.forBikeHelmets"/>*
                        </a></b>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p4_firearmSafety24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formFirearmSafety"/>*
                        </a></b>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p4_injuryMatches24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.forMatches"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_poisons24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()" href="javascript:showNotes()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_2.formPoisons"/>
                        </a></b>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p4_smokeSafety24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formSmokeSafety"/>*
                        </a>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p4_waterSafety24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <i><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formWaterSafety"/>*
                        </a></i>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_falls24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <i><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formFallsFurnitureAndTrampolines"/>*
                        </a></i>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p4_carSeat24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()" href="javascript:showNotes()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formCarBoosterSeat"/>*
                        </a></b>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p4_noPacifiers24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <i><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formNoPacifiers"/>*
                        </a></i>
                    </td>
                </tr>
                <tr>
                    <td valign="top" colspan="12"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formBehaviourAndFamily"/></td>
                </tr>
                <tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_highRisk24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formHighRisk"/>**</a></b>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p4_discipline24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.formDiscipline"/>**
                        </a></b>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p4_parentChild24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formParentChild"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_pFatigue24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formParentFatigueStressDepression"/>**
                        </a></b>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p4_famConflict24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formFamConflict"/></td>
                    <rourke:discussionRadioSelect sectionName="p4_siblings24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formSiblings"/></td>
                </tr>

                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_healthySleep24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formHealthySleep"/>**</a></b>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p4_schoolReadiness24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <i><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.forChildCareAndSchoolReadiness"/>**</a></i>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_encourageReading24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <i><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_3.formEncourageReading"/>**
                        </a></i>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p4_activeLiving24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td colspan="5" valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formHealthyActiveLiving"/>**
                        </a></b>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_socializing24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formSocializing"/></td>
                    <rourke:discussionRadioSelect sectionName="p4_makingEndsMeetInquiry24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td colspan="5" valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formMakingEndsMeetInquiry"/>**
                        </a></b>
                    </td>
                </tr>
                <tr>
                    <td colspan="12" valign="top">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formEnvironmentalHealth"/> &amp; <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formOtherIssues"/>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_2ndSmoke24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formSecondHandSmoke"/>*
                        </a></b>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p4_sunExposure24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td colspan="5" valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formSunExposure"/>*
                        </a>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_noCoughMed24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formNoOtcColdMed"/>*
                        </a></b>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p4_dentalCareOk24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td colspan="5" valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formDentalCleaningFluoride"/>*</a></b>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_pesticides24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <i><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_2.formPesticides"/>*
                        </a></i>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p4_altMed24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <i><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formAltMed"/>*
                        </a></i>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p4_toiletLearning24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formToiletLearning"/>**
                        </a>
                    </td>
                </tr>
                <tr>
                    <td colspan="12" style="vertical-align:bottom">
                        <table cellpadding="0" cellspacing="0" width="100%">
                            <tr>
                                <td style="vertical-align:bottom;">
                                    <textarea id="p4_education24m" name="p4_education24m" style="width: 100%"
                                              rows="5"><%= props.getProperty("p4_education24m", "") %></textarea>
                                </td>
                                <td style="vertical-align:bottom;">
                                    <textarea id="p4_education48m" name="p4_education48m" style="width: 100%"
                                              rows="5"><%= props.getProperty("p4_education48m", "") %></textarea>
                                </td>
                            </tr>
                        </table>
                    </td>
                </tr>
            </table>
        </td>
        <td class="column">
            <a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgEducational"/></a><br/>
            <small><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgEducationalSubtitle"/></small><br/>
            <img height="15" width="20" src="graphics/Checkmark_Lwhite.gif"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.msgEducationalLegend"/>
        </td>
    </tr>
    <tr id="developmentp4">
        <td colspan="3" align="center">
            <table id="dt41" cellpadding="0" cellspacing="0" width="300px" height="100%">
                <tr>
                    <td colspan="4">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.msgNippissing"/>
                        <textarea id="p4_nippisingattained" name="p4_nippisingattained" class="wide" cols="10"
                                  rows="2"><%= props.getProperty("p4_nippisingattained", "") %></textarea>
                    </td>
                </tr>
                <tr>
                    <td colspan="4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formSocialEmotion"/>**</td>
                </tr>
                <tr>
                    <td style="padding-right: 5pt" valign="top"><img height="15" width="20"
                                                                     src="graphics/Checkmark_L.gif"></td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_manageable18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formManageable"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_otherChildren18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.formOtherChildren"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_soothability18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formSoothability"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_comfort18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formComfort"/></td>
                </tr>
                <tr>
                    <td colspan="4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formCommSkills"/>**</td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_points2body18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.formPoints"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_getAttn18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.formGetAttn"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_recsName18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.formRecsName"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_points2want18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.formPoints2want"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_looks4toy18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.formLooks4toy"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_initSpeech18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.formInitSpeech"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_says20words18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formSays15Words"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_4consonants18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.form4consonants"/></td>
                </tr>
                <tr>
                    <td colspan="4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formMotorSkills"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_walksbackAlone18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.formWalksAlone"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_feedsSelf18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formFeedsSelf"/></td>
                </tr>
                <tr>
                    <td colspan="4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formAdaptiv"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_removesHat18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formRemovesHat"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_noParentsConcerns18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNoparentConcerns"/></td>
                </tr>
                <tr>
                    <td colspan="4" style="vertical-align:bottom;">
                        <textarea id="p4_development18m" name="p4_development18m" class="wide" cols="10"
                                  rows="5"><%= props.getProperty("p4_development18m", "") %></textarea>
                    </td>
                </tr>
            </table>
        </td>
        <td colspan="7" align="center">
            <table cellpadding="0" cellspacing="2" border="1" width="100%" height="100%">
                <tr>
                    <td>
                        <table cellpadding="0" cellspacing="0" width="100%" height="100%">
                            <tr>
                                <td colspan="4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.form2yrs"/>**</td>
                            </tr>
                            <tr>
                                <td style="padding-right: 5pt" valign="top"><img height="15" width="20"
                                                                                 src="graphics/Checkmark_L.gif"></td>
                                <td class="edcol" valign="top">X</td>
                                <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_2wSentence24m" formProperties="<%=props%>"
                                                              showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.form2wordSentence"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_one2stepdirections24m"
                                                              formProperties="<%=props%>" showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.formone2stepDirections"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_walksbackward24m"
                                                              formProperties="<%=props%>" showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.formwalksbackward"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_runse24m" formProperties="<%=props%>"
                                                              showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formTriestoRun"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_smallContainer24m"
                                                              formProperties="<%=props%>" showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formSmallContainer"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_pretendsPlay24m"
                                                              formProperties="<%=props%>" showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.formpretendsPlay"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_newSkills24m" formProperties="<%=props%>"
                                                              showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formNewSkills"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_noParentsConcerns24m"
                                                              formProperties="<%=props%>" showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNoparentConcerns"/></td>
                            </tr>
                            <tr>
                                <td colspan="4" style="vertical-align:bottom;">
                                    <textarea id="p4_development24m" name="p4_development24m" class="wide" cols="10"
                                              rows="5"><%= props.getProperty("p4_development24m", "") %></textarea>
                                </td>
                            </tr>
                        </table>
                    </td>
                    <td>
                        <table cellpadding="0" cellspacing="0" height="100%">
                            <tr>
                                <td colspan="4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.form4yrs"/></td>
                            </tr>
                            <tr>
                                <td style="padding-right: 5pt" valign="top"><img height="15" width="20"
                                                                                 src="graphics/Checkmark_L.gif"></td>
                                <td class="edcol" valign="top">X</td>
                                <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_3directions48m"
                                                              formProperties="<%=props%>" showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.form3Directions"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_asksQuestions48m"
                                                              formProperties="<%=props%>" showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.formAsksQuestions"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_upDownStairs48m"
                                                              formProperties="<%=props%>" showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.formupDownStairs"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_undoesZippers48m"
                                                              formProperties="<%=props%>" showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.formundoesZippers"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_tries2comfort48m"
                                                              formProperties="<%=props%>" showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formTries2comfort"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_noParentsConcerns48m"
                                                              formProperties="<%=props%>" showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNoparentConcerns"/></td>
                            </tr>
                            <tr>
                                <td colspan="4" style="vertical-align:bottom;">
                                    <textarea id="p4_development48m" name="p4_development48m" class="wide" cols="10"
                                              rows="5"><%= props.getProperty("p4_development48m", "") %></textarea>
                                </td>
                            </tr>
                        </table>
                    </td>
                </tr>
                <tr>
                    <td>
                        <table cellpadding="0" cellspacing="0" width="100%" height="100%">
                            <tr>
                                <td colspan="4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.form3yrs"/></td>
                            </tr>
                            <tr>
                                <td style="padding-right: 5pt" valign="top"><img height="15" width="20"
                                                                                 src="graphics/Checkmark_L.gif"></td>
                                <td class="edcol" valign="top">X</td>
                                <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_2directions36m"
                                                              formProperties="<%=props%>" showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.form2Directions"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_5ormoreWords36m"
                                                              formProperties="<%=props%>" showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.form5ormoreWords"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_walksUpStairs36m"
                                                              formProperties="<%=props%>" showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.formwalksUpStairs"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_twistsLids36m" formProperties="<%=props%>"
                                                              showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formTwistsLids"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_sharesSometime36m"
                                                              formProperties="<%=props%>" showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formSharesSometimes"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_playMakeBelieve36m"
                                                              formProperties="<%=props%>" showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.formplayMakeBelieve"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_turnsPages36m" formProperties="<%=props%>"
                                                              showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formTurnsPages"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_listenMusic36m"
                                                              formProperties="<%=props%>" showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formListensMusic"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_noParentsConcerns36m"
                                                              formProperties="<%=props%>" showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNoparentConcerns"/></td>
                            </tr>
                            <tr>
                                <td colspan="4" style="vertical-align:bottom;">
                                    <textarea id="p4_development36m" name="p4_development36m" class="wide" cols="10"
                                              rows="5"><%= props.getProperty("p4_development36m", "") %></textarea>
                                </td>
                            </tr>
                        </table>
                    </td>
                    <td>
                        <table cellpadding="0" cellspacing="0" width="100%" height="100%">
                            <tr>
                                <td colspan="4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.form5yrs"/></td>
                            </tr>
                            <tr>
                                <td style="padding-right: 5pt" valign="top"><img height="15" width="20"
                                                                                 src="graphics/Checkmark_L.gif"></td>
                                <td class="edcol" valign="top">X</td>
                                <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_countsOutloud60m"
                                                              formProperties="<%=props%>" showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.formCountsOutloud"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_speaksClearly60m"
                                                              formProperties="<%=props%>" showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.formSpeaksClearly"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_throwsCatches60m"
                                                              formProperties="<%=props%>" showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formThrowsCatches"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_hops1foot60m" formProperties="<%=props%>"
                                                              showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.formHops1Foot"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_dressesUndresses60m"
                                                              formProperties="<%=props%>" showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.formdressesUndresses"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_obeysAdult60m" formProperties="<%=props%>"
                                                              showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.formobeysAdult"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_retellsStory60m"
                                                              formProperties="<%=props%>" showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.formretellsStory"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_separates60m" formProperties="<%=props%>"
                                                              showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_4.formSeparates"/></td>
                            </tr>
                            <tr>
                                <rourke:discussionRadioSelect sectionName="p4_noParentsConcerns60m"
                                                              formProperties="<%=props%>" showNoOption="<%=false%>"
                                                              showNotDiscussedOption="<%=true%>"/>
                                <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNoparentConcerns"/></td>
                            </tr>
                            <tr>
                                <td colspan="4" style="vertical-align:bottom;">
                                    <textarea id="p4_development60m" name="p4_development60m" class="wide" cols="10"
                                              rows="5"><%= props.getProperty("p4_development60m", "") %></textarea>
                                </td>
                            </tr>
                        </table>
                    </td>
            </table>
        </td>
        <td class="column">
            <a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgDevelopment"/>**</a><br>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_1.msgDevelopmentDesc"/><br>
            <img height="15" width="20" src="graphics/Checkmark_Lwhite.gif"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.msgDevelopmentLegend"/>
        </td>
    </tr>
    <tr id="physicalExamp4">
        <td colspan="3" id="physicalExamp4a">
            <table id="pt41" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td colspan="4">&nbsp;</td>
                </tr>
                <tr>
                    <td style="padding-right: 5pt" valign="top"><img height="15" width="20"
                                                                     src="graphics/Checkmark_L.gif"></td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_fontanelles18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formAnteriorFontanelleClosed"/>**
                    </a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_eyes18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><b><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formRedReflex"/>**
                    </a></b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_corneal18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formCornealReflex"/>/<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formCoverUncoverTest"/>**
                        </a></b>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_hearing18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formHearingInquiry"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_tonsilSize18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><b><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formTonsilSize"/>**
                    </a></b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_teeth18m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formTeeth"/>**
                    </a></td>
                </tr>
                <tr>
                    <td colspan="4" style="vertical-align:bottom;">
                        <textarea id="p4_physical18m" name="p4_physical18m" class="wide" cols="10"
                                  rows="5"><%= props.getProperty("p4_physical18m", "") %></textarea>
                    </td>
                </tr>
            </table>
        </td>
        <td colspan="4" id="physicalExamp4b">
            <table id="pt42" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td colspan="4">&nbsp;</td>
                </tr>
                <tr>
                    <td style="padding-right: 5pt" valign="top"><img height="15" width="20"
                                                                     src="graphics/Checkmark_L.gif"></td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_bloodPressure24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><i><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formBloodPressureIfAtRisk"/>**
                    </a></i></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_eyes24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><b><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.EyesVisualAcuity"/>**
                    </a></b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_corneal24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formCornealReflex"/>/<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formCoverUncoverTest"/>**
                        </a></b>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_hearing24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formHearingInquiry"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_tonsilSize24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><b><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formTonsilSize"/>**
                    </a></b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_teeth24m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><i><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formTeeth"/>**
                    </a></i></td>
                </tr>
                <tr>
                    <td colspan="4" style="vertical-align:bottom;">
                        <textarea id="p4_physicalm" name="p4_physical24m" class="wide" cols="10"
                                  rows="5"><%= props.getProperty("p4_physical24m", "") %></textarea>
                    </td>
                </tr>
            </table>
        </td>
        <td colspan="3" id="physicalExamp4c">
            <table id="pt43" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td colspan="4">&nbsp;</td>
                </tr>
                <tr>
                    <td style="padding-right: 5pt" valign="top"><img height="15" width="20"
                                                                     src="graphics/Checkmark_L.gif"></td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_bloodPressure48m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><i><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formBloodPressureIfAtRisk"/>**
                    </a></i></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_eyes48m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><b><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.EyesVisualAcuity"/>**
                    </a></b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_corneal48m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formCornealReflex"/>/<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formCoverUncoverTest"/>**
                        </a></b>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_hearing48m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_4.formHearingInquiry"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_tonsilSize48m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><b><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formTonsilSize"/>**
                    </a></b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_teeth48m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><i><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formTeeth"/>**
                    </a></i></td>
                </tr>
                <tr>
                    <td colspan="4" style="vertical-align:bottom;">
                        <textarea id="p4_physical48m" name="p4_physical48m" class="wide" cols="10"
                                  rows="5"><%= props.getProperty("p4_physical48m", "") %></textarea>
                    </td>
                </tr>
            </table>
        </td>
        <td class="column">
            <a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgPhysicalExamination"/></a><br>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgPhysicalExaminationDesc"/><br>
            <img height="15" width="20" src="graphics/Checkmark_Lwhite.gif">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.msgPhysicalExaminationLegend"/>
        </td>
    </tr>
    <tr id="problemsPlansp4">
        <td colspan="3" valign="bottom">
            <textarea id="p4_problems18m" name="p4_problems18m" rows="5" cols="25" class="wide limit-rows"
                      maxlength="400"><%= props.getProperty("p4_problems18m", "") %></textarea>
        </td>
        <td colspan="4" valign="bottom">
            <textarea id="p4_problems24m" name="p4_problems24m" rows="5" cols="25" class="wide limit-rows"
                      maxlength="400"><%= props.getProperty("p4_problems24m", "") %></textarea>
        </td>
        <td colspan="3" valign="bottom">
            <textarea id="p4_problems48m" name="p4_problems48m" rows="5" cols="25" class="wide limit-rows"
                      maxlength="400"><%= props.getProperty("p4_problems48m", "") %></textarea>
        </td>
        <td class="column">
            <a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.msgProblemsAndPlans"/>/<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.msgCurrentAndNewReferrals"/></a><br/>
            <small><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.msgPlansAndReferralsDescription"/></small><br/>
            <img height="15" width="20" src="graphics/Checkmark_Lwhite.gif"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.msgProblemsLegend"/>
        </td>
    </tr>
    <tr id="immunizationp4">
        <td colspan="10" valign="top">
            <table id="immt41" style="font-size: 9pt;" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td colspan="9" style="text-align: center"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.msgImmunizationColTitle"/></b></td>
                </tr>
                <tr>
                    <td style="padding-right: 5pt" valign="top"><img height="15" width="20"
                                                                     src="graphics/Checkmark_L.gif"></td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="3"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                    <td style="padding-right: 5pt" valign="top"><img height="15" width="20"
                                                                     src="graphics/Checkmark_L.gif"></td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="3"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p4_hemoglobin" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td colspan="2"><i><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formImmunizationHemoglobin"/>**</a></i>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p4_BloodLead" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td colspan="3"><i><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.formBloodLead"/>*</a></i>
                    </td>
                </tr>
                <tr>
                    <td colspan="9" style="vertical-align:bottom;">
                        <textarea id="p4_immunization" name="p4_immunization" rows="5" cols="25"
                                  class="wide"></textarea>
                    </td>
                </tr>
            </table>
        </td>
        <td class="column">
            <a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.msgInvestigationsScreeningAndImmunization"/></a><br>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2017.msgInvestigationsScreeningAndImmunizationDesc"/><br/>
            <img height="15" width="20" src="graphics/Checkmark_Lwhite.gif">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.msgProblemsLegend"/>
        </td>
    </tr>
    <tr>
        <td colspan="3"><input type="text" class="wide" style="width: 100%" name="p4_signature18m"
                               value="<%= props.getProperty("p4_signature18m", "") %>"/></td>
        <td colspan="4"><input type="text" class="wide" maxlength="42" style="width: 100%" name="p4_signature24m"
                               value="<%= props.getProperty("p4_signature24m", "") %>"/></td>
        <td colspan="3"><input type="text" class="wide" style="width: 100%" name="p4_signature48m"
                               value="<%= props.getProperty("p4_signature48m", "") %>"/></td>
        <td class="column"><a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formSignature"/></a></td>
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
                                       onclick="onGraph('<%=request.getContextPath()%>/form/formname.do?submit=graph&form_class=Rourke2017&__title=Baby+Growth+Graph1&__cfgfile=<%=growthCharts[0]%>&demographic_no=<%=demoNo%>&formId=<%=formId%>&provNo=<%=provNo%>','<%= "growth1" + demoNo %>');return false;">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btnGraphLenghtWeight"/></a><br>
            <a name="headCirc" href="#"
               onclick="onGraph('<%=request.getContextPath()%>/form/formname.do?submit=graph&form_class=Rourke2017&__title=Baby+Head+Circumference&__cfgfile=<%=growthCharts[1]%>&demographic_no=<%=demoNo%>&formId=<%=formId%>&provNo=<%=provNo%>','<%= "growth2" + demoNo %>');return false;">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btnGraphHead"/></a> <% } else { %>
            &nbsp; <% } %>
        </td>
    </tr>
</table>
<p style="font-size: 8pt;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.footer"/><br/></p>

<script type="text/javascript">
    Calendar.setup({
        inputField: "p4_date18m",
        ifFormat: "%d/%m/%Y",
        showsTime: false,
        button: "p4_date18m_cal",
        singleClick: true,
        step: 1
    });
    Calendar.setup({
        inputField: "p4_date24m",
        ifFormat: "%d/%m/%Y",
        showsTime: false,
        button: "p4_date24m_cal",
        singleClick: true,
        step: 1
    });
    Calendar.setup({
        inputField: "p4_date48m",
        ifFormat: "%d/%m/%Y",
        showsTime: false,
        button: "p4_date48m_cal",
        singleClick: true,
        step: 1
    });
</script>
