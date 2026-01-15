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

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ page import="ca.openosp.openo.report.oscarMeasurements.pageUtil.*" %>
<%@ page import="java.util.*, java.sql.*, java.text.*, java.net.*" %>
<%
    GregorianCalendar now = new GregorianCalendar();
    int curYear = now.get(Calendar.YEAR);
    int curMonth = (now.get(Calendar.MONTH) + 1);
    int curDay = now.get(Calendar.DAY_OF_MONTH);
%>

<html>

    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.msgPercentageOfPatientInAbnormalRange"/></title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>
    </head>
    <script language="javascript">
        function isArray(elementInQuestion) {
            if (elementInQuestion.length) {
                return true;
            } else {
                return false;
            }
        }

        function checkAll(field) {
            var i;

            if (isArray(field)) {
                for (i = 0; i < field.length; i++) {
                    field[i].checked = true;
                }
            } else {
                field.checked = true;
            }
        }

        function unCheckAll(field) {
            var i;

            if (isArray(field)) {
                for (i = 0; i < field.length; i++) {
                    field[i].checked = false;
                }
            } else {
                field.checked = false;
            }
        }
    </script>
    <link rel="stylesheet" type="text/css"
          href="<%= request.getContextPath() %>/oscarEncounter/encounterStyles.css">
    <body topmargin="0" leftmargin="0" vlink="#0000FF"
          onload="window.focus();">
    <% 
    java.util.List<String> actionErrors = (java.util.List<String>) request.getAttribute("actionErrors");
    if (actionErrors != null && !actionErrors.isEmpty()) {
%>
    <div class="action-errors">
        <ul>
            <% for (String error : actionErrors) { %>
                <li><%= error %></li>
            <% } %>
        </ul>
    </div>
<% } %>
    <form action="${pageContext.request.contextPath}/oscarReport/oscarMeasurements/InitializePatientsInAbnormalRangeCDMReport.do" method="post">
        <table class="MainTable" id="scrollNumber1" name="encounterTable">
            <tr class="MainTableTopRow">
                <td class="MainTableTopRowLeftColumn"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.msgReport"/></td>
                <td class="MainTableTopRowRightColumn">
                    <table class="TopStatusBar">
                        <tr>
                            <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.msgTitle"/>: <c:out value="${CDMGroup}"/></td>
                            <td></td>
                            <td style="text-align: right"><a
                                    href="javascript:popupStart(300,400,'About.jsp')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.about"/></a> | <a
                                    href="javascript:popupStart(300,400,'License.jsp')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.license"/></a></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="MainTableLeftColumn">&nbsp;</td>
                <td class="MainTableRightColumn">
                    <table border=0 cellspacing=4 width=900>
                        <tr>
                            <td>
                                <table>
                                    <tr>
                                        <td class="nameBox" colspan='4'><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.msgNumberOfPatientsSeen"/></td>
                                    </tr>
                                    <tr>
                                        <th align="left" class="subTitles" width="2"></th>
                                        <th align="left" class="subTitles" width="120"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.msgStartDate"/></th>
                                        <th align="left" class="subTitles" width="120"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.msgEndDate"/></th>
                                        <th align="left" class="subTitles" width="650"></th>
                                    </tr>
                                    <tr>
                                        <td width="2" class="fieldBox" bgcolor="#ddddff"><input
                                                type="checkbox" name="patientSeenCheckbox" checked="checked"
                                                value="ctr"/></td>
                                        <td width="120" class="fieldBox" bgcolor="#ddddff"><input
                                                type="text" name='startDateA'
                                                value='<c:out value="${lastYear}"/>' size="10"> <img
                                                src="<%= request.getContextPath() %>/img/calendar.gif" border="0"
                                                onClick="window.open('<%= request.getContextPath() %>/oscarReport/oscarReportCalendarPopup.jsp?type=startDateA&amp;year=<%=curYear%>&amp;month=<%=curMonth%>&amp;form=<%="RptInitializePatientsInAbnormalRangeCDMReportForm"%>','','width=300,height=300')"/>
                                        </td>
                                        <td width="120" class="fieldBox" bgcolor="#ddddff"><input
                                                type="text" name='endDateA' value='<c:out value="${today}"/>'
                                                size="10"> <img src="<%= request.getContextPath() %>/img/calendar.gif" border="0"
                                                                onClick="window.open('<%= request.getContextPath() %>/oscarReport/oscarReportCalendarPopup.jsp?type=endDateA&amp;year=<%=curYear%>&amp;month=<%=curMonth%>&amp;form=<%="RptInitializePatientsInAbnormalRangeCDMReportForm"%>','','width=300,height=300')"/>
                                        </td>
                                        <td width="450" class="fieldBox" bgcolor="#ddddff"></td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td>
                                <table>
                                    <tr>
                                        <c:if test="${not empty messages}">
                                            <c:forEach var="msg" items="${messages}">
                                                <c:out value="${msg}"/>
                                                <br>
                                            </c:forEach>
                                        </c:if>
                                    </tr>
                                    <tr>
                                        <td>
                                    <tr>
                                        <td class="nameBox" colspan='8'><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.msgPercentageOfPatientInAbnormalRange"/>
                                        </td>
                                    </tr>
                                    <tr>
                                        <th align="left" class="subTitles" width="2"></th>
                                        <th align="left" class="subTitles" width="4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.msgTest"/></th>
                                        <th align="left" class="subTitles" width="200"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.msgTestDescription"/></th>
                                        <th align="left" class="subTitles" width="200"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.msgMeasuringInstruction"/></th>
                                        <th align="left" class="subTitles" width="50"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.msgUpperBound"/></th>
                                        <th align="left" class="subTitles" width="50"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.msgLowerBound"/></th>
                                        <th align="left" class="subTitles" width="120"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.msgStartDate"/></th>
                                        <th align="left" class="subTitles" width="120"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.msgEndDate"/></th>
                                    </tr>
                                    <c:forEach var="measurementType" items="${measurementTypes.measurementTypeVector}" varStatus="ctr">
                                    <tr>
                                        <td width="2" class="fieldBox" bgcolor="#ddddff"><input
                                                type="checkbox" name="abnormalCheckbox" value="${ctr.index}"/></td>
                                        <td width="4" class="fieldBox" bgcolor="#ddddff" width="5"><c:out value="${measurementType.typeDisplayName}"/></td>
                                        <td width="200" class="fieldBox" bgcolor="#ddddff"><c:out value="${measurementType.typeDesc}"/></td>
                                        <td width="200" class="fieldBox" bgcolor="#ddddff"></td>
                                        <td width="50" class="fieldBox" bgcolor="#ddddff"><input type="text" name="upperBound" size="6"/></td>
                                        <td width="50" class="fieldBox" bgcolor="#ddddff"><input type="text" name="lowerBound" size="6"/></td>
                                        <td width="120" class="fieldBox" bgcolor="#ddddff"><input type="text" name="startDateC" value='<c:out value="${lastYear}"/>' size="10"> 
                                            <img src="<%= request.getContextPath() %>/img/calendar.gif" border="0" onClick="window.open('<%= request.getContextPath() %>/oscarReport/oscarReportCalendarPopup.jsp?type=startDateC[${ctr.index}]&amp;year=<%=curYear%>&amp;month=<%=curMonth%>&amp;form=RptInitializePatientsInAbnormalRangeCDMReportForm','','width=300,height=300')"/>
                                        </td>
                                        <td width="120" class="fieldBox" bgcolor="#ddddff"><input type="text" name="endDateC" value='<c:out value="${today}"/>' size="10"> 
                                            <img src="<%= request.getContextPath() %>/img/calendar.gif" border="0" onClick="window.open('<%= request.getContextPath() %>/oscarReport/oscarReportCalendarPopup.jsp?type=endDateC[${ctr.index}]&amp;year=<%=curYear%>&amp;month=<%=curMonth%>&amp;form=RptInitializePatientsInAbnormalRangeCDMReportForm','','width=300,height=300')"/>
                                        </td>
                                        <input type="hidden"
                                               name='value(measurementTypeC${ctr.index})'
                                               value="<c:out value="${measurementType.type}"/>"/>
                                    </tr>
                                    <tr>
                                        <td width="2" class="fieldBox" bgcolor="#ddddff"></td>
                                        <td width="4" class="fieldBox" bgcolor="#ddddff" width="5"></td>
                                        <td width="200" class="fieldBox" bgcolor="#ddddff"></td>
                                        <td width="200" class="fieldBox" bgcolor="#ddddff">
                                            <table>
                                                <%int j = 0;%>
                                                <c:forEach var="mInstrc" items="${mInstrcs[ctr.index].measuringInstrcVector}" varStatus="index">
                                                    <tr>
                                                        <td><input type="checkbox"
                                                                   name='value(mInstrcsCheckboxC${ctr.index}${index.index})'
                                                                   checked="checked"
                                                                   value='<c:out value="${mInstrc.measuringInstrc}" />'/><c:out
                                                                value="${mInstrc.measuringInstrc}" /></td>
                                                    </tr>
                                                    <%j++;%>
                                                </c:forEach>
                                            </table>
                                        </td>
                                        <input type="hidden"
                                               name='value(mNbInstrcsC${ctr.index})' value='<%=j%>'/>
                                        <td width="50" class="fieldBox" bgcolor="#ddddff"></td>
                                        <td width="50" class="fieldBox" bgcolor="#ddddff"></td>
                                        <td width="120" class="fieldBox" bgcolor="#ddddff"></td>
                                        <td width="120" class="fieldBox" bgcolor="#ddddff"></td>
                                    </tr>
                                    </c:forEach>
                                    <tr>
                                    </tr>

                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
        </table>
        </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn"></td>
            <td class="MainTableBottomRowRightColumn">
                <table>
                    <tr>
                        <td align="left"><input type="submit" name="submitBtn"
                                                value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.btnGenerateReport"/>"/>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        </table>

    </form>

    </body>
</html>
