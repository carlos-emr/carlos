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
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>

<script>
    /**
     * Updates the vacancy status display for the selected vacancy.
     * Uses textContent to safely insert the vacancy name without XSS risk.
     *
     * @param {HTMLSelectElement} selectBox - The select element containing vacancy options.
     */
    function updateVacancyStatus(selectBox) {
        var selectedOption = selectBox.options[selectBox.selectedIndex];
        var vacancyName = selectedOption.textContent || selectedOption.innerText;
        var displayEl = document.getElementById('selectedVacancyName');
        if (displayEl) {
            // Use textContent to avoid DOM-based XSS — never use innerHTML here
            displayEl.textContent = vacancyName;
        }
    }

    /**
     * Submits the form to add a new vacancy using the provided template ID.
     * Reads the template value from the select box safely.
     *
     * @param {HTMLSelectElement} selectBox - The select element with vacancy template options.
     */
    function addVacancy(selectBox) {
        var templateId = selectBox.options[selectBox.selectedIndex].value;
        var form = document.programManagerViewForm;
        if (form) {
            form.elements['vacancyOrTemplateId'].value = templateId;
            form.elements['method'].value = 'add_vacancy';
            form.submit();
        }
    }
</script>

<div class="tabs" id="tabs">
    <table cellpadding="3" cellspacing="0" border="0">
        <tr>
            <th title="Vacancies">Vacancies</th>
        </tr>
    </table>
</div>

<c:if test="${not empty vacancies}">
    <table class="simple" cellspacing="2" cellpadding="3" border="0">
        <thead>
            <tr>
                <th>Name</th>
                <th>Status</th>
                <th>Date Created</th>
                <th>Date Closed</th>
                <th>Reason Closed</th>
            </tr>
        </thead>
        <tbody>
            <c:forEach var="vacancy" items="${vacancies}">
                <tr>
                    <td>${e:forHtml(vacancy.name)}</td>
                    <td>${e:forHtml(vacancy.status)}</td>
                    <td>${e:forHtml(vacancy.dateCreated)}</td>
                    <td>${e:forHtml(vacancy.dateClosed)}</td>
                    <td>${e:forHtml(vacancy.reasonClosed)}</td>
                </tr>
            </c:forEach>
        </tbody>
    </table>
</c:if>
<c:if test="${empty vacancies}">
    <p>No vacancies found for this program.</p>
</c:if>

<span id="selectedVacancyName"></span>
