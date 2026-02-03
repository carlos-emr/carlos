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
        <input type="checkbox" class="action-required" name="action_required_control_of_life_events" data-score="1">
        <strong class="tt highlight" data-toggle="tooltip" data-field-name="control_of_life_events"
                title="Reports positive ability to manage their life">
            Control of Life Events</strong>
    </div>

    <label class="flex items-center font-normal pl-4">
        <input type="radio" name="control_of_life_events" value="0" class="m-0" autocomplete="off" data-score="0">
        <span class="pl-1">Y</span>
    </label>
    <label class="flex items-center font-normal pl-6">
        <input type="radio" name="control_of_life_events" value="1" class="m-0" autocomplete="off" data-score="1">
        <span class="pl-1">N</span>
    </label>

    <span class="tt inline-block pl-8 font-bold" data-toggle="tooltip"
          title="No recent change in ability to conduct usual activities" style="width: 210px;">
        <svg height="10" width="10">
            <circle cx="5" cy="5" r="4" stroke="black" stroke-width="1" fill="white"/>
        </svg><input type="checkbox" class="action-required" name="action_required_usual_activities" data-score="1">
        <strong class="highlight" data-field-name="usual_activities">Usual Activities</strong></span>

    <label class="flex items-center font-normal pl-6">
        <input type="radio" name="usual_activities" value="no_problem" class="m-0" autocomplete="off" data-score="0">
        <span class="pl-1" rel="usual_activities_no" data-names="problem">
            No Problem
        </span>
    </label>

    <label class="flex items-center font-normal pl-4">
        <input type="radio" name="usual_activities" value="some_problem" class="m-0" autocomplete="off"
               data-score="0.5">
        <span class="pl-1">Some Problem</span>
    </label>

    <label class="flex items-center font-normal pl-4">
        <input type="radio" name="usual_activities" value="unable" class="m-0" autocomplete="off" data-score="1">
        <span class="pl-1">Unable</span>
    </label>

</div>