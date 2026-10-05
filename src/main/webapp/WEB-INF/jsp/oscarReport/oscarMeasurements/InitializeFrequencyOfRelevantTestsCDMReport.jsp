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
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<%@ page import="io.github.carlos_emr.carlos.report.oscarMeasurements.pageUtil.*" %>
<%@ page import="java.util.*, java.sql.*, java.text.*, java.net.*" %>
<%

    GregorianCalendar now = new GregorianCalendar();
    int curYear = now.get(Calendar.YEAR);
    int curMonth = (now.get(Calendar.MONTH) + 1);
    int curDay = now.get(Calendar.DAY_OF_MONTH);
%>

<%@ include file="cdmReportAccess.jspf" %>
<html>

    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="oscarReport.CDMReport.msgFrequencyOfRelevantTestsBeingPerformed"/>
        </title>
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
          href="<%= request.getContextPath() %>/encounter/encounterStyles.css">
    <body topmargin="0" leftmargin="0" vlink="#0000FF"
          onload="window.focus();">
    <%@ include file="cdmValidationErrors.jspf" %>
    <form action="${pageContext.request.contextPath}/oscarReport/oscarMeasurements/InitializeFrequencyOfRelevantTestsCDMReport" method="post">
        <table class="MainTable" id="scrollNumber1" name="encounterTable">
            <tr class="MainTableTopRow">
                <td class="MainTableTopRowLeftColumn"><fmt:message key="oscarReport.CDMReport.msgReport"/></td>
                <td class="MainTableTopRowRightColumn">
                    <table class="TopStatusBar">
                        <tr>
                            <td><fmt:message key="oscarReport.CDMReport.msgTitle"/>: ${carlos:forHtml(CDMGroup)}</td>
                            <td></td>
                            <td style="text-align: right"><a
                                    href="javascript:popupStart(300,400,'<%=request.getContextPath()%>/encounter/ViewAbout')"><fmt:message key="global.about"/></a> | <a
                                    href="javascript:popupStart(300,400,'<%=request.getContextPath()%>/encounter/ViewLicense')"><fmt:message key="global.license"/></a></td>
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
                                        <td class="nameBox" colspan='4'><fmt:message key="oscarReport.CDMReport.msgNumberOfPatientsSeen"/></td>
                                    </tr>
                                    <tr>
                                        <th align="left" class="subTitles" width="2"></th>
                                        <th align="left" class="subTitles" width="120"><fmt:message key="oscarReport.CDMReport.msgStartDate"/></th>
                                        <th align="left" class="subTitles" width="120"><fmt:message key="oscarReport.CDMReport.msgEndDate"/></th>
                                        <th align="left" class="subTitles" width="650"></th>
                                    </tr>
                                    <tr>
                                        <td width="2" class="fieldBox" bgcolor="#ddddff"><input
                                                type="checkbox" name="patientSeenCheckbox" value="ctr"
                                                <c:if test="${cdmForm.selected('patientSeenCheckbox', 'ctr', true)}">checked="checked"</c:if>/></td>
                                        <td width="120" class="fieldBox" bgcolor="#ddddff"><input
                                                type="text" name='startDateA' aria-label="<fmt:message key="oscarReport.CDMReport.msgStartDate"/>"
                                                value="${carlos:forHtmlAttribute(cdmForm.value('startDateA', 0, lastYear))}" size="10"> <button type="button" aria-label="<fmt:message key="oscarReport.CDMReport.msgStartDate"/>"
                                                onclick="window.open('<%= request.getContextPath() %>/oscarReport/ViewOscarReportCalendarPopup?type=startDateA&amp;year=<%=curYear%>&amp;month=<%=curMonth%>&amp;form=<%="RptInitializeFrequencyOfRelevantTestsCDMReportForm"%>','','width=300,height=300')"><img src="<%= request.getContextPath() %>/images/calendar.gif" alt="" /></button>
                                        </td>
                                        <td width="120" class="fieldBox" bgcolor="#ddddff"><input
                                                type="text" name='endDateA' aria-label="<fmt:message key="oscarReport.CDMReport.msgEndDate"/>" value="${carlos:forHtmlAttribute(cdmForm.value('endDateA', 0, today))}"
                                                size="10"> <button type="button" aria-label="<fmt:message key="oscarReport.CDMReport.msgEndDate"/>"
                                                onclick="window.open('<%= request.getContextPath() %>/oscarReport/ViewOscarReportCalendarPopup?type=endDateA&amp;year=<%=curYear%>&amp;month=<%=curMonth%>&amp;form=<%="RptInitializeFrequencyOfRelevantTestsCDMReportForm"%>','','width=300,height=300')"><img src="<%= request.getContextPath() %>/images/calendar.gif" alt="" /></button>
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
                                                ${carlos:forHtml(msg)}
                                                <br>
                                            </c:forEach>
                                        </c:if>
                                    </tr>
                                    <tr>
                                        <td>
                                    <tr>
                                        <td class="nameBox" colspan='9'><fmt:message key="oscarReport.CDMReport.msgFrequencyOfRelevantTestsBeingPerformed"/>
                                        </td>
                                    </tr>
                                    <tr>
                                        <th align="left" class="subTitles" width="2"></th>
                                        <th align="left" class="subTitles" width="4"><fmt:message key="oscarReport.CDMReport.msgTest"/></th>
                                        <th align="left" class="subTitles" width="200"><fmt:message key="oscarReport.CDMReport.msgTestDescription"/></th>
                                        <th align="left" class="subTitles" width="200"><fmt:message key="oscarReport.CDMReport.msgMeasuringInstruction"/></th>
                                        <th align="left" class="subTitles" width="80"><fmt:message key="oscarReport.CDMReport.msgExactly"/></th>
                                        <th align="left" class="subTitles" width="80"><fmt:message key="oscarReport.CDMReport.msgMoreThan"/></th>
                                        <th align="left" class="subTitles" width="80"><fmt:message key="oscarReport.CDMReport.msgLessThan"/></th>
                                        <th align="left" class="subTitles" width="120"><fmt:message key="oscarReport.CDMReport.msgStartDate"/></th>
                                        <th align="left" class="subTitles" width="120"><fmt:message key="oscarReport.CDMReport.msgEndDate"/></th>
                                    </tr>
                                    <c:forEach var="measurementType" items="${measurementTypes.measurementTypeVector}" varStatus="ctr">
                                    <tr>
                                        <td width="2" class="fieldBox" bgcolor="#ddddff"><input
                                                type="checkbox" name="frequencyCheckbox" aria-label="${carlos:forHtmlAttribute(measurementType.typeDisplayName)} - <fmt:message key="oscarReport.CDMReport.msgTest"/>" value="${ctr.index}" <c:if test="${cdmForm.selected('frequencyCheckbox', ctr.index, false)}">checked="checked"</c:if>/></td>
                                        <td width="4" class="fieldBox" bgcolor="#ddddff">${carlos:forHtml(measurementType.typeDisplayName)}</td>
                                        <td width="200" class="fieldBox" bgcolor="#ddddff">${carlos:forHtml(measurementType.typeDesc)}</td>
                                        <td width="200" class="fieldBox" bgcolor="#ddddff"></td>
                                        <td width="80" class="fieldBox" bgcolor="#ddddff"><input type="text" name="exactly" aria-label="${carlos:forHtmlAttribute(measurementType.typeDisplayName)} - <fmt:message key="oscarReport.CDMReport.msgExactly"/>" value="${carlos:forHtmlAttribute(cdmForm.value('exactly', ctr.index, ''))}" size="6"/></td>
                                        <td width="80" class="fieldBox" bgcolor="#ddddff"><input type="text" name="moreThan" aria-label="${carlos:forHtmlAttribute(measurementType.typeDisplayName)} - <fmt:message key="oscarReport.CDMReport.msgMoreThan"/>" value="${carlos:forHtmlAttribute(cdmForm.value('moreThan', ctr.index, ''))}" size="6"/></td>
                                        <td width="80" class="fieldBox" bgcolor="#ddddff"><input type="text" name="lessThan" aria-label="${carlos:forHtmlAttribute(measurementType.typeDisplayName)} - <fmt:message key="oscarReport.CDMReport.msgLessThan"/>" value="${carlos:forHtmlAttribute(cdmForm.value('lessThan', ctr.index, ''))}" size="6"/></td>
                                        <td width="120" class="fieldBox" bgcolor="#ddddff"><input type="text" name="startDateD" aria-label="${carlos:forHtmlAttribute(measurementType.typeDisplayName)} - <fmt:message key="oscarReport.CDMReport.msgStartDate"/>" value="${carlos:forHtmlAttribute(cdmForm.value('startDateD', ctr.index, lastYear))}" size="10">
                                            <button type="button" aria-label="${carlos:forHtmlAttribute(measurementType.typeDisplayName)} - <fmt:message key="oscarReport.CDMReport.msgStartDate"/>"
                                                onclick="window.open('<%= request.getContextPath() %>/oscarReport/ViewOscarReportCalendarPopup?type=startDateD[${ctr.index}]&amp;year=<%=curYear%>&amp;month=<%=curMonth%>&amp;form=RptInitializeFrequencyOfRelevantTestsCDMReportForm','','width=300,height=300')"><img src="<%= request.getContextPath() %>/images/calendar.gif" alt="" /></button>
                                        </td>
                                        <td width="120" class="fieldBox" bgcolor="#ddddff"><input type="text" name="endDateD" aria-label="${carlos:forHtmlAttribute(measurementType.typeDisplayName)} - <fmt:message key="oscarReport.CDMReport.msgEndDate"/>" value="${carlos:forHtmlAttribute(cdmForm.value('endDateD', ctr.index, today))}" size="10">
                                            <button type="button" aria-label="${carlos:forHtmlAttribute(measurementType.typeDisplayName)} - <fmt:message key="oscarReport.CDMReport.msgEndDate"/>"
                                                onclick="window.open('<%= request.getContextPath() %>/oscarReport/ViewOscarReportCalendarPopup?type=endDateD[${ctr.index}]&amp;year=<%=curYear%>&amp;month=<%=curMonth%>&amp;form=RptInitializeFrequencyOfRelevantTestsCDMReportForm','','width=300,height=300')"><img src="<%= request.getContextPath() %>/images/calendar.gif" alt="" /></button>
                                        </td>
                                        <input type="hidden" name='value(measurementTypeD${ctr.index})' value="${carlos:forHtmlAttribute(measurementType.type)}"/>
                                    </tr>
                                    <tr>
                                        <td width="2" class="fieldBox" bgcolor="#ddddff"></td>
                                        <td width="4" class="fieldBox" bgcolor="#ddddff" width="5"></td>
                                        <td width="200" class="fieldBox" bgcolor="#ddddff"></td>
                                        <td width="200" class="fieldBox" bgcolor="#ddddff">
                                            <table>
                                                <%-- See InitializePatientsMetGuidelineCDMReport.jsp: row N's instructions are
                                                     the Nth handler of measurementTypes, including legacy stored ones. --%>
                                                <%int k = 0;%>
                                                <c:forEach var="mInstrc" items="${measurementTypes.measuringInstrcBeanVector[ctr.index].measuringInstrcVector}" varStatus="index">
                                                    <tr>
                                                        <td><c:set var="cdmInstructionName" value="value(mInstrcsCheckboxD${ctr.index}${index.index})" /><input type="checkbox"
                                                                   name='value(mInstrcsCheckboxD${ctr.index}${index.index})'
                                                                   aria-label="${carlos:forHtmlAttribute(measurementType.typeDisplayName)} - ${carlos:forHtmlAttribute(mInstrc.measuringInstrc)}"
                                                                   <c:if test="${cdmForm.selected(cdmInstructionName, mInstrc.measuringInstrc, true)}">checked="checked"</c:if>
                                                                   value='${carlos:forHtmlAttribute(mInstrc.measuringInstrc)}'/>
                                                                   ${carlos:forHtml(mInstrc.measuringInstrc)}
                                                                   </td>
                                                    </tr>
                                                    <%k++;%>
                                                </c:forEach>
                                            </table>
                                        </td>
                                        <input type="hidden"
                                               name='value(mNbInstrcsD${ctr.index})' value='<%=k%>'/>
                                        <td width="80" class="fieldBox" bgcolor="#ddddff"></td>
                                        <td width="80" class="fieldBox" bgcolor="#ddddff"></td>
                                        <td width="80" class="fieldBox" bgcolor="#ddddff"></td>
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
                                                value="<fmt:message key="oscarReport.CDMReport.btnGenerateReport"/>"/>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        </table>

    </form>

    </body>
</html>
