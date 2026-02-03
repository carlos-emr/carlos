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
        <input type="checkbox" class="action-required" name="action_required_communication" data-score="1">
        <strong>Communication</strong>
    </div>

    <span class="mandatory tt ml-6 font-bold highlight" data-field-name="speech" data-names="wnl,impaired"
          data-toggle="tooltip" title="Ability to articulate and communicate verbally">
        Speech
    </span>

    <label class="flex items-center font-normal pl-4">
        <input type="radio" name="speech" value="wnl" class="m-0" autocomplete="off" data-score="0">
        <span class="pl-1">WNL</span>
    </label>

    <label class="flex items-center font-normal pl-4">
        <input type="radio" name="speech" value="impaired" class="m-0" autocomplete="off" data-score="1">
        <span class="pl-1">Impaired</span>
    </label>

    <span class="mandatory tt font-bold ml-6 highlight" rel="hearing" data-names="wnl,impaired"
          data-field-name="hearing" data-toggle="tooltip" title="Ability to hear with or without hearing aids">
        Hearing
    </span>

    <label class="flex items-center font-normal pl-4">
        <input type="radio" name="hearing" value="wnl" class="m-0" autocomplete="off" data-score="0">
        <span class="pl-1">WNL</span>
    </label>

    <label class="flex items-center font-normal pl-4">
        <input type="radio" name="hearing" value="impaired" class="m-0" autocomplete="off" data-score="1">
        <span class="pl-1">Impaired</span>
    </label>

    <span class="mandatory tt font-bold ml-6 highlight" rel="vision" data-names="wnl,impaired" data-field-name="vision"
          data-toggle="tooltip" title="Ability to see with or without corrective lenses">
        Vision
    </span>

    <label class="flex items-center font-normal pl-4">
        <input type="radio" name="vision" value="wnl" class="m-0" autocomplete="off" data-score="0">
        <span class="pl-1">WNL</span>
    </label>

    <label class="flex items-center font-normal pl-4">
        <input type="radio" name="vision" value="impaired" class="m-0" autocomplete="off" data-score="1">
        <span class="pl-1">Impaired</span>
    </label>

</div>