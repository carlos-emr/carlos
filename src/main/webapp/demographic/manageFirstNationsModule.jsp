<%--

    Copyright 2015. Trimara Corporation. All Rights Reserved.
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
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_demographic" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_demographic");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="java.util.*" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.DemographicExtDao" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.managers.LookupListManager" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.LookupList" %>
<%@ page import="io.github.carlos_emr.OscarProperties" %>

<%
    String demographic_no = request.getParameter("demo");
    DemographicExtDao demographicExtDao = SpringUtils.getBean(DemographicExtDao.class);

    Map<String, String> demoExt = null;
    if (demographic_no != null) {
        demoExt = demographicExtDao.getAllValuesForDemo(Integer.valueOf(demographic_no));
    }
    pageContext.setAttribute("demoExt", demoExt);
    LookupListManager lookupListManager = SpringUtils.getBean(LookupListManager.class);
    LookupList firstNationCommunities = lookupListManager.findLookupListByName(LoggedInInfo.getLoggedInInfoFromSession(request), "firstNationCommunity");
    pageContext.setAttribute("firstNationCommunities", firstNationCommunities);
%>

<%--
    Valid INAC is 10 digits: first 3 = band, next 5 = family unit, last 2 = family position.
    If not begins with "T", "N", "B" or "9-digit number starting with a "1" and ending with a "5"" all have less than
    10 digits.
 --%>
<script type="text/javascript">
    //<!--
    document.addEventListener('DOMContentLoaded', function () {

        // all that is needed is a full band number OR a Band Name/number, Family Number
        // and family position.

        function evaluateNumber(number) {
            var band;
            var family;
            var familyPostion;
            if (number) {
                var length = number.length;

                if (length == 10) {
                    band = number.substring(0, 3);
                    family = number.substring(3, 8);
                    familyPostion = number.substring(8, 10);

                    if (!document.getElementById('fNationCom').value) {
                        document.getElementById('fNationCom').value = band;
                    }
                    if (!document.getElementById('fNationFamilyNumber').value) {
                        document.getElementById('fNationFamilyNumber').value = family;
                    }
                    if (!document.getElementById('fNationFamilyPosition').value) {
                        document.getElementById('fNationFamilyPosition').value = familyPostion;
                    }
                }
            }

        }

        document.getElementById('statusNum').addEventListener('focus', function () {
            toggleFirstNationFields(true)
        });


        document.getElementById('statusNum').addEventListener('blur', function () {
            evaluateNumber(document.getElementById('statusNum').value);

            if (document.getElementById('statusNum').value) {
                document.querySelector("[name='aboriginal']").value = "Yes";
            } else {
                document.querySelector("[name='aboriginal']").value = "";
            }
        });

        document.getElementById('fNationCom').addEventListener('change', function () {
            var selectedOption = document.getElementById('fNationCom').options[document.getElementById('fNationCom').selectedIndex];
            document.getElementById('labelfNationCom').value = selectedOption.text.trim();
        })

    });
    //-->
</script>

<%--<tr><td colspan="2">First Nations (INAC)</td></tr>--%>
<tr>

    <td align="right" class="label"><strong>Status Number:</strong></td>

    <td align="left">
        <%--
            Official title is band number. Left key value as status number so that
            older users can roll back.
         --%>
        <input type="text" id="statusNum" name="statusNum" maxlength="10" size="10" value="${ demoExt["statusNum"] }">
        <input type="hidden" name="statusNumOrig" value="${ demoExt["statusNum"] }">
    </td>
    <% if (!OscarProperties.getInstance().isPropertyActive("showBandNumberOnly")) { %>
    <td align="right" class="label disableStyle">
        <strong>First Nation Community:</strong>
    </td>
    <td align="left">
        <select id="fNationCom" name="fNationCom">
            <c:forEach items="${firstNationCommunities.items}" var="firstNationCommunity">
                <option value="${firstNationCommunity.value}" ${firstNationCommunity.value eq demoExt["fNationCom"] ? 'selected' : '' }>
                    <c:out value="${firstNationCommunity.label}"/>
                </option>
            </c:forEach>
        </select>
        <input type="hidden" name="labelfNationCom" id="labelfNationCom" value="">
        <%--	        <input type="hidden" name="fNationComOrig" value="${ demoExt["fNationCom"] }">--%>
    </td>
    <% } %>
</tr>
<tr>
    <td align="right" class="label disableStyle">
        <strong>Family Number:</strong>
    </td>
    <td align="left">
        <input type="text" id="fNationFamilyNumber" name="fNationFamilyNumber"
               value="${ demoExt["fNationFamilyNumber"] }">
        <%--	    	<input type="hidden" name="fNationFamilyNumberOrig" value="${ demoExt["fNationFamilyNumber"] }">--%>
    </td>
    <td align="right" class="label disableStyle">
        <strong>Family Position:</strong>
    </td>
    <td align="left">
        <input type="text" id="fNationFamilyPosition" name="fNationFamilyPosition"
               value="${ demoExt["fNationFamilyPosition"] }">
        <%--	    	<input type="hidden" name="fNationFamilyPositionOrig" value="${ demoExt["fNationFamilyPosition"] }">--%>
    </td>
</tr>

<tr>
    <td align="right" class="label"><strong>First Nation Status:</strong></td>
    <td align="left">

        <select name="ethnicity">
            <option value="-1" ${ demoExt['ethnicity'] eq -1 ? 'selected' : '' } >Not Set</option>
            <option value="1" ${ demoExt['ethnicity'] eq 1 ? 'selected' : '' } >On-reserve</option>
            <option value="2" ${ demoExt['ethnicity'] eq 2 ? 'selected' : '' } >Off-reserve</option>
            <option value="3" ${ demoExt['ethnicity'] eq 3 ? 'selected' : '' } >Non-status On-reserve</option>
            <option value="4" ${ demoExt['ethnicity'] eq 4 ? 'selected' : '' } >Non-status Off-reserve</option>
            <option value="5" ${ demoExt['ethnicity'] eq 5 ? 'selected' : '' } >Metis</option>
            <option value="6" ${ demoExt['ethnicity'] eq 6 ? 'selected' : '' } >Inuit</option>
            <option value="11" ${ demoExt['ethnicity'] eq 11 ? 'selected' : '' } >Homeless</option>
            <option value="12" ${ demoExt['ethnicity'] eq 12 ? 'selected' : '' } >Out of Country Residents</option>
            <option value="13" ${ demoExt['ethnicity'] eq 13 ? 'selected' : '' } >Other</option>
        </select>
        <input type="hidden" name="ethnicityOrig" value="${ demoExt["ethnicity"] }"/>
    </td>
    <td><!-- padding --></td>
    <td><!-- padding --></td>
</tr>

