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
<%@ page
        import="io.github.carlos_emr.carlos.util.*, io.github.carlos_emr.carlos.form.*, io.github.carlos_emr.carlos.form.data.*,java.util.*,io.github.carlos_emr.carlos.prevention.*" %>
<%@ page
        import="io.github.carlos_emr.carlos.providers.data.*,io.github.carlos_emr.carlos.workflow.*,io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.*" %>
<%@ page import="io.github.carlos_emr.carlos.providers.data.ProviderData" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilDateUtilities" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecord" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecordFactory" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>




<%
    String formClass = "RhImmuneGlobulin";
    String formLink = "formRhImmuneGlobulin.jsp";

    String demographicNo = request.getParameter("demographic_no");
    if (demographicNo == null) {
        demographicNo = (String) request.getAttribute("demographic_no");
    }
    int demoNo = Integer.parseInt(demographicNo);

    String workflowId = request.getParameter("workflowId");
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
<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="form.addRhInjection.title"/></title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/calendar/calendar.css" title="win2k-cold-1"/>

        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar.js"></script>
        <script type="text/javascript"
                src="<%= request.getContextPath() %>/share/calendar/lang/<fmt:message key="global.javascript.calendar"/>"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar-setup.js"></script>


        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>
    </head>


    <script type="text/javascript" language="Javascript">

        var choiceFormat = new Array(6, 7, 8, 9, 12, 13);
        var allNumericField = new Array(14, 15);
        var allMatch = null;
        var action = "/<%=project_home%>/form/formname";

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
          onload="window.resizeTo(768,833)">


    <script type="text/javascript">
        function process(formInject) {
            for (i = 0; formInject.reason_check.length; i++) {
                if (formInject.reason_check[i].checked) {
                    //alert(formInject.reason_check[i].value);
                    if (formInject.reason_check[i].value == '<fmt:message key="form.addRhInjection.reason.other"/>') {
                        formInject.reason.value = document.getElementById('reasonOtherText').value;
                    } else {
                        formInject.reason.value = formInject.reason_check[i].value;
                    }
                    break;
                }
            }
            //alert( formInject.reason.value );
            return False;
        }
    </script>

    <form action="${pageContext.request.contextPath}/prevention/AddPrevention" method="post" onsubmit="return process(this);" id="injectForm">
        <input type="hidden" name="prevention" value="RH"/>
        <input type="hidden" name="demographic_no" value="<carlos:encode value='<%= demographicNo %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="workflowId" value="<carlos:encode value='<%= workflowId %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="formId" value="<carlos:encode value='<%= formIdStr %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="reason" value=""/>


        <fieldset>
            <legend><fmt:message key="form.addRhInjection.legend"/></legend>
            <div style="float: left;"><input name="given" type="radio"
                                             value="given" checked><fmt:message key="form.addRhInjection.completed"/></input><br/>
                <input name="given" type="radio" value="refused"><fmt:message key="form.addRhInjection.refused"/></input><br/>

            </div>
            <div style="float: left; margin-left: 30px;"><label
                    for="prevDate" class="fields"><fmt:message key="form.addRhInjection.date"/></label> <input type="text"
                                                                       name="prevDate" id="prevDate"
                                                                       value="<carlos:encode value='<%= prevDate %>' context="htmlAttribute"/>" size="9">
                <a id="date"><img title="<fmt:message key="form.addRhInjection.calendar"/>" src="<%= request.getContextPath() %>/images/cal.gif"
                                  alt="<fmt:message key="form.addRhInjection.calendar"/>" border="0"/></a> <br>
                <label for="provider" class="fields"><fmt:message key="form.addRhInjection.provider"/></label> <input
                        type="text" name="providerName" id="providerName"
                        value="<carlos:encode value='<%= providerName %>' context="htmlAttribute"/>"/> <select
                        onchange="javascript:hideExtraName(this);" id="providerDrop"
                        name="provider">
                    <%
                        for (int i = 0; i < providers.size(); i++) {
                            Map h = (Map) providers.get(i);
                    %>
                    <option value="<carlos:encode value='<%= String.valueOf(h.get("providerNo")) %>' context="htmlAttribute"/>"
                            <%= (h.get("providerNo").equals(provider) ? " selected" : "") %>><carlos:encode value='<%= String.valueOf(h.get("lastName")) %>' context="html"/>
                        <carlos:encode value='<%= String.valueOf(h.get("firstName")) %>' context="html"/>
                    </option>
                    <%}%>
                    <option value="-1" <%= ("-1".equals(provider) ? " selected" : "") %>><fmt:message key="form.addRhInjection.reason.other"/></option>
                </select></div>
        </fieldset>
        <fieldset>
            <legend><fmt:message key="form.addRhInjection.result"/></legend>
            <label for="location"><fmt:message key="form.addRhInjection.hospitalClinic"/></label>
            <input type="text" name="location"/> <br/>
            <label for="route"><fmt:message key="form.addRhInjection.route"/></label> <input type="text" name="route"/><br/>
            <label for="lot"><fmt:message key="form.addRhInjection.lotNo"/></label> <input type="text" name="lot"/><br/>
            <label for="lot"><fmt:message key="form.addRhInjection.product"/></label> <input type="text" name="product"/><br/>
            <label for="manufacture"><fmt:message key="form.addRhInjection.manufacture"/></label> <input type="text"
                                                                 name="manufacture"/><br/>
            <label><fmt:message key="form.addRhInjection.dosage"/></label> <input type="text" name="dosage" size="9"/><small><fmt:message key="form.addRhInjection.unitsMcg"/></small>
        </fieldset>


        <%--
                            <div class="boxed2">
                                <label for="prevDate" class="fields" ><fmt:message key="form.addRhInjection.date"/></label>    <input type="text" name="prevDate" id="prevDate" value="<e:forHtmlAttribute value='<%= prevDate %>' />" size="9" > <a id="date" style="float:left;"><img title="<fmt:message key="form.addRhInjection.calendar"/>" src="<%= request.getContextPath() %>/images/cal.gif" alt="<fmt:message key="form.addRhInjection.calendar"/>" border="0" /></a>
                                <label ><fmt:message key="form.addRhInjection.hospitalClinic"/></label><input type="text" name="location" size="9"/>
                                <br/>

                                <label for="providers" class="fields"><fmt:message key="form.addRhInjection.provider"/></label> <input type="text" name="providerName" id="providerName" value="<e:forHtmlAttribute value='<%= providerName %>' />"/>
                                      <select onchange="javascript:hideExtraName(this);" id="providerDrop" name="providers">
                                          <%for (int i=0; i < providers.size(); i++) {
                                               Hashtable ph = (Hashtable) providers.get(i);%>
                                            <option value="<e:forHtmlAttribute value='<%= String.valueOf(ph.get("providerNo")) %>' />" <%= ( ph.get("providerNo").equals(providers) ? " selected" : "" ) %>><e:forHtmlContent value='<%= String.valueOf(ph.get("lastName")) %>' /> <e:forHtmlContent value='<%= String.valueOf(ph.get("firstName")) %>' /></option>
                                          <%}%>
                                          <option value="-1" <%= ( "-1".equals(providers) ? " selected" : "" ) %> ><fmt:message key="form.addRhInjection.reason.other"/></option>
                                      </select>

                                <br/>
                                <label>Lot No:</label><input type="text" name="lot" size="9"/>
                                <label>Dosage:</label> <input type="text" name="dosage" size="9"/><small>mcg</small>
                            </div>
                        --%>

        <fieldset>
            <legend><fmt:message key="form.addRhInjection.reasonLegend"/></legend>
            <ul>
                <li><input type="radio" name="reason_check"
                           value="<fmt:message key="form.addRhInjection.reason.antepartum"/>" checked><fmt:message key="form.addRhInjection.reason.antepartum"/></input></li>
                <li><input type="radio" name="reason_check" value="<fmt:message key="form.addRhInjection.amniocentesis"/>"><fmt:message key="form.addRhInjection.amniocentesis"/>
                    </input></li>
                <li><input type="radio" name="reason_check"
                           value="<fmt:message key="form.addRhInjection.reason.ectopicPregnancy"/>"><fmt:message key="form.addRhInjection.reason.ectopicPregnancy"/> </input></li>
                <li><input type="radio" name="reason_check"
                           value="<fmt:message key="form.addRhInjection.antenatalBleeding"/>"><fmt:message key="form.addRhInjection.antenatalBleeding"/> </input></li>
                <li><input type="radio" name="reason_check"
                           value="<fmt:message key="form.addRhInjection.reason.spontaneousAbortion"/>"><fmt:message key="form.addRhInjection.reason.spontaneousAbortion"/> </input></li>
                <li><input type="radio" name="reason_check"
                           value="<fmt:message key="form.addRhInjection.reason.therapeuticAbortion"/>"><fmt:message key="form.addRhInjection.reason.therapeuticAbortion"/> </input></li>
                <li><input type="radio" name="reason_check"
                           value="<fmt:message key="form.addRhInjection.reason.plateletTransfusion"/>"><fmt:message key="form.addRhInjection.reason.plateletTransfusion"/> </input></li>
                <li><input type="radio" name="reason_check" value="<fmt:message key="form.addRhInjection.postpartum"/>"><fmt:message key="form.addRhInjection.postpartum"/></input></li>
                <li><input type="radio" name="reason_check" value="<fmt:message key="form.addRhInjection.reason.other"/>"><fmt:message key="form.addRhInjection.reason.other"/></input>
                    <input type="text" name="reasonOtherText" id="reasonOtherText"/></li>
            </ul>
        </fieldset>
        <%-- input type="button" onclick="process(document.getElementById('injectForm'))"/ --%>
        &nbsp;<input type="submit"
        value="<fmt:message key='form.addRhInjection.saveInjection'/>" />

    </form>
    </div>

    <script type="text/javascript">
        Calendar.setup({
            inputField: "prevDate",
            ifFormat: "%Y-%m-%d",
            showsTime: false,
            button: "date",
            singleClick: true,
            step: 1
        });
        hideExtraName(document.getElementById('providerDrop'));

    </script>


    </body>
</html>


<%!


%>
