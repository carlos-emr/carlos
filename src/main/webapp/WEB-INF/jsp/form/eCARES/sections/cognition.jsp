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
        <svg height="10" width="10" class="tt" data-bs-toggle="tooltip" data-bs-html="true" data-bs-container="body"
             title="CIND: Cognitive Impairment Non-Dementia<br>MCI: Mild Cognitive Impairment">
            <circle cx="5" cy="5" r="4" stroke="black" stroke-width="1" fill="white"/>
        </svg>
        <input type="checkbox" class="action-required" name="action_required_cognition" data-score="1">
        <strong class="tt highlight" data-bs-toggle="tooltip" data-bs-html="true" data-field-name="cognition"
                title="CIND: Cognitive Impairment Non-Dementia<br><br>MCI: Mild Cognitive Impairment">Cognition</strong>
    </div>
    <div class="pl-4">
        <label class="font-normal flex items-center">
            <input type="radio" name="cognition" value="wnl" class="Xbox m-0" autocomplete="off" data-score="0">
            <span class="ml-1">WNL</span>
        </label>
    </div>
    <div class="pl-2">
        <label class="font-normal flex items-center">
            <input type="radio" name="cognition" value="cind_mci" class="Xbox m-0" autocomplete="off" data-score="0.5">
            <span class="ml-1">CIND/MCI</span>
        </label>
    </div>
    <div class="pl-2">
        <label class="font-normal flex items-center">
            <input type="radio" name="cognition" value="dementia" class="Xbox m-0" autocomplete="off" data-score="1">
            <span class="ml-1">Dementia</span>
        </label>
    </div>
    <div class="pl-2 flex">
        <span class="mandatory font-bold ml-4 flex highlight" rel="delirium" data-names="y,n"
              data-field-name="delirium">Delirium</span>
        <label class="pl-2 font-normal flex items-center">
            <input type="radio" name="delirium" value="1" class="m-0 p-0" autocomplete="off" data-score="1">
            <span class="ml-1">Y</span>
        </label>
        <label class="pl-2 font-normal flex items-center">
            <input type="radio" name="delirium" value="0" class="m-0 p-0" autocomplete="off" data-score="0">
            <span class="ml-1">N</span>
        </label>
    </div>
    <div class="flex pl-4 items-center">
        <label for="moca" class="inline-block font-bold highlight" data-field-name="moca">MoCA:</label>
        <input type="number" min="0" max="30" name="moca" id="moca" class="ecares-input w-si h-si">
    </div>
    <div class="flex pl-2 items-center">
        <label for="mini-cog" class="inline-block font-bold highlight" data-field-name="mini-cog">Mini-Cog:</label>
        <input type="number" min="0" max="5" step="0.1" name="mini-cog" id="mini-cog" class="ecares-input w-si h-si">
    </div>
    <div class="flex pl-2 items-center">
        <label for="fast" class="inline-block font-bold highlight" data-field-name="fast">FAST:</label>
        <select id="fast" name="fast" class="ecares-input" style="width: 150px;">
            <option disabled selected></option>
            <option data-score="0" value="1">1 - No functional decline</option>
            <option data-score="0" value="2">2 - Personal awareness of some functional decline</option>
            <option data-score="0.5" value="3">3 - Noticeable deficits in demanding tasks</option>
            <option data-score="0.5" value="4">4 - Requires assistance in complicated tasks</option>
            <option data-score="1" value="5">5 - Requires assistance in simple tasks</option>
            <option data-score="1" value="6">6 - Requires assistance with daily living</option>
            <option data-score="1" value="7">7 -Dependency</option>
        </select>
    </div>
</div>