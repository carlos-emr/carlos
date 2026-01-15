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
<security:oscarSec roleName="<%=roleName$%>" objectName="_lab" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_lab");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@page import="java.io.Serializable" %>
<%@page import="org.w3c.dom.Document" %>
<%@page import="ca.openosp.openo.caisi_integrator.ws.CachedDemographicLabResult" %>
<%@page import="ca.openosp.openo.lab.ca.all.web.LabDisplayHelper" %>
<%@ page
        import="java.util.*,ca.openosp.openo.lab.ca.on.*,ca.openosp.openo.demographic.data.*" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.commn.model.Demographic" %>
<%@ page import="ca.openosp.openo.managers.DemographicManager" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.demographic.data.DemographicData" %>
<%@ page import="ca.openosp.openo.lab.ca.on.CommonLabTestValues" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%
    String labType = request.getParameter("labType");
    String demographicNo = request.getParameter("demo");
    String testName = request.getParameter("testName");
    String identifier = request.getParameter("identifier");
    String remoteFacilityIdString = request.getParameter("remoteFacilityId");
    String remoteLabKey = request.getParameter("remoteLabKey");

    if (identifier == null) identifier = "NULL";

    String highlight = "#E0E0FF";

    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

    DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    Demographic demographic = demographicManager.getDemographic(loggedInInfo, demographicNo);

    ArrayList list = null;

    if (!(demographicNo == null || "null".equals(demographicNo) || "undefined".equals(demographicNo))) {
        if (remoteFacilityIdString == null) {
            list = CommonLabTestValues.findValuesForTest(labType, Integer.valueOf(demographicNo), testName, identifier);
        } else {
            CachedDemographicLabResult remoteLab = LabDisplayHelper.getRemoteLab(loggedInInfo, Integer.parseInt(remoteFacilityIdString), remoteLabKey, Integer.parseInt(demographicNo));
            Document labContentsAsXml = LabDisplayHelper.getXmlDocument(remoteLab);
            HashMap<String, ArrayList<Map<String, Serializable>>> mapOfTestValues = LabDisplayHelper.getMapOfTestValues(labContentsAsXml);
            list = mapOfTestValues.get(identifier);
        }
    }
%>
<!DOCTYPE html>
<html>
<head>
    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.title"/></title>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

    <link href="<%=request.getContextPath() %>/css/bootstrap.css" rel="stylesheet" type="text/css">
    <link href="<%=request.getContextPath() %>/css/DT_bootstrap.css" rel="stylesheet" type="text/css">
    <script type="text/javascript" src="<%=request.getContextPath() %>/library/jquery/jquery-3.6.4.min.js"></script>
    <script type="text/javascript"
            src="<%=request.getContextPath() %>/library/DataTables/DataTables-1.13.4/js/jquery.dataTables.js"></script>
    <script type="text/javascript">
        jQuery(document).ready(function () {

            jQuery('#tblDiscs').DataTable({
                "order": [],
                "bPaginate": false,
                "searching": false
            });
        });

    </script>
    <style media="all">
        .AbnormalRes {
            color: red;
        }

        .LoRes {
            color: blue;
        }

        table {
            border-collapse: collapse;
            width: 100%;
            margin: 0;
            padding: 0;
        }
    </style>

    <style media="print">
        .DoNotPrint {
            display: none;

        }
    </style>

</head>

<script language="JavaScript">
    function getComment() {
        var commentVal = prompt('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.msgComment"/>', '');
        document.acknowledgeForm.comment.value = commentVal;
        return true;
    }

    function popupStart(vheight, vwidth, varpage, windowname) {
        var page = varpage;
        windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes";
        var popup = window.open(varpage, windowname, windowprops);
    }
</script>

<body>

<% if (demographic == null) {%>

<script language="JavaScript">
    alert("The demographic number is not valid");
    window.close();
</script>

<%} else {%>
<div class="container">
    <form name="acknowledgeForm" method="post" action="<%=request.getContextPath()%>/oscarMDS/UpdateStatus.do">

        <table>
            <tr>
                <td>
                    <table>
                        <tr>
                            <td>
                                <div class="Field2" style="text-align: center;">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formDetailResults"/>
                                </div>
                            </td>
                        </tr>
                        <tr>
                            <td>
                                <table>
                                    <tr>
                                        <td>
                                            <table>
                                                <tr>
                                                    <td>
                                                        <table>
                                                            <tr>
                                                                <td>
                                                                    <div class="FieldData"><strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formPatientName"/>: </strong>
                                                                        <%=Encode.forHtml(demographic.getFormattedName())%>
                                                                    </div>

                                                                </td>
                                                                <td>
                                                                    <div class="" nowrap><strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formSex"/>: </strong><%=demographic.getSex()%>
                                                                    </div>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td>
                                                                    <div class="FieldData"><strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formDateBirth"/>: </strong> <%=DemographicData.getDob(demographic, "-")%>
                                                                    </div>
                                                                </td>
                                                                <td>
                                                                    <div class="FieldData"><strong><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formAge"/>: </strong><%=demographic.getAge()%>
                                                                    </div>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td width="33%"></td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td>
                    <table name="tblDiscs" id="tblDiscs" class="table table-condensed table-striped">
                        <thead>
                        <tr class="Field2">
                            <th class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formTestName"/></th>
                            <th class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formResult"/></th>
                            <th class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formAbn"/></th>
                            <th class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formReferenceRange"/></th>
                            <th class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formUnits"/></th>
                            <th class="Cell"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.segmentDisplay.formDateTimeCompleted"/></th>
                        </tr>
                        </thead>
                        <tbody>
                        <%
                            if (list != null) {
                                for (int i = 0; i < list.size(); i++) {
                                    Map h = (Map) list.get(i);
                                    String lineClass = "NormalRes";
                                    if (h.get("abn") != null && h.get("abn").equals("A")) {
                                        lineClass = "AbnormalRes";
                                    }
                                    if (h.get("abn") != null && (h.get("abn").toString().toLowerCase().contains("l"))) {

                                        lineClass = "LoRes";

                                    }

                                    if (h.get("abn") != null && (h.get("abn").toString().toLowerCase().contains("h"))) {

                                        lineClass = "AbnormalRes";

                                    }
                        %>

                        <tr class="<%=lineClass%>">
                            <td><%=h.get("testName") %>
                            </td>
                            <td><%=h.get("result") %>
                            </td>
                            <td><%=h.get("abn") %>
                            </td>
                            <td><%=h.get("range")%>
                            </td>
                            <td><%=h.get("units") %>
                            </td>
                            <td><%=h.get("collDate")%>
                            </td>
                        </tr>

                        <% }
                        } %>
                        </tbody>
                    </table>
                </td>
            <tr>
                <td>
                    <table class="MainTableBottomRowRightColumn" bgcolor="#003399">
                        <tr>
                            <td align="left"><input type="button" class="btn btn-danger DoNotPrint"
                                                    value=" <fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/> "
                                                    onClick="window.close()"> <input type="button"
                                                                                     class="btn DoNotPrint"
                                                                                     value=" <fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnPrint"/> "
                                                                                     onClick="window.print()">
                                <input type="button" value="Plot" class="btn btn-primary DoNotPrint"
                                       onclick="window.location = '<%= Encode.forJavaScript(request.getContextPath()) %>/lab/CA/ON/labValuesGraph.jsp?demographic_no=<%= Encode.forUriComponent(String.valueOf(demographicNo)) %>&labType=<%= Encode.forUriComponent(labType) %>&identifier=<%= Encode.forUriComponent(identifier) %>&testName=<%= Encode.forUriComponent(testName) %>';"/>

                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
        </table>

    </form>
</div>
<%}%>
</body>
</html>
