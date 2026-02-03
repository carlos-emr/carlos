<%--


    Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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

    This software was written for
    Centre for Research on Inner City Health, St. Michael's Hospital,
    Toronto, Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%@ include file="/taglibs.jsp" %>

<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title>On Call Questionnaire</title>
</head>
<body>

<h3>On Call Questionnaire</h3>
<br/>
<br/>
<table border="0" width="100%">
    <form action="${pageContext.request.contextPath}/OnCallQuestionnaire.do" method="post">
        <input type="hidden" name="method" value="save"/>
        <input type="hidden" name="providerNo"
               value="<%=request.getParameter("providerNo") %>"/>
        <input type="hidden" name="type"
               value="<%=request.getParameter("type") %>"/>
        <tr>
            <td>Type of Health problem:</td>
            <td><select name="type_health">
                <option value=""></option>
                <option value="Primary Care">Primary Care</option>
                <option value="Mental Health">Mental Health</option>
            </select></td>
        </tr>
        <tr>
            <td>Nurse Involved:</td>
            <td><select name="nurse">
                <option value=""></option>
                <option value="yes">Yes</option>
                <option value="no">No</option>
            </select></td>
        </tr>
        <tr>

            <td>Course of action:</td>
            <td><select name="course_action">
                <option value=""></option>
                <option value="sh">SH visit (immediate, 24 hours, same week)</option>
                <option value="er">ER</option>
                <option value="medication">Medication</option>
                <option value="investigations">investigations (24 hours, same weeks)</option>
                <option value="watch">watch and wait</option>
            </select></td>
        </tr>
        <tr>
            <td>Required physician consultation:</td>
            <td><select name="physician_consult">
                <option value=""></option>
                <option value="yes">Yes</option>
                <option value="no">No</option>
            </select></td>
        </tr>
        <tr>
            <td>Date:</td>
            <td><input type="text" name="date" id="date" /></td>
        </tr>
        <tr>
            <td>Time:</td>
            <td><input type="text" name="time" id="time" /></td>
        </tr>
        <tr>
            <td><input type="submit" name="submit" value="Submit" /></td>
        </tr>
    </form>
</table>
</body>
</html>
