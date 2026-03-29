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
<%--
    dxResearchSelectAssociations.jsp - Manage diagnosis code associations

    Purpose:
    Administration page for managing associations between issue list codes and
    disease registry codes. Supports CSV import (replace or append), export,
    automatch (auto-generate registry entries from associations), and clearing
    all associations.

    The associations table is populated dynamically via AJAX from
    dxResearchLoadAssociations.do?method=getAllAssociations.

    Opened from dxResearchCustomization.jsp "Edit Associations" button.

    @since 2006-01-01 (original OSCAR implementation)
--%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<html>
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxCustomization.selectAssociations"/></title>

        <script type="text/javascript">

            function setfocus() {
                window.focus();
                window.resizeTo(850, 650);
            }

            /** Fetches all associations via JSON and renders them in the #associations table. */
            function populateListOfAssociations() {
                $("#associations tr").remove();

                $('#associations').append('<thead><tr><th colspan="3">Issue List</th><th colspan="3">Disease Registry</th></tr><tr><th>CodeType</th><th>Code</th><th>Description</th><th>CodeType</th><th>Code</th><th>Description</th></tr></thead><tbody></tbody>');
                $.getJSON("<%= request.getContextPath() %>/oscarResearch/oscarDxResearch/dxResearchLoadAssociations.do?method=getAllAssociations",
                    function (data, textStatus) {
                        for (var x = 0; x < data.length; x++) {
                            $('#associations tbody').append('<tr><td>' + data[x].codeType + '</td><td>' + data[x].code + '</td><td>' + data[x].description + '</td><td>' + data[x].dxCodeType + '</td><td>' + data[x].dxCode + '</td><td>' + data[x].dxDescription + '</td></tr>');
                        }
                    });
            }

            $(document).ready(function () {

                // Clear all associations after confirmation
                $("#clear_list").click(function () {
                    if (confirm('Are you sure you want to delete all associations?')) {
                        $.ajax({
                            type: "POST",
                            url: "<%= request.getContextPath() %>/oscarResearch/oscarDxResearch/dxResearchLoadAssociations.do",
                            data: { method: "clearAssociations" },
                            async: false,
                            success: function () {
                                populateListOfAssociations();
                            }
                        });
                    }
                });

                // Export associations as CSV download
                $("#export").click(function () {
                    window.open("<%= request.getContextPath() %>/oscarResearch/oscarDxResearch/dxResearchLoadAssociations.do?method=export");
                });

                // Auto-generate disease registry entries from associations
                $("#automatch").click(function () {
                    if (confirm('This function will remove and re-generate all entries in the disease registry where the entry was created by an association.\nWould you like to continue?')) {
                        $.post("<%= request.getContextPath() %>/oscarResearch/oscarDxResearch/dxResearchLoadAssociations.do",
                            { method: "autoPopulateAssociations" },
                            function (data, textStatus) {
                                alert("Automatch generated " + data.recordsAdded + " records.");
                            }, "json");
                    }
                });

                // Load existing associations on page ready
                populateListOfAssociations();
            });
        </script>
    </head>

    <body onload="setfocus()">
    <div class="container" style="padding-top:10px;">

        <%-- Page header matching search.jsp / report.jsp pattern --%>
        <div class="page-header-bar">
            <h4 class="page-header-title">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" class="page-header-icon" viewBox="0 0 16 16">
                    <path d="M1 11.5a.5.5 0 0 0 .7.5L8 8.9l6.3 3.1a.5.5 0 0 0 .7-.5V4.5a.5.5 0 0 0-.7-.5L8 7.1 1.7 4a.5.5 0 0 0-.7.5z"/>
                </svg>
                &nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxCustomization.selectAssociations"/>
            </h4>
        </div>

        <%-- Associations table — populated dynamically via AJAX --%>
        <table id="associations" class="table table-sm table-bordered table-striped" style="margin-top:10px;">
        </table>

        <%-- CSV upload form for importing associations --%>
        <div class="card" style="margin-top:15px;">
            <div class="card-body">
                <h5 class="card-title">Upload CSV file</h5>
                <form action="${pageContext.request.contextPath}/oscarResearch/oscarDxResearch/dxResearchLoadAssociations.do?method=uploadFile"
                      method="post" enctype="multipart/form-data">
                    <div class="mb-2">
                        <input type="file" class="form-control form-control-sm" name="file" id="file"/>
                    </div>
                    <div class="mb-2">
                        <div class="form-check form-check-inline">
                            <input class="form-check-input" type="radio" name="replace" value="true" id="replaceRadio"/>
                            <label class="form-check-label" for="replaceRadio">Replace</label>
                        </div>
                        <div class="form-check form-check-inline">
                            <input class="form-check-input" type="radio" name="replace" value="false" id="appendRadio"/>
                            <label class="form-check-label" for="appendRadio">Append</label>
                        </div>
                    </div>
                    <input type="submit" class="btn btn-primary btn-sm" name="submit" value="Submit"/>
                </form>
            </div>
        </div>

        <%-- Action buttons --%>
        <div style="margin-top:15px; display:flex; gap:8px; flex-wrap:wrap;">
            <input id="automatch" type="button" class="btn btn-primary" value="Automatch"/>
            <input id="clear_list" type="button" class="btn btn-danger" value="Clear Associations"/>
            <input id="export" type="button" class="btn btn-secondary" value="Export"/>
            <input id="close" type="button" class="btn btn-secondary" value="Close" onclick="window.close();"/>
        </div>

    </div>
    </body>
</html>
