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

<!DOCTYPE html>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@page import="io.github.carlos_emr.carlos.lab.ca.all.pageUtil.SendOruR01UIBean" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@page import="io.github.carlos_emr.carlos.commn.Gender" %>
<%@page import="org.apache.commons.lang3.StringUtils" %>
<html>
<head>
    <title>Send eData</title>
    <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.3/css/bootstrap.min.css" rel="stylesheet" type="text/css">
    <link href="<%=request.getContextPath() %>/library/flatpickr/flatpickr.min.css" rel="stylesheet" type="text/css">


    <script src="<%=request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js"></script>
    <script src="<%=request.getContextPath() %>/library/jquery/jquery-compat.js"></script>

    <script src="<%=request.getContextPath() %>/library/bootstrap/5.3.3/js/bootstrap.bundle.min.js"></script>
    <script src="<%=request.getContextPath() %>/library/flatpickr/flatpickr.min.js"></script>
    <script src="<%=request.getContextPath() %>/library/jquery/jquery.validate.min.js"></script>

    <script>
        function checkRequiredFields() {
            if (document.getElementById('professionalSpecialistId').value.length == 0) {
                alert('Select a providers / specialist to send to.');
                return (false);
            }
            if (document.getElementById('clientFirstName').value.length == 0 || document.getElementById('clientLastName').value.length == 0) {
                alert('The clients first and last name is required.');
                return (false);
            }
            if (document.getElementById('subject').value.length == 0) {
                alert('The subject is required.');
                return (false);
            }
            if (document.getElementById('textMessage').value.length == 0 && document.getElementById('uploadFile').value.length == 0) {
                alert('Either Text Data or an Upload File is required.');
                return (false);
            }
            return (true);
        }
    </script>
</head>
<body>
<h4>Send eData <span style="font-size:9px">(ORU_R01 : Unsolicited Observation Message)</span></h4>
<%--
This jsp accepts parameters with the same name as
the fields in the SendOruR01UIBean. All parameters are optional
for pre-populating data.
--%>
<%
    SendOruR01UIBean sendOruR01UIBean = new SendOruR01UIBean(request);
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
%>

<form method="post" enctype="multipart/form-data" action="oruR01Upload.do" onsubmit="return checkRequiredFields()"
      class="card card-body bg-body-tertiary">
    <fieldset>
        <div class="mb-3">
            <label class="form-label">From Provider:</label>
            <div>
                <%=SendOruR01UIBean.getLoggedInProviderDisplayLine(loggedInInfo)%>
            </div>
        </div>
        <div class="mb-3">
            <label class="form-label">To Provider / Specialist:</label>
            <div>
                <select name="professionalSpecialistId" id="professionalSpecialistId">
                    <option value="">--- none selected ---</option>
                    <%
                        for (ProfessionalSpecialist professionalSpecialist : SendOruR01UIBean.getRemoteCapableProfessionalSpecialists()) {
                    %>
                    <option value="<%=professionalSpecialist.getId()%>" <%=sendOruR01UIBean.renderSelectedProfessionalSpecialistOption(professionalSpecialist.getId())%> ><%=SendOruR01UIBean.getProfessionalSpecialistDisplayString(professionalSpecialist)%>
                    </option>
                    <%
                        }
                    %>
                </select>
            </div>
        </div>
        <div class="mb-3">
            <label class="form-label"><strong>For Client</strong></label>
            <div>&nbsp;</div>
        </div>
        <div class="mb-3">
            <label class="form-label">First Name</label>
            <div>
                <input type="text" id="clientFirstName" name="clientFirstName"
                       value="<%=sendOruR01UIBean.getClientFirstName()%>"/>
            </div>
        </div>
        <div class="mb-3">
            <label class="form-label">Last Name</label>
            <div>
                <input type="text" id="clientLastName" name="clientLastName"
                       value="<%=sendOruR01UIBean.getClientLastName()%>"/>
            </div>
        </div>
        <div class="mb-3">
            <label class="form-label">Health Number<br/>(excluding version code)</label>
            <div>
                <input type="text" name="clientHealthNumber" value="<%=sendOruR01UIBean.getClientHin()%>"/>
            </div>
        </div>
        <div class="mb-3">
            <label class="form-label">BirthDay</label>
            <div>
                <input type="text" id="clientBirthDay" name="clientBirthDay"
                       value="<%=sendOruR01UIBean.getClientBirthDate()%>"/>
                <script>
                    document.addEventListener('DOMContentLoaded', function () {
                        flatpickr("#clientBirthDay", {dateFormat: "Y-m-d", allowInput: true});
                    });
                </script>
            </div>
        </div>
        <div class="mb-3">
            <label class="form-label">Gender</label>
            <div>
                <select name="clientGender">
                    <option value="">--- none selected ---</option>
                    <%
                        for (Gender gender : Gender.values()) {
                    %>
                    <option value="<%=gender.name()%>" <%=sendOruR01UIBean.renderSelectedGenderOption(gender)%> ><%=gender.getText()%>
                    </option>
                    <%
                        }
                    %>
                </select>
            </div>
        </div>
        <hr/>
        <div class="mb-3">
            <label class="form-label">Subject</label>
            <div>
                <input type="text" id="subject" name="subject" value="<%=sendOruR01UIBean.getSubject()%>"/>
            </div>
        </div>
        <div class="mb-3">
            <label class="form-label">Text Message</label>
            <div>
                <textarea id="textMessage" name="textMessage"
                          style="width:40em;height:8em"><%=sendOruR01UIBean.getTextMessage()%></textarea>
            </div>
        </div>
        <div class="mb-3">
            <label class="form-label">Upload File</label>
            <div>
                <input type="file" id="uploadFile" name="uploadFile"/>
            </div>
        </div>
        <div class="mb-3">
            <label class="form-label">&nbsp;</label>
            <div>
                <input type="submit" class="btn btn-primary" value="Electronically Send Data"/>&nbsp;<input
                    type="button" class="btn btn-secondary" value="close" onclick='window.close()'/>
            </div>
        </div>
    </fieldset>
</form>
</body>
</html>