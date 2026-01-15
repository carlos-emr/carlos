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
<security:oscarSec roleName="<%=roleName$%>" objectName="_lab" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_lab");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
"http://www.w3.org/TR/html4/loose.dtd">
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>Lab Creator</title>
    <link rel="stylesheet" type="text/css" media="all" href="<%=request.getContextPath()%>/share/calendar/calendar.css"
          title="win2k-cold-1"/>
    <script type="text/javascript" src="<%=request.getContextPath()%>/share/calendar/calendar.js"></script>
    <script type="text/javascript"
            src="<%=request.getContextPath()%>/share/calendar/lang/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.javascript.calendar"/>"></script>
    <script type="text/javascript" src="<%=request.getContextPath()%>/share/calendar/calendar-setup.js"></script>

    <link href="${pageContext.request.contextPath}/css/bootstrap.css" rel="stylesheet"> <!-- Bootstrap 2.3.1 -->
    <link href="${pageContext.request.contextPath}/library/jquery/jquery-ui.theme-1.12.1.min.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/library/jquery/jquery-ui.structure-1.12.1.min.css" rel="stylesheet">

    <style>
        form[name="testForm"] fieldset table td {
            padding: 5px 10px;
        }

        form[name="testForm"] fieldset table td label {
            margin-right: 5px;
        }

        form[name="testForm"] .lab-test-table td label {
            display: block;
            margin-bottom: 3px;
        }
    </style>

    <!-- jquery -->
    <script src="<%=request.getContextPath() %>/library/jquery/jquery-3.6.4.min.js"></script>
    <script src="<%=request.getContextPath() %>/library/jquery/jquery-migrate-3.4.0.js"></script>
    <script src="<%=request.getContextPath() %>/library/jquery/jquery-ui-1.12.1.min.js"></script>

    <script>
        $(document).ready(function() {
            $( document ).tooltip();

            var url = "<%= request.getContextPath() %>/demographic/SearchDemographic.do?jqueryJSON=true&activeOnly=true";

            $("#lastname").autocomplete( {
                source: url,
                minLength: 2,

                focus: function( event, ui ) {
                    if (ui.item.formattedName) {
                        const myArray = ui.item.formattedName.split(",");
                        if (myArray.length > 1) {
                            $("#lastname").val( myArray[0].trim() );
                            $("#firstname").val( myArray[1].trim() );
                        }
                    }
                    return false;
                },
                select: function( event, ui ) {
                    const myArray = ui.item.formattedName.split(",");
                    if (myArray.length > 1) {
                        $("#lastname").val( myArray[0].trim() );
                        $("#firstname").val( myArray[1].trim() );
                    }

                    // Check for dedicated DOB field (note: backend has typo "fomattedDob")
                    let dob = null;
                    if (ui.item.fomattedDob) {
                        dob = ui.item.fomattedDob;
                    } else if (ui.item.formattedDob) {
                        dob = ui.item.formattedDob;
                    } else if (typeof ui.item.label === "string") {
                        // Extract YYYY-MM-DD pattern from label
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
            })
                    .autocomplete( "instance" )._renderItem = function( ul, item ) {
                return $( "<li>" )
                        .append( "<div><b>" + item.label + "</b>" + "<br>" + item.provider + "</div>" )
                        .appendTo( ul );
            };

            var url2 = "<%= request.getContextPath() %>/provider/SearchProvider.do?method=labSearch";

            $( "#pLastname" ).autocomplete({
                source: url2,
                minLength: 2,

                focus: function( event, ui ) {
                    const myArray = ui.item.label.split(",");
                    if (myArray.length > 1) {
                        $("#pLastname").val( myArray[0].trim() );
                        $("#pFirstname").val( myArray[1].trim() );
                    }
                    return false;
                },
                select: function(event, ui) {
                    const myArray = ui.item.label.split(",");
                    if (myArray.length > 1) {
                        $("#pLastname").val( myArray[0].trim() );
                        $("#pFirstname").val( myArray[1].trim() );
                    }

                    return false;
                }
            });

        });

        function addTest() {
            var total = jQuery("#test_num").val();
            total++;
            jQuery("#test_num").val(total);
            jQuery.ajax({url:'CreateLabTest.jsp?id='+total,async:false, success:function(data) {
                    jQuery("#test_container").append(data);
                    jQuery('form[name="testForm"] :submit').prop('disabled', false);
                }});
        }

        function deleteTest(id) {
            var testId = jQuery("input[name='test_"+id+".id']").val();
            // Create the hidden input element safely to prevent XSS
            var hiddenInput = jQuery("<input>").attr({
                type: "hidden",
                name: "test.delete",
                value: testId
            });
            jQuery("form[name='testForm']").append(hiddenInput);
            jQuery("#test_" + id).remove();
            var total = jQuery("#test_num").val();
            total--;
            jQuery("#test_num").val(total);
            if (total<1) {
                jQuery('form[name="testForm"] :submit').prop('disabled', true);
            }
        }

        function confirmSave() {
            var c = confirm("Are you sure you want to submit this lab to the system?");
            return c;
        }

    </script>

</head>
<body>
<div class="well">

    <form name="testForm" method="post" action="<%=request.getContextPath()%>/oscarMDS/SubmitLab.do?method=saveManage"
          onsubmit="return confirmSave();">

        <table width="100%">
            <tr>
                <td valign="top">
                    <fieldset>
                        <legend>Laboratory Information</legend>
                        <table>
                        <tr>
                            <td><label>Lab Name:</label></td>
                            <td>
                                <select name="labname" id="labname">
                                    <option value="MDS">MDS</option>
                                    <option value="CML">CML</option>
                                    <option value="GDML">GDML</option>
                                </select>
                            </td>
                        </tr>
                        <tr>
                            <td><label>Accession #</label></td>
                            <td><input type="text" name="accession" id="accession"/></td>
                        </tr>
                        <tr>
                            <td><label>Lab Req Date/Time:</label></td>
                            <td class="input-append"><input type="text" class="input-medium" name="lab_req_date" id="lab_req_date" required><img
                                    src="<%=request.getContextPath()%>/images/cal.gif" id="lab_req_date_cal" class="add-on"></td>
                        </tr>
                        </table>
                    </fieldset>
                </td>
                <td valign="top">

                    <fieldset>
                        <legend>Ordering Provider</legend>
                        <table>
                            <tr>
                                <td><label>Billing #</label></td>
                                <td><input type="text" name="billingNo" id="billingNo"/></td>
                            </tr>
                            <tr>
                                <td><label>Last Name</label></td>
                                <td><input type="text" name="pLastname" id="pLastname"/></td>
                            </tr>
                            <tr>
                                <td><label>First Name</label></td>
                                <td><input type="text" name="pFirstname" id="pFirstname"/></td>
                            </tr>
                            <tr>
                                <td><label>CC</label></td>
                                <td><input type="text" name="cc" id="cc" size="50"/></td>
                            </tr>
                        </table>
                    </fieldset>
                </td>
            </tr>
        </table>


        <fieldset>
            <legend>Patient Information</legend>
            <table>
                <tr>
                    <td><label>Last Name:</label><input type="text" name="lastname" id="lastname" required></td>
                    <td><label>First Name:</label><input type="text" name="firstname" id="firstname"/></td>
                    <td><label>Sex:</label><select name="sex" id="sex">
                        <option value="M">Male</option>
                        <option value="F">Female</option>
                    </select>
                    </td>
                </tr>
                <tr>
                    <td class="input-append"><label>DOB:</label><input type="text" class="input-medium" required name="dob" id="dob"/><img src="<%=request.getContextPath()%>/images/cal.gif"
                                                                     id="dob_cal" class="add-on"></td>
                    <td><label>HIN:</label><input type="text" name="hin" id="hin"/></td>
                    <td><label>Phone:</label><input type="text" name="phone" id="phone"/></td>
                </tr>
            </table>
        </fieldset>


        <b>Tests:</b>
        <br/>
        <div id="test_container"></div>
        <input type="hidden" id="test_num" name="test_num" value="0"/>
        <a href="#" onclick="addTest();" class="btn btn-success" style="width: 80px; margin-top: 10px;">Add Test</a>

        <br/><br/>
        <input type="submit" class="btn btn-primary" value="Submit to OSCAR" disabled>
    </form>
</div>


<script>
    Calendar.setup({
        inputField: "lab_req_date",
        ifFormat: "%Y-%m-%d %H:%M",
        showsTime: true,
        button: "lab_req_date_cal"
    });
    Calendar.setup({inputField: "dob", ifFormat: "%Y-%m-%d", showsTime: true, button: "dob_cal"});

</script>
</body>
</html>
