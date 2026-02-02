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
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_eChart" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_eChart");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@page import="ca.openosp.openo.util.StringUtils" %>
<%@page import="org.owasp.encoder.Encode" %>
<%@ page
        import="ca.openosp.openo.demographic.data.*, ca.openosp.openo.commn.model.Demographic" %>
<%@ page import="ca.openosp.openo.demographic.data.DemographicData" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%
    String sex = request.getParameter("sex");
    String age = request.getParameter("age");
    String demo = request.getParameter("demo");

    if (!StringUtils.isNullOrEmpty(demo)) {
        DemographicData dd = new DemographicData();
        Demographic d = dd.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), demo);
        sex = d.getSex();
        age = d.getAge();
    }

    // OWASP encode for URL query parameters - MANDATORY for security
    String sexEncoded = (sex != null) ? Encode.forUriComponent(sex) : "";
    String ageEncoded = (age != null) ? Encode.forUriComponent(age) : "";

    pageContext.setAttribute("sexEncoded", sexEncoded);
    pageContext.setAttribute("ageEncoded", ageEncoded);
%>

<c:set var="ctx" value="${pageContext.request.contextPath}"/>

<html>
    <head>
        <script type="text/javascript" src="${ctx}/js/global.js"></script>
        <link rel="stylesheet" type="text/css" href="${ctx}/oscarEncounter/encounterStyles.css">
        <script type="text/javascript" language=javascript>
            function popperup(vheight, vwidth, varpage, pageName) { //open a new popup window
                var page = varpage;
                windowprops = "height=" + vheight + ",width=" + vwidth + ",status=yes,location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=100,left=100";
                var popup = window.open(varpage, pageName, windowprops);
                popup.pastewin = opener;
                popup.focus();
                close();
            }
        </script>
    </head>

    <body>
    <table border=0 cellpadding="0" cellspacing="1" width="100%"
           class="layerTable" bgcolor="#FFFFFF">
        <tr>
            <td align="center" class="menuLayer"><a
                    href="javascript: function myFunction() {return false; }"
                    onclick="popperup(650,775,'https://www.mcw.edu/calculators/body-mass-index','BodyMassIndex');">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.bodyMass"/> </a></td>
        </tr>
        <tr>
            <td align="center" class="menuLayer"><a
                    href="javascript: function myFunction() {return false; }"
                    onclick="popperup(525,775,'${ctx}/oscarEncounter/calculators/CoronaryArteryDiseaseRiskPrediction.jsp?sex=${sexEncoded}&age=${ageEncoded}','CoronaryArteryDiseaseRisk');">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.coronary"/> </a></td>
        </tr>
        <tr>
            <td align="center" class="menuLayer"><a
                    href="javascript: function myFunction() {return false; }"
                    onclick="popperup(525,775,'${ctx}/oscarEncounter/calculators/riskcalc/index.html?sex=${sexEncoded}&age=${ageEncoded}','CoronaryArteryDiseaseRisk');">
                Framingham/UKPDS Risk Calculator </a></td>
        </tr>
        <tr>
            <td align="center" class="menuLayer">
                <a href="javascript: function myFunction() {return false; }"
                   onclick="popperup(525,775,'https://cvrisk.mvm.ed.ac.uk/calculator/calc.asp','CVRisk');">CV Risk</a>
            </td>
        </tr>


        <tr>
            <td align="center" class="menuLayer"><a
                    href="javascript: function myFunction() {return false; }"
                    onclick="popperup(525,775,'${ctx}/oscarEncounter/calculators/OsteoporoticFracture.jsp?sex=${sexEncoded}&age=${ageEncoded}','OsteoporoticFracture');">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.msgOsteoporotic"/> </a></td>
        </tr>
        <tr>
            <td align="center" class="menuLayer"><a
                    href="javascript: function myFunction() {return false; }"
                    onclick="popperup(650,775,'https://www.mcw.edu/calculators/pregnancy-date','PregancyCalculator');">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.pregnancy"/> </a></td>
        </tr>
        <tr>
            <td align="center" class="menuLayer"><a
                    href="javascript: function myFunction() {return false; }"
                    onclick="popperup(400,500,'${ctx}/oscarEncounter/calculators/SimpleCalculator.jsp','SimpleCalc');">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.simpleCalculator"/> </a></td>
        </tr>
        <tr>
            <td align="center" class="menuLayer"><a
                    href="javascript: function myFunction() {return false; }"
                    onclick="popperup(650,775,'${ctx}/oscarEncounter/calculators/GeneralCalculators.jsp','GeneralConversions'); ">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.generalConversions"/> </a></td>
        </tr>
    </table>
    </body>
</html>
