<%--


    Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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

    This software was written for
    Centre for Research on Inner City Health, St. Michael's Hospital,
    Toronto, Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%--
    Left column of the legacy case-management chart (encounterLayout.jsp) and the
    casemgmt/ViewNavigation fragment: links to the patient's master record, billing,
    prescriptions, consultations, immunizations, forms, messages, ticklers and labs. Parameter:
    demographicNo (required; demographic_no is accepted as an alias) names the patient every link
    is built for. It used to copy the login-wide EctSessionBean into a session-wide casemgmt_bean,
    so a navigation rendered for one patient after another chart was entered linked to the other
    patient. Requires the authenticated session and _demographic read for the patient; a request
    without a valid patient renders nothing.
    @since 2026-10-16 (request-owned patient, issue #4673)
--%>

<%-- Updated by Eugene Petruhin on 11 dec 2008 while fixing #2356548 & #2393547 --%>

<%@page import="java.nio.charset.StandardCharsets" %>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<% long loadPage = System.currentTimeMillis(); %>
<%@ include file="/WEB-INF/jsp/casemgmt/taglibs.jsp" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementFlowSheet" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementTemplateFlowSheetConfig" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.MeasurementHelper" %>
<%@ page import="io.github.carlos_emr.carlos.dxresearch.bean.dxResearchBeanHandler" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Vector" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.immunization.data.EctImmImmunizationData" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.pageUtil.EncounterPatientContext" %>
<%@ page import="io.github.carlos_emr.carlos.managers.DemographicManager" %>
<%@ page import="io.github.carlos_emr.carlos.managers.SecurityInfoManager" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager" %>
<%@ page import="io.github.carlos_emr.carlos.util.LabelValueBean" %>
<%@ page import="java.util.List" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.MeasurementGroupStyleDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.MeasurementGroupStyle" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.carlos.form.gate.FormViewRoutes" %>

<% java.util.Properties oscarVariables = CarlosProperties.getInstance(); %>
<%
    String province = ((String) oscarVariables.getProperty("billregion", "")).trim().toUpperCase();
    // Every link names the patient this request carries (the layout's demographicNo). The page
    // used to copy the login-wide EctSessionBean into a session-wide casemgmt_bean, so a
    // navigation rendered for A after B's chart was entered linked to B (issue #4673).
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    String chartPatientNo;
    try {
        chartPatientNo = EncounterPatientContext.requireDemographicNo(request);
    } catch (IllegalArgumentException invalidPatient) {
        // Nothing to link without this chart's own patient.
        return;
    }
    // Reading the record checks _demographic read for this patient.
    DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    Demographic chartPatient = demographicManager.getDemographic(loggedInInfo, chartPatientNo);
    if (chartPatient == null) {
        return;
    }
    // This chart's own entry (provider, appointment, status), or the logged-in provider's.
    EctSessionBean bean = EncounterPatientContext.entryOrLoggedIn(request, loggedInInfo, chartPatientNo);
    bean.patientLastName = chartPatient.getLastName();
    bean.patientFirstName = chartPatient.getFirstName();
    // Unrecorded birth-date parts read "0", as the encounter entry stores them for the age below.
    String[] birth = {chartPatient.getYearOfBirth(), chartPatient.getMonthOfBirth(), chartPatient.getDateOfBirth()};
    for (int part = 0; part < birth.length; part++) {
        if (birth[part] == null || birth[part].isEmpty() || "null".equals(birth[part])) birth[part] = "0";
    }
    bean.yearOfBirth = birth[0];
    bean.monthOfBirth = birth[1];
    bean.dateOfBirth = birth[2];
    if (bean.appointmentNo == null) bean.appointmentNo = "0";
    if (bean.status == null) bean.status = "";
    bean.measurementGroupNames = new ArrayList<String>();
    for (MeasurementGroupStyle groupStyle : SpringUtils.getBean(MeasurementGroupStyleDao.class).findAll()) {
        bean.measurementGroupNames.add(groupStyle.getGroupName());
    }
    // The page's EL links (the current and new forms lists) read this request's own entry; they
    // used to name no scoped bean at all and so carried no patient or provider.
    pageContext.setAttribute("bean", bean);
    // This patient's own messages, for a user who may read messages. The select used to name the
    // session-wide casemgmt_msgBeans (whichever chart's view loaded last) as a literal string, so
    // every rendering failed there with a PropertyNotFoundException.
    SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    CaseManagementManager caseManagementManager = SpringUtils.getBean(CaseManagementManager.class);
    List<LabelValueBean> navigationMessages = securityInfoManager.hasPrivilege(loggedInInfo, "_msg", "r", null)
            ? caseManagementManager.getMsgBeans(Integer.valueOf(chartPatientNo)) : List.of();
    pageContext.setAttribute("navigationMessages", navigationMessages);
    String bsurl = (String) session.getAttribute("casemgmt_oscar_baseurl");
    String backurl = bsurl + "/encounter/IncomingEncounter?";
//get programId
    String pgId = io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.resolve(request);
    if (pgId == null) pgId = "";
%>

<script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/carlosCsrfForm.js"></script>
<script type="text/javascript" language=javascript>
    function popupPage(varpage) {
        var page = "" + varpage;
        windowprops = "height=600,width=700,location=no,"
            + "scrollbars=yes,menubars=no,toolbars=no,resizable=yes,top=0,left=0";
        window.open(page, "", windowprops);
    }

    // The unbill (-B) link: billing deletes take only form posts, so post the link's parameters into
    // the popup (opened in the click so popup blockers allow it); carlosPostForm adds the CSRF token.
    function onUnbilled(url) {
        if (confirm("You are about to delete the previous billing, are you sure?")) {
            var parts = String(url).split('?');
            var fields = {};
            new URLSearchParams(parts[1] || '').forEach(function (value, key) { fields[key] = value; });
            var popup = window.open('', 'unbilled', 'height=600,width=700,location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,top=0,left=0');
            carlosPostForm(parts[0], fields, {target: 'unbilled', popup: popup});
        }
    }

    function selectBox(name) {
        to = name.options[name.selectedIndex].value;
        name.selectedIndex = 0;
        if (to != "null")
            popupPage(to);
    }

    function popUpMsg(vheight, vwidth, msgPosition) {


        var page = "<%=session.getAttribute("casemgmt_oscar_baseurl")%>" + "/messenger/ViewMessageByPosition?from=encounter&orderBy=!date&demographic_no=<%=bean.demographicNo%>&messagePosition=" + msgPosition;
        windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
        var popup = window.open(page, "", windowprops);
        if (popup != null) {
            if (popup.opener == null) {
                popup.opener = self;
            }
        }
        popup.focus();
    }

    function popUpMeasurements(vheight, vwidth, name, varpage) { //open a new popup window
        if (varpage != 'null') {
            name.options[0].selected = true;
            var page = "<%=session.getAttribute("casemgmt_oscar_baseurl")%>" + "/encounter/oscarMeasurements/SetupMeasurements?groupName=" + encodeURIComponent(varpage)
                + "&demographicNo=${carlos:forJavaScript(carlos:forUriComponent(param.demographicNo))}";
            windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=600,screenY=200,top=0,left=0";
            var popup = window.open(page, "", windowprops);
            if (popup != null) {
                if (popup.opener == null) {
                    popup.opener = self;
                    alert("hi this is a null for self!");
                }
            }
        }
    }

    function popupSearchPage(vheight, vwidth, varpage) { //open a new popup window
        var page = "" + varpage;
        windowprop = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=50,screenY=50,top=0,left=0";
        var popup = window.open(page, "", windowprop);
    }

    function grabEnter(event) {
        if (window.event && window.event.keyCode == 13) {
            popupSearchPage(600, 800, document.forms['ksearch'].channel.options[document.forms['ksearch'].channel.selectedIndex].value + urlencode(document.forms['ksearch'].keyword.value));
            return false;
        } else if (event && event.which == 13) {
            popupSearchPage(600, 800, document.forms['ksearch'].channel.options[document.forms['ksearch'].channel.selectedIndex].value + urlencode(document.forms['ksearch'].keyword.value));
            return false;
        }
    }

    function urlencode(str) {
        var ns = (navigator.appName == "Netscape") ? 1 : 0;
        if (ns) {
            return escape(str);
        }
        var ms = "%25#23 20+2B?3F<3C>3E{7B}7D[5B]5D|7C^5E~7E`60";
        var msi = 0;
        var i, c, rs, ts;
        while (msi < ms.length) {
            c = ms.charAt(msi);
            rs = ms.substring(++msi, msi + 2);
            msi += 2;
            i = 0;
            while (true) {
                i = str.indexOf(c, i);
                if (i == -1) break;
                ts = str.substring(0, i);
                str = ts + "%" + rs + str.substring(++i, str.length);
            }
        }
        return str;
    }
</script>

<style type="text/css">
    .ControlSelect {
        font-family: Verdana, Tahoma, Arial, sans-serif;
        font-size: 80%;
        width: 100%;
    }
</style>
<div id="noprint" name="noprint">
    <table style="font:bold small-caps 10pt/12pt">
        <form name="navigationForm" method="get">
            <% java.util.Date today = new java.util.Date();
                String curYear = Integer.toString(today.getYear());
                String curMonth = Integer.toString(today.getMonth());
                String curDay = Integer.toString(today.getDay());
                String Hour = Integer.toString(today.getHours());
                String Min = Integer.toString(today.getMinutes());
                String eURL = "/encounter/IncomingEncounter?casetoEncounter=true&providerNo=" + bean.providerNo + "&appointmentNo=" + bean.appointmentNo + "&demographicNo=" + bean.demographicNo + "&curProviderNo=" + bean.providerNo + "&reason=" + java.net.URLEncoder.encode(" ", StandardCharsets.UTF_8) + "&userName=" + java.net.URLEncoder.encode(bean.patientFirstName + " " + bean.patientLastName, StandardCharsets.UTF_8) + "&curDate=" + curYear + "-" + curMonth + "-" + curDay + "&appointmentDate=" + curYear + "-" + curMonth + "-" + curDay + "&startTime=" + Hour + ":" + Min + "&status=t";%>
            <caisirole:SecurityAccess accessName="medical encounter" accessType="access"
                                      providerNo="<%=bean.providerNo%>" demoNo="<%=bean.demographicNo%>"
                                      programId="<%=pgId%>">
            </caisirole:SecurityAccess>
            <!-- tr><td><a href="</td></tr -->

            <tr style="background-color:#BBBBBB;">
                <td>Clinical Modules</td>
            </tr>

            <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                <!-- master -->
                <caisirole:SecurityAccess accessName="master file" accessType="access" providerNo="<%=bean.providerNo%>"
                                          demoNo="<%=bean.demographicNo%>" programId="<%=pgId%>">

                    <tr>
                        <td><a href="javascript:void(0)"
                               onClick="popupPage('<%=bsurl%>/demographic/DemographicEdit?demographic_no=<%=bean.demographicNo%>');return false;">Master</a>
                        </td>
                    </tr>

                </caisirole:SecurityAccess>

                <caisirole:SecurityAccess accessName="billing" accessType="access" providerNo="<%=bean.providerNo%>"
                                          demoNo="<%=bean.demographicNo%>" programId="<%=pgId%>">
                    <!-- billing -->

                    <% if (bean.status.indexOf('B') == -1) { %>
                    <tr>
                        <td><a href="javascript:void(0)"
                               onClick="popupPage('<%=bsurl%>/billing?billRegion=<%=java.net.URLEncoder.encode(province, StandardCharsets.UTF_8)%>&billForm=<%=java.net.URLEncoder.encode(oscarVariables.getProperty("default_view"), StandardCharsets.UTF_8)%>&hotclick=<%=java.net.URLEncoder.encode("", StandardCharsets.UTF_8)%>&appointment_no=<%=bean.appointmentNo%>&appointment_date=<%=bean.appointmentDate%>&start_time=<%=Hour+":"+Min%>&demographic_name=<%=java.net.URLEncoder.encode(bean.patientLastName+","+bean.patientFirstName)%>&demographic_no=<%=bean.demographicNo%>&providerview=<%=bean.curProviderNo%>&user_no=<%=bean.providerNo%>&apptProvider_no=<%=bean.curProviderNo%>&bNewForm=1&status=t');return false;">Billing</a>
                        </td>
                    </tr>
                    <%} else { %>
                    <tr>
                        <td><a href="javascript:void(0)"
                               onClick="onUnbilled('<%=bsurl%>/billing/CA/<%=province%>/BillingDeleteWithoutNo?appointment_no=<%=bean.appointmentNo%>');return false;">Billing</a>
                        </td>
                    </tr>
                    <%} %>

                </caisirole:SecurityAccess>

                <caisirole:SecurityAccess accessName="prescription Write" accessType="access"
                                          providerNo="<%=bean.providerNo%>" demoNo="<%=bean.demographicNo%>"
                                          programId="<%=pgId%>">

                    <!-- prescription -->

                    <tr>
                        <td><a href="javascript:void(0)"
                               onClick="popupPage('<%=bsurl%>/rx/choosePatient?providerNo=<%=bean.providerNo%>&demographicNo=<%=bean.demographicNo%>');return false;">Prescriptions</a>
                        </td>
                    </tr>

                </caisirole:SecurityAccess>

                <!-- allergies -->
                <!-- tr><td><a href="javascript:void(0)" onClick="popupPage('<%=bsurl%>/rx/ShowAllergies2.jsp?providerNo=<%=bean.providerNo%>&demographicNo=<%=bean.demographicNo%>');return false;">Allergies</a></td></tr -->


                <!-- Consultations -->
                <tr>
                    <td><a href="javascript:void(0)"
                           onClick="popupPage('<%=bsurl%>/encounter/oscarConsultationRequest/ViewDisplayDemographicConsultationRequests?de=<%=bean.demographicNo%>');return false;">Consultations</a>
                    </td>
                </tr>

                <caisirole:SecurityAccess accessName="immunization" accessType="access"
                                          providerNo="<%=bean.providerNo%>" demoNo="<%=bean.demographicNo%>"
                                          programId="<%=pgId%>">
                    <!-- IMMUNIZATION -->
                    <oscar:oscarPropertiesCheck property="IMMUNIZATION" value="yes" defaultVal="true">
                        <% if (EctImmImmunizationData.hasImmunizations(bean.demographicNo)) { %>
                        <tr>
                            <td><a style="color:red" href="javascript:void(0)"
                                   onClick="popupPage('<%=bsurl%>/encounter/immunization/initSchedule?demographic_no=<carlos:encode value='<%= chartPatientNo %>' context="uriComponent"/>');return false;">Immunizations</a>
                            </td>
                        </tr>
                        <% } else {%>
                        <tr>
                            <td><a href="javascript:void(0)"
                                   onClick="popupPage('<%=bsurl%>/encounter/immunization/initSchedule?demographic_no=<carlos:encode value='<%= chartPatientNo %>' context="uriComponent"/>');return false;">Immunizations</a>
                            </td>
                        </tr>
                        <% } %>
                    </oscar:oscarPropertiesCheck>
                </caisirole:SecurityAccess>

                <!-- Prevention -->
                <caisirole:SecurityAccess accessName="prevention" accessType="access" providerNo="<%=bean.providerNo%>"
                                          demoNo="<%=bean.demographicNo%>" programId="<%=pgId%>">
                    <oscar:oscarPropertiesCheck property="PREVENTION" value="yes">
                        <tr>
                            <td><a href="javascript:void(0)"
                                   onClick="popupPage('<%=bsurl%>/prevention/ViewPreventionIndex?demographic_no=<%=bean.demographicNo%>');return false;">
                                <oscar:preventionWarnings
                                        demographicNo="<%=bean.demographicNo%>">prevention</oscar:preventionWarnings></a>
                            </td>
                        </tr>
                    </oscar:oscarPropertiesCheck>
                </caisirole:SecurityAccess>

                <!-- oscarcomm -->
                <caisirole:SecurityAccess accessName="oscarcomm" accessType="access" providerNo="<%=bean.providerNo%>"
                                          demoNo="<%=bean.demographicNo%>" programId="<%=pgId%>">

                </caisirole:SecurityAccess>

                <!-- Disease Registry -->
                <caisirole:SecurityAccess accessName="disease registry" accessType="access"
                                          providerNo="<%=bean.providerNo%>" demoNo="<%=bean.demographicNo%>"
                                          programId="<%=pgId%>">

                    <tr>
                        <td><a href="javascript:void(0)"
                               onClick="popupPage('<%=bsurl%>/oscarResearch/dxresearch/setupDxResearch?demographicNo=<%=bean.demographicNo%>&providerNo=<%=bean.providerNo%>&quickList=');return false;">Disease
                            Registry</a></td>
                    </tr>
                </caisirole:SecurityAccess>

            </caisi:isModuleLoad>

            <!-- add tickler -->
            <caisirole:SecurityAccess accessName="Write Ticklers" accessType="Action" providerNo="<%=bean.providerNo%>"
                                      demoNo="<%=bean.demographicNo%>" programId="<%=pgId%>">
                <tr>
                    <td><a href="javascript:void(0)"
                           <c:set var="__enc_1"><carlos:encode value='<%= bean.getPatientLastName() +"," + bean.getPatientFirstName() %>' context="uriComponent"/></c:set>
                           onClick="popupPage('<%=bsurl%>/tickler/ViewAddTickler?demographic_no=<%=bean.demographicNo%>&name=<carlos:encode value='${__enc_1}' context="javaScriptAttribute"/>');return false;">Add
                        Tickler</a></td>
                </tr>
            </caisirole:SecurityAccess>

            <caisirole:SecurityAccess accessName="medical form" accessType="access" providerNo="<%=bean.providerNo%>"
                                      demoNo="<%=bean.demographicNo%>" programId="<%=pgId%>">
                <tr style="background-color:#BBBBBB;">
                    <td>Forms</td>
                </tr>
                <!-- current forms -->
                <tr>
                    <td>

                        <select name="selectCurrentForms" class="ControlSelect" onChange="javascript:selectBox(this)"
                                onMouseOver="javascript:window.status='View any of <%= SafeEncode.forJavaScriptAttribute(bean.patientLastName + "," + bean.patientFirstName) %>\'s current forms.';return true;">
                            <option value="null" selected>-current forms-</option>
                            <c:forEach var="cf" items="${casemgmt_newFormBeans}">
                                <c:if test="${cf.formTable ne ''}">
                                    <c:set var="pforms" value="${cf.patientForms}" />
                                    <c:if test="${fn:length(pforms) > 0}">
                                        <c:set var="pfrm" value="${pforms[0]}" />
                                        <c:set var="value" value="${sessionScope.casemgmt_oscar_baseurl}/form/forwardshortcutname?formname=${cf.formName}&demographic_no=${bean.demographicNo}" />
                                        <c:set var="label" value="${cf.formName}&nbsp;Cr:${pfrm.created}&nbsp;Ed:${pfrm.edited}" />
                                        <option value="${value}">${label}</option>
                                    </c:if>
                                </c:if>
                            </c:forEach>


                        </select>
                    </td>
                </tr>

                <!-- add new form -->
                <tr>
                    <td>
                        <select name="selectNewForms" class="ControlSelect" onChange="javascript:selectBox(this)"
                                onMouseOver="javascript:window.status='View <%= SafeEncode.forJavaScriptAttribute(bean.patientLastName + "," + bean.patientFirstName) %>\'s new forms list.';return true;">
                            <option value="null" selected>-add new form-</option>
                            <c:forEach var="cf" items="${casemgmt_newFormBeans}">
                                <c:if test="${cf.hidden}">
                                    <%-- Legacy form_value stores paths like "../form/formX.jsp?demographic_no=".
                                         Form JSPs now live under /WEB-INF/jsp/form/ and are served by the
                                         form/* Struts wildcard action, so translate the legacy path to the
                                         new action route to avoid 404s. Fall back to the legacy value for
                                         forms not in the allow-list. --%>
                                    <%
                                        io.github.carlos_emr.carlos.commn.model.EncounterForm encounterForm =
                                                (io.github.carlos_emr.carlos.commn.model.EncounterForm) pageContext.getAttribute("cf");
                                        String legacyFormValue = encounterForm.getFormValue();
                                        String resolvedActionPath = FormViewRoutes.resolveActionPath(legacyFormValue);
                                        String formUrlBase = (resolvedActionPath != null)
                                                ? request.getContextPath() + resolvedActionPath
                                                : legacyFormValue;
                                        pageContext.setAttribute("formUrlBase", formUrlBase);
                                    %>
                                    <c:set var="value" value="${formUrlBase}${bean.demographicNo}&formId=0&provNo=${bean.providerNo}" />
                                    <c:set var="label" value="${cf.formName}" />
                                    <option value="${carlos:forHtmlAttribute(value)}">${carlos:forHtmlContent(label)}</option>
                                </c:if>
                            </c:forEach>
                        </select>
                    </td>
                </tr>

                <!-- old forms -->
                <tr>
                    <td>
                        <a href="javascript:void(0)"
                           onClick="popupPage('<%=bsurl%>/encounter/ViewFormlist?demographic_no=<%=bean.demographicNo%>'); return false;">-old
                            forms-</a>
                    </td>
                </tr>
            </caisirole:SecurityAccess>

            <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                <tr style="background-color:#BBBBBB;">
                    <td>messenger</td>
                </tr>
                <!-- select message -->
                <tr>
                    <td>
                        <select name="msgSelect" class="ControlSelect"
                                onchange="javascript:popUpMsg(600,900,this.options[this.selectedIndex].value)">
                            <option value="null" selected>-Select Message-</option>
                            <c:forEach var="cmb" items="${navigationMessages}">
                                <option value="<carlos:encode value='${cmb.label}' context="htmlAttribute"/>"><carlos:encode value='${cmb.value}'/></option>
                            </c:forEach>
                        </select>
                    </td>
                </tr>

                <!-- add a new message -->
                <tr>
                    <td>
                        <a href="javascript:void(0)"
                           onClick="popupPage('<%=bsurl%>/messenger/SendDemoMessage?orderby=date&boxType=3&demographic_no=<%=bean.demographicNo%>&providerNo=<%=bean.providerNo%>&userName=<%= SafeEncode.forJavaScriptAttribute(SafeEncode.forUriComponent(bean.userName)) %>'); return false;">New
                            Messages</a>
                    </td>
                </tr>

                <!-- all message -->
                <tr>
                    <td>
                        <a href="javascript:void(0)"
                           onClick="popupPage('<%=bsurl%>/messenger/DisplayDemographicMessages?orderby=date&boxType=3&demographic_no=<%=bean.demographicNo%>&providerNo=<%=bean.providerNo%>&userName=<%= SafeEncode.forJavaScriptAttribute(SafeEncode.forUriComponent(bean.userName)) %>'); return false;">-All
                            Messages-</a>
                    </td>
                </tr>
            </caisi:isModuleLoad>
            <%
                dxResearchBeanHandler dxRes;
                ArrayList<String> flowsheets;
                Vector dxCodes;
            %>
            <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                <caisirole:SecurityAccess accessName="measurements" accessType="access"
                                          providerNo="<%=bean.providerNo%>" demoNo="<%=bean.demographicNo%>"
                                          programId="<%=pgId%>">
                    <tr style="background-color:#BBBBBB;">
                        <td>Case Management Flowsheets</td>
                    </tr>

                    <!-- caisi flow sheet -->
                    <tr>
                        <td>
                            <%
                                dxRes = new dxResearchBeanHandler(bean.demographicNo);
                                dxCodes = dxRes.getActiveCodeListWithCodingSystem();
                                flowsheets = MeasurementTemplateFlowSheetConfig.getInstance().getUniveralFlowsheets();
                                for (String flowsheet : flowsheets) {
                                    MeasurementFlowSheet measurementFlowSheet = MeasurementTemplateFlowSheetConfig.getInstance().getFlowSheet(flowsheet);
                                    if (MeasurementHelper.flowSheetRequiresWork(bean.demographicNo, measurementFlowSheet)) {
                            %>* <% }
                        %>
                            <a href="javascript:void(0)"
                               onClick="popupPage('<%=bsurl%>/encounter/oscarMeasurements/ViewTemplateFlowSheet?demographic_no=<%=bean.demographicNo%>&template=<%=flowsheet%>','flowsheet')"><%=MeasurementTemplateFlowSheetConfig.getInstance().getDisplayName(flowsheet)%>
                            </a><br/>
                            <%}%>
                        </td>
                    </tr>
                </caisirole:SecurityAccess>

                <caisirole:SecurityAccess accessName="measurements" accessType="access"
                                          providerNo="<%=bean.providerNo%>" demoNo="<%=bean.demographicNo%>"
                                          programId="<%=pgId%>">
                    <tr style="background-color:#BBBBBB;">
                        <td>Measurements</td>
                    </tr>
                    <!-- measurement -->
                    <tr>
                        <td>
                            <%
                                dxRes = new dxResearchBeanHandler(bean.demographicNo);
                                dxCodes = dxRes.getActiveCodeListWithCodingSystem();
                                flowsheets = MeasurementTemplateFlowSheetConfig.getInstance().getFlowsheetsFromDxCodes(dxCodes);
                                for (String flowsheet : flowsheets) {
                            %>
                            <a href="javascript:void(0)"
                               onClick="popupPage('<%=bsurl%>/encounter/oscarMeasurements/ViewTemplateFlowSheet?demographic_no=<%=bean.demographicNo%>&template=<%=flowsheet%>','flowsheet')"><%=MeasurementTemplateFlowSheetConfig.getInstance().getDisplayName(flowsheet)%>
                            </a>
                            <%}%>

                            <select name="measurementGroupSelect" class="ControlSelect"
                                    onchange="javascript:popUpMeasurements(500,1000,this,this.options[this.selectedIndex].value)">
                                <option value="null" selected>-select group-</option>
                                <%
                                    for (int j = 0; j < bean.measurementGroupNames.size(); j++) {
                                        String tmp = (String) bean.measurementGroupNames.get(j);
                                %>
                                <option value="<%= SafeEncode.forHtmlAttribute(tmp) %>"><%= SafeEncode.forHtmlContent(tmp) %>
                                </option>
                                <%}%>
                            </select>
                        </td>
                    </tr>

                    <!-- old measurements -->
                    <tr>
                        <td>
                            <a href="javascript:void(0)"
                               onClick="popupPage('<%=bsurl%>/encounter/oscarMeasurements/SetupHistoryIndex?demographicNo=${carlos:forJavaScriptAttribute(carlos:forUriComponent(param.demographicNo))}'); return false;">-Old
                                Measurements--</a>
                        </td>
                    </tr>
                </caisirole:SecurityAccess>
            </caisi:isModuleLoad>

            <tr style="background-color:#BBBBBB;">
                <td>Clinical Resources</td>
            </tr>

            <%-- This page used to load the patient's lab results into the session (as
                 casemgmt_labsbeans) for the lab dropdown below, which read another name and so
                 never listed one. The session copy outlived the page as the last-rendered chart's
                 labs (issue #4673); the unused query is gone and the dropdown stays as it was. --%>
            <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                <tr>
                    <td>
                        <a href="javascript:void(0)"
                           ONCLICK="popupPage('http://resource.oscarmcmaster.org/oscarResource/');return false;">resource</a><br>
                    </td>
                </tr>

            </caisi:isModuleLoad>
            <tr>
                <td>
                    <a href="javascript:void(0)"
                       onClick="popupPage('<%=bsurl%>/documentManager/ViewDocumentReport?function=demographic&doctype=lab&functionid=<carlos:encode value='<%= bean.demographicNo %>' context="uriComponent"/>');return false;">documents</a><br>
                </td>
            </tr>

            <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                <caisirole:SecurityAccess accessName="eform" accessType="access" providerNo="<%=bean.providerNo%>"
                                          demoNo="<%=bean.demographicNo%>" programId="<%=pgId%>">
                    <!-- eform -->
                    <tr>
                        <td>
                            <a href="javascript:void(0)"
                               onClick="popupPage('<%=bsurl%>/eform/efmpatientformlist?demographic_no=<%=bean.demographicNo%>');return false;">E-Forms</a><br>
                        </td>
                    </tr>
                </caisirole:SecurityAccess>
            </caisi:isModuleLoad>

            <caisirole:SecurityAccess accessName="read ticklers" accessType="access" providerNo="<%=bean.providerNo%>"
                                      demoNo="<%=bean.demographicNo%>" programId="<%=pgId%>">
                <tr>
                    <td>
                        <a href="javascript:void(0)"
                           onClick="popupPage('<%=bsurl%>/tickler/ViewTicklerMain?demoview=<%=bean.demographicNo%>');return false;">View
                            Tickler</a><br>
                    </td>
                </tr>
            </caisirole:SecurityAccess>

            <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                <tr>
                    <td>
                        <a href="javascript:void(0)"
                           onClick="popupPage('<carlos:encode value='<%=bsurl%>' context="javaScriptAttribute"/>/encounter/ViewCalculators?demo=<carlos:encode value='<%=bean.demographicNo%>' context="uriComponent"/>'); return false;">calculators</a><br>
                    </td>
                </tr>

                <caisirole:SecurityAccess accessName="lab" accessType="access" providerNo="<%=bean.providerNo%>"
                                          demoNo="<%=bean.demographicNo%>" programId="<%=pgId%>">

                    <!-- lab result -->
                    <tr>
                        <td>
                            <select name="selectCurrentForms" class="ControlSelect"
                                    onChange="javascript:selectBox(this)"
                                    onMouseOver="javascript:window.status='View <%= SafeEncode.forJavaScriptAttribute(bean.patientFirstName + " " + bean.patientLastName) %>\'s lab results'; return true;">
                                <option value="null" selected>-lab results-</option>
                            </select>
                        </td>
                    </tr>
                </caisirole:SecurityAccess>
            </caisi:isModuleLoad>

        </form>

        <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
            <form name="ksearch" method="get">
                <tr style="background-color:#BBBBBB;">
                    <td>Internet Resources</td>
                </tr>
                <tr>
                    <td>
                        search for ...
                        <br>
                        <input type="text" name="keyword" value="" onkeypress="return grabEnter(event)"/>
                        <br>
                        using ...
                        <br>
                        <select name="channel" class="ControlSelect">
                            <option value="http://resource.oscarmcmaster.org/oscarResource/OSCAR_search/OSCAR_search_results?title=">
                                OSCAR search
                            </option>
                            <option value="http://www.google.com/search?q=">Google</option>
                            <option value="http://www.ncbi.nlm.nih.gov/entrez/query.fcgi?SUBMIT=y&CDM=Search&DB=PubMed&term=">
                                Pubmed
                            </option>
                            <option value="http://search.nlm.nih.gov/medlineplus/query?DISAMBIGUATION=true&FUNCTION=search&SERVER2=server2&SERVER1=server1&PARAMETER=">
                                Medline Plus
                            </option>
                            <option value="http://www.bnf.org/bnf/bnf/current/noframes/search.htm?n=50&searchButton=Search&q=">
                                BNF.org
                            </option>
                        </select>
                        <input type="button" name="button" value="Go"
                               onClick="popupSearchPage(600,800,forms['ksearch'].channel.options[forms['ksearch'].channel.selectedIndex].value+urlencode(forms['ksearch'].keyword.value) ); return false;">
                    </td>
                </tr>
            </form>
        </caisi:isModuleLoad>
    </table>
</div>
