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
<div class="flex">
    <div class="mw-9">
        <svg height="10" width="10">
            <circle cx="5" cy="5" r="4" stroke="black" stroke-width="1" fill="white"/>
        </svg>
        <input type="checkbox" class="action-required" name="action_required_enough_income" data-score="1">
        <strong class="tt highlight" data-field-name="enough_income" data-bs-toggle="tooltip"
                title="Does the patient have enough income? Does the patient need SAFER rent grants, Fair Pharmacare, Disability tax credits?">
            Enough Income?
        </strong>
    </div>
    <div class="pl-4 flex items-center">
        <label class="font-normal flex items-center"><input type="radio" name="enough_income" class="m-0"
                                                            autocomplete="off" value="1" data-score="0">
            <span class="pl-1">Yes</span></label>

        <label class="font-normal flex items-center ml-2"><input type="radio" name="enough_income" class="m-0"
                                                                 autocomplete="off" value="0" data-score="1">
            <span class="pl-1">No</span></label>
    </div>
    <div class="flex items-center">
        <span class="tt form-check-inline form-label font-bold" data-bs-toggle="tooltip" data-bs-html="true"
              title="Frequent: more than weekly excursions outside of the home.<br><br>Occasional: less than weekly excursions outside of the home">
            <svg height="10" width="10">
                <circle cx="5" cy="5" r="4" stroke="black" stroke-width="1" fill="white"/>
            </svg><input type="checkbox" class="action-required" name="action_required_socially_engaged" data-score="1">
            <strong class="tt highlight" data-field-name="socially_engaged">Socially Engaged</strong>
        </span>

        <label class="font-normal flex items-center ml-2"><input type="radio" name="socially_engaged" class="m-0"
                                                                 value="frequent"
                                                                 autocomplete="off" data-score="0">
            <span class="pl-1">Frequent</span></label>

        <label class="font-normal flex items-center ml-2"><input type="radio" name="socially_engaged" class="m-0"
                                                                 value="occasional"
                                                                 autocomplete="off" data-score="0.5">
            <span class="pl-1">Occasional</span></label>

        <label class="font-normal flex items-center ml-2"><input type="radio" name="socially_engaged" class="m-0"
                                                                 autocomplete="off" value="not" data-score="1">
            <span class="pl-1">Not</span></label>
    </div>
</div>