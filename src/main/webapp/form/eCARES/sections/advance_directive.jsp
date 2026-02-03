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
<div class="flex items-center">
    <div style="width: 200px;">
        <svg height="10" width="10">
            <circle cx="5" cy="5" r="4" stroke="black" stroke-width="1" fill="white"/>
        </svg>
        <input type="checkbox" class="action-required" name="action_required_advance_directive_in_place" data-score="1">
        <strong class="tt highlight" data-toggle="tooltip" data-field-name="advance_directive_in_place"
                title="Advance directive in place">
            Advance directive in place
        </strong>
    </div>

    <label class="flex items-center font-normal pl-4">
        <input type="radio" name="advance_directive_in_place" value="1" class="m-0" autocomplete="off">
        <span class="pl-1">Y</span>
    </label>

    <label class="flex items-center font-normal pl-6">
        <input type="radio" name="advance_directive_in_place" value="0" class="m-0" autocomplete="off">
        <span class="pl-1">N</span>
    </label>

    <span class="tt font-bold pl-8" data-toggle="tooltip" title="Code Status" style="width:210px;">
        <svg height="10" width="10">
            <circle cx="5" cy="5" r="4" stroke="black" stroke-width="1" fill="white"/>
        </svg><input type="checkbox" class="action-required" name="action_required_code_status" data-score="1">
        <strong>Code Status</strong>
    </span>

    <label class="flex items-center font-normal pl-6">
        <input type="radio" name="code_status" value="dnr" class="m-0" autocomplete="off">
        <span class="pl-1">Do not resuscitate</span>
    </label>
    <label class="flex items-center font-normal pl-6"><input type="radio" name="code_status" value="r" class="m-0"
                                                             autocomplete="off">
        <span class="pl-1">Resuscitate</span>
    </label>

</div>