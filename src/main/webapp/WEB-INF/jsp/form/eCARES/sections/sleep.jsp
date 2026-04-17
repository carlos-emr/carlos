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
        <input type="checkbox" class="action-required" name="action_required_sleep" data-score="1">
        <strong class="highlight" data-field-name="sleep">Sleep</strong>
    </div>

    <label class="flex items-center font-normal pl-4">
        <input type="radio" name="sleep" value="wnl" class="m-0" autocomplete="off" data-score="0">
        <span class="pl-1">WNL</span>
    </label>
    <label class="flex items-center font-normal pl-4">
        <input type="radio" name="sleep" value="disrupted" class="m-0" autocomplete="off" data-score="1">
        <span class="pl-1">Disrupted</span>
    </label>

    <span class="mandatory font-bold ml-8 highlight tt" rel="daytime_drowsiness" data-names="y,n"
          data-bs-toggle="tooltip" title="Daytime Drowsiness" data-field-name="daytime_drowsiness">
        Daytime Drowsiness
    </span>
    <label class="flex items-center font-normal pl-2">
        <input type="radio" name="daytime_drowsiness" value="1" class="m-0" autocomplete="off" data-score="1">
        <span class="pl-1">Y</span>
    </label>
    <label class="flex items-center font-normal pl-2">
        <input type="radio" name="daytime_drowsiness" value="0" class="m-0" autocomplete="off" data-score="0">
        <span class="pl-1">N</span>
    </label>

    <span class="tt font-bold pl-8" data-bs-toggle="tooltip" data-bs-html="true"
          title="From the pain scale:<br>1-2: None to Low<br>2-5: Moderate Over<br>5: Extreme">
        <svg height="10" width="10">
            <circle cx="5" cy="5" r="4" stroke="black" stroke-width="1" fill="white"/>
        </svg><input type="checkbox" class="action-required" name="action_required_pain" data-score="1">
        <strong class="highlight" data-field-name="pain">Pain</strong>
    </span>

    <label class="flex items-center font-normal pl-4">
        <input type="radio" name="pain" value="none" class="m-0" autocomplete="off" data-score="0">
        <span class="pl-1">
            None
        </span>
    </label>

    <label class="flex items-center font-normal pl-4">
        <input type="radio" name="pain" value="moderate" class="m-0" autocomplete="off" data-score="0.5">
        <span class="pl-1">Moderate</span>
    </label>

    <label class="flex items-center font-normal pl-4">
        <input type="radio" name="pain" value="extreme" class="m-0" autocomplete="off" data-score="1">
        <span class="pl-1">Extreme</span>
    </label>

</div>