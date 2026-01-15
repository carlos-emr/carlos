<!DOCTYPE html>
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
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<%@page import="ca.openosp.openo.commn.dao.DataExportDao" %>
<%@page import="org.apache.commons.lang3.time.DateFormatUtils" %>
<%@page import="ca.openosp.openo.report.data.DemographicSets" %>
<%@page import="java.util.List" %>
<%@page import="ca.openosp.openo.commn.model.DataExport" %>
<%@include file="/casemgmt/taglibs.jsp" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>
<%
    DemographicSets ds = new DemographicSets();
    List<String> setsList = ds.getDemographicSets();
%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<script src="${pageContext.request.contextPath}/csrfguard"></script>
<head>
    <title>CIHI Export</title>
    <link href="<%=request.getContextPath() %>/css/bootstrap.min.css" rel="stylesheet">
    <script type="text/javascript">
        function setReportType(select) {

            if (select.options[select.selectedIndex].value == "<%=DataExportDao.CIHI_OMD4%>") {
                document.forms[0].action = "<c:out value="${ctx}"/>/demographic/cihiExportOMD4.do";

            } else if (select.options[select.selectedIndex].value == "<%=DataExportDao.CIHI_PHC_VRS%>") {
                document.forms[0].action = "<c:out value="${ctx}"/>/demographic/cihiExportPHC_VRS.do";
            }

        }

    </script>
</head>
<body>
<div class="container-fluid well">
    <form action="${pageContext.request.contextPath}/demographic/cihiExportOMD4.do" method="get">
        <h3>Vendor Information</h3>
        <table class="table-condensed">
            <tr>
                <td>Organization Name</td>
                <td><input type="text" name="orgName" class="right" /></td>
            </tr>
            <tr>
                <td>Contact Last Name</td>
                <td><input type="text" name="contactLName" class="right" /></td>
            </tr>
            <tr>
                <td>Contact First Name</td>
                <td><input type="text" name="contactFName" class="right" /></td>
            </tr>
            <tr>
                <td>Contact Phone</td>
                <td><input type="text" name="contactPhone" class="right" /></td>
            </tr>
            <tr>
                <td>Contact Email</td>
                <td><input type="text" name="contactEmail" class="right" /></td>
            </tr>
            <tr>
                <td>Contact Username</td>
                <td><input type="text" name="contactUserName" class="right" /></td>
            </tr>
            <tr>
                <td>Vendor Business Name</td>
                <td><input type="text" name="vendorBusinessName" class="right" /></td>
            </tr>
            <tr>
                <td>Vendor ID</td>
                <td><input type="text" name="vendorId" class="right" readonly="true" /></td>
            </tr>
            <tr>
                <td>Vendor Common Name</td>
                <td><input type="text" name="vendorCommonName" class="right" readonly="true" /></td>
            </tr>
            <tr>
                <td>Vendor Software</td>
                <td><input type="text" name="vendorSoftware" class="right" readonly="true" /></td>
            </tr>
            <tr>
                <td>Vendor Software Common Name</td>
                <td><input type="text" name="vendorSoftwareCommonName" class="right" readonly="true" /></td>
            </tr>
            <tr>
                <td>Vendor Software Ver</td>
                <td><input type="text" name="vendorSoftwareVer" class="right" readonly="true" /></td>
            </tr>
            <tr>
                <td>Vendor Install Date</td>
                <td><input type="text" name="installDate" class="right" readonly="true" /></td>
            </tr>

            <tr>
                <td>Extract Type</td>
                <td>
                    <select name="extractType" onchange="setReportType(this);">
                        <option value="<%=DataExportDao.CIHI_OMD4%>"><%=DataExportDao.CIHI_OMD4%>
                        </option>
                        <option value="<%=DataExportDao.CIHI_PHC_VRS%>"><%=DataExportDao.CIHI_PHC_VRS%>
                        </option>
                    </select>
                </td>
            </tr>

            <tr>
                <td>
                    Patient Set
                </td>
                <td>
                    <select name="patientSet" id="patientSet">
                        <option value="-1">--Select Set--</option>
                        <%
                            String setName;
                            for (int idx = 0; idx < setsList.size(); ++idx) {
                                setName = setsList.get(idx);
                        %>
                        <option value="<%=setName%>"><%=setName%>
                        </option>
                        <%
                            }
                        %>
                    </select>
                </td>
            </tr>

            <tr>
                <td colspan="2" align="right"><input class="btn btn-primary" type="submit" value="Run Report"/></td>
            </tr>

        </table>


        <h3>Previous Reports</h3>
        <table class="table table-striped  table-condensed">
            <tr>
                <th>Run Date</th>
                <th>File</th>
                <th>User</th>
                <th>Type</th>
            </tr>
            <%
                List<DataExport> dataExportList = (List<DataExport>) request.getAttribute("dataExportList");
                for (int idx = dataExportList.size() - 1; idx >= 0; --idx) {
                    DataExport dataExport = dataExportList.get(idx);
                    String file = dataExport.getFile();
            %>
            <tr>
                <td><%=DateFormatUtils.format(dataExport.getDaterun().getTime(), DateFormatUtils.ISO_DATETIME_FORMAT.getPattern()) %>
                </td>
                <td>
                    <a href='<c:out value="${ctx}/demographic/cihiExportOMD4.do"></c:out>?method=getFile&zipFile=<%=file%>'><%=file %>
                    </a></td>
                <td><%=dataExport.getUser()%>
                <td><%=dataExport.getType()%>
                </td>
            </tr>
            <%
                }
            %>
        </table>
    </form>
</div>
</body>
</html>
