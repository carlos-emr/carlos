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
        <input type="checkbox" class="action-required" name="action_required_balance" data-score="1">
        <strong>Balance</strong>
    </div>
    <div class="flex w-full border-left-gray items-center">
        <div class="flex-1 flex items-center">
            <span class="font-bold pl-1 w-10 inline-block" data-names="wnl,impaired">
                <span class="highlight" data-field-name="balance">Balance</span>
            </span>

            <label class="flex items-center font-normal w-9"><input type="radio" name="balance" class="m-0"
                                                                    autocomplete="off"
                                                                    value="wnl" data-score="0">
                <span class="pl-1">WNL</span></label>

            <label class="flex items-center font-normal"><input type="radio" name="balance" class="m-0"
                                                                autocomplete="off"
                                                                value="impaired" data-score="1">
                <span class="pl-1">Impaired</span></label>

        </div>
        <div class="flex-1 flex items-center">
            <span class="tt font-bold w-9" data-names="y,n,number" data-toggle="tooltip"
                  title="Flag if there is any falls in last 6 months">
                <span class="highlight" data-field-name="falls">Falls</span>
            </span>

            <label class="font-normal flex items-center">
                <input type="radio" name="falls" class="m-0" autocomplete="off" data-score="1" value="1">
                <span class="pl-1">Y</span>
            </label>
            <label class="font-normal pl-4 flex items-center">
                <input type="radio" name="falls" class="m-0" autocomplete="off" data-score="0" value="0">
                <span class="pl-1">N</span>
            </label>
            <span class="tt ml-4 highlight" data-toggle="tooltip" data-field-name="falls_number"
                  title="Number of falls in last 6 months">
                Number
            </span>
            <input type="number" min="0" max="99" name="falls_number" id="falls_number"
                   class="bottom-line form-control ecares-input h-si w-si">
        </div>
    </div>

</div>