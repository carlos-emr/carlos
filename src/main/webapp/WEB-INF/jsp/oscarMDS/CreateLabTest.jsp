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
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_lab" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_lab");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
    String id = request.getParameter("id");
%>
<div id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>">
    <input type="hidden"
           name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.id"
           id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.id"
           value="<carlos:encode value='<%= id %>' context="htmlAttribute"/>"/>
    <div class="card mb-3">
        <div class="card-header d-flex justify-content-between align-items-center">
            <span class="fw-bold"><fmt:message key="oscarMDS.createLab.testInformation"/></span>
            <a href="#" onclick="deleteTest(<carlos:encode value='<%= id %>' context="javaScriptAttribute"/>); return false;"
               class="btn btn-danger btn-sm">
                <fmt:message key="oscarMDS.createLab.delete"/>
            </a>
        </div>
        <div class="card-body">
            <%-- Row 1: Date, Flag, Status --%>
            <div class="row g-2 mb-2">
                <div class="col-md-4">
                    <label class="form-label"
                           for="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.valDate">
                        <span class="text-danger" aria-hidden="true">*</span> <fmt:message key="oscarMDS.createLab.date"/>
                    </label>
                    <div class="input-group has-validation">
                        <input type="text" class="form-control"
                               name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.valDate"
                               id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.valDate"
                               required>
                        <img src="<%=request.getContextPath()%>/images/cal.gif"
                             id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.valDate_cal"
                             class="input-group-text" style="cursor:pointer;">
                        <div class="invalid-feedback"><fmt:message key="oscarMDS.createLab.validation.testDate"/></div>
                    </div>
                </div>
                <div class="col-md-4">
                    <label class="form-label"
                           for="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.flag">
                        <fmt:message key="oscarMDS.createLab.flag"/>
                    </label>
                    <select class="form-select"
                            name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.flag"
                            id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.flag">
                        <option value=""><fmt:message key="oscarMDS.createLab.flagNone"/></option>
                        <option value="A"><fmt:message key="oscarMDS.createLab.flagAbnormal"/></option>
                        <option value="N"><fmt:message key="oscarMDS.createLab.flagNormal"/></option>
                    </select>
                </div>
                <div class="col-md-4">
                    <label class="form-label"
                           for="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.stat">
                        <fmt:message key="oscarMDS.createLab.status"/>
                    </label>
                    <select class="form-select"
                            name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.stat"
                            id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.stat">
                        <option value="F"><fmt:message key="oscarMDS.createLab.statusFinal"/></option>
                        <option value="P"><fmt:message key="oscarMDS.createLab.statusPartial"/></option>
                    </select>
                </div>
            </div>

            <%-- Row 2: Code Type, Code, Name, Description --%>
            <div class="row g-2 mb-2">
                <div class="col-md-3">
                    <label class="form-label"
                           for="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.codeType">
                        <fmt:message key="oscarMDS.createLab.codeType"/>
                    </label>
                    <select class="form-select"
                            name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.codeType"
                            id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.codeType">
                        <option value="ST"><fmt:message key="oscarMDS.createLab.codeTypeShortText"/></option>
                        <option value="FT"><fmt:message key="oscarMDS.createLab.codeTypeFormattedText"/></option>
                    </select>
                </div>
                <div class="col-md-2">
                    <label class="form-label"
                           for="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.code">
                        <fmt:message key="oscarMDS.createLab.code"/>
                    </label>
                    <input type="text" class="form-control"
                           name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.code"
                           id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.code"/>
                </div>
                <div class="col-md-3">
                    <label class="form-label"
                           for="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.lab_test_name">
                        <span class="text-danger" aria-hidden="true">*</span> <fmt:message key="oscarMDS.createLab.testName"/>
                    </label>
                    <input type="text" class="form-control"
                           name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.lab_test_name"
                           id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.lab_test_name"
                           required>
                    <div class="invalid-feedback"><fmt:message key="oscarMDS.createLab.validation.testName"/></div>
                </div>
                <div class="col-md-4">
                    <label class="form-label"
                           for="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.test_descr">
                        <fmt:message key="oscarMDS.createLab.testDescription"/>
                    </label>
                    <input type="text" class="form-control"
                           name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.test_descr"
                           id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.test_descr"/>
                </div>
            </div>

            <%-- Row 3: Value, Unit, Ref Range Low, Ref Range High, Ref Range Text --%>
            <div class="row g-2 mb-2">
                <div class="col-md-2">
                    <label class="form-label"
                           for="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.codeVal">
                        <fmt:message key="oscarMDS.createLab.value"/>
                    </label>
                    <input type="text" class="form-control"
                           name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.codeVal"
                           id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.codeVal"/>
                </div>
                <div class="col-md-2">
                    <label class="form-label"
                           for="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.codeUnit">
                        <fmt:message key="oscarMDS.createLab.unit"/>
                    </label>
                    <input type="text" class="form-control"
                           name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.codeUnit"
                           id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.codeUnit"/>
                </div>
                <div class="col-md-2">
                    <label class="form-label"
                           for="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.refRangeLow">
                        <fmt:message key="oscarMDS.createLab.refRangeLow"/>
                    </label>
                    <input type="text" class="form-control"
                           name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.refRangeLow"
                           id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.refRangeLow"/>
                </div>
                <div class="col-md-2">
                    <label class="form-label"
                           for="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.refRangeHigh">
                        <fmt:message key="oscarMDS.createLab.refRangeHigh"/>
                    </label>
                    <input type="text" class="form-control"
                           name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.refRangeHigh"
                           id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.refRangeHigh"/>
                </div>
                <div class="col-md-4">
                    <label class="form-label"
                           for="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.refRangeText">
                        <fmt:message key="oscarMDS.createLab.refRangeText"/>
                    </label>
                    <input type="text" class="form-control"
                           name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.refRangeText"
                           id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.refRangeText"/>
                </div>
            </div>

            <%-- Row 4: Lab Notes, Blocked Test Result --%>
            <div class="row g-2">
                <div class="col-md-6">
                    <label class="form-label"
                           for="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.labnotes">
                        <fmt:message key="oscarMDS.createLab.labNotes"/>
                    </label>
                    <textarea class="form-control" rows="4"
                              name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.labnotes"
                              id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.labnotes"></textarea>
                </div>
                <div class="col-md-6">
                    <label class="form-label"
                           for="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.blocked">
                        <fmt:message key="oscarMDS.createLab.blockedResult"/>
                    </label>
                    <select class="form-select"
                            name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.blocked"
                            id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.blocked">
                        <option value=""><fmt:message key="oscarMDS.createLab.blockedNo"/></option>
                        <option value="BLOCKED"><fmt:message key="oscarMDS.createLab.blockedYes"/></option>
                    </select>
                </div>
            </div>
        </div>
    </div>
</div>
<script>
    Calendar.setup({
        inputField: "test_<carlos:encode value='<%= id %>' context="javaScript"/>.valDate",
        ifFormat: "%Y-%m-%d %H:%M",
        showsTime: true,
        button: "test_<carlos:encode value='<%= id %>' context="javaScript"/>.valDate_cal"
    });
</script>
