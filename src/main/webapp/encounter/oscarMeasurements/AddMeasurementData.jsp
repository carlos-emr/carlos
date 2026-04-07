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

<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.demographic.data.*,java.util.*,io.github.carlos_emr.carlos.prevention.*,io.github.carlos_emr.carlos.providers.data.*,io.github.carlos_emr.carlos.util.*,io.github.carlos_emr.carlos.encounter.oscarMeasurements.*,io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.*,io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.*" %>
<%@page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@page import="org.springframework.web.context.WebApplicationContext" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.*,io.github.carlos_emr.carlos.commn.model.FlowSheetCustomization,io.github.carlos_emr.carlos.commn.model.Validations" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementFlowSheet" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementTemplateFlowSheetConfig" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementTypeBeanHandler" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementsDataBeanHandler" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementTypesBean" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctMeasurementsForm" %>
<%@ page import="io.github.carlos_emr.carlos.providers.data.ProviderData" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilDateUtilities" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ValidationsDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.FlowSheetCustomizationDao" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%
    if (session.getAttribute("user") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");
    String demographic_no = request.getParameter("demographic_no");
    String id = request.getParameter("id");
    String measurement = request.getParameter("measurement");
    String[] measurements = request.getParameterValues("measurement");
    String temp = request.getParameter("template");

    FlowSheetCustomizationDao flowSheetCustomizationDao = (FlowSheetCustomizationDao) SpringUtils.getBean(FlowSheetCustomizationDao.class);
    ValidationsDao validationsDao = (ValidationsDao) SpringUtils.getBean(ValidationsDao.class);
    MeasurementTemplateFlowSheetConfig templateConfig = MeasurementTemplateFlowSheetConfig.getInstance();


    MeasurementFlowSheet mFlowsheet = templateConfig.getFlowSheet(temp, LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), Integer.parseInt(demographic_no));
    EctMeasurementTypeBeanHandler mType = new EctMeasurementTypeBeanHandler();


    Hashtable existingPrevention = null;


    String providerName = "";
    String provider = (String) session.getAttribute("user");
    String prevDate = UtilDateUtilities.getToday("yyyy-MM-dd H:mm");
    String completed = "0";
    String nextDate = "";
    boolean never = false;
    Hashtable extraData = new Hashtable();

    List providers = ProviderData.getProviderList();
%>


<html>

    <head>
        <title>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.Index.measurements"/>
        </title><!--I18n-->
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/share/css/OscarStandardLayout.css">
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/calendar/calendar.css"
              title="win2k-cold-1"/>

        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar.js"></script>
        <script type="text/javascript"
                src="<%= request.getContextPath() %>/share/calendar/lang/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.javascript.calendar"/>"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar-setup.js"></script>
        <!-- Prototype.js removed (Phase 4d migration) -->

        <style type="text/css">
            div.ImmSet {
                background-color: #ffffff;
            }

            div.ImmSet h2 {
            }

            div.ImmSet ul {
            }

            div.ImmSet li {
            }

            div.ImmSet li a {
                text-decoration: none;
                color: blue;
            }

            div.ImmSet li a:hover {
                text-decoration: none;
                color: red;
            }

            div.ImmSet li a:visited {
                text-decoration: none;
                color: blue;
            }

            div.prevention fieldset {
                width: 35em;
                font-weight: bold;
            }

            div.prevention legend {
                font-weight: bold;
            }

        </style>


        <script type="text/javascript" src="<%=request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js"></script>
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-compat.js"></script>
        <script>
            jQuery.noConflict();
        </script>

        <script>
            function doSubmit() {

                jQuery.post(jQuery("#measurementForm").attr('action') + '?ajax=true&skipCreateNote=true', jQuery('#measurementForm').serialize(), function (data) {

                    if (data && data.errors && data.errors.length > 0) {
                        jQuery("#errorList").empty();
                        for (var x = 0; x < data.errors.length; x++) {
                            jQuery("#errorList").append(data.errors[x]);
                        }
                        jQuery("#errorDiv").show();
                        return false;

                    }

                    if (opener != null && opener.opener != null) {
                        opener.opener.postMessage(data, "*");
                    }
                    if (opener != null) {
                        opener.location.reload();
                    }
                    window.close();
                }, "json");

                return false;

            }
        </script>

        <SCRIPT LANGUAGE="JavaScript">

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

            function masterDateFill(v) {
                var x =<%=measurements.length%>;


                for (i = 0; i <= x; i++) {
                    document.getElementById('prevDate' + i).value = v;
                }

            }
        </SCRIPT>

        <style type="text/css">
            Body {
                background-color: #fff;
            }

            table.outline {
                margin-top: 50px;
                border-bottom: 1pt solid #888888;
                border-left: 1pt solid #888888;
                border-top: 1pt solid #888888;
                border-right: 1pt solid #888888;
            }

            table.grid {
                border-bottom: 1pt solid #888888;
                border-left: 1pt solid #888888;
                border-top: 1pt solid #888888;
                border-right: 1pt solid #888888;
            }

            td.gridTitles {
                border-bottom: 2pt solid #888888;
                font-weight: bold;
                text-align: center;
            }

            td.gridTitlesWOBottom {
                font-weight: bold;
                text-align: center;
            }

            td.middleGrid {
                border-left: 1pt solid #888888;
                border-right: 1pt solid #888888;
                text-align: center;
            }


            label {
                float: left;
                width: 120px;
                font-weight: bold;
            }

            label.checkbox {
                float: left;
                width: 116px;
                font-weight: bold;
            }

            label.fields {
                float: left;
                width: 125px;
                font-weight: bold;
            }

            span.labelLook {
                font-weight: bold;

            }

            input, textarea, select {

                /* margin-bottom: 5 px; */
            }

            textarea {
                width: 450px;
                height: 100px;
            }


            .boxes {
                width: 1em;
            }

            #submitbutton {
                margin-left: 120px;
                margin-top: 5px;
                width: 90px;
            }

            br {
                clear: left;
            }
        </style>

        <script type="text/javascript">
            function hideExtraName(ele) {
                if (ele == null) {
                    return;
                }
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
        </script>
    </head>

    <body class="BodyStyle" vlink="#0000FF" onload="var el=document.getElementById('inputValue-0');if(el)el.focus();">

    <table class="MainTable" id="scrollNumber1">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn" width="100">
                measurement
            </td>
            <td class="MainTableTopRowRightColumn">
                <table class="TopStatusBar">
                    <tr>
                        <td>
                            <oscar:nameage demographicNo="<%=demographic_no%>"/>
                        </td>
                        <td>&nbsp;

                        </td>
                        <td style="text-align:right">
                            <a
                                href="javascript:popupStart(300,400,'About.jsp')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.about"/></a>
                            | <a href="javascript:popupStart(300,400,'License.jsp')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.license"/></a>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableLeftColumn" valign="top">
                &nbsp;

            </td>
            <td valign="top" class="MainTableRightColumn">
                <% 
    java.util.List<String> actionErrors = (java.util.List<String>) request.getAttribute("actionErrors");
    if (actionErrors != null && !actionErrors.isEmpty()) {
%>
    <div class="action-errors">
        <ul>
            <% for (String error : actionErrors) { %>
                <li><%=Encode.forHtml(error)%></li>
            <% } %>
        </ul>
    </div>
<% } %>
                <% String val = "";
                    String saveAction = "encounter/Measurements2.do";
                    String comment = "";
                    Hashtable h = null;
                    if (id != null) {
                        saveAction = "encounter/oscarMeasurements/DeleteData2.do";
                        h = EctMeasurementsDataBeanHandler.getMeasurementDataById(id);
                        prevDate = (String) h.get("dateObserved");
                        val = (String) h.get("value");
                        comment = (String) h.get("comments");
                    }
                %>

                <!-- Place Master Calendar Input Here -->
                <%
                    int iDate;
                    //only display if more than one measurement
                    if (measurements.length > 1) {
                        iDate = measurements.length;    //create a master date value
                %>
                <fieldset>
                    <legend><b>Master Date/Time</b></legend>
                    <div style="float:left;margin-left:30px;">
                        <label for="prevDate<%=iDate%>" class="fields">Obs Date/Time: </label>

                        <input type="text" name="date-<%=iDate%>" id="prevDate<%=iDate%>" value="<%=Encode.forHtmlAttribute(prevDate)%>"
                               size="17" onchange="javascript:masterDateFill(this.value);">
                        <% if (id == null) { %>
                        <a id="date<%=iDate%>"><img title="Calendar" src="<%= request.getContextPath() %>/images/cal.gif" alt="Calendar"
                                                    border="0"/></a>
                        <%}%>
                        <br/><font size="1">*Use this field to change the observation date/time for all items
                        below.</font>
                    </div>
                </fieldset>
                <br/>
                <%
                        iDate = iDate + 1; //after names are assigned to input above increasing iDate for the Cal script
                    } else {
                        iDate = measurements.length;
                    }
                %>
                <!-- END of Master Calendar Input -->

                <form action="${pageContext.request.contextPath}/<%=saveAction%>" id="measurementForm" method="post">
                    <input type="hidden" name="numType" value="<%=measurements.length%>"/>
                    <input type="hidden" name="groupName" value=""/>
                    <input type="hidden" name="css" value=""/>
                    <input type="hidden" name="demographicNo" value="<%= Encode.forHtmlAttribute(demographic_no) %>"/>
                    <input type="hidden" name="inputFrom" value="AddMeasurementData"/>
                    <input type="hidden" name="template" value="<%= Encode.forHtmlAttribute(temp) %>"/>

                    <%
                        int ctr = 0;
                        EctMeasurementsForm ectMeasurementsForm = (EctMeasurementsForm) request.getAttribute("EctMeasurementsForm");

                        for (int i = 0; i < measurements.length; i++) {
                            measurement = measurements[i];
                            Map<String, String> h2 = mFlowsheet.getMeasurementFlowSheetInfo(measurement);

                            EctMeasurementTypesBean mtypeBean = mType.getMeasurementType(measurement);
                            Validations validations = null;

                            if (mtypeBean != null && mtypeBean.getValidation() != null && !mtypeBean.getValidation().isEmpty()) {
                                validations = validationsDao.find(Integer.parseInt(mtypeBean.getValidation()));
                            }

                            if (ectMeasurementsForm != null && !ectMeasurementsForm.isEmpty()) {

                                h = new Hashtable(ectMeasurementsForm.values);

                                prevDate = (String) h.get("date-" + ctr);
                                val = (String) h.get("inputValue-" + ctr);
                                comment = (String) h.get("comments-" + ctr);
                            }
                            if (mtypeBean != null) {
                    %>


                    <input type="hidden" name="measurement" value="<%= Encode.forHtmlAttribute(measurement) %>"/>

                    <input type="hidden" name="<%= "inputType-" + ctr %>" value="<%=Encode.forHtmlAttribute(mtypeBean.getType())%>"/>
                    <input type="hidden" name="<%= "inputTypeDisplayName-" + ctr %>"
                           value="<%=Encode.forHtmlAttribute(mtypeBean.getTypeDisplayName())%>"/>
                    <input type="hidden" name="<%= "validation-" + ctr %>"
                           value="<%=Encode.forHtmlAttribute(mtypeBean.getValidation())%>"/>

                    <% if (id != null) { %>
                    <input type="hidden" name="id" value="<%= Encode.forHtmlAttribute(id) %>"/>
                    <input type="hidden" name="deleteCheckbox" id="deleteCheck" value="<%= Encode.forHtmlAttribute(id) %>"/>
                    <% } %>

                    <div class="prevention">
                        <fieldset>
                            <legend>Measurement : <%=Encode.forHtml(mtypeBean.getTypeDisplayName())%>
                            </legend>
                            <div style="float:left;display:none;">
                                <input type="radio" name="<%= "inputMInstrc-" + ctr %>"
                                       value="<%=Encode.forHtmlAttribute(mtypeBean.getMeasuringInstrc())%>" checked/>
                            </div>
                            <div style="float:left;margin-left:30px;">
                                <label for="prevDate<%=ctr%>" class="fields">Obs Date/Time:</label>

                                <input type="text" name="<%= "date-" + ctr %>" id="prevDate<%=ctr%>"
                                       value="<%=Encode.forHtmlAttribute(prevDate)%>" size="17">

                                <% if (id == null) { %>
                                <a id="date<%=ctr%>"><img title="Calendar" src="<%= request.getContextPath() %>/images/cal.gif" alt="Calendar"
                                                          border="0"/></a>
                                <%}%>
                                <br/>

                                <label for="<%="inputValue-"+ctr%>"
                                       class="fields"><%=Encode.forHtmlContent(h2.get("value_name"))%>:</label>
                                <% if (validations != null && validations.getRegularExp() != null && (validations.getRegularExp().contains("|") || validations.getRegularExp().equals("Yes"))) { %>
                                <select id="<%="inputValue-"+ctr%>"
                                        name="<%= "inputValue-" + ctr %>">
                                    <option value=""></option>
                                    <% String[] opts = validations.getName().contains("/") ? validations.getName().split("/") : validations.getRegularExp().split("\\|");
                                        for (String opt : opts) {%>
                                    <option value="<%=Encode.forHtmlAttribute(opt)%>"  <%=sel(opt, val)%>><%=Encode.forHtml(opt)%>
                                    </option>
                                    <% }%>
                                </select>
                                <%} else if (validations != null && validations.getName().startsWith("Integer")) { %>
                                <select id="<%= "inputValue-" + ctr %>"
                                        name="<%= "inputValue-" + ctr %>">
                                    <option value=""></option>
                                    <%for (int v = validations.getMinValue().intValue(); v <= validations.getMaxValue().intValue(); v++) { %>
                                    <option value="<%=v%>" <%=sel("" + v, val)%>><%=v%>
                                    </option>
                                    <%} %>
                                </select>
                                <%} else {%>
                                <input type="text" id="<%= "inputValue-" + ctr %>"
                                       name="<%= "inputValue-" + ctr %>" size="5" value="<%=Encode.forHtmlAttribute(val)%>"/> <br/>
                                <%}%>
                            </div>
                            <br/>

                            <div id="errorDiv" style="display:none">
                                <ul id="errorList" style="color:red">

                                </ul>
                            </div>
                            <fieldset>
                                <legend>Comments</legend>
                                <textarea name="<%= "comments-" + ctr %>"><%=Encode.forHtmlContent(comment)%></textarea>
                            </fieldset>
                        </fieldset>


                    </div>
                    <% }
                        ctr++;
                    } // end for loop
                    %>
                    <script type="text/javascript">
                        hideExtraName(document.getElementById('providerDrop'));
                    </script>

                    <br/>

                    <% if (id != null) { %>
                    <input type="submit" name="delete" value="Delete" id="deleteButton"/>
                    <% } else { %>
                    <input type="button" value="Save" onClick="doSubmit();">
                    <%}%>
                </form>
            </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn">
                &nbsp;
            </td>
            <td class="MainTableBottomRowRightColumn" valign="top">
                &nbsp;
            </td>
        </tr>
    </table>
    <script type="text/javascript">
        <% if ( id != null ) { %>
        (function() { var f = document.getElementById('measurementForm'); if(f) { Array.from(f.elements).forEach(function(el) { el.disabled = true; }); } })();
        document.getElementById('deleteButton').disabled = false;
        document.getElementById('deleteCheck').disabled = false;

        <% } %>
        <% for (int i =0; i < iDate; i++){ %>
        Calendar.setup({
            inputField: "prevDate<%=i%>",
            ifFormat: "%Y-%m-%d %H:%M",
            showsTime: true,
            button: "date<%=i%>",
            singleClick: true,
            step: 1
        });
        <%}%>
        //Calendar.setup( { inputField : "nextDate", ifFormat : "%Y-%m-%d", showsTime :false, button : "nextDateCal", singleClick : true, step : 1 } );


        var wt_input = '';
        var ht_input = '';
        var bmi_input = '';

        var wt_instrc = '';
        var ht_instrc = '';

        var is_units_metric = true;

        var form = document.forms[0];
        var inputTypes = form.querySelectorAll('[name^="inputType-"]');

        for (var i = 0; i < inputTypes.length; i++) {

            if (inputTypes[i].value == 'WT') {
                wt_input = "input[name='inputValue-" + i + "']";
                wt_instrc = jQuery("input[name='inputMInstrc-" + i + "']").val();

                if (wt_instrc.toLowerCase().indexOf("kg") == 0) {
                    is_units_metric = false;
                }
            }

            if (inputTypes[i].value == 'HT') {
                ht_input = "input[name='inputValue-" + i + "']";
                ht_instrc = jQuery("input[name='inputMInstrc-" + i + "']").val();

                if (ht_instrc.toLowerCase().indexOf("cm") == 0) {
                    is_units_metric = false;
                }

            }

            if (inputTypes[i].value == 'BMI') {
                bmi_input = "input[name='inputValue-" + i + "']";
            }
        }//end loop


        jQuery(document).ready(function () {

            if (wt_input != '' && ht_input != '' && bmi_input != '' && is_units_metric) {

//add auto-calc message
                custom_html = `<div style="width:100%;padding:10px 0 10px 20px; font-size:16px;">
<img src="<%= request.getContextPath() %>/images/Information16x16.gif"> <b>BMI</b> will auto calculate after you enter the weight and height.
</div>`;

                jQuery(custom_html).insertBefore(jQuery('#measurementForm'));

                jQuery(wt_input).on('keyup change', function () {
                    calcBMI();
                });

                jQuery(ht_input).on('keyup change', function () {
                    calcBMI();
                });

            }


            function calcBMI(w, h) {
                w = jQuery(wt_input).val();
                h = jQuery(ht_input).val();
                b = '';

                if (jQuery.isNumeric(w) && jQuery.isNumeric(h) && h !== "" && w !== "") {
                    if (h > 0) {
                        b = (w / Math.pow(h / 100, 2)).toFixed(1);
                        jQuery(bmi_input).val(b);
                        console.log("bmi: " + b);
                    }
                }
            }

        });
    </script>
    </body>
</html>
<%!

    String completed(boolean b) {
        String ret = "";
        if (b) {
            ret = "checked";
        }
        return ret;
    }

    String refused(boolean b) {
        String ret = "";
        if (!b) {
            ret = "checked";
        }
        return ret;
    }

    String str(String first, String second) {
        String ret = "";
        if (first != null) {
            ret = first;
        } else if (second != null) {
            ret = second;
        }
        return ret;
    }

    String checked(String first, String second) {
        String ret = "";
        if (first != null && second != null) {
            if (first.equals(second)) {
                ret = "checked";
            }
        }
        return ret;
    }

    String sel(String first, String second) {
        String ret = "";
        if (first != null && second != null) {
            if (first.equals(second)) {
                ret = "selected";
            }
        }
        return ret;
    }
%>
