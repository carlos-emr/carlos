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

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<ul class="m-0 p-0 patient-info">
    <li class="flex justify-center items-center" style="min-width: 600px;">
        <div>
            <span>Chart #:</span>
            <span class="font-bold" data-field-name="demographicNo">
                <c:out value="${ param.demographicNo }"/>
            </span>
        </div>
        <div class="ml-2">
            <span>Patient Name:</span>
            <span class="font-bold" data-field-name="patientFirstName"></span>
        </div>
        <div class="ml-2">
            <span class="font-bold" data-field-name="patientLastName"></span>
        </div>
        <div class="ml-2">
            <span>PHN:</span>
            <span class="font-bold" data-field-name="patientPHN"></span>
        </div>
        <div class="ml-2">
            <span>Gender:</span>
            <span class="font-bold" data-field-name="patientGender"></span>
        </div>
        <div class="ml-2">
            <span>Birthdate:</span>
            <span class="font-bold" data-field-name="patientDOB"></span>
        </div>
        <div class="ml-2">
            <span>Age:</span>
            <span class="font-bold" data-field-name="patientAge"></span>
        </div>
    </li>
</ul>