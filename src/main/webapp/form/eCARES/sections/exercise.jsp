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
    <div style='width: 80px;'>
        <svg height="10" width="10">
            <circle cx="5" cy="5" r="4" stroke="black" stroke-width="1" fill="white"/>
        </svg>
        <input type="checkbox" class="action-required" name="action_required_exercise" data-score="1">
        <strong class="tt highlight" data-bs-toggle="tooltip" data-field-name="exercise"
                title="Frequent being more than 150 minutes per week.">
            Exercise
        </strong>
    </div>

    <label class="flex items-center font-normal pl-4">
        <input type="radio" name="exercise" class="m-0" autocomplete="off" data-score="0" value="frequent">
        <span class="pl-1">Frequent</span>
    </label>


    <label class="flex items-center font-normal pl-4"><input type="radio" name="exercise" class="m-0"
                                                             autocomplete="off" data-score="0.5" value="occasional">
        <span class="pl-1">Occasional</span></label>

    <label class="flex items-center font-normal pl-4"><input type="radio" value="not" name="exercise" class="m-0"
                                                             autocomplete="off"
                                                             data-score="1">
        <span class="pl-1">Not</span></label>

    <span class="tt inline-block font-bold" data-bs-toggle="tooltip" title="Smoker" style="padding-left: 46px;">
        <svg height="10" width="10">
            <circle cx="5" cy="5" r="4" stroke="black" stroke-width="1" fill="white"/>
        </svg><input type="checkbox" class="action-required" name="action_required_smoke" data-score="1">
        <strong class="tt highlight" data-field-name="smoke">Smoker</strong>
    </span>

    <label class="flex items-center font-normal pl-4"><input type="radio" name="smoke" class="m-0" value="current"
                                                             autocomplete="off" data-score="1">
        <span class="pl-1">Current</span></label>

    <label class="flex items-center font-normal pl-4"><input type="radio" name="smoke" class="m-0" autocomplete="off"
                                                             value="ever" data-score="0.5">
        <span class="pl-1">Ever</span></label>

    <label class="flex items-center font-normal pl-4"><input type="radio" name="smoke" class="m-0" autocomplete="off"
                                                             value="never" data-score="0">
        <span class="pl-1">Never</span></label>
</div>