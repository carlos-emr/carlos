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
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<fmt:setBundle basename="oscarResources"/>
<fmt:message key="tickler.ticklerAdd.additionalMessage" var="ticklerAdditionalMessage"/>
<script type="text/javascript">

    //--> Date picker
    document.addEventListener('DOMContentLoaded', function () {
        flatpickr('.date-picker', {
            dateFormat: 'Y-m-d',
            allowInput: true
        });

        // --> Time picker
        flatpickr('.time-picker', {
            enableTime: true,
            noCalendar: true,
            dateFormat: 'h:i K',
            allowInput: true
        });
    });

</script>
<form name="ticklerAddForm" id="ticklerAddForm"
      action="${ pageContext.request.contextPath }/web/dashboard/display/AssignTickler" method="POST" novalidate>
    <input type="hidden" value="saveTickler" name="method"/>
    <div class="row">
        <div class="col-12">
            <div class="mb-3">
                <div class="card card-body bg-body-tertiary" id="patientTicklerList">
						<span class="message">
							<fmt:message key="tickler.ticklerAdd.msgAssignTicklerForSelectedPatients"/>
						</span>
                    <span class="error" style="color:red;display:none;">
							<fmt:message key="tickler.ticklerAdd.msgAssignTicklerError"/>
						</span>
                    <input type="hidden" name="demographics" value="${ demographics }"/>
                </div>
            </div>
        </div>
    </div>

    <div class="row">
        <div class="col-12">
            <div class="mb-3">
                <label><fmt:message key="tickler.ticklerAdd.action"/></label>
                <select class="form-select required" name="ticklerCategoryId">
                    <c:forEach items="${ ticklerCategories }" var="ticklerCategory">
                        <option title="${ ticklerCategory.description }" value="${ ticklerCategory.id }">
                            ${e:forHtml(ticklerCategory.category)}
                        </option>
                    </c:forEach>
                </select>
            </div>
        </div>
    </div>

    <div class="row">
        <div class="col-6">
            <div class="mb-3">
                <label><fmt:message key="tickler.ticklerAdd.assignTaskTo"/></label>
                <select class="form-select required" name="taskAssignedTo">
                    <option value=""></option>
                    <c:forEach items="${ providers }" var="provider">
                        <option value="${ provider.providerNo }">
                            ${e:forHtml(provider.formattedName)}
                        </option>
                    </c:forEach>
                </select>
            </div>
        </div>

        <div class="col-6">
            <div class="mb-3">
                <label><fmt:message key="tickler.ticklerEdit.priority"/></label>

                <select class="form-select required" name="priority">
                    <option value=""></option>
                    <option value="Low"><fmt:message key="tickler.ticklerMain.priority.low"/></option>
                    <option value="Normal"><fmt:message key="tickler.ticklerMain.priority.normal"/></option>
                    <option value="High"><fmt:message key="tickler.ticklerMain.priority.high"/></option>
                </select>
            </div>
        </div>
    </div>

    <div class="row">
        <div class="col-6">

            <label for="datePickerServiceDate" class="form-label"><fmt:message key="tickler.ticklerEdit.serviceDate"/></label>
            <div>
                <div class="input-group">
                    <input name="serviceDate" id="datePickerServiceDate" type="text"
                           class="date-picker form-control required"/>
                    <label for="datePickerServiceDate" class="input-group-text btn">
                        <span class="fa-solid fa-calendar"></span>
                    </label>
                </div>
            </div>
        </div>

        <div class="col-6">
            <label for="ticklerTime" class="form-label"><fmt:message key="tickler.ticklerAdd.serviceTime"/></label>
            <div>
                <div class="input-group">
                    <input type="text" name="serviceTime" id="ticklerTime" class="time-picker form-control required"/>
                    <label for="ticklerTime" class="input-group-text btn">
                        <span class="fa-solid fa-clock"></span>
                    </label>
                </div>
            </div>
        </div>
    </div>

    <div class="row">
        <div class="col-12">
            <div class="mb-3">
                <label><fmt:message key="tickler.ticklerAdd.message"/></label>
                <select class="form-select" name="message">
                    <option value=""></option>
                    <c:forEach items="${ textSuggestions }" var="textSuggestion">
                        <option>
                            ${e:forHtml(textSuggestion.suggestedText)}
                        </option>
                    </c:forEach>
                </select>
            </div>
            <textarea name="messageAppend" class="form-control" rows="6" placeholder="${ticklerAdditionalMessage}"></textarea>
        </div>
    </div>

</form>
