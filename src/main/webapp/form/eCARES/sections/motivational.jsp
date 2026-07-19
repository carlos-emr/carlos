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
    <div>
        <svg height="10" width="10">
            <circle cx="5" cy="5" r="4" stroke="black" stroke-width="1" fill="white"/>
        </svg>
        <input type="checkbox" class="action-required" name="action_required_motivation" data-score="1">
        <strong class="highlight" data-field-name="motivation">Motivation</strong>
    </div>

    <label class="font-normal pl-4 flex items-center">
        <input type="radio" name="motivation" value="high" class="m-0" autocomplete="off" data-score="0">
        <span class="pl-1" rel="motivation" data-names="high">
            High
        </span>
    </label>

    <label class="flex items-center font-normal pl-4">
        <input type="radio" name="motivation" value="usual" class="m-0" autocomplete="off" data-score="0.5">
        <span class="pl-1">Usual</span>
    </label>

    <label class="flex items-center font-normal pl-4">
        <input type="radio" name="motivation" value="low" class="m-0" autocomplete="off" data-score="1">
        <span class="pl-1">Low</span>
    </label>


    <span class="tt mandatory font-bold ml-4 highlight" data-toggle="tooltip"
          data-field-name="health_attitude"
          title="Engaged attitude and desire to participate in efforts to enhance their health" rel="health_attitude"
          data-names="excellent,good,fair,poor,couldnt_say">
        Health Attitude
    </span>

    <label class="font-normal pl-4 flex items-center">
        <input type="radio" name="health_attitude" value="excellent" class="m-0" autocomplete="off" data-score="0">
        <span class="pl-1">Excellent</span>
    </label>

    <label class="font-normal pl-4 flex items-center">
        <input type="radio" name="health_attitude" value="good" class="m-0" autocomplete="off" data-score="0.33">
        <span class="pl-1">Good</span>
    </label>

    <label class="font-normal pl-4 flex items-center">
        <input type="radio" name="health_attitude" value="fair" class="m-0" autocomplete="off" data-score="0.66">
        <span class="pl-1">Fair</span>
    </label>

    <label class="font-normal pl-4 flex items-center">
        <input type="radio" name="health_attitude" value="couldnt_say" class="m-0" autocomplete="off" data-score="1">
        <span class="pl-1">Poor</span>
    </label>

</div>