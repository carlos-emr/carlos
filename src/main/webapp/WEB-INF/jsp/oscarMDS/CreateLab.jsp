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
<%@ taglib uri="/struts-tags" prefix="s" %>
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
%>
<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<fmt:message key="oscarMDS.createLab.confirmSave" var="confirmSaveMsg"/>
<html lang="<%= request.getLocale().toLanguageTag() %>">
<head>
    <meta charset="UTF-8">
    <title><fmt:message key="global.createLab"/></title>
    <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
    <%-- Calendar widget (not in global-head) --%>
    <link rel="stylesheet" type="text/css" media="all" href="<%=request.getContextPath()%>/share/calendar/calendar.css" title="win2k-cold-1"/>
    <script type="text/javascript" src="<%=request.getContextPath()%>/share/calendar/calendar.js"></script>
    <script type="text/javascript" src="<%=request.getContextPath()%>/share/calendar/lang/<fmt:message key="global.javascript.calendar"/>"></script>
    <script type="text/javascript" src="<%=request.getContextPath()%>/share/calendar/calendar-setup.js"></script>
    <%-- jQuery UI JS — loaded per-page; CSS already provided by global-head --%>
    <script src="${pageContext.request.contextPath}/library/jquery/jquery-ui-1.14.2.min.js"></script>
    <style>
        form[name="testForm"] .lab-test-table td label {
            display: block;
            margin-bottom: 3px;
        }
    </style>
    <script>
        $(document).ready(function () {

            var searchDemoUrl = "<%= request.getContextPath() %>/demographic/SearchDemographic";

            $("#lastname").autocomplete({
                source: function (req, res) {
                    $.ajax({
                        url: searchDemoUrl,
                        type: 'GET',
                        data: {jqueryJSON: 'true', activeOnly: 'true', term: req.term},
                        success: function (data) { res(data); },
                        error: function () { res([]); }
                    });
                },
                minLength: 2,
                focus: function (event, ui) {
                    if (ui.item.formattedName) {
                        const myArray = ui.item.formattedName.split(",");
                        if (myArray.length > 1) {
                            $("#lastname").val(myArray[0].trim());
                            $("#firstname").val(myArray[1].trim());
                        }
                    }
                    return false;
                },
                select: function (event, ui) {
                    const myArray = ui.item.formattedName.split(",");
                    if (myArray.length > 1) {
                        $("#lastname").val(myArray[0].trim());
                        $("#firstname").val(myArray[1].trim());
                    }
                    let dob = null;
                    if (ui.item.fomattedDob) {
                        dob = ui.item.fomattedDob;
                    } else if (ui.item.formattedDob) {
                        dob = ui.item.formattedDob;
                    } else if (typeof ui.item.label === "string") {
                        const dobMatch = ui.item.label.match(/\b(\d{4}-\d{2}-\d{2})\b/);
                        if (dobMatch && dobMatch[1]) {
                            dob = dobMatch[1];
                        }
                    }
                    if (dob) {
                        $("#dob").val(dob);
                    }
                    return false;
                }
            }).autocomplete("instance")._renderItem = function (ul, item) {
                var li = $("<li>");
                var div = $("<div>");
                $("<b>").text(item.label).appendTo(div);
                div.append("<br>");
                $("<span>").text(item.provider).appendTo(div);
                div.appendTo(li);
                return li.appendTo(ul);
            };

            var url2 = "<%= request.getContextPath() %>/provider/SearchProvider?method=labSearch";

            $("#pLastname").autocomplete({
                source: url2,
                minLength: 2,
                focus: function (event, ui) {
                    const myArray = ui.item.label.split(",");
                    if (myArray.length > 1) {
                        $("#pLastname").val(myArray[0].trim());
                        $("#pFirstname").val(myArray[1].trim());
                    }
                    return false;
                },
                select: function (event, ui) {
                    const myArray = ui.item.label.split(",");
                    if (myArray.length > 1) {
                        $("#pLastname").val(myArray[0].trim());
                        $("#pFirstname").val(myArray[1].trim());
                    }
                    return false;
                }
            });
        });

        function addTest() {
            var count = Number(jQuery("#test_num").val() || 0);
            var nextId = Number(jQuery("#next_test_id").val() || 0) + 1;
            jQuery("#next_test_id").val(nextId);
            jQuery("#test_num").val(count + 1);
            jQuery.ajax({
                url: '<%=request.getContextPath()%>/oscarMDS/ViewCreateLabTest?id=' + nextId,
                async: true,
                success: function (data) {
                    jQuery("#test_container").append(data);
                    jQuery('form[name="testForm"] :submit').prop('disabled', false);
                }
            });
        }

        function deleteTest(id) {
            var testId = jQuery("input[name='test_" + id + ".id']").val();
            var hiddenInput = jQuery("<input>").attr({
                type: "hidden",
                name: "test.delete",
                value: testId
            });
            jQuery("form[name='testForm']").append(hiddenInput);
            jQuery("#test_" + id).remove();
            var count = Math.max(0, Number(jQuery("#test_num").val() || 0) - 1);
            jQuery("#test_num").val(count);
            if (count < 1) {
                jQuery('form[name="testForm"] :submit').prop('disabled', true);
            }
        }

        function confirmSave() {
            var form = document.querySelector('form[name="testForm"]');
            if (!form.checkValidity()) {
                form.classList.add('was-validated');
                return false;
            }
            form.classList.add('was-validated');
            return confirm("${carlos:forJavaScript(confirmSaveMsg)}");
        }

      	function setUpValidation() {
            document.addEventListener('DOMContentLoaded', () => {
              'use strict'
              // Fetch all the forms we want to apply custom Bootstrap validation styles to
              const forms = document.querySelectorAll('.needs-validation')
              // Loop over them and prevent submission
              Array.from(forms).forEach(form => {
                form.addEventListener('submit', event => {
                  if (!form.checkValidity()) {
                    event.preventDefault()
                    event.stopPropagation()
                  }
                  form.classList.add('was-validated')
                }, false)
              })
            })
      	}
    </script>
</head>
<body>
<div class="container">
    <div class="page-header-bar">
        <h4 class="page-header-title"><fmt:message key="global.createLab"/></h4>
        <button type="button" class="btn btn-secondary btn-sm" onclick="window.close();"><fmt:message key="oscarMDS.createLab.back"/></button>
    </div>

    <%-- Display Struts action messages and errors from form submission --%>
    <s:if test="hasActionMessages()">
        <div class="alert alert-success" role="alert">
            <s:actionmessage/>
        </div>
    </s:if>
    <s:if test="hasActionErrors()">
        <div class="alert alert-danger" role="alert">
            <s:actionerror/>
        </div>
    </s:if>

    <form name="testForm" method="post" action="<%=request.getContextPath()%>/oscarMDS/SubmitLab?method=saveManage"
          onsubmit="return confirmSave();" novalidate class="needs-validation">

        <div class="row mb-3">
            <%-- Laboratory Information --%>
            <div class="col-md-6">
                <div class="card h-100">
                    <div class="card-header fw-bold"><fmt:message key="oscarMDS.createLab.laboratoryInformation"/></div>
                    <div class="card-body">
                        <div class="mb-2">
                            <label class="form-label" for="labname"><fmt:message key="oscarMDS.createLab.labName"/></label>
                            <select name="labname" id="labname" class="form-select">
                                <option value="MDS">MDS</option>
                                <option value="CML">CML</option>
                                <option value="GDML">GDML</option>
                            </select>
                        </div>
                        <div class="mb-2">
                            <label class="form-label" for="accession"><fmt:message key="oscarMDS.createLab.accession"/></label>
                            <input type="text" class="form-control" name="accession" id="accession"/>
                        </div>
                        <div class="mb-2">
                            <label class="form-label" for="lab_req_date"><span class="text-danger" aria-hidden="true">*</span> <fmt:message key="oscarMDS.createLab.labReqDate"/></label>
                            <div class="input-group has-validation">
                                <input type="text" class="form-control" name="lab_req_date" id="lab_req_date" required>
                                <img src="<carlos:encode value='<%= request.getContextPath() %>' context="htmlAttribute"/>/images/cal.gif" id="lab_req_date_cal" class="input-group-text" style="cursor:pointer;">
                                <div class="invalid-feedback"><fmt:message key="oscarMDS.createLab.validation.labReqDate"/></div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <%-- Ordering Provider --%>
            <div class="col-md-6">
                <div class="card h-100">
                    <div class="card-header fw-bold"><fmt:message key="oscarMDS.createLab.orderingProvider"/></div>
                    <div class="card-body">
                        <div class="mb-2">
                            <label class="form-label" for="billingNo"><fmt:message key="oscarMDS.createLab.billingNum"/></label>
                            <input type="text" class="form-control" name="billingNo" id="billingNo"/>
                        </div>
                        <div class="mb-2">
                            <label class="form-label" for="pLastname"><fmt:message key="oscarMDS.createLab.lastname"/></label>
                            <input type="text" class="form-control" name="pLastname" id="pLastname"/>
                        </div>
                        <div class="mb-2">
                            <label class="form-label" for="pFirstname"><fmt:message key="oscarMDS.createLab.firstname"/></label>
                            <input type="text" class="form-control" name="pFirstname" id="pFirstname"/>
                        </div>
                        <div class="mb-2">
                            <label class="form-label" for="cc"><fmt:message key="oscarMDS.createLab.cc"/></label>
                            <input type="text" class="form-control" name="cc" id="cc"/>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <%-- Patient Information --%>
        <div class="card mb-3">
            <div class="card-header fw-bold"><fmt:message key="oscarMDS.createLab.patientInformation"/></div>
            <div class="card-body">
                <div class="row mb-2">
                    <div class="col-md-4">
                        <label class="form-label" for="lastname"><span class="text-danger" aria-hidden="true">*</span> <fmt:message key="oscarMDS.createLab.lastname"/></label>
                        <input type="text" class="form-control" name="lastname" id="lastname" required>
                        <div class="invalid-feedback"><fmt:message key="oscarMDS.createLab.validation.lastname"/></div>
                    </div>
                    <div class="col-md-4">
                        <label class="form-label" for="firstname"><fmt:message key="oscarMDS.createLab.firstname"/></label>
                        <input type="text" class="form-control" name="firstname" id="firstname"/>
                    </div>
                    <div class="col-md-4">
                        <label class="form-label" for="sex"><fmt:message key="oscarMDS.createLab.sex"/></label>
                        <select name="sex" id="sex" class="form-select">
                            <option value="M"><fmt:message key="oscarMDS.createLab.male"/></option>
                            <option value="F"><fmt:message key="oscarMDS.createLab.female"/></option>
                        </select>
                    </div>
                </div>
                <div class="row">
                    <div class="col-md-4">
                        <label class="form-label" for="dob"><span class="text-danger" aria-hidden="true">*</span> <fmt:message key="oscarMDS.createLab.dob"/></label>
                        <div class="input-group has-validation">
                            <input type="text" class="form-control" required name="dob" id="dob"/>
                            <img src="<carlos:encode value='<%= request.getContextPath() %>' context="htmlAttribute"/>/images/cal.gif" id="dob_cal" class="input-group-text" style="cursor:pointer;">
                            <div class="invalid-feedback"><fmt:message key="oscarMDS.createLab.validation.dob"/></div>
                        </div>
                    </div>
                    <div class="col-md-4">
                        <label class="form-label" for="hin"><fmt:message key="oscarMDS.createLab.hin"/></label>
                        <input type="text" class="form-control" name="hin" id="hin"/>
                    </div>
                    <div class="col-md-4">
                        <label class="form-label" for="phone"><fmt:message key="oscarMDS.createLab.phone"/></label>
                        <input type="text" class="form-control" name="phone" id="phone"/>
                    </div>
                </div>
            </div>
        </div>

        <%-- Tests --%>
        <div class="card mb-3">
            <div class="card-header fw-bold"><fmt:message key="oscarMDS.createLab.tests"/></div>
            <div class="card-body">
                <div id="test_container"></div>
                <input type="hidden" id="test_num" name="test_num" value="0"/>
                <input type="hidden" id="next_test_id" value="0"/>
                <a href="#" onclick="addTest(); setUpValidation(); return false;" class="btn btn-success btn-sm mt-2">
                    <fmt:message key="oscarMDS.createLab.addTest"/>
                </a>
            </div>
        </div>

        <button type="submit" class="btn btn-primary" disabled>
            <fmt:message key="oscarMDS.createLab.submitEMR"/>
        </button>
    </form>
</div>

<script>
    Calendar.setup({
        inputField: "lab_req_date",
        ifFormat: "%Y-%m-%d %H:%M",
        showsTime: true,
        button: "lab_req_date_cal"
    });
    Calendar.setup({
        inputField: "dob",
        ifFormat: "%Y-%m-%d",
        showsTime: false,
        button: "dob_cal"
    });
</script>
</body>
</html>
