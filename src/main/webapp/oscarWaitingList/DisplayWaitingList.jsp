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

<%@page import="ca.openosp.openo.utility.SessionConstants" %>
<%@page import="ca.openosp.openo.commn.model.ProviderPreference" %>
<%
    if (session.getValue("user") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");

    String styleClass = "data2";
%>
<%@ page
        import="java.util.*,ca.openosp.openo.util.*, ca.openosp.openo.waitinglist.bean.*" %>
<%@ page import="ca.openosp.openo.waitinglist.bean.WLWaitingListNameBeanHandler" %>
<%@ page import="ca.openosp.openo.waitinglist.bean.WLWaitingListNameBean" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%
    String wlid = (String) request.getAttribute("WLId");
    if (wlid == null) {
        request.setAttribute("WLId", "0");
    }
%>
<link rel="stylesheet" type="text/css"
      href="<%= request.getContextPath() %>/oscarEncounter/encounterStyles.css">
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title>Waiting List</title>

        <!-- calendar stylesheet -->
        <link rel="stylesheet" type="text/css" media="all"
              href="<%= request.getContextPath() %>/share/calendar/calendar.css" title="win2k-cold-1"/>

        <!-- main calendar program -->
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar.js"></script>

        <!-- language for the calendar -->
        <script type="text/javascript"
                src="<%= request.getContextPath() %>/share/calendar/lang/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.javascript.calendar"/>"></script>

        <!-- the following script defines the Calendar.setup helper function, which makes
               adding a calendar a matter of 1 or 2 lines of code. -->
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar-setup.js"></script>

    </head>
    <body class="BodyStyle" vlink="#0000FF"
          onload='window.resizeTo(900,400)'>
    <!--  -->
    <form action="${pageContext.request.contextPath}/oscarWaitingList/SetupDisplayWaitingList.do?update=Y" method="post">

        <input type="hidden" name="demographicNumSelected" value=""/>
        <input type="hidden" name="wlNoteSelected" value=""/>
        <input type="hidden" name="onListSinceSelected" value=""/>


        <input type="hidden" name="waitingListId" value=""/>
        <input type="hidden" name="update" value=""/>


        <table class="MainTable" id="scrollNumber1" name="encounterTable">
            <tr class="MainTableTopRow">
                <td class="MainTableTopRowRightColumn" colspan="2" width="85%">
                    <table class="TopStatusBar">
                        <tr>
                            <td>Current List: <c:if test="${not empty waitingListName}">
                                <c:out value="${waitingListName}"/>
                            </c:if></td>
                            <td align="right"></td>

                            <td align="left">Please Select a Waiting List:</td>
                            <td>
                                <select name="selectedWL" id="selectedWL">
                                <option value=""></option>
                                        <%
                                	String providerNo = (String)session.getAttribute("user");
                                	String groupNo = "";
                                	ProviderPreference providerPreference=(ProviderPreference)session.getAttribute(SessionConstants.LOGGED_IN_PROVIDER_PREFERENCE);
                                    if(providerPreference.getMyGroupNo() != null){
                                    	groupNo = providerPreference.getMyGroupNo();
                                    }
                                    WLWaitingListNameBeanHandler wlNameHd = new WLWaitingListNameBeanHandler(groupNo, providerNo);
                                    List allWaitingListName = wlNameHd.getWaitingListNameList();
                                    for(int i=0; i<allWaitingListName.size(); i++){
                                        WLWaitingListNameBean wLBean = (WLWaitingListNameBean) allWaitingListName.get(i);
                                        String id = wLBean.getId();
                                        String name = wLBean.getWaitingListName();                                       
                                        String selected = id.compareTo((String) request.getAttribute("WLId")==null?"0":(String) request.getAttribute("WLId"))==0?"SELECTED":"";                                        
                                %>
                                <option value="<%=id%>" <%=selected%>><%=name%>
                                </option>
                                        <%}%>
                                </select> <INPUT type="button" onClick="goToPage()" value="Generate Report">
                                        <%
        String userRole = "";
        if(session.getAttribute("userrole") != null){
                userRole = (String)session.getAttribute("userrole");
        }
        if(userRole.indexOf("admin") >= 0){
%> <a href="#" onclick="popupEditWlNamePage();"
      style="color: #000000; text-decoration: none;">Create List</a> <%
        }else{
%> test <%
}
%>

                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="MainTableLeftColumn"></td>
                <td class="tableColumnEven">
                    <table border=0 cellspacing=4 width="100%">
                        <tr>
                            <td>
                                <table>
                                    <tr>
                                        <td>
                                    <tr>
                                        <td align="left" class="Header" width="20"></td>
                                        <td align="left" class="Header" width="100">Patient Name</td>
                                        <td align="center" class="Header" width="370">Note</td>
                                        <td align="left" class="Header" width="50">
                                            <nobr>Date
                                                of
                                            </nobr>
                                            Request<br/>
                                            <nobr>(yyyy-mm-dd)</nobr>
                                        </td>
                                        <td align="center" class="Header" width="300">Provider</td>
                                        <td align="left" class="Header" width="50"></td>
                                    </tr>
                                    <c:forEach var="waitingListBean" items="${waitingList.waitingList}" varStatus="ctr">
                                        <input type="hidden" name="demographicNo" id="demographicNo" indexed="true"/>

                                        <c:set var="styleClass" value="${(ctr.index % 2 == 0) ? 'data5' : 'data2'}"/>

                                    <tr>
                                        <td><a name="anchor_${ctr.index}"></a></td>
                                    </tr>
                                    <tr>
                                        <td class="${styleClass}">
                                            <c:out value="${waitingListBean.position}"/>
                                        </td>
                                        <td class="${styleClass}">
                                            <a href="#" onclick="popupDemographicPage('<%= request.getContextPath() %>/demographic/demographiccontrol.jsp?demographic_no=${waitingListBean.demographicNo}&displaymode=edit&dboperation=search_detail'); return false;">
                                                <c:out value="${waitingListBean.patientName}"/>
                                            </a>
                                            <input type="button" value="Update" name="update_${ctr.index}" style="font-size: 7pt;"
                                                   onClick="updateWaitingList('${waitingListBean.waitingListID}', ${ctr.index});"/>
                                        </td>
                                        <td class="${styleClass}">
                                            <textarea cols="45" name="note" indexed="true" class="data3" onblur="setParameters(this);"></textarea>
                                        </td>
                                        <td class="${styleClass}">
                                            <input type="text" name="onListSince" indexed="true" class="data3" onblur="setParameters(this);" onchange="setParameters(this);"/>
                                            <img src="<%= request.getContextPath() %>/images/cal.gif" id="referral_date_cal_${ctr.index}">
                                            <script type="text/javascript">
                                                Calendar.setup({
                                                    inputField: "waitingListBean[${ctr.index}].onListSince",
                                                    ifFormat: "%Y-%m-%d",
                                                    showsTime: false,
                                                    button: "referral_date_cal_${ctr.index}",
                                                    singleClick: true,
                                                    step: 1
                                                });
                                            </script>
                                        </td>
                                        <td class="${styleClass}">
                                            <select name="selectedProvider" class="data3">
                                                <c:forEach var="allProvider" items="${allProviders}">
                                                    <option value="${allProvider.providerID}">
                                                            ${allProvider.providerName}
                                                    </option>
                                                </c:forEach>
                                            </select>
                                            <a href="#" onClick="popupPage(${ctr.index}, '${waitingListBean.patientName}', '${waitingListBean.demographicNo}', '${today}', 400, 780, '<%= request.getContextPath() %>/schedule/scheduleflipview.jsp?originalpage=<%= request.getContextPath() %>/oscarWaitingList/DisplayWaitingList.jsp'); return false;">
                                                make_appt
                                            </a>
                                        </td>
                                        <td class="${styleClass}">
                                            <a href="#" onClick="removePatient('${waitingListBean.demographicNo}', '${WLId}');">remove</a>
                                        </td>
                                    </tr>
                                    </c:forEach>
                            </td>
                        </tr>
                    </table>
                    <table>
                        <tr>

                            <td><input type="button" name="closeWindow"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/>"
                                       style="font-size: 8pt;" onClick="window.close()"></td>
                        </tr>
                    </table>
                </td>
            </tr>
        </table>
        </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn"></td>
            <td class="MainTableBottomRowRightColumn"></td>
        </tr>
        </table>
    </form>
    <script type="text/javascript">
        function goToPage() {
            document.forms[0].waitingListId.value =
                document.forms[0].selectedWL.options[document.forms[0].selectedWL.selectedIndex].value;
            window.location = "<%=request.getContextPath()%>/oscarWaitingList/SetupDisplayWaitingList.do?waitingListId=" +
                document.forms[0].selectedWL.options[document.forms[0].selectedWL.selectedIndex].value;
        }

        function popupEditWlNamePage() {
            document.forms[0].waitingListId.value =
                document.forms[0].selectedWL.options[document.forms[0].selectedWL.selectedIndex].value;

            var redirectPage = "<%=request.getContextPath()%>/oscarWaitingList/WLEditWaitingListNameAction.do?waitingListId=" +
                document.forms[0].selectedWL.options[document.forms[0].selectedWL.selectedIndex].value + "&edit=Y";
            popupDemographicPage(redirectPage);
        }

        function popupDemographicPage(varpage) { //open a new popup window
            var page = "" + varpage;
            var windowprops = "height=660,width=1000,location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=50,screenY=50,top=0,left=0";
            var popup = window.open(page, "studydemo", windowprops);
            if (popup != null) {
                if (popup.opener == null) {
                    popup.opener = self;
                }
            }
        }

        function setParameters(thisObj) {

            var thisObjName = thisObj.name;
            var indexNum1 = thisObjName.indexOf("[");
            var indexNum2 = thisObjName.indexOf("]");

            var wlcount = 0;
            if (indexNum1 > 0) {
                wlCount = thisObjName.substring(indexNum1 + 1, indexNum2);
            }
            //alert("setParameters(): wlCount = " + wlCount);
            document.forms[0].demographicNumSelected.value = "waitingListBean[" + wlCount + "].demographicNo";
            document.forms[0].wlNoteSelected.value = "waitingListBean[" + wlCount + "].note";
            document.forms[0].onListSinceSelected.value = "waitingListBean[" + wlCount + "].onListSince";
            //alert("setParameters(): wlNoteSelected = " + document.forms[0].wlNoteSelected.value);
            //alert("setParameters(): onListSinceSelected = " + document.forms[0].onListSinceSelected.value);
        }

        function popupPage(ctr, patientName, demographicNo, startDate, vheight, vwidth, varpage) {
            var nbPatients = parseInt('<c:out value="${nbPatients}" default="0"/>');
            if (nbPatients > 1) {
                var selected = document.forms[0].selectedProvider[ctr].options[document.forms[0].selectedProvider[ctr].selectedIndex].value;
            } else {
                var selected = document.forms[0].selectedProvider.options[document.forms[0].selectedProvider.selectedIndex].value;
            }
            var page = varpage + '&provider_no=' + selected + '&startDate=' + startDate + '&demographic_no=' + demographicNo + '&demographic_name=' + patientName;
            var windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=50,screenY=50,top=0,left=0";
            var popup = window.open(page, "<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.apptProvider"/>", windowprops);
            if (popup != null) {
                if (popup.opener == null) {
                    popup.opener = self;
                }
                popup.focus();
            }
        }

        function updateWaitingList(waitingListId, ctr) {
            document.forms[0].waitingListId.value = waitingListId;
            document.forms[0].update.value = "Y";
            document.forms[0].action = "<%=request.getContextPath()%>/oscarWaitingList/SetupDisplayWaitingList.do#anchor_" + ctr;
            document.forms[0].submit();
        }

        function removePatient(demographicNo, waitingList) {
            var agree = confirm("Are you sure you want to remove this patient from the waiting list?");
            if (agree) {
                var windowprops = "height=50,width=50,location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=50,screenY=50,top=0,left=0";
                var page = "RemoveFromWaitingList.jsp?listId=" + waitingList + "&demographicNo=" + demographicNo + "&remove=Y";
                var popup = window.open(page, "removeWaitingList", windowprops);
            } else {
                return false;
            }
        }
    </script>
    </body>
</html>
