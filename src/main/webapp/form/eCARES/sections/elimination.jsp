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
    <div class="mw-9">
        <svg height="10" width="10">
            <circle cx="5" cy="5" r="4" stroke="black" stroke-width="1" fill="white"/>
        </svg>
        <input type="checkbox" class="action-required" name="action_required_elimination" data-score="1">
        <strong>Elimination</strong>
    </div>

    <div class="flex w-full items-center border-left-gray">
        <div class="flex-1 flex items-center">
        <span class="mandatory font-bold pl-1 w-10" data-names="cont,incont">
            <span class="highlight flex items-center" data-field-name="bowel">Bowel</span></span>

            <label class="font-normal flex items-center"><input type="radio" name="bowel" class="m-0" autocomplete="off"
                                                                data-score="0" value="cont">
                <span class="pl-1">CONT</span></label>

            <label class="font-normal flex items-center ml-1"><input type="radio" name="bowel" class="m-0"
                                                                     autocomplete="off" data-score="1" value="incont">
                <span class="pl-1">INCONT</span></label>

            <span class="mandatory tt font-bold ml-2 highlight flex items-center" data-field-name="constipation"
                  data-names="y,n" data-toggle="tooltip"
                  title="Constipated">
            Constip</span>

            <label class="font-normal pl-1 flex items-center"><input type="radio" name="constipation" class="m-0"
                                                                     autocomplete="off" value="1"
                                                                     data-score="1">
                <span class="pl-1">Y</span></label>
            <label class="font-normal ml-1 flex items-center"><input type="radio" name="constipation" class="m-0"
                                                                     autocomplete="off" value="0"
                                                                     data-score="0">
                <span class="pl-1">N</span></label>
        </div>
        <div class="flex-1 flex items-center">
        <span class="mandatory font-bold w-9" data-names="cont,incont">
            <span class="highlight" data-field-name="bladder">Bladder</span></span>

            <label class="font-normal flex items-center"><input type="radio" name="bladder" class="m-0"
                                                                autocomplete="off" data-score="0" value="cont">
                <span class="pl-1">CONT</span></label>

            <label class="font-normal flex items-center ml-1"><input type="radio" name="bladder" class="m-0"
                                                                     autocomplete="off" data-score="1" value="incont">
                <span class="pl-1">INCONT</span></label>
            <span class="mandatory font-bold ml-2 flex items-center" rel="catheter" data-names="y,n">
            <span class="highlight" data-field-name="catheter">Catheter</span></span>

            <label class="font-normal pl-1 flex items-center"><input type="radio" name="catheter" class="m-0"
                                                                     autocomplete="off" value="1"
                                                                     data-score="1">
                <span class="pl-1">Y</span></label>
            <label class="font-normal ml-1 flex items-center"><input type="radio" name="catheter" class="m-0"
                                                                     autocomplete="off" data-score="0" value="0">
                <span class="pl-1">N</span></label>
        </div>
    </div>
</div>