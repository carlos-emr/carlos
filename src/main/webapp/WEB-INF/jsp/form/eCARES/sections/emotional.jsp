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
<div class="flex emotional-row w-full items-center" style="padding-top: 2px;">
    <div>
        <svg height="10" width="10">
            <circle cx="5" cy="5" r="4" stroke="black" stroke-width="1" fill="white"/>
        </svg>
        <input type="checkbox" class="action-required" name="action_required_emotional" data-score="1">
        <strong class="highlight" data-field-name="emotional">Emotional</strong>
    </div>

    <div class="mandatory tt font-bold ml-4 highlight" data-field-name="low_mood" data-bs-toggle="tooltip" title="Low Mood"
         rel="low_mood" data-names="y,n">
        <font size="4" style="line-height:10px;">&#8595;</font>
        Mood
    </div>
    <div class="pl-1 flex">
        <label class="flex items-center font-normal pl-1">
            <input type="radio" name="low_mood" value="1" rel="low_mood" data-score="1" class="m-0" autocomplete="off">
            <span class="pl-1">Y</span>
        </label>
        <label class="flex items-center font-normal pl-1">
            <input type="radio" name="low_mood" value="0" rel="low_mood" data-score="0" class="m-0" autocomplete="off">
            <span class="pl-1">N</span>
        </label>
    </div>


    <div class="tt mandatory font-bold ml-2 highlight" data-bs-toggle="tooltip" data-field-name="depression"
         title="Clinical diagnosis of depression, not low mood" rel="depression" data-names="y,n">
        Depression
    </div>


    <label class="flex items-center font-normal pl-1">
        <input type="radio" name="depression" value="1" class="m-0" autocomplete="off" data-score="1">
        <span class="pl-1">Y</span>
    </label>
    <label class="flex items-center font-normal ml-1">
        <input type="radio" name="depression" value="0" class="m-0" autocomplete="off" data-score="0">
        <span class="pl-1">N</span>
    </label>

    <div class="tt mandatory font-bold ml-2 highlight" data-field-name="anxiety" data-bs-toggle="tooltip"
         title="Clinical diagnosis of anxiety disorder" data-names="y,n">Anxiety
    </div>
    <div class="pl-1 flex">
        <label class="flex items-center font-normal">
            <input type="radio" name="anxiety" value="1" class="m-0" autocomplete="off" data-score="1">
            <span class="pl-1">Y</span>
        </label>
        <label class="flex items-center font-normal ml-1">
            <input type="radio" name="anxiety" value="0" class="m-0" autocomplete="off" data-score="0">
            <span class="pl-1">N</span>
        </label>
    </div>
    <div class="mandatory ml-2 font-bold highlight" data-field-name="fatigue" data-names="y,n">Fatigue</div>
    <div class="pl-1 flex items-center">
        <label class="font-normal flex items-center">
            <input type="radio" name="fatigue" value="1" class="m-0" autocomplete="off" data-score="1">
            <span class="pl-1">Y</span>
        </label>
        <label class="font-normal ml-1 flex items-center">
            <input type="radio" name="fatigue" value="0" class="m-0" autocomplete="off" data-score="0">
            <span class="pl-1">N</span>
        </label>
    </div>
    <div class="tt mandatory ml-2 font-bold highlight" data-bs-toggle="tooltip" data-field-name="hallucination"
         title="Visual or Auditory" rel="hallucination"
         data-names="y,n">Hallucination
    </div>
    <div class="pl-1 flex items-center">
        <label class="font-normal flex items-center">
            <input type="radio" name="hallucination" value="1" class="m-0" autocomplete="off" data-score="1">
            <span class="pl-1">Y</span>
        </label>
        <label class="font-normal ml-1 flex items-center">
            <input type="radio" name="hallucination" value="0" class="m-0" autocomplete="off" data-score="0">
            <span class="pl-1">N</span>
        </label>
    </div>
    <div class="tt mandatory ml-2 font-bold highlight" data-field-name="delusion" data-bs-toggle="tooltip"
         title="Visual or Auditory" rel="delusion"
         data-names="y,n">Delusion
    </div>
    <div class="pl-1 flex items-center">
        <label class="font-normal flex items-center">
            <input type="radio" name="delusion" value="1" class="m-0" autocomplete="off" data-score="1">
            <span class="pl-1">Y</span>
        </label>
        <label class="font-normal ml-1 flex items-center">
            <input type="radio" name="delusion" value="0" class="m-0" autocomplete="off" data-score="0">
            <span class="pl-1">N</span>
        </label>
    </div>
    <div class="mandatory ml-2 font-bold highlight" rel="emotional_other" data-field-name="other" data-names="y,n">Other
    </div>
    <div class="pl-1 flex items-center">
        <label class="font-normal flex items-center">
            <input type="radio" name="other" value="1" class="m-0" autocomplete="off" data-score="1">
            <span class="pl-1">Y</span>
        </label>
        <label class="font-normal ml-1 flex items-center">
            <input type="radio" name="other" value="0" class="m-0" autocomplete="off" data-score="0">
            <span class="pl-1">N</span>
        </label>
    </div>
</div>