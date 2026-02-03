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
        <input type="checkbox" class="action-required" name="action_required_nutrition" data-score="1">
        <strong>Nutrition</strong>
    </div>
    <div class="flex w-full border-left-gray">


        <div class="flex-1 flex items-center">
            <strong class="mandatory font-bold pl-1 w-10" data-names="good,under,over,obese">
                <span class="highlight" data-field-name="weight">Weight</span>
            </strong>

            <label class="font-normal flex items-center"><input type="radio" name="weight" class="m-0"
                                                                autocomplete="off" value="good"
                                                                data-score="0">
                <span class="pl-1">Good</span></label>

            <label class="font-normal flex items-center pl-2"><input type="radio" name="weight" class="m-0"
                                                                     autocomplete="off" value="under"
                                                                     data-score="0.5">
                <span class="pl-1">Under</span></label>

            <label class="font-normal flex items-center pl-1"><input type="radio" name="weight" class="m-0"
                                                                     autocomplete="off" value="over"
                                                                     data-score="0.5">
                <span class="pl-1">Over</span></label>

            <label class="font-normal flex items-center pl-1"><input type="radio" name="weight" class="m-0"
                                                                     autocomplete="off" value="obese"
                                                                     data-score="1">
                <span class="pl-1">Obese</span></label>
        </div>
        <div class="flex-1 flex">

            <strong class="mandatory control-label font-bold w-9" data-names="wnl,fair,poor">
                <span class="highlight" data-field-name="appetite">Appetite</span></strong>

            <label class="font-normal flex items-center"><input type="radio" name="appetite" class="m-0"
                                                                autocomplete="off" value="wnl"
                                                                data-score="0">
                <span class="pl-1">WNL</span></label>

            <label class="font-normal flex items-center pl-4"><input type="radio" name="appetite" class="m-0"
                                                                     autocomplete="off" value="fair"
                                                                     data-score="0.5">
                <span class="pl-1">FAIR</span></label>

            <label class="font-normal flex items-center pl-4"><input type="radio" name="appetite" class="m-0"
                                                                     autocomplete="off" value="poor"
                                                                     data-score="1">
                <span class="pl-1">POOR</span></label>

        </div>
    </div>
</div>