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
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<script class="include" type="text/javascript"
        src="${pageContext.request.contextPath}/library/flatpickr/flatpickr.min.js"></script>
<link rel="stylesheet" href="${pageContext.request.contextPath}/library/flatpickr/flatpickr.min.css">

<script type="text/javascript">

    //--> Date picker
    document.addEventListener('DOMContentLoaded', function () {
        flatpickr('.date-picker', {
            dateFormat: 'Y-m-d',
            allowInput: true,
            minDate: 'today'
        });

// --> Time picker
        flatpickr('.time-picker', {
            enableTime: true,
            noCalendar: true,
            dateFormat: 'h:i K',
            allowInput: true
        });

        // --> Message pre-select list action
        document.querySelectorAll('.select-tickler-message').forEach(function (el) {
            el.addEventListener('click', function () {
                document.getElementById('message').value = this.textContent;
            });
        });
    });

</script>
<form name="ticklerAddForm" id="ticklerAddForm">
    <input type="hidden" name="demographicNo" value="${ param.demographicNo }"/>
    <div class="row">
        <div class="col-12">
            <div class="mb-3">
                <label>Action:</label>
                <select class="form-select required" name="categoryId" required="true">
                    <option value="" selected></option>
                    <c:forEach items="${ ticklerCategories }" var="ticklerCategory">
                        <option title="${ ticklerCategory.description }" value="${ ticklerCategory.id }">
                            <c:out value="${ ticklerCategory.category }"/>
                        </option>
                    </c:forEach>
                </select>
            </div>
        </div>
    </div>

    <div class="row">
        <div class="col-6">
            <div class="mb-3">
                <label>Assign to:</label>
                <select class="form-select required" name="taskAssignedTo" required="true">
                    <option value=""></option>
                    <c:forEach items="${ providers }" var="provider">
                        <option value="${ provider.providerNo }">
                            <c:out value="${ provider.formattedName }"/>
                        </option>
                    </c:forEach>
                </select>
            </div>
        </div>

        <div class="col-6">
            <div class="mb-3">
                <label>Priority:</label>
                <select class="form-select required" name="priority" required="true">
                    <option value="Low">Low</option>
                    <option value="Normal" selected>Normal</option>
                    <option value="High">High</option>
                </select>
            </div>
        </div>
    </div>

    <div class="row">
        <div class="col-6">
            <div class="mb-3">
                <label for="datePickerServiceDate" class="form-label">
                    Service Date:
                </label>
                <div>
                    <div class="input-group">
                        <input name="serviceDate" id="datePickerServiceDate" type="text"
                               class="date-picker form-control required" required="true"/>
                        <label for="datePickerServiceDate" class="input-group-text">
                            <span class="fa-solid fa-calendar"></span>
                        </label>
                    </div>
                </div>
            </div>
        </div>

        <div class="col-6">
            <div class="mb-3">
                <label for="ticklerTime" class="form-label"> Time:</label>
                <div>
                    <div class="input-group">
                        <input type="text" name="serviceTime" id="ticklerTime" class="time-picker form-control required"
                               required="true"/>
                        <label for="ticklerTime" class="input-group-text btn">
                            <span class="fa-solid fa-clock"></span>
                        </label>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <div class="row">
        <div class="col-12">
            <div class="mb-3">
                <label>Message:</label>
                <div class="input-group">
                    <input type="text" id="message" name="message" class="form-control" aria-label="..." required/>
                    <div class="input-group">
                        <button type="button" class="btn btn-secondary dropdown-toggle" data-bs-toggle="dropdown"
                                aria-haspopup="true" aria-expanded="false">

                        </button>
                        <ul class="dropdown-menu" style="height:300px;overflow-y:scroll;">
                            <c:forEach items="${ textSuggestions }" var="textSuggestion">
                                <li>
                                    <a class="dropdown-item select-tickler-message" href="#"><c:out
                                            value="${ textSuggestion.suggestedText }"/></a>
                                </li>
                            </c:forEach>
                        </ul>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <div class="row">
        <div class="col-12">
            <div class="mb-3">
                <label>Encounter Note:</label>
                <textarea name="comments" class="form-control" rows="6" placeholder="Additional comments."></textarea>
            </div>
        </div>
    </div>
</form>

