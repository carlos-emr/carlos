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
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>

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

<%@ page
        import="io.github.carlos_emr.carlos.util.*, io.github.carlos_emr.carlos.form.*, io.github.carlos_emr.carlos.form.data.*,java.util.*,io.github.carlos_emr.carlos.prevention.*" %>
<%@ page
        import="io.github.carlos_emr.carlos.providers.data.*,io.github.carlos_emr.carlos.workflow.*,io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.*" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>

<%--
 //TODO: Mother's Information Doesn't save
 //TODO: Reason for injection needs to save better
 //TODO: Injection Input Needs styling
 //TODO: Page shuts when saving an Injection

 This Form works in two modes  no form started mode and started
 
 Use case 1 No form started 
 
 User comes into this 
 
 
 
 What happens when users look at the form from the form history??
 
 
--%>
<%
    String formClass = "RhImmuneGlobulin";
    String formLink = "formRhImmuneGlobulin.jsp";

    String demographicNo = request.getParameter("demographic_no");
    if (demographicNo == null) {
        demographicNo = (String) request.getAttribute("demographic_no");
    }
    int demoNo = Integer.parseInt(demographicNo);


    String formIdStr = "0";
    if (request.getParameter("formId") != null) {   ////TEMPORARY
        formIdStr = request.getParameter("formId");
    }

    int formId = Integer.parseInt(formIdStr);
    int provNo = Integer.parseInt((String) session.getAttribute("user"));
    FrmRecord rec = (new FrmRecordFactory()).factory(formClass);
    java.util.Properties props = rec.getFormRecord(LoggedInInfo.getLoggedInInfoFromSession(request), demoNo, formId);

    String project_home = request.getContextPath().substring(1);
    boolean bView = false;
    if (request.getParameter("view") != null && request.getParameter("view").equals("1")) bView = true;

    List providers = ProviderData.getProviderList();
    String prevDate = UtilDateUtilities.getToday("yyyy-MM-dd");
    String providerName = "";
    String provider = (String) session.getAttribute("user");


%>
<!--
/*
*
* Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved. *
* This software is published under the GPL GNU General Public License.
* This program is free software; you can redistribute it and/or
* modify it under the terms of the GNU General Public License
* as published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version. *
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details. *
* You should have received a copy of the GNU General Public License
* along with this program; if not, write to the Free Software
* Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA. *
*
* <OSCAR TEAM>
*
* This software was written for the
* Department of Family Medicine
* McMaster University
* Hamilton
* Ontario, Canada
*/
-->

<%@page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="io.github.carlos_emr.carlos.providers.data.ProviderData" %>
<%@ page import="io.github.carlos_emr.carlos.workflow.WorkFlowFactory" %>
<%@ page import="io.github.carlos_emr.carlos.workflow.WFState" %>
<%@ page import="io.github.carlos_emr.carlos.workflow.WorkFlow" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilDateUtilities" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecord" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecordFactory" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title>Rh Immune Globulin Injection Reporting Form</title>
        <%-- S5131: getServerName() returns the Host header — safe when deployed behind a reverse proxy that validates the Host header (required for production) --%>
        <base href="<carlos:encode value='<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>' context="htmlAttribute"/>"> <%-- NOSONAR --%>

        <link rel="stylesheet" type="text/css" media="all"
              href="<%= request.getContextPath() %>/share/calendar/calendar.css" title="win2k-cold-1"/>

        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar.js"></script>
        <script type="text/javascript"
                src="<%= request.getContextPath() %>/share/calendar/lang/<fmt:message key='global.javascript.calendar'/>"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar-setup.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/carlos-ajax.js"></script>

        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>
    </head>


    <script type="text/javascript" language="Javascript">

        var choiceFormat = new Array(6, 7, 8, 9, 12, 13);
        var allNumericField = new Array(14, 15);
        var allMatch = null;
        var action = "/<carlos:encode value='<%= StringUtils.noNull(project_home) %>' context="javaScript"/>/form/formname";

    </script>

    <script type="text/javascript">
        function hideExtraName(ele) {
            //alert(ele);
            if (ele.options[ele.selectedIndex].value != -1) {
                hideItem('providerName');
                //alert('hidding');
            } else {
                showItem('providerName');
                document.getElementById('providerName').focus();
                //alert('showing');
            }
        }

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
    </script>

    <script type="text/javascript" src="formScripts.js">

    </script>


    <body bgproperties="fixed" topmargin="0" leftmargin="0" rightmargin="0"
          onload="window.resizeTo(807,833)">

    <div class="title">Rh Immune Globulin Injection Reporting Form</div>


    <%
        Map<String, Object> h = null;
        String newFlowNeeded = (String) request.getAttribute("newWorkFlowNeeded");
    %>
    <div>
        <fieldset>
            <legend>Current Pregnancy</legend>
            <%
                String workflowType = "RH";//request.getParameter("workflowType");
                //WorkFlowState workFlow = new WorkFlowState();
                WorkFlowFactory flowFactory = new WorkFlowFactory();
                WorkFlow flow = flowFactory.getWorkFlow(workflowType);

                List<Map<String, Object>> currentWorkFlows = flow.getActiveWorkFlowList(demographicNo);

                if (currentWorkFlows != null && currentWorkFlows.size() > 0) {
                    request.setAttribute("currentWorkFlow", currentWorkFlows.get(0));
                    h = currentWorkFlows.get(0);
                }

                if (h != null) {
                    String gestAge = "";
                    try {
                        gestAge = "" + UtilDateUtilities.calculateGestationAge(new Date(), (Date) h.get("completion_date"));
                    } catch (Exception gestAgeEx) {
                    }
            %> <span style="margin-right: 20px;">EDD: <carlos:encode value='<%= String.valueOf(h.get("completion_date")) %>' context="html"/></span>
            <!-- span style="margin-right:20px;">Start date: <carlos:encode value='<%= String.valueOf(h.get("create_date_time")) %>' context="html"/> </span -->
            <span style="margin-right: 20px;">Current State:<carlos:encode value='<%= flow.getState("" + h.get("current_state")) %>' context="html"/>
</span> <span style="margin-right: 20px;">Weeks: <carlos:encode value='<%= gestAge %>' context="html"/></span> <%} else {%> <span
                style="margin-right: 20px;">No Current Pregnancy</span> <%}%> <br/>
            <form action="${pageContext.request.contextPath}/form/RHPrevention" method="post">

                <%-- input type="hidden" name="demographic_no" value="<e:forHtmlAttribute value='<%= props.getProperty("demographic_no", "0") %>' />" / --%>
            <input type="hidden" name="formCreated"
                   value="<carlos:encode value='<%= props.getProperty("formCreated", "") %>' context="htmlAttribute"/>"/>
            <input type="hidden" name="form_class" value="<carlos:encode value='<%= formClass %>' context="htmlAttribute"/>"/>
            <input type="hidden" name="form_link" value="<carlos:encode value='<%= formLink %>' context="htmlAttribute"/>"/>
            <input type="hidden" name="formId" value="<carlos:encode value='<%= String.valueOf(formId) %>' context="htmlAttribute"/>"/>
            <input type="hidden" name="submit" value="exit"/>
            <input type="hidden" name="demographic_no" value="<carlos:encode value='<%= demographicNo %>' context="htmlAttribute"/>"/>

            <%if (h != null) { %>
            <input type="hidden" name="workflowId" value="<carlos:encode value='<%= String.valueOf(h.get("ID")) %>' context="htmlAttribute"/>"/>


            <label>Change State:</label>
            <select name="state">
                <%
                    List<WFState> states = new ArrayList<WFState>(flow.getStates());
                    for (int i = 0; i < states.size(); i++) {
                        WFState state = states.get(i);
                %>
                <option value="<carlos:encode value='<%= state.getKey() %>' context="htmlAttribute"/>"
                        <%= (state.getKey().equals(h.get("current_state")) ? " selected" : "")%>><carlos:encode value='<%= state.getName() %>' context="html"/>
                </option>

                <%}%>

            </select>
            <%}%>
        </fieldset>

        <fieldset>
            <legend>Mother's Information</legend>
            <label>Date
                of Referral:</label> <input type="text" name="dateOfReferral"
                                            id="dateOfReferral" size="9"
                                            value="<carlos:encode value='<%= props.getProperty("dateOfReferral","") %>' context="htmlAttribute"/>"/> <a
                id="dateOfRefButton"><img title="Calendar" src="<%= request.getContextPath() %>/images/cal.gif"
                                          alt="Calendar" border="0"/></a> <label>EDD:</label> <input type="text"
                                                                                                     name="edd"
                                                                                                     id="end_date"
                                                                                                     size="9"
                                                                                                     value="<carlos:encode value='<%= props.getProperty("edd","") %>' context="htmlAttribute"/>">
            <a id="date"><img
                    title="Calendar" src="<%= request.getContextPath() %>/images/cal.gif" alt="Calendar" border="0"/></a>
            <br/>

            <label>Last Name:</label> <input type="text" name="motherSurname"
                                             value="<carlos:encode value='<%= props.getProperty("motherSurname","") %>' context="htmlAttribute"/>"/> <label>First
            Name:</label> <input type="text" name="motherFirstname"
                                 value="<carlos:encode value='<%= props.getProperty("motherFirstname","") %>' context="htmlAttribute"/>"/> <br/>
            <label>Date of Birth:</label> <input type="text" name="dob" size="9"
                                                 id="dob" value="<carlos:encode value='<%= props.getProperty("dob","") %>' context="htmlAttribute"/>"/> <a id="dateOB"><img
                title="Calendar" src="<%= request.getContextPath() %>/images/cal.gif" alt="Calendar" border="0"/></a>
            <br/>

            <label>Health Card #:</label> <input type="text" name="motherHIN"
                                                 value="<carlos:encode value='<%= props.getProperty("motherHIN","") %>' context="htmlAttribute"/>"/> <label>VC:</label>
            <input
                    type="text" name="motherVC" size="3"
                    value="<carlos:encode value='<%= props.getProperty("motherVC","") %>' context="htmlAttribute"/>"/> <br/>
            <label>Address:</label> <input type="text" name="motherAddress"
                                           value="<carlos:encode value='<%= props.getProperty("motherAddress","") %>' context="htmlAttribute"/>"/> <label>City:</label>
            <input type="text" name="motherCity"
                   value="<carlos:encode value='<%= props.getProperty("motherCity","") %>' context="htmlAttribute"/>"/> <br/>
            <label>Province:</label> <input type="text" name="motherProvince"
                                            value="<carlos:encode value='<%= props.getProperty("motherProvince","") %>' context="htmlAttribute"/>"/> <label>Postal
            Code:</label> <input type="text" name="motherPostalCode"
                                 value="<carlos:encode value='<%= props.getProperty("motherPostalCode","") %>' context="htmlAttribute"/>"/> <br/>
            <label>ABO:</label> <select name="motherABO">
            <option>Not Set</option>
            <option value="A"
                    <%=props.getProperty("motherABO", "").equalsIgnoreCase("A") ? "selected" : ""%>>A
            </option>
            <option value="B"
                    <%=props.getProperty("motherABO", "").equalsIgnoreCase("B") ? "selected" : ""%>>B
            </option>
            <option value="o"
                    <%=props.getProperty("motherABO", "").equalsIgnoreCase("o") ? "selected" : ""%>>O
            </option>
            <option value="AB"
                    <%=props.getProperty("motherABO", "").equalsIgnoreCase("AB") ? "selected" : ""%>>AB
            </option>
        </select> <label class="smallmargin">Rh type:</label> <select name="motherRHtype">
            <option>Not Set</option>
            <option value="N"
                    <%=props.getProperty("motherRHtype", "").equalsIgnoreCase("N") ? "selected" : ""%>>Neg
            </option>
            <option value="P"
                    <%=props.getProperty("motherRHtype", "").equalsIgnoreCase("P") ? "selected" : ""%>>Pos
            </option>
        </select> <label class="smallmargin">Antibodies Detected:</label> <select
                name="motherAntibodies">
            <option>Not Set</option>
            <option value="Y"
                    <%=props.getProperty("motherAntibodies", "").equalsIgnoreCase("Y") ? "selected" : ""%>>Yes
            </option>
            <option value="N"
                    <%=props.getProperty("motherAntibodies", "").equalsIgnoreCase("N") ? "selected" : ""%>>No
            </option>
        </select> <br/>
            <label>Hospital for Delivery:</label> <input type="text"
                                                         name="hospitalForDelivery"
                                                         value="<carlos:encode value='<%= props.getProperty("hospitalForDelivery","") %>' context="htmlAttribute"/>"/>
        </fieldset>


        <fieldset>
            <legend>Physician (OB) / Midwife</legend>
            <label>Last
                Name:</label> <input type="text" name="refPhySurname"
                                     value="<carlos:encode value='<%= props.getProperty("refPhySurname","") %>' context="htmlAttribute"/>"/> <label>First
            Name:</label> <input type="text" name="refPhyFirstname"
                                 value="<carlos:encode value='<%= props.getProperty("refPhyFirstname","") %>' context="htmlAttribute"/>"/> <br/>
            <label>Address:</label> <input type="text" name="refPhyAddress"
                                           size="20" value="<carlos:encode value='<%= props.getProperty("refPhyAddress","") %>' context="htmlAttribute"/>"/>
            <label>City:</label>
            <input type="text" name="refPhyCity"
                   value="<carlos:encode value='<%= props.getProperty("refPhyCity","") %>' context="htmlAttribute"/>"/> <br/>
            <label>Province:</label> <input type="text" name="refPhyProvince"
                                            value="<carlos:encode value='<%= props.getProperty("refPhyProvince","") %>' context="htmlAttribute"/>"/> <label>Postal
            Code:</label> <input type="text" name="refPhyPostalCode"
                                 value="<carlos:encode value='<%= props.getProperty("refPhyPostalCode","") %>' context="htmlAttribute"/>"/> <br/>
            <label>Telephone:</label> <input type="text" name="refPhyPhone"
                                             value="<carlos:encode value='<%= props.getProperty("refPhyPhone","") %>' context="htmlAttribute"/>"/> <label>Fax:</label>
            <input type="text" name="refPhyFax"
                   value="<carlos:encode value='<%= props.getProperty("refPhyFax","") %>' context="htmlAttribute"/>"/> <br/>
        </fieldset>


        <fieldset>
            <legend>Family Doctor</legend>
            <label>Last
                Name:</label> <input type="text" name="famPhySurname"
                                     value="<carlos:encode value='<%= props.getProperty("famPhySurname","") %>' context="htmlAttribute"/>"/> <label>First
            Name:</label> <input type="text" name="famPhyFirstname"
                                 value="<carlos:encode value='<%= props.getProperty("famPhyFirstname","") %>' context="htmlAttribute"/>"/> <br/>
            <label>Address:</label> <input type="text" name="famPhyAddress"
                                           size="20" value="<carlos:encode value='<%= props.getProperty("famPhyAddress","") %>' context="htmlAttribute"/>"/>
            <label>City:</label>
            <input type="text" name="famPhyCity"
                   value="<carlos:encode value='<%= props.getProperty("famPhyCity","") %>' context="htmlAttribute"/>"/> <br/>
            <label>Province:</label> <input type="text" name="famPhyProvince"
                                            value="<carlos:encode value='<%= props.getProperty("famPhyProvince","") %>' context="htmlAttribute"/>"/> <label>Postal
            Code:</label> <input type="text" name="famPhyPostalCode"
                                 value="<carlos:encode value='<%= props.getProperty("famPhyPostalCode","") %>' context="htmlAttribute"/>"/> <br/>
            <label>Telephone:</label> <input type="text" name="famPhyPhone"
                                             value="<carlos:encode value='<%= props.getProperty("famPhyPhone","") %>' context="htmlAttribute"/>"/> <label>Fax:</label>
            <input type="text" name="famPhyFax"
                   value="<carlos:encode value='<%= props.getProperty("famPhyFax","") %>' context="htmlAttribute"/>"/> <br/>
        </fieldset>


        <fieldset class="obsHist">
            <legend>Obstetrical
                History
            </legend>
            <label>G</label> <input type="text" name="obsHisG" size="2"
                                    value="<carlos:encode value='<%= props.getProperty("obsHisG","") %>' context="htmlAttribute"/>"/> <label>P</label> <input
                type="text" name="obsHisP" size="2"
                value="<carlos:encode value='<%= props.getProperty("obsHisP","") %>' context="htmlAttribute"/>"/> <label>T</label> <input
                type="text" name="obsHisT" size="2"
                value="<carlos:encode value='<%= props.getProperty("obsHisT","") %>' context="htmlAttribute"/>"/> <label>A</label> <input
                type="text" name="obsHisA" size="2"
                value="<carlos:encode value='<%= props.getProperty("obsHisA","") %>' context="htmlAttribute"/>"/> <label>L</label> <input
                type="text" name="obsHisL" size="2"
                value="<carlos:encode value='<%= props.getProperty("obsHisL","") %>' context="htmlAttribute"/>"/> <br/>

            <input type="checkbox" name="obsHisTubMolPregYes"
                    <%="checked='checked'".equals(props.getProperty("obsHisTubMolPregYes", "")) ? "checked=\"checked\"" : ""%>>Yes</input> <input
                type="checkbox" name="obsHisTubMolPregNo"
                <%="checked='checked'".equals(props.getProperty("obsHisTubMolPregNo", "")) ? "checked=\"checked\"" : ""%>>No</input> <label>Any
            previous tubal or molar pregnancy?</label> <br/>
            <input type="checkbox" name="obsHisMisAbortionYes"
                    <%="checked='checked'".equals(props.getProperty("obsHisMisAbortionYes", "")) ? "checked=\"checked\"" : ""%>>Yes</input> <input
                type="checkbox" name="obsHisMisAbortionNo"
                <%="checked='checked'".equals(props.getProperty("obsHisMisAbortionNo", "")) ? "checked=\"checked\"" : ""%>>No</input> <label
                style="float: none;">Any previous miscarriage, pregnancy loss,
            or therapeutic abortions?</label> <br/>
            <input type="checkbox" name="obsHisReceiveAntiDYes"
                    <%="checked='checked'".equals(props.getProperty("obsHisReceiveAntiDYes", "")) ? "checked=\"checked\"" : ""%>>Yes</input> <input
                type="checkbox" name="obsHisReceiveAntiDNo"
                <%="checked='checked'".equals(props.getProperty("obsHisReceiveAntiDNo", "")) ? "checked=\"checked\"" : ""%>>No</input> <label>Did
            you receive Anti-D during each of these pregnancies or following the
            pregnancy loss?</label> <br/>


        </fieldset>


        <fieldset class="obsHist">
            <legend>Past Medical
                History
            </legend>
            <input type="checkbox" name="pmHisBlClDisordersYes"
                    <%="checked='checked'".equals(props.getProperty("pmHisBlClDisordersYes", "")) ? "checked=\"checked\"" : ""%>>Yes</input> <input
                type="checkbox" name="pmHisBlClDisordersNo"
                <%="checked='checked'".equals(props.getProperty("pmHisBlClDisordersNo", "")) ? "checked=\"checked\"" : ""%>>No</input> <label>Do
            you have any bleeding or clotting disorders?</label> If yes, describe<input
                type="text" name="pmHisBlClDisordersComment"
                value="<carlos:encode value='<%= props.getProperty("pmHisBlClDisordersComment","") %>' context="htmlAttribute"/>"/> <br/>
            <input type="checkbox" name="pmHisBlPlTransfusYes"
                    <%="checked='checked'".equals(props.getProperty("pmHisBlPlTransfusYes", "")) ? "checked=\"checked\"" : ""%>>Yes</input> <input
                type="checkbox" name="pmHisBlPlTransfusNo"
                <%="checked='checked'".equals(props.getProperty("pmHisBlPlTransfusNo", "")) ? "checked=\"checked\"" : ""%>>No</input> <label>Have
            you had any blood or platelet transfusions?</label> If yes, when<input
                type="text" name="pmHisBlPlTransfusComment"
                value="<carlos:encode value='<%= props.getProperty("pmHisBlPlTransfusComment","") %>' context="htmlAttribute"/>"/></fieldset>


        <fieldset class="obsHist">
            <legend>Allergies</legend>
            <input
                    type="checkbox" name="allReactionsYes"
                    <%="checked='checked'".equals(props.getProperty("allReactionsYes", "")) ? "checked=\"checked\"" : ""%>>Yes</input> <input
                type="checkbox" name="allReactionsNo"
                <%="checked='checked'".equals(props.getProperty("allReactionsNo", "")) ? "checked=\"checked\"" : ""%>>No</input> <label>Any
            adverse reactions to previous immune globulin or other blood products?</label>
            If yes, describe<input type="text" name="allReactionsComment"
                                   value="<carlos:encode value='<%= props.getProperty("allReactionsComment","") %>' context="htmlAttribute"/>"/> <br/>
        </fieldset>


        <fieldset class="obsHist">
            <legend>Current Pregnancy</legend>

            <label>Father's ABO:</label> <select style="float: none;"
                                                 name="fatherABO">
            <option>Not Set</option>
            <option value="A"
                    <%=props.getProperty("fatherABO", "").equalsIgnoreCase("A") ? "selected" : ""%>>A
            </option>
            <option value="B"
                    <%=props.getProperty("fatherABO", "").equalsIgnoreCase("B") ? "selected" : ""%>>B
            </option>
            <option value="o"
                    <%=props.getProperty("fatherABO", "").equalsIgnoreCase("o") ? "selected" : ""%>>O
            </option>
            <option value="AB"
                    <%=props.getProperty("fatherABO", "").equalsIgnoreCase("AB") ? "selected" : ""%>>AB
            </option>
            <option value="U"
                    <%=props.getProperty("fatherABO", "").equalsIgnoreCase("U") ? "selected" : ""%>>Unknown
            </option>
        </select> <label class="smallmargin">Father's Rh type:</label> <select
                style="float: none;" name="fatherRHtype">
            <option>Not Set</option>
            <option value="N"
                    <%=props.getProperty("fatherRHtype", "").equalsIgnoreCase("N") ? "selected" : ""%>>Neg
            </option>
            <option value="P"
                    <%=props.getProperty("fatherRHtype", "").equalsIgnoreCase("P") ? "selected" : ""%>>Pos
            </option>
        </select> <br/>

            <input type="checkbox" name="curPregDueDateChangeYes"
                    <%="checked='checked'".equals(props.getProperty("curPregDueDateChangeYes", "")) ? "checked=\"checked\"" : ""%>>Yes</input> <input
                type="checkbox" name="curPregDueDateChangeNo"
                <%="checked='checked'".equals(props.getProperty("curPregDueDateChangeNo", "")) ? "checked=\"checked\"" : ""%>>No</input> <label>Has
            your due date changed during this pregnancy?</label> Comment<input type="text"
                                                                               name="curPregDueDateChangeComment"
                                                                               value="<carlos:encode value='<%= props.getProperty("curPregDueDateChangeComment","") %>' context="htmlAttribute"/>"/>
            <br/>

            <input type="checkbox" name="curPregProceduresYes"
                    <%="checked='checked'".equals(props.getProperty("curPregProceduresYes", "")) ? "checked=\"checked\"" : ""%>>Yes</input> <input
                type="checkbox" name="curPregProceduresNo"
                <%="checked='checked'".equals(props.getProperty("curPregProceduresNo", "")) ? "checked=\"checked\"" : ""%>>No</input> <label>Any
            procedures during this pregnancy such as amniocentesis, chorionic
            villous sampling, cordocentesis, or external cephalic version?</label> If yes,
            when<input type="text" name="curPregProceduresComment"
                       value="<carlos:encode value='<%= props.getProperty("curPregProceduresComment","") %>' context="htmlAttribute"/>"/> <br/>

            <input type="checkbox" name="curPregBleedingYes"
                    <%="checked='checked'".equals(props.getProperty("curPregBleedingYes", "")) ? "checked=\"checked\"" : ""%>>Yes</input> <input
                type="checkbox" name="curPregBleedingNo"
                <%="checked='checked'".equals(props.getProperty("curPregBleedingNo", "")) ? "checked=\"checked\"" : ""%>>No</input> <label>Any
            bleeding or threatened miscarriage during this pregnancy?</label> <br/>
            If yes, when<input type="text" name="curPregBleedingComment"
                               value="<carlos:encode value='<%= props.getProperty("curPregBleedingComment","") %>' context="htmlAttribute"/>"/> <br/>

            <input type="checkbox" name="curPregBleedingContYes"
                    <%="checked='checked'".equals(props.getProperty("curPregBleedingContYes", "")) ? "checked=\"checked\"" : ""%>>Yes</input> <input
                type="checkbox" name="curPregBleedingContNo"
                <%="checked='checked'".equals(props.getProperty("curPregBleedingContNo", "")) ? "checked=\"checked\"" : ""%>>No</input> <label>Has
            the bleeding continued?</label> <br/>


            <input type="checkbox" name="curPregTraumaYes"
                    <%="checked='checked'".equals(props.getProperty("curPregTraumaYes", "")) ? "checked=\"checked\"" : ""%>>Yes</input> <input
                type="checkbox" name="curPregTraumaNo"
                <%="checked='checked'".equals(props.getProperty("curPregTraumaNo", "")) ? "checked=\"checked\"" : ""%>>No</input> <label>Any
            abdominal trauma, serious fall or car accident?</label> <br/>

            <input type="checkbox" name="curPregAntiDYes"
                    <%="checked='checked'".equals(props.getProperty("curPregAntiDYes", "")) ? "checked=\"checked\"" : ""%>>Yes</input> <input
                type="checkbox" name="curPregAntiDNo"
                <%="checked='checked'".equals(props.getProperty("curPregAntiDNo", "")) ? "checked=\"checked\"" : ""%>>No</input> <label>Have
            you received any Anti-D during this pregnancy?</label> If yes, when<input
                type="text" name="curPregAntiDComment"
                value="<carlos:encode value='<%= props.getProperty("curPregAntiDComment","") %>' context="htmlAttribute"/>"/> <br/>

            <input type="checkbox" name="curPregAntiDReactionYes"
                    <%="checked='checked'".equals(props.getProperty("curPregAntiDReactionYes", "")) ? "checked=\"checked\"" : ""%>>Yes</input> <input
                type="checkbox" name="curPregAntiDReactionNo"
                <%="checked='checked'".equals(props.getProperty("curPregAntiDReactionNo", "")) ? "checked=\"checked\"" : ""%>>No</input> <label>Any
            adverse reaction?</label> <br/>

            <c:set var="__enc_1"><carlos:encode value='<%= demographicNo %>' context="uriComponent"/></c:set>
            <input type="checkbox" name="curPregBloodDrawnYes"
                    <%="checked='checked'".equals(props.getProperty("curPregBloodDrawnYes", "")) ? "checked=\"checked\"" : ""%>>Yes</input> <input
                type="checkbox" name="curPregBloodDrawnNo"
                <%="checked='checked'".equals(props.getProperty("curPregBloodDrawnNo", "")) ? "checked=\"checked\"" : ""%>>No</input> <label>Blood
            sample drawn?</label></fieldset>


        <fieldset>
            <legend>Comments</legend>
            <textarea name="comments"
                      style="width: 45em;"><carlos:encode value='<%= props.getProperty("comments", "") %>' context="html"/></textarea></fieldset>

        <input type="submit" value="<fmt:message key='global.save'/>"/> <%
                if ( h != null && h.get("ID") != null){ %> <input
            type="button"
            onClick="javascript: popup(700,600,'addRhInjection?demographic_no=<carlos:encode value='${__enc_1}' context="javaScriptAttribute"/>&amp;workflowId=<carlos:encode value='<%= String.valueOf(h.get("ID")) %>' context="javaScriptAttribute"/>&amp;formId=<carlos:encode value='<%= String.valueOf(formId) %>' context="javaScriptAttribute"/>','addInjection');"
            value="Add Injection"/> <%-- a style="color:blue; " href="javascript: function myFunction() {return false; }" onClick="popup(700,600,'addRhInjection?demographic_no=<%=demographicNo%>&amp;workflowId=<%=h.get("ID")%>&amp;formId=<%=formId%>','addInjection')">Add Injection</a --%>
                <%}%>
        </form>

        <div id="injectionInfo"></div>

        <form action="${pageContext.request.contextPath}/prevention/AddPrevention" id="deleteForm"
                method="post" target="_blank">

        <input type="hidden" name="id" id="deleteId"/>
        <input type="hidden" name="demographic_no" value="<carlos:encode value='<%= demographicNo %>' context="htmlAttribute"/>"/>

        <input type="hidden" name="delete" value="delete"/>
        </form>
        <script type="text/javascript">

            <%
            //Hack to display injections after a workflow has been closed.
            if (h == null && !props.getProperty("workflowId","").equals("") && !props.getProperty("workflowId","").equals("-1")){
               try{
                   h = new  Hashtable<String, Object>();
                   h.put("ID", props.getProperty("workflowId",""));
                   String ddate = props.getProperty("edd","");
                   ddate = ddate.substring(0,10);
                   h.put("completion_date",  new  java.sql.Date( UtilDateUtilities.StringToDate(ddate , "yyyy-MM-dd").getTime()                
 )  );

               }catch(Exception eo){
                   MiscUtils.getLogger().error("Error", eo);
               }
            }

            %>




            <%if (h != null) { %>

            function getInjectionInformation(origRequest) {
                //console.log("calling get renal dosing information");
                var url = "<%= request.getContextPath() %>/form/RhInjectionDisplay";
                var ran_number = Math.round(Math.random() * 1000000);
                var params = "demographicNo=<carlos:encode value='${__enc_1}' context="javaScript"/>&id=<carlos:encode value='<%= String.valueOf(h.get("ID")) %>' context="javaScript"/>&date=<carlos:encode value='<%= String.valueOf((Date) h.get("completion_date")) %>' context="javaScript"/>&rand=" + ran_number;  //hack to get around ie caching the page
                //console.log("params" + params);
                CarlosAjax.updater('injectionInfo', url, {method: 'get', parameters: params});
                //alert(origRequest.responseText);
            }

            getInjectionInformation();


            function refreshInfo() {
                getInjectionInformation();

            }

            <%}%>


            function deleteCall() {
                var url = "<%=request.getContextPath()%>/prevention/AddPrevention";
                var data = new URLSearchParams(new FormData(document.getElementById('deleteForm'))).toString();
                console.log("deleteCall " + data);
                var csrfEl = document.querySelector('input[name="CSRF-TOKEN"]');
                var csrfToken = csrfEl ? csrfEl.value : '';
                fetch(url, {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: {'Content-Type': 'application/x-www-form-urlencoded', 'CSRF-TOKEN': csrfToken, 'X-Requested-With': 'XMLHttpRequest'},
                    body: data
                }).then(function() { getInjectionInformation(); });
            }

            function deleteInjection(idval) {
                var delId = document.getElementById('deleteId');
                delId.value = idval;
                //console.log("calling deleteInjection "+idval);

                deleteCall();

                //alert(delId.value);
                //document.getElementById('deleteForm').submit();

            }

            Calendar.setup({
                inputField: "prevDate",
                ifFormat: "%Y-%m-%d",
                showsTime: false,
                button: "date",
                singleClick: true,
                step: 1
            });
            Calendar.setup({
                inputField: "end_date",
                ifFormat: "%Y-%m-%d",
                showsTime: false,
                button: "date",
                singleClick: true,
                step: 1
            });
            Calendar.setup({
                inputField: "dob",
                ifFormat: "%Y-%m-%d",
                showsTime: false,
                button: "dateOB",
                singleClick: true,
                step: 1
            });
            Calendar.setup({
                inputField: "dateOfReferral",
                ifFormat: "%Y-%m-%d",
                showsTime: false,
                button: "dateOfRefButton",
                singleClick: true,
                step: 1
            });
            hideExtraName(document.getElementById('providerDrop'));
        </script>
    </body>
</html>
