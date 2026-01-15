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
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_report,_admin.reporting" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_report&type=_admin.reporting");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
"http://www.w3.org/TR/html4/loose.dtd">
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@page
        import="ca.openosp.openo.demographic.data.*,java.util.*,ca.openosp.openo.prevention.*,ca.openosp.openo.providers.data.*,ca.openosp.openo.util.*,ca.openosp.openo.report.data.*,ca.openosp.openo.prevention.pageUtil.*,java.net.*,ca.openosp.openo.eform.*" %>
<%@ page import="ca.openosp.openo.demographic.data.DemographicNameAgeString" %>
<%@ page import="ca.openosp.openo.report.data.ManageLetters" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<jsp:useBean id="providerBean" class="java.util.Properties"
             scope="session"/>

<%

    String demographic_no = request.getParameter("demographic_no");

    String[] demos = request.getParameterValues("demo");

%>

<html>

    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <title>oscarPrevention</title>
        <!-- i18n -->

        <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>
        <link rel="stylesheet" type="text/css"
              href="<%= request.getContextPath() %>/share/css/OscarStandardLayout.css">
        <link rel="stylesheet" type="text/css" media="all"
              href="<%= request.getContextPath() %>/share/calendar/calendar.css" title="win2k-cold-1">

        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar.js"></script>
        <script type="text/javascript"
                src="<%= request.getContextPath() %>/share/calendar/lang/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.javascript.calendar"/>"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar-setup.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/prototype.js"></script>

        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>

        <SCRIPT type="text/javascript">

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

        </SCRIPT>


        <script type="text/javascript">


            //Function sends AJAX request to action
            function completedProcedure(idval, followUpType, procedure, demographic) {
                var comment = prompt('Are you sure you want to added this to patients record \n\nAdd Comment Below ', '');
                if (comment != null) {
                    var params = "id=" + idval + "&followupType=" + followUpType + "&followupValue=" + procedure + "&demos=" + demographic + "&message=" + comment;
                    var url = "<%=request.getContextPath()%>/oscarMeasurement/AddShortMeasurement.do";

                    new Ajax.Request(url, {
                        method: 'get',
                        parameters: params,
                        asynchronous: true,
                        onComplete: followUp
                    });
                }
                return false;
            }

            function followUp(origRequest) {
                //alert(origRequest.responseText);
                var hash = origRequest.responseText.parseQuery();
                //alert( hash['id'] + " " + hash['followupValue']+" "+hash['Date'] );
                //("id="+id+"&followupValue="+followUpValue+"&Date=
                var lastFollowupTD = $(hash['id'] + 'lastFollowup');
                var nextProcedureTD = $(hash['id'] + 'nextSuggestedProcedure');
                //alert(nextProcedureTD);
                nextProcedureTD.innerHTML = "----";
                lastFollowupTD.innerHTML = hash['followupValue'] + " " + hash['Date'];

                //alert(nextProcedureTD.innerText);

            }
        </script>


        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>

        <style type="text/css" media="print">
            .MainTable {
                display: none;
            }

            .hiddenInPrint {
                display: none;
            }

            .shownInPrint {
                display: block;
            }
        </style>


    </head>

    <body class="BodyStyle" vlink="#0000FF">
    <!--  -->
    <table class="MainTable" id="scrollNumber1">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn" width="100">report</td>
            <td class="MainTableTopRowRightColumn">
                <table class="TopStatusBar">
                    <tr>
                        <td>Choose Letter</td>
                        <td>&nbsp; <a href="ManageLetters.jsp">manage</a></td>
                        <td style="text-align: right">
                            <a
                                    href="javascript:popupStart(300,400,'About.jsp')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.about"/></a> | <a
                                    href="javascript:popupStart(300,400,'License.jsp')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.license"/></a></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableLeftColumn" valign="top">&nbsp;</td>
            <td valign="top" class="MainTableRightColumn"><form
                    action="${pageContext.request.contextPath}/report/GenerateLetters.do" method="POST"
                    id="listDemographic">

                <%
                    ManageLetters mLetter = new ManageLetters();
                    ArrayList list = mLetter.getActiveReportList();

                    if (list.size() > 0) {%>


                <div>Select Letter: <select name="reportLetter">
                    <%
                        for (int i = 0; i < list.size(); i++) {
                            Hashtable h = (Hashtable) list.get(i);
                    %>
                    <option value="<%=h.get("ID")%>"><%=h.get("report_name")%>
                    </option>
                    <%}%>
                </select> <%
                    //MARK IN MEASUREMENTS????
                    String followUpType = request.getParameter("followupType");//"FLUF";
                    String followUpValue = request.getParameter("followupValue"); //"L1";
                    String comment = request.getParameter("message");
                    if (followUpType != null && followUpValue != null) { %>
                    Mark in patients Records: <input type="checkbox" name="addFollowUp"
                                                     value="ON" checked/> <input type="hidden" name="followupType"
                                                                                 value="<%=followUpType%>"/> <input
                            type="hidden"
                            name="followupValue" value="<%=followUpValue%>"/> <%}%>
                </div>

                <input type="submit" value="Generate Letters"/>
                <input type="button" value="Generate Envelopes"
                       onclick="javascript:genEnvelopes('listDemographic')"/>


                <%} else {%>
                <div>No Reports Loaded</div>

                <%}%>


                <%if (demos != null) {%>
                <table>

                    <% DemographicNameAgeString deName = DemographicNameAgeString.getInstance();
                        for (int i = 0; i < demos.length; i++) {
                            Map<String, String> h = deName.getNameAgeSexHashtable(LoggedInInfo.getLoggedInInfoFromSession(request), demos[i]);
                    %>
                    <tr>
                        <td><%=i + 1%>
                        </td>
                        <td><input type="checkbox" name="demos" value="<%=demos[i]%>"
                                   checked/></td>
					<td><%=Encode.forHtmlContent(h.get("lastName"))%>, <%=Encode.forHtmlContent(h.get("firstName"))%></td>
					<td><%=Encode.forHtmlContent(h.get("sex"))%></td>
					<td><%=Encode.forHtmlContent(h.get("age"))%></td>
                    </tr>
                    <%}%>
                </table>
                <%}%>

            </form></td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn">&nbsp;</td>
            <td class="MainTableBottomRowRightColumn" valign="top">&nbsp;</td>
        </tr>
    </table>

    <div>
        <%
            // if ( firstLetter.size() > 0 ) {
            //    String queryStr = getUrlParamList(firstLetter, "demo");
            //
            //&message=<%=java.net.URLEncoder.encode("Letter 1 Reminder Letter sent for :" + request.getAttribute("prevType"), StandardCharsets.UTF_8)
            //&followupType=
            //&followupValue=L1
    %>
    </div>

    <script type="text/javascript">
        // Calendar.setup( { inputField : "asofDate", ifFormat : "%Y-%m-%d", showsTime :false, button : "date", singleClick : true, step : 1 } );
        function genEnvelopes(form) {
            window.location = "<%=request.getContextPath()%>/report/GenerateEnvelopes.do?" + Form.serialize('listDemographic');
        }

    </script>

    </body>
</html>

<%!
    String getUrlParamList(ArrayList list, String paramName) {
        String queryStr = "";
        for (int i = 0; i < list.size(); i++) {
            String demo = (String) list.get(i);
            if (i == 0) {
                queryStr += paramName + "=" + demo;
            } else {
                queryStr += "&" + paramName + "=" + demo;
            }
        }
        return queryStr;
    }
%>
