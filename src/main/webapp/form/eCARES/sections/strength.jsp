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
    <div style="width: 80px;">
        <svg height="10" width="10">
            <circle cx="5" cy="5" r="4" stroke="black" stroke-width="1" fill="white"/>
        </svg>
        <input type="checkbox" class="action-required" name="action_required_strength" data-score="1">
        <strong class="tt highlight" data-field-name="strength" data-toggle="tooltip"
                title="Especially Hip Flexors">Strength</strong>
    </div>

    <label class="flex items-center font-normal pl-4">
        <input type="radio" name="strength" class="m-0" autocomplete="off" data-score="0" value="wnl">
        <span class="pl-1" rel="strength" data-names="wnl">WNL</span>
    </label>

    <label class="flex items-center font-normal pl-4">
        <input type="radio" name="strength" class="m-0" autocomplete="off" data-score="1" value="weak">
        <span class="pl-1">Weak</span>
    </label>

    <span class="inline-block font-bold ml-6 highlight" data-field-name="upper_proximal">Upper Proximal:</span>
    <label class="flex items-center font-normal ml-2"><input type="radio" name="upper_proximal" class="m-0"
                                                             autocomplete="off" value="proximal">
        <span class="pl-1" rel="upper" data-names="proximal">Y</span></label>
    <label class="flex items-center font-normal ml-2"><input type="radio" name="upper_proximal" class="m-0"
                                                             autocomplete="off" value="distal">
        <span class="pl-1" rel="upper" data-names="distal">N</span></label>

    <span class="inline-block font-bold  ml-3 highlight w-9" data-field-name="upper_distal">Distal:</span>
    <label class="flex items-center font-normal" style="margin-left: 2px;"><input type="radio" name="upper_distal"
                                                                                  class="m-0" autocomplete="off"
                                                                                  value="proximal">
        <span class="pl-1" rel="upper" data-names="proximal">Y</span></label>
    <label class="flex items-center font-normal ml-4"><input type="radio" name="upper_distal" class="m-0"
                                                             autocomplete="off"
                                                             value="distal">
        <span class="pl-1" rel="upper" data-names="distal">N</span></label>

    <span class="inline-block ml-2 highlight font-bold" data-field-name="lower_proximal">Lower Proximal:</span>
    <label class="flex items-center font-normal ml-2"><input type="radio" name="lower_proximal" class="m-0"
                                                             data-score="1"
                                                             autocomplete="off" value="1">
        <span class="pl-1" rel="upper" data-names="proximal">Y</span></label>
    <label class="flex items-center font-normal ml-2"><input type="radio" name="lower_proximal" class="m-0"
                                                             data-score="0"
                                                             autocomplete="off" value="0">
        <span class="pl-1" rel="upper" data-names="distal">N</span></label>

    <span class="inline-block font-bold  font-bold ml-2 highlight" data-field-name="lower_distal">Distal:</span>
    <label class="flex items-center font-normal ml-2"><input type="radio" name="lower_distal" class="m-0"
                                                             autocomplete="off"
                                                             value="proximal">
        <span class="pl-1" rel="upper" data-names="proximal">Y</span></label>
    <label class="flex items-center font-normal ml-2"><input type="radio" name="lower_distal" class="m-0"
                                                             autocomplete="off"
                                                             value="distal">
        <span class="pl-1" rel="upper" data-names="distal">N</span></label>


</div>