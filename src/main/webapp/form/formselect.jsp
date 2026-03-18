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


<%@ page import="java.util.*,io.github.carlos_emr.carlos.report.pageUtil.*" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %> 
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>



<html>
    <head>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnSelectForm"/></title>
    </head>

    <body>
    <h3><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnSelectForm"/></h3>

    <div class="card card-body bg-body-tertiary">

        <form action="${pageContext.request.contextPath}/form/select.do" method="post" id="selectForm" name="selectForm">
            <input type="hidden" id="savedAddSelection" name="savedAddSelection" value="${param.savedAddSelection}" />
            <input type="hidden" id="savedDeleteSelection" name="savedDeleteSelection" value="${param.savedDeleteSelection}" />
            
            <table id="scrollNumber1" name="encounterTable">
                <tr>
                    <td class="MainTableLeftColumn"></td>
                    <td class="MainTableRightColumn">
                        <table border=0 cellspacing=4 width=400>
                            <tr>
                                <td>
                                    <table>
                                        <tr>
                                            <th align="left"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.form.msgAllAvailableForms"/></th>
                                            <th></th>
                                            <th align="left"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.form.msgSelectedForms"/></th>
                                        </tr>
                                        <td><select multiple="true" name="selectedAddTypes"
                                                         size="10" style="width:150">
                                            <c:forEach var="f" items="${formHiddenVector}">
                                                <c:set var="searchIn" value=",${param.savedAddSelection}," />
                                                <c:set var="searchFor" value=",${f.formName}," />
                                                <option value="${f.formName}"
                                                        <c:if test="${not empty param.savedAddSelection and (param.savedAddSelection eq f.formName or fn:contains(searchIn, searchFor))}">selected</c:if>>
                                                        ${f.formName}
                                                </option>
                                            </c:forEach>
                                        </select></td>
                                        <td>
                                            <table>
                                                <tr>
                                                    <td><input type="button" name="button" id="add" class="btn function"
                                                               style="width:80px"
                                                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.MeasurementsAction.addBtn"/> >>"
                                                    /></td>
                                                </tr>
                                                <tr>
                                                    <td>
                                                        <input type="button" name="button" class="btn function"
                                                               id="delete" style="width:80px"
                                                               value="<< <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.MeasurementsAction.deleteBtn"/>"/>
                                                    </td>
                                                </tr>
                                            </table>
                                        </td>
                                        <td><select multiple="true"
                                                         name="selectedDeleteTypes" size="10" style="width:150">
                                            <c:forEach var="f" items="${formShownVector}">
                                                <c:set var="searchIn" value=",${param.savedDeleteSelection}," />
                                                <c:set var="searchFor" value=",${f.formName}," />
                                                <option value="${f.formName}" 
                                                        <c:if test="${not empty param.savedDeleteSelection and (param.savedDeleteSelection eq f.formName or fn:contains(searchIn, searchFor))}">selected</c:if>>
                                                        ${f.formName}
                                                </option>
                                            </c:forEach>
                                        </select></td>
                            </tr>
                            <tr>

                            </tr>
                            <tr>
                                <td></td>
                                <td></td>
                            </tr>
                        </table>
                    </td>
                    <td>
                        <input type="button" name="button" class="btn function" value="Move Up" style="width:100px"
                               id="up"/> <br>
                        <input type="button" name="button" class="btn function" value="Move Down" style="width:100px"
                               id="down"/></td>
                </tr>
            </table>
            </td>
            </tr>
            </table>

            <input type="hidden" name="forward" id="forward" value="error"/>
        </form>
    </div>

    <script>
        registerFormSubmit('selectForm', 'dynamic-content');

        $(document).ready(function () {
            // Restore selections on page load
            var savedAdd = $("#savedAddSelection").val();
            var savedDelete = $("#savedDeleteSelection").val();
            
            if (savedAdd) {
                // Split comma-separated string back into array for multiple select
                var addArray = savedAdd.split(',').filter(function(item) { 
                    return item.trim() !== ''; 
                });
                $("select[name='selectedAddTypes']").val(addArray);
            }
            if (savedDelete) {
                // Split comma-separated string back into array for multiple select
                var deleteArray = savedDelete.split(',').filter(function(item) { 
                    return item.trim() !== ''; 
                });
                $("select[name='selectedDeleteTypes']").val(deleteArray);
            }

            $(".function").click(function () {
                // Save current selections to hidden fields
                var addSelections = $("select[name='selectedAddTypes']").val() || [];
                var deleteSelections = $("select[name='selectedDeleteTypes']").val() || [];

                $("#savedAddSelection").val(Array.isArray(addSelections) ? addSelections.join(',') : addSelections);
                $("#savedDeleteSelection").val(Array.isArray(deleteSelections) ? deleteSelections.join(',') : deleteSelections);

                $("#forward").val($(this).attr("id"));
                $("#selectForm").submit();
            });

        });
    </script>
    </body>
</html>
