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
<%@ taglib uri="/WEB-INF/rourke-tag.tld" prefix="rourke" %>
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
<%@ page import="ca.openosp.openo.commn.model.Demographic" %>
<%@ page import="ca.openosp.openo.managers.DemographicManager" %>
<%@ page import="ca.openosp.openo.util.UtilMisc" %>
<%@ page import="ca.openosp.openo.form.FrmRecord" %>
<%@ page import="ca.openosp.openo.form.FrmRourke2020Record" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    String formClass = "Rourke2020";
    String formLink = "formrourke2020complete.jsp";

    int demoNo = Integer.parseInt(request.getParameter("demographic_no"));
    int formId = Integer.parseInt(request.getParameter("formId"));
    int provNo = Integer.parseInt((String) session.getAttribute("user"));
    String remoteFacilityId = request.getParameter("remoteFacilityId");
    String appointmentNo = request.getParameter("appointmentNo");
    Demographic demographic = demographicManager.getDemographic(loggedInInfo, demoNo);

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

<div style="display:block; width:100%;">
    <img alt="copyright" width="80%" src="graphics/Rourke2020Banner.png"
         onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formCopyRight"/>')"
         onMouseOut="hideLayer()">
</div>
<div id="object1"
     style="position: absolute; background-color: #FFFFDD; color: black; border-color: black; border-width: 20px; left: 25px; top: -100px; z-index: +1"
     onmouseover="overdiv=1;" onmouseout="overdiv=0; setTimeout('hideLayer()',1000)">pop up description layer
</div>


<input type="hidden" name="demographic_no" value="<%= props.getProperty("demographic_no", "0") %>"/>
<input type="hidden" name="ID" value="<%= props.getProperty("ID", "0") %>"/>
<input type="hidden" name="provider_no" value=<%=request.getParameter("provNo")%>/>
<input type="hidden" name="formCreated" value="<%= props.getProperty("formCreated", "") %>"/>
<input type="hidden" name="form_class" value="<%=formClass%>"/>
<input type="hidden" name="form_link" value="<%=formLink%>"/>
<input type="hidden" name="formId" value="<%=formId%>"/>
<input type="hidden" name="remoteFacilityId" value="<%=remoteFacilityId != null ? remoteFacilityId : ""%>"/>
<input type="hidden" name="appointmentNo" value="<%=appointmentNo != null ? appointmentNo : ""%>"/>

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
            <% if (formId > 0) { %>
            <a name="length" href="#"
               onclick="onGraph('<%=request.getContextPath()%>/form/formname.do?submit=graph&form_class=Rourke2020&__title=Baby+Growth+Graph1&__cfgfile=<%=growthCharts[0]%>&demographic_no=<%=demoNo%>&formId=<%=formId%>&provNo=<%=provNo%>','<%= "growth1" + demoNo %>');return false;">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btnGraphLenghtWeight"/>
            </a><br>
            <a name="headCirc" href="#"
               onclick="onGraph('<%=request.getContextPath()%>/form/formname.do?submit=graph&form_class=Rourke2020&__title=Baby+Head+Circumference&__cfgfile=<%=growthCharts[1]%>&demographic_no=<%=demoNo%>&formId=<%=formId%>&provNo=<%=provNo%>','<%= "growth2" + demoNo %>');return false;">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btnGraphHead"/>
            </a>
            <% } else { %>
            &nbsp; <% } %>
        </td>
    </tr>
</table>

<table cellpadding="0" cellspacing="0" border="0" width="100%">
    <tr class="titleBar">
        <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgRourkeBabyRecord"/></th>
    </tr>
</table>
<div id="patientInfop1" style="height:10%; overflow:auto;">
    <table cellpadding="0" cellspacing="0" width="100%" border="0">
        <tr valign="top">
            <td align="center">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formBirhtRemarks"/><br>
                <input type="radio" id="p1_birthRemarksr1" name="p1_birthRemarksr1"
                       onclick="onCheck(this,'p1_birthRemarksr')" <%= props.getProperty("p1_birthRemarksr1", "") %>>
                <label for="p1_birthRemarksr1"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formPremature"/></label>&nbsp;
                <input type="radio" id="p1_birthRemarksr2" name="p1_birthRemarksr2"
                       onclick="onCheck(this,'p1_birthRemarksr')" <%= props.getProperty("p1_birthRemarksr2", "") %>>
                <label for="p1_birthRemarksr2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formHighRisk"/></label>&nbsp;<br>
                <input type="radio" id="p1_birthRemarksr3" name="p1_birthRemarksr3"
                       onclick="onCheck(this,'p1_birthRemarksr')"<%= props.getProperty("p1_birthRemarksr3", "") %>>
                <label for="p1_birthRemarksr3"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNoConcerns"/></label>&nbsp;
            </td>
            <td width="65%" nowrap align="center">
                <p>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgName"/>: <input type="text" name="c_pName"
                                                                                     maxlength="60" size="30"
                                                                                     value="<%= Encode.forHtmlAttribute(props.getProperty("c_pName", "")) %>"
                                                                                     readonly="true"/>
                    &nbsp;&nbsp; <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgBirthDate"/> (d/m/yyyy):
                    <input type="text" id="c_birthDate" name="c_birthDate" size="10" maxlength="10"
                           value="<%= props.getProperty("c_birthDate", "") %>" readonly="true">
                    &nbsp;&nbsp;
                    Age: <input type="text" id="currentAge" size="10" maxlength="10" readonly="true"
                                ondblclick="calcAge();">
                    <% if (!((FrmRourke2020Record) rec).isFemale(loggedInInfo, demoNo)) { %>
                    (<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgMale"/>) <input type="hidden" name="c_male"
                                                                                      value="x">
                    <% } else { %>
                    (<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgFemale"/>) <input type="hidden" name="c_female"
                                                                                        value="x">
                    <% } %>
                    &nbsp;&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formFSA"/>
                    <input type="text" name="c_fsa" size="3" maxlength="3"
                           value="<%= props.getProperty("c_fsa", "") %>">
                </p>
                <p>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgStartOfPregnancy"/>:
                    <input type="text" id="c_startOfGestation" name="c_startOfGestation" size="6" maxlength="7"
                           value="<%= props.getProperty("c_startOfGestation", "") %>">
                    <img src="<%= request.getContextPath() %>/images/cal.gif" id="c_startOfGestation_cal">
                    &nbsp;&nbsp; <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgLenght"/>:
                    <input type="text" ondblclick="htEnglish2Metric(this);" name="c_length" size="6" maxlength="6"
                           value="<%= props.getProperty("c_length", "") %>"/>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgLenghtUnit"/> &nbsp;&nbsp;
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgHeadCirc"/>:
                    <input type="text" ondblclick="htEnglish2Metric(this);" name="c_headCirc" size="6" maxlength="6"
                           value="<%= props.getProperty("c_headCirc", "") %>"/>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgHeadCircUnit"/>
                    &nbsp;&nbsp; <a href="javascript:void(0)"
                                    onclick="displayDemographicMeasurements('c_birthWeight', 'WT', '<%=demographic.getDemographicNo()%>', '<%=demographic.getFormattedDob()%>', '<%=appointmentNo%>')"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgBirthWt"/>:</a>
                    <input type="text" ondblclick="wtEnglish2Metric(this);" name="c_birthWeight" id="c_birthWeight"
                           size="6" maxlength="7" value="<%= props.getProperty("c_birthWeight", "") %>"/>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgBirthWtUnit"/>
                    &nbsp;&nbsp; <a href="javascript:void(0)"
                                    onclick="displayDemographicMeasurements('c_dischargeWeight', 'WT', '<%=demographic.getDemographicNo()%>', '<%=demographic.getFormattedDob()%>', '<%=appointmentNo%>')"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgDischargeWt"/>:</a>
                    <input type="text" ondblclick="wtEnglish2Metric(this);" name="c_dischargeWeight"
                           id="c_dischargeWeight" size="6" maxlength="7"
                           value="<%= props.getProperty("c_dischargeWeight", "") %>">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgDischargeWtUnit"/>
                </p>
            </td>
        </tr>
        <tr>
            <td style="padding:10px;" align="center">
                <textarea id="c_birthRemarks" name="c_birthRemarks" rows="6"
                          cols="17"><%= props.getProperty("c_birthRemarks", "") %></textarea>
            </td>
            <td style="padding:10px;" align="center">
                <table cellpadding="0" cellspacing="0" width="100%" border="0">
                    <tr>
                        <td align="center">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formRiksFactors"/>

                            <br>
                            <input type="checkbox" class="chk"
                                   name="p1_2ndhandsmoke" <%= props.getProperty("p1_2ndhandsmoke", "") %>>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.form2ndHandSmoke"/>
                            <p>
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formSubstanceabuse"/><br>
                                <input type="checkbox" class="chk"
                                       name="p1_alcohol" <%= props.getProperty("p1_alcohol", "") %>>
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formAlcohol"/><br>
                                <input type="checkbox" class="chk"
                                       name="p1_drugs" <%= props.getProperty("p1_drugs", "") %>>
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formDrugs"/>
                            </p>
                        </td>
                        <td nowrap align="center">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formAPGAR"/><br>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.form1min"/>
                            <select name="c_APGAR1min">
                                <option <%= props.getProperty("c_APGAR1min", "").equals("") || props.getProperty("c_APGAR1min", "").equals("-1") ? "selected='selected'" : ""%>
                                        value="-1">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotSet"/>
                                </option>
                                <% for (Integer idx = 0; idx <= 10; ++idx) { %>
                                <option <%= props.getProperty("c_APGAR1min", "").equals(idx.toString()) ? "selected='selected'" : ""%>
                                        value="<%=idx%>"><%=idx%>
                                </option>
                                <%}%>
                            </select><br>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.form5min"/>
                            <select name="c_APGAR5min">
                                <option <%= props.getProperty("c_APGAR5min", "").equals("") || props.getProperty("c_APGAR5min", "").equals("-1") ? "selected='selected'" : ""%>
                                        value="-1">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotSet"/>
                                </option>
                                <% for (Integer idx = 0; idx <= 10; ++idx) { %>
                                <option <%= props.getProperty("c_APGAR5min", "").equals(idx.toString()) ? "selected='selected'" : ""%>
                                        value="<%=idx%>"><%=idx%>
                                </option>
                                <%}%>
                            </select><br>
                        </td>
                        <td align="center">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formFamHistory"/><br>
                            <textarea id="c_famHistory" name="c_famHistory" rows="5"
                                      cols="17"><%= props.getProperty("c_famHistory", "") %></textarea>
                        </td>
                    </tr>
                </table>
            </td>
    </table>
</div>

<table cellpadding="0" cellspacing="0" width="100%" border="1">
    <tr align="center">
        <td class="column"><a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.visitDate"/></a></td>
        <td colspan="3" class="row"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgWithin"/> <a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btn1Week"/></a></td>
        <td colspan="3" class="row"><a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btn2Weeks"/></a> <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgOptional"/></td>
        <td colspan="3" class="row"><a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btn1month"/></a> <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgOptional"/></td>
    </tr>
    <tr align="center">
        <td class="column"><a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgDate"/></a></td>
        <td colspan="3">
            <img src="<%= request.getContextPath() %>/images/cal.gif" id="p1_date1w_cal" style="vertical-align: middle;">
            <input readonly type="text" id="p1_date1w" name="p1_date1w" size="10" ondblclick="resetDate(this)"
                   value="<%=UtilMisc.htmlEscape(props.getProperty("p1_date1w", ""))%>"/>
            <img src="<%= request.getContextPath() %>/images/clear.png" id="p1_date1w_delete" style="vertical-align: middle; cursor: pointer;"
                 onClick="resetDateUsingID('p1_date1w')">
        </td>
        <td colspan="3">
            <img src="<%= request.getContextPath() %>/images/cal.gif" id="p1_date2w_cal" style="vertical-align: middle;">
            <input readonly type="text" id="p1_date2w" name="p1_date2w" size="10" ondblclick="resetDate(this)"
                   value="<%=UtilMisc.htmlEscape(props.getProperty("p1_date2w", ""))%>"/>
            <img src="<%= request.getContextPath() %>/images/clear.png" id="1_date2w_delete" style="vertical-align: middle; cursor: pointer;"
                 onClick="resetDateUsingID('p1_date2w')">
        </td>
        <td colspan="3">
            <img src="<%= request.getContextPath() %>/images/cal.gif" id="p1_date1m_cal" style="vertical-align: middle;">
            <input readonly type="text" id="p1_date1m" name="p1_date1m" size="10" ondblclick="resetDate(this)"
                   value="<%=UtilMisc.htmlEscape(props.getProperty("p1_date1m", ""))%>"/>
            <img src="<%= request.getContextPath() %>/images/clear.png" id="p1_date1m_delete" style="vertical-align: middle; cursor: pointer;"
                 onClick="resetDateUsingID('p1_date1m')">
        </td>
    </tr>
    <tr align="center" id="growthAp1">
        <td class="column" rowspan="2">
            <a>
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020_1.btnGrowth"/><br/>
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_1.btnGrowthmsg"/>
            </a>
        </td>
        <td>
            <a href="javascript:void(0)"
               onclick="displayDemographicMeasurements('p1_ht1w', 'HT', '<%=demographic.getDemographicNo()%>',
                       '<%=demographic.getFormattedDob()%>', '<%=appointmentNo%>')">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formHt"/>
            </a>
        </td>
        <td>
            <a href="javascript:void(0)"
               onclick="displayDemographicMeasurements('p1_wt1w', 'WT', '<%=demographic.getDemographicNo()%>',
                       '<%=demographic.getFormattedDob()%>', '<%=appointmentNo%>')">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formWt"/>
            </a>
        </td>
        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_3.formHdCirc"/></td>
        <td>
            <a href="javascript:void(0)"
               onclick="displayDemographicMeasurements('p1_ht2w', 'HT', '<%=demographic.getDemographicNo()%>',
                       '<%=demographic.getFormattedDob()%>', '<%=appointmentNo%>')">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formHt"/>
            </a>
        </td>
        <td>
            <a href="javascript:void(0)"
               onclick="displayDemographicMeasurements('p1_wt2w', 'WT', '<%=demographic.getDemographicNo()%>',
                       '<%=demographic.getFormattedDob()%>', '<%=appointmentNo%>')">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formWt"/>
            </a>
        </td>
        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_3.formHdCirc"/></td>
        <td>
            <a href="javascript:void(0)"
               onclick="displayDemographicMeasurements('p1_ht1m', 'HT', '<%=demographic.getDemographicNo()%>',
                       '<%=demographic.getFormattedDob()%>', '<%=appointmentNo%>')">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formHt"/>
            </a>
        </td>
        <td>
            <a href="javascript:void(0)"
               onclick="displayDemographicMeasurements('p1_wt1m', 'WT', '<%=demographic.getDemographicNo()%>',
                       '<%=demographic.getFormattedDob()%>', '<%=appointmentNo%>')">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formWt"/>
            </a>
        </td>
        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_3.formHdCirc"/></td>
    </tr>
    <tr align="center" id="growthBp1">
        <td>
            <input type="text" class="wide" ondblclick="htEnglish2Metric(this);" name="p1_ht1w" id="p1_ht1w" size="4"
                   maxlength="5" value="<%= props.getProperty("p1_ht1w", "") %>">
        </td>
        <td>
            <input type="text" class="wide" ondblclick="wtEnglish2Metric(this);" name="p1_wt1w" id="p1_wt1w" size="4"
                   maxlength="5" value="<%= props.getProperty("p1_wt1w", "") %>">
        </td>
        <td>
            <input type="text" class="wide" ondblclick="htEnglish2Metric(this);" name="p1_hc1w" size="4" maxlength="5"
                   value="<%= props.getProperty("p1_hc1w", "") %>">
        </td>
        <td>
            <input type="text" class="wide" ondblclick="htEnglish2Metric(this);" name="p1_ht2w" id="p1_ht2w" size="4"
                   maxlength="5" value="<%= props.getProperty("p1_ht2w", "") %>">
        </td>
        <td>
            <input type="text" class="wide" ondblclick="wtEnglish2Metric(this);" name="p1_wt2w" id="p1_wt2w" size="4"
                   maxlength="5" value="<%= props.getProperty("p1_wt2w", "") %>">
        </td>
        <td>
            <input type="text" class="wide" ondblclick="htEnglish2Metric(this);" name="p1_hc2w" size="4" maxlength="5"
                   value="<%= props.getProperty("p1_hc2w", "") %>">
        </td>
        <td>
            <input type="text" class="wide" ondblclick="htEnglish2Metric(this);" name="p1_ht1m" id="p1_ht1m" size="4"
                   maxlength="5" value="<%= props.getProperty("p1_ht1m", "") %>">
        </td>
        <td>
            <input type="text" class="wide" ondblclick="wtEnglish2Metric(this);" name="p1_wt1m" id="p1_wt1m" size="4"
                   maxlength="5" value="<%= props.getProperty("p1_wt1m", "") %>">
        </td>
        <td>
            <input type="text" class="wide" ondblclick="htEnglish2Metric(this);" name="p1_hc1m" size="4" maxlength="5"
                   value="<%= props.getProperty("p1_hc1m", "") %>">
        </td>
    </tr>
    <tr align="center">
        <td class="column"><a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.msgParentConcerns"/></a></td>
        <td colspan="3"><textarea id="p1_pConcern1w" name="p1_pConcern1w" class="wide limit-rows" cols="10" rows="5"
                                  maxlength="400"><%= props.getProperty("p1_pConcern1w", "") %></textarea></td>
        <td colspan="3"><textarea id="p1_pConcern2w" name="p1_pConcern2w" class="wide limit-rows" cols="10" rows="5"
                                  maxlength="400"><%= props.getProperty("p1_pConcern2w", "") %></textarea></td>
        <td colspan="3"><textarea id="p1_pConcern1m" name="p1_pConcern1m" class="wide limit-rows" cols="10" rows="5"
                                  maxlength="400"><%= props.getProperty("p1_pConcern1m", "") %></textarea></td>
    </tr>
    <tr align="center" id="nutritionp1">

        <td class="column"><a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgNutrition"/>*</a><br/>
            <img height="15" width="20" src="graphics/Checkmark_Lwhite.gif">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.msgNutritionLegend"/>
        </td>
        <td colspan="3">
            <table id="ntp11" cellpadding="0" cellspacing="0" width="100%">
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
                    <rourke:discussionRadioSelect sectionName="p1_breastFeeding1w" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td><b>
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.btnBreastFeeding"/>
                        </a>
                    </b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_vitaminD1w" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td><b>
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formVitaminD200"/>*
                        </a>
                    </b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_formulaFeeding1w" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td>
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.msgFormulaFeeding"/>*
                        </a>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_stoolUrine1w" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formStoolPatern"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_supplementationWater1w" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formSupplementationWater"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_supplementationOtherFluids1w"
                                                  formProperties="<%=props%>" showNoOption="<%=true%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formSupplementationOtherFluids"/></td>
                </tr>


                <tr style="vertical-align: bottom;">
                    <td style="vertical-align: bottom;" colspan="5">
                        <textarea id="p1_pNutrution1w" name="p1_pNutrition1w" style="width: 100%" cols="10"
                                  rows="5"><%= props.getProperty("p1_pNutrition1w", "") %></textarea>
                    </td>
                </tr>
            </table>

        </td>
        <td colspan="3">
            <table id="ntp12" cellpadding="0" cellspacing="0" width="100%">
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
                    <rourke:discussionRadioSelect sectionName="p1_breastFeeding2w" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td><b>
                        <a onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()" href="javascript:showNotes()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.btnBreastFeeding"/>
                        </a>
                    </b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_vitaminD2w" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td><b>
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formVitaminD200"/>*
                        </a>
                    </b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_formulaFeeding2w" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td>
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.msgFormulaFeeding"/>
                        </a>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_stoolUrine2w" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formStoolPatern"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_supplementationWater2w" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formSupplementationWater"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_supplementationOtherFluids2w"
                                                  formProperties="<%=props%>" showNoOption="<%=true%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formSupplementationOtherFluids"/></td>
                </tr>

                <tr>
                    <td style="vertical-align: bottom;" colspan="5">
                        <textarea id="p1_pNutrution2w" name="p1_pNutrition2w" style="width: 100%" cols="10"
                                  rows="5"><%= props.getProperty("p1_pNutrition2w", "") %></textarea>
                    </td>
                </tr>
            </table>
        </td>
        <td colspan="3">
            <table id="ntp13" cellpadding="0" cellspacing="0" width="100%">
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
                    <rourke:discussionRadioSelect sectionName="p1_breastFeeding1m" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td><b>
                        <a onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()" href="javascript:showNotes()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.btnBreastFeeding"/>
                        </a>
                    </b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_vitaminD1m" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td><b>
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formVitaminD200"/>*
                        </a>
                    </b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_formulaFeeding1m" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td>
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_1.msgFormulaFeeding"/>
                        </a>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_stoolUrine1m" formProperties="<%=props%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formStoolPatern"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_supplementationSolids1m" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formSupplementationSolids"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_supplementationWater1m" formProperties="<%=props%>"
                                                  showNoOption="<%=true%>" showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formSupplementationWater"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_supplementationOtherFluids1m"
                                                  formProperties="<%=props%>" showNoOption="<%=true%>"
                                                  showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formSupplementationOtherFluids"/></td>
                </tr>

                <tr>
                    <td style="vertical-align: bottom;" colspan="5">
                        <textarea id="p1_pNutrution1m" name="p1_pNutrition1m" style="width: 100%" cols="10"
                                  rows="5"><%= props.getProperty("p1_pNutrition1m", "") %></textarea>
                    </td>
                </tr>
            </table>
        </td>
    </tr>
    <tr id="educationp1">
        <td class="column">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgEducational"/><br/>
            <small><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgEducationalSubtitle"/></small><br/>
            <img height="15" width="20" src="graphics/Checkmark_Lwhite.gif"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.msgEducationalLegend"/>
        </td>
        <td colspan="9">
            <table id="edt1" style="font-size: 9pt;" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td colspan="16">&nbsp;</td>
                </tr>
                <tr>
                    <td valign="top" colspan="16"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formInjuryPrev"/></td>
                </tr>
                <tr>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif">
                        </div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif">
                        </div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif">
                        </div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif">
                        </div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_carSeat" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()" href="javascript:showNotes()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020_1.formMotorizedvehicles"/>/<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020_1.formCarSeat"/>
                        </a>*</b>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p1_smokeSafety" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formSmokeSafety"/>*
                        </a>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p1_firearmSafety" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formFireArm"/>*
                        </a></b>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p1_hotWater" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <i><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formHotWater"/>/<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formBathSafety"/>*
                        </a></i>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_safeToys" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formSafeToys"/>*
                        </a>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p1_pacifier" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formPacifierUse"/>*
                        </a>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p1_sleepPos" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_1.formSleepPos"/>/<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formCribSafety"/>*
                        </a></b>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p1_falls" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_2.formFalls"/>*
                        </a>
                    </td>
                </tr>
                <tr>
                    <td colspan="16">&nbsp;</td>
                </tr>
                <tr>
                    <td valign="top" colspan="16"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formBehaviour"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_crying" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formCrying"/>**</a>
                    </td>

                    <rourke:discussionRadioSelect sectionName="p1_healthySleep" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formHealthySleep"/>**</a></b>
                    </td>

                    <rourke:discussionRadioSelect sectionName="p1_nightWaking" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formNightWaking"/>**</a></b>
                    </td>

                    <rourke:discussionRadioSelect sectionName="p1_soothability" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formSoothability"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_bonding" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formBonding"/>**</a></b>
                    </td>

                    <rourke:discussionRadioSelect sectionName="p1_famConflict" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formFamConflict"/></td>

                    <rourke:discussionRadioSelect sectionName="p1_siblings" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formSiblings"/></td>

                    <rourke:discussionRadioSelect sectionName="p1_pFatigue" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formParentFatigue"/>**
                        </a></b>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_homeVisit" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top" colspan="5">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formHighRiskOrHomeVisit"/>**
                        </a></b>
                    </td>

                    <rourke:discussionRadioSelect sectionName="p1_makingEndsMeetInquiry" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top" colspan="5">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote2"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formMakingEndsMeetInquiry"/>**
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
                    <rourke:discussionRadioSelect sectionName="p1_2ndSmoke" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020_1.formSecondHandSmoke"/>*
                        </a></b>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p1_sunExposure" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formSunExposure"/>*
                        </a>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p1_noCoughMed" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_1.formCough"/>*
                        </a></b>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p1_altMed" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formAltMed"/>*
                        </a>
                    </td>
                </tr>
                <tr>

                    <rourke:discussionRadioSelect sectionName="p1_tmpControl" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formTempCtrl"/></td>
                    <rourke:discussionRadioSelect sectionName="p1_fever" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formFever"/>*
                        </a>
                    </td>
                    <rourke:discussionRadioSelect sectionName="p1_tummyTime" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formTummyTime"/>*
                        </a></b>
                    </td>
                </tr>
                <tr>
                    <td colspan="16" style="vertical-align:bottom;">
                        <table cellpadding="0" cellspacing="0" width="100%">
                            <tr>
                                <td style="vertical-align:bottom;">
                                    <textarea id="p1_education1w" name="p1_education1w" style="width: 100%"
                                              rows="5"><%= props.getProperty("p1_education1w", "") %></textarea>
                                </td>
                                <td style="vertical-align:bottom;">
                                    <textarea id="p1_education2w" name="p1_education2w" style="width: 100%"
                                              rows="5"><%= props.getProperty("p1_education2w", "") %></textarea>
                                </td>
                                <td style="vertical-align:bottom;">
                                    <textarea id="p1_education1m" name="p1_education1m" style="width: 100%"
                                              rows="5"><%= props.getProperty("p1_education1m", "") %></textarea>
                                </td>
                            </tr>
                        </table>
                    </td>
                </tr>
            </table>
        </td>
    </tr>
    <tr id="developmentp1">
        <td class="column">
            <a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.msgDevelopment"/>**</a><br>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.msgDevelopmentDesc"/><br/>
            <img height="15" width="20" src="graphics/Checkmark_Lwhite.gif">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.msgDevelopmentLegend"/>
        </td>
        <td colspan="3" align="center">
            <table id="dt11" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif">
                        </div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_sucks1w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formSucksWell"/></td>
                </tr>
                <tr align="center">
                    <td colspan="4" style="vertical-align:bottom;"><textarea id="p1_development1w"
                                                                             name="p1_development1w" rows="5" cols="25"
                                                                             class="wide"><%= props.getProperty("p1_development1w", "") %></textarea>
                    </td>
                </tr>
            </table>
        </td>
        <td colspan="3" align="center">
            <table id="dt12" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif">
                        </div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_sucks2w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formSucksWell"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_noParentsConcerns2w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formNoparentConcerns"/>**</td>
                </tr>
                <tr align="center">
                    <td colspan="4" style="vertical-align:bottom;">
                        <textarea id="p1_development2w" name="p1_development2w" rows="5" cols="25"
                                  class="wide"><%= props.getProperty("p1_development2w", "") %></textarea>
                    </td>
                </tr>
            </table>
        </td>
        <td colspan="3" valign="top">
            <table id="dt13" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif">
                        </div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_focusGaze1m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formFocusesGaze"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_startles1m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formSuddenNoise"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_calms1m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009_1.formCalmes"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_sucks1m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formSucksWell"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_noParentsConcerns1m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formNoparentConcerns"/>**</td>
                </tr>
                <tr align="center">
                    <td colspan="4" style="vertical-align:bottom;">
                        <textarea id="p1_development1m" name="p1_development1m" rows="5" cols="25"
                                  class="wide"><%= props.getProperty("p1_development1m", "") %></textarea>
                    </td>
                </tr>
            </table>
        </td>
    </tr>
    <tr id="physicalExamp1">
        <td class="column">
            <a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgPhysicalExamination"/>**</a><br>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.msgPhysicalExaminationDesc"/><br>
            <img height="15" width="20" src="graphics/Checkmark_Lwhite.gif">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.msgPhysicalExaminationLegend"/>
        </td>
        <td colspan="3">
            <table id="pt11" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif">
                        </div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_fontanelles1w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formFontanelles"/>**</a>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_skin1w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formDrySkin"/></a>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_eyes1w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formRedReflex"/>**
                        </a></b>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_ears1w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <i><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formEarDrums"/>**</i>
                        </a>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_IntactPalate1w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <i><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formIntectPalate"/>**</i>
                        </a>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_tongueMobility1w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formTongueMobility"/>**</a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_neckTorticollis1w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formNeckTorticollis"/>**</a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_heartLungs1w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formHeart"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_femoralPulses1w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formFemoralPulses"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_umbilicus1w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formUmbilicus"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_hips1w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formHips"/>**
                    </a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_genitalia1w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formGenitalia"/></td>
                </tr>

                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_maleUrinary1w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formMaleUrinaryStream"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_patencyOfAnus1w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formSpine"/>**/<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formPatencyOfAnus"/>**
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_muscleTone1w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formMuscleTone"/>**
                    </a></td>
                </tr>
                <tr>
                    <td colspan="4" style="vertical-align:bottom;">
                        <textarea id="p1_pPhysical1w" name="p1_pPhysical1w" style="width: 100%" cols="10"
                                  rows="5"><%= props.getProperty("p1_pPhysical1w", "") %></textarea>
                    </td>
                </tr>
            </table>
        </td>
        <td colspan="3">
            <table id="pt12" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif">
                        </div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_fontanelles2w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formFontanelles"/>**</a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_skin2w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formDrySkin"/></a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_eyes2w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formRedReflex"/>**
                        </a></b>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_ears2w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <i><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formEarDrums"/>**</i>
                        </a>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_IntactPalate2w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <i><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formIntectPalate"/>**</i>
                        </a>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_tongueMobility2w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formTongueMobility"/>**</a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_neckTorticollis2w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formNeckTorticollis"/>**</a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_heartLungs2w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formHeart"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_femoralPulses2w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formFemoralPulses"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_umbilicus2w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formUmbilicus"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_hips2w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formHips"/>**
                    </a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_genitalia2w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formGenitalia"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_spine2w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formSpine"/>**</td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_maleUrinary2w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formMaleUrinaryStream"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_muscleTone2w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formMuscleTone"/>**
                    </a></td>
                </tr>
                <tr>
                    <td colspan="4" style="vertical-align:bottom;">
						<textarea id="p1_pPhysical2w"
                                  name="p1_pPhysical2w" style="width: 100%" cols="10"
                                  rows="5"><%= props.getProperty("p1_pPhysical2w", "") %></textarea>
                    </td>
                </tr>
            </table>
        </td>
        <td colspan="3">
            <table id="pt13" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif">
                        </div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_fontanelles1m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formFontanelles"/>**</a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_skin1m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formDrySkin"/>**</a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_eyes1m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formRedReflex"/>**
                        </a></b>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_ears1m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <i><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formHearingInquiry"/>**</i>
                        </a>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_IntactPalate1m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top">
                        <a href="javascript:showNotes()"
                           onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                           onMouseOut="hideLayer()">
                            <i><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formIntectPalate"/>**</i>
                        </a>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_tongueMobility1m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formTongueMobility"/>**</a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_heartAbdomen1m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formHeartAbdomen"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_neckTorticollis1m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formNeckTorticollis"/>**</a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_hips1m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formHips"/>**
                    </a></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_muscleTone1m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td valign="top"><a href="javascript:showNotes()" onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')" onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006_1.formMuscleTone"/>**
                    </a></td>
                </tr>
                <tr>
                    <td colspan="4" style="vertical-align:bottom;">
                        <textarea id="p1_pPhysical1m" name="p1_pPhysical1m" style="width: 100%" cols="10"
                                  rows="5"><%= props.getProperty("p1_pPhysical1m", "") %></textarea>
                    </td>
                </tr>
            </table>
        </td>
    </tr>
    <tr id="problemsPlansp1">
        <td class="column">
            <a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.msgProblemsAndPlans"/>/<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.msgCurrentAndNewReferrals"/></a><br/>
            <small><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.msgPlansAndReferralsDescription"/></small><br/>
            <img height="15" width="20" src="graphics/Checkmark_Lwhite.gif"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.msgProblemsLegend"/>
        </td>
        <td colspan="3" style="vertical-align:bottom;">
            <textarea id="p1_problems1w" name="p1_problems1w" rows="5" cols="25" class="wide limit-rows"
                      maxlength="400"><%= props.getProperty("p1_problems1w", "") %></textarea>
        </td>
        <td colspan="3" style="vertical-align:bottom;">
            <textarea id="p1_problems2w" name="p1_problems2w" rows="5" cols="25" class="wide limit-rows"
                      maxlength="400"><%= props.getProperty("p1_problems2w", "") %></textarea>
        </td>
        <td colspan="3" style="vertical-align:bottom;">
            <textarea id="p1_problems1m" name="p1_problems1m" rows="5" cols="25" class="wide limit-rows"
                      maxlength="400"><%= props.getProperty("p1_problems1m", "") %></textarea>
        </td>
    </tr>
    <tr id="immunizationp1">
        <td class="column">
            <a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.msgInvestigationsScreeningAndImmunization"/></a><br>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.msgInvestigationsScreeningAndImmunizationDesc"/><br/>
            <img height="15" width="20" src="graphics/Checkmark_Lwhite.gif">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.msgProblemsLegend"/>
        </td>
        <td colspan="3">
            <table id="immt11" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif">
                        </div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_newbornScreening1w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td><b><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formImmunizationNewbornScreening"/></b></td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_hemoglobinopathyScreen1w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td><b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formImmunizationHemoglobinopathyScreen"/></a></b>
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_newbornHearingScreen1w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td><b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formImmunizationNewbornHearingScreening"/></a></b>
                    </td>
                </tr>
                <tr>
                    <td colspan="4" style="padding-left: 4px;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formImmunizationIfHepatitis"/>:
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_hepatitisVaccine1w" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td><b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formImmunizationHepatitisVaccine1"/>***</a></b>
                    </td>
                </tr>
                <tr>
                    <td colspan="4" style="vertical-align:bottom;">
                        <textarea id="p1_immunization1w" name="p1_immunization1w" rows="5" cols="25"
                                  class="wide"><%= props.getProperty("p1_immunization1w", "") %></textarea>
                    </td>
                </tr>
            </table>
        </td>
        <td colspan="3">
            <table id="immt12" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td style="vertical-align:bottom;">
                        <textarea id="p1_immunization2w" name="p1_immunization2w" rows="5" cols="25"
                                  class="wide"><%= props.getProperty("p1_immunization2w", "") %></textarea>
                    </td>
                </tr>
            </table>
        </td>
        <td colspan="3">
            <table id="immt13" cellpadding="0" cellspacing="0" width="100%">
                <tr>
                    <td style="padding-right: 5pt" valign="top" onclick="selectAllOkRadioButtons(this)">
                        <div class="checkmark-img-wrapper"><img height="15" width="20" src="graphics/Checkmark_L.gif">
                        </div>
                    </td>
                    <td class="edcol" valign="top">X</td>
                    <td class="edcol" valign="top" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.formNotDiscussed"/></td>
                </tr>
                <tr>
                    <td colspan="4" style="padding-left: 4px;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formImmunizationIfHepatitis"/>:
                    </td>
                </tr>
                <tr>
                    <rourke:discussionRadioSelect sectionName="p1_hepatitisVaccine1m" formProperties="<%=props%>"
                                                  showNoOption="<%=false%>" showNotDiscussedOption="<%=true%>"/>
                    <td><b><a href="javascript:showNotes()"
                              onMouseOver="popLayer('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.footnote1"/>')"
                              onMouseOut="hideLayer()">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2020.formImmunizationHepatitisVaccine2"/>***</a></b>
                    </td>
                </tr>
                <tr>
                    <td colspan="4" style="vertical-align:bottom;">
                        <textarea id="p1_immunization1m" name="p1_immunization1m" rows="5" cols="25"
                                  class="wide"><%= props.getProperty("p1_immunization1m", "") %></textarea>
                    </td>
                </tr>
            </table>
        </td>
    </tr>
    <tr>
        <td class="column"><a><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.formSignature"/></a></td>
        <td colspan="3"><input type="text" class="wide" style="width: 100%" name="p1_signature1w"
                               value="<%= props.getProperty("p1_signature1w", "") %>"/></td>
        <td colspan="3"><input type="text" class="wide" maxlength="42" style="width: 100%" name="p1_signature2w"
                               value="<%= props.getProperty("p1_signature2w", "") %>"/></td>
        <td colspan="3"><input type="text" class="wide" style="width: 100%" name="p1_signature1m"
                               value="<%= props.getProperty("p1_signature1m", "") %>"/></td>
    </tr>

</table>
<table cellpadding="0" cellspacing="0" class="Header" class="hidePrint">
    <tr>
        <td nowrap="true"><input type="submit" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btnSave"/>"
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
        <td>
            <div name="saveMessageDiv"></div>
        </td>
        <td align="center">
            <% if (formId > 0) { %> <a name="length" href="#"
                                       onclick="onGraph('<%=request.getContextPath()%>/form/formname.do?submit=graph&form_class=Rourke2020&__title=Baby+Head+Circumference&__cfgfile=<%=growthCharts[0]%>&demographic_no=<%=demoNo%>&formId=<%=formId%>&provNo=<%=provNo%>','<%= "growth1" + demoNo %>');return false;">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btnGraphLenghtWeight"/></a><br>
            <a name="headCirc" href="#"
               onclick="onGraph('<%=request.getContextPath()%>/form/formname.do?submit=graph&form_class=Rourke2020&__title=Baby+Head+Circumference&__cfgfile=<%=growthCharts[1]%>&demographic_no=<%=demoNo%>&formId=<%=formId%>&provNo=<%=provNo%>','<%= "growth2" + demoNo %>');return false;">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke1.btnGraphHead"/></a> <% } else { %>
            &nbsp; <% } %>
        </td>
    </tr>
</table>
<p style="font-size: 8pt;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2009.footer"/><br/></p>
<script type="text/javascript">
    Calendar.setup({
        inputField: "c_startOfGestation",
        ifFormat: "%d/%m/%Y",
        showsTime: false,
        button: "c_startOfGestation_cal",
        singleClick: true,
        step: 1
    });
    Calendar.setup({
        inputField: "p1_date1w",
        ifFormat: "%d/%m/%Y",
        showsTime: false,
        button: "p1_date1w_cal",
        singleClick: true,
        step: 1
    });
    Calendar.setup({
        inputField: "p1_date2w",
        ifFormat: "%d/%m/%Y",
        showsTime: false,
        button: "p1_date2w_cal",
        singleClick: true,
        step: 1
    });
    Calendar.setup({
        inputField: "p1_date1m",
        ifFormat: "%d/%m/%Y",
        showsTime: false,
        button: "p1_date1m_cal",
        singleClick: true,
        step: 1
    });
</script>
