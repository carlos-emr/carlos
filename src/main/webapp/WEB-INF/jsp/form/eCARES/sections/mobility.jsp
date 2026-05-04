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
<div class="flex">
    <div class="mw-9">
        <svg height="10" width="10">
            <circle cx="5" cy="5" r="4" stroke="black" stroke-width="1" fill="white"/>
        </svg>
        <input type="checkbox" class="action-required" name="action_required_mobility" data-score="1">
        <strong>Mobility</strong>
    </div>

    <div class="flex w-full border-left-gray">
        <div class="flex-1">
            <div class="flex">
                <span class="mandatory tt font-bold inline-block pl-1 w-10" data-names="ind,asst,cant"
                      data-bs-toggle="tooltip" title="Walk Outside">
                    <span class="highlight" data-field-name="walk_outside">Walk Outside</span>
                </span>

                <label class="flex items-center font-normal"><input type="radio" name="walk_outside" class="m-0"
                                                                    autocomplete="off" value="ind" data-score="0">
                    <span class="pl-1">IND</span></label>

                <label class="font-normal pl-4 flex items-center"><input type="radio" name="walk_outside" class="m-0"
                                                                         value="asst" autocomplete="off"
                                                                         data-score="0.5">
                    <span class="pl-1">ASST</span></label>

                <label class="font-normal pl-4 flex items-center"><input type="radio" name="walk_outside" class="m-0"
                                                                         value="can't" autocomplete="off"
                                                                         data-score="1">
                    <span class="pl-1">Can't</span></label>
            </div>
            <div class="flex"><span class="mandatory tt font-bold pl-1 w-10" data-names="ind,stand_by,asst,dep"
                                    data-bs-toggle="tooltip" title="Transfers">
                    <span class="highlight" data-field-name="transfers">Transfers</span>
                </span>

                <label class="font-normal flex items-center"><input type="radio" name="transfers" class="m-0"
                                                                    autocomplete="off"
                                                                    value="ind" data-score="0">
                    <span class="pl-1">IND</span></label>

                <label class="font-normal pl-2 flex items-center"><input type="radio" name="transfers" class="m-0"
                                                                         value="stand by"
                                                                         autocomplete="off" data-score="0.33">
                    <span class="pl-1">Stand by</span></label>

                <label class="font-normal pl-2 flex items-center"><input type="radio" name="transfers" class="m-0"
                                                                         value="asst"
                                                                         autocomplete="off" data-score="0.66">
                    <span class="pl-1">ASST</span></label>

                <label class="font-normal pl-2 flex items-center"><input type="radio" name="transfers" class="m-0"
                                                                         autocomplete="off"
                                                                         value="dep" data-score="1">
                    <span class="pl-1">DEP</span></label>
            </div>
            <div class="flex">
                <span class="mandatory tt font-bold pl-1 w-10" data-names="none,case,walker,chair" data-bs-toggle="tooltip"
                      title="Walking Aid">
                    <span class="highlight" data-field-name="aid">Aid</span>
                </span>

                <label class="font-normal flex items-center"><input type="radio" name="aid" class="m-0"
                                                                    autocomplete="off" value="none"
                                                                    data-score="0">
                    <span class="pl-1">None</span></label>

                <label class="font-normal flex items-center pl-2"><input type="radio" name="aid" class="m-0"
                                                                         autocomplete="off"
                                                                         value="cane" data-score="0.33">
                    <span class="pl-1">Cane</span></label>

                <label class="font-normal flex items-center pl-2"><input type="radio" name="aid" class="m-0"
                                                                         autocomplete="off"
                                                                         value="walker" data-score="0.66">
                    <span class="pl-1">Walker</span></label>

                <label class="font-normal flex items-center pl-2"><input type="radio" name="aid" class="m-0"
                                                                         autocomplete="off"
                                                                         value="chair" data-score="1">
                    <span class="pl-1">Chair</span></label>
            </div>

        </div>
        <div class="flex-1">
            <div class="flex"> <span class="mandatory font-bold inline-block w-9" data-names="ind,slow,asst,dep">
                    <span class="highlight" data-field-name="walking">Walking</span>
                </span>

                <label class="font-normal flex items-center"><input type="radio" name="walking" class="m-0"
                                                                    autocomplete="off"
                                                                    value="ind" data-score="0">
                    <span class="pl-1">IND</span></label>

                <label class="font-normal flex items-center pl-2"><input type="radio" name="walking" class="m-0"
                                                                         autocomplete="off"
                                                                         value="slow" data-score="0.33">
                    <span class="pl-1">SLOW</span></label>

                <label class="font-normal flex items-center pl-2"><input type="radio" name="walking" class="m-0"
                                                                         autocomplete="off"
                                                                         value="asst" data-score="0.66">
                    <span class="pl-1">ASST</span></label>

                <label class="font-normal flex items-center pl-2"><input type="radio" name="walking" class="m-0"
                                                                         autocomplete="off"
                                                                         value="dep" data-score="1">
                    <span class="pl-1">DEP</span></label>
            </div>
            <div class="flex">
                <span class="mandatory font-bold w-9" data-names="ind,pull,asst,dep">
                    <span class="highlight" data-field-name="bed">Bed</span>
                </span>

                <label class="font-normal flex items-center"><input type="radio" name="bed" class="m-0"
                                                                    autocomplete="off" value="ind"
                                                                    data-score="0">
                    <span class="pl-1">IND</span></label>

                <label class="font-normal flex items-center pl-2"><input type="radio" name="bed" class="m-0"
                                                                         autocomplete="off" value="pull"
                                                                         data-score="0.33">
                    <span class="pl-1">PULL</span></label>

                <label class="font-normal flex items-center pl-2"><input type="radio" name="bed" class="m-0"
                                                                         autocomplete="off" value="asst"
                                                                         data-score="0.66">
                    <span class="pl-1">ASST</span></label>

                <label class="font-normal flex items-center pl-2"><input type="radio" name="bed" class="m-0"
                                                                         autocomplete="off" value="dep"
                                                                         data-score="1">
                    <span class="pl-1">DEP</span></label>
            </div>

            <div class="flex">
                <label for="five_times_sit_to_stand_time" class="tt font-normal" data-bs-toggle="tooltip"
                       title="Five Times Sit to Stand Score Time in Seconds">
                    <span class="font-bold highlight" data-field-name="five_times_sit_to_stand_time">5TSTS Time:</span>
                </label>
                <input type="number" id="five_times_sit_to_stand_time" min="0" max="1000"
                       name="five_times_sit_to_stand_time" class="h-si ecares-input" style="width:45px;">

                <label for="five_times_sit_to_stand_attempt" class="tt font-normal" data-bs-toggle="tooltip"
                       title="Five Times Sit to Stand Number Attempts">
                    <span class="highlight" data-field-name="five_times_sit_to_stand_attempt">5TSTS ATPT:</span>
                </label>

                <select id="five_times_sit_to_stand_attempt"
                        name="five_times_sit_to_stand_attempt" class="h-si ecares-input" style="width:30px;">
                    <option disabled selected></option>
                    <option value="1">1</option>
                    <option value="2">2</option>
                    <option value="3">3</option>
                    <option value="4">4</option>
                    <option value="5">5</option>
                </select>

                <div class="tt form-label inline-block pl-1" data-bs-toggle="tooltip"
                     title="Able to cross arms in front">
                    CRS Arms
                </div>

                <div class=" ml-1 flex items-center">
                    <label class="font-normal flex items-center">
                        <input type="radio" name="five_times_sit_to_stand_crossed_arms" class="m-0" value="1"
                               autocomplete="off">
                        <span class="pl-1">Y</span></label>
                    <label class="font-normal ml-1 flex items-center">
                        <input type="radio" name="five_times_sit_to_stand_crossed_arms" class="m-0" value="0"
                               autocomplete="off">
                        <span class="pl-1">N</span></label>
                </div>
            </div>
        </div>
    </div>

</div>