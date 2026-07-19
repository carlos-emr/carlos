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
<div class="flex ">
    <div class="mw-9">
        <svg height="10" width="10">
            <circle cx="5" cy="5" r="4" stroke="black" stroke-width="1" fill="white"/>
        </svg>
        <input type="checkbox" class="action-required" name="action_required_adls" data-score="1">
        <strong>ADLs</strong>
    </div>

    <div class="flex w-full border-left-gray">
        <div class="flex-1 flex flex-col">
            <div class="flex">
                <span class="mandatory tt font-bold w-10 pl-1" data-names="ind,asst,dep"
                      data-toggle="tooltip" title="Feeding">
                    <span class="highlight" data-field-name="feeding">Feeding</span>
                </span>

                <label class="font-normal flex items-center"><input type="radio" name="feeding" class="m-0"
                                                                    autocomplete="off" value="ind"
                                                                    data-score="0">
                    <span class="pl-1">IND</span></label>

                <label class="font-normal flex items-center pl-6"><input type="radio" name="feeding" class="m-0"
                                                                         autocomplete="off" value="asst"
                                                                         data-score="0.5">
                    <span class="pl-1">ASST</span></label>

                <label class="font-normal flex items-center pl-6"><input type="radio" name="feeding" class="m-0"
                                                                         autocomplete="off" value="dep"
                                                                         data-score="1">
                    <span class="pl-1">DEP</span></label></div>
            <div class="flex">
                <span class="mandatory tt font-bold w-10 pl-1" data-names="ind,asst,dep"
                      data-toggle="tooltip" title="Dressing">
                    <span class="highlight" data-field-name="dressing">Dressing</span>
                </span>

                <label class="font-normal flex items-center"><input type="radio" name="dressing" class="m-0"
                                                                    autocomplete="off" value="ind"
                                                                    data-score="0">
                    <span class="pl-1">IND</span></label>

                <label class="font-normal flex items-center pl-6"><input type="radio" name="dressing" class="m-0"
                                                                         autocomplete="off" value="asst"
                                                                         data-score="0.5">
                    <span class="pl-1">ASST</span></label>

                <label class="font-normal flex items-center pl-6"><input type="radio" name="dressing" class="m-0"
                                                                         autocomplete="off" value="dep"
                                                                         data-score="1">
                    <span class="pl-1">DEP</span></label>
            </div>
        </div>

        <div class="flex-1">
            <div class="flex">
                <span class="mandatory tt font-bold w-9"
                      data-names="ind,asst,dep" data-toggle="tooltip"
                      title="Bathing">
                    <span class="highlight" data-field-name="bathing">Bathing</span>
                </span>

                <label class="font-normal flex items-center"><input type="radio" name="bathing" class="m-0"
                                                                    autocomplete="off" value="ind"
                                                                    data-score="0">
                    <span class="pl-1">IND</span></label>

                <label class="font-normal flex items-center pl-6"><input type="radio" name="bathing" class="m-0"
                                                                         autocomplete="off" value="asst"
                                                                         data-score="0.5">
                    <span class="pl-1">ASST</span></label>

                <label class="font-normal flex items-center pl-6"><input type="radio" name="bathing" class="m-0"
                                                                         autocomplete="off" value="dep"
                                                                         data-score="1">
                    <span class="pl-1">DEP</span></label>
            </div>

            <div class="flex">
                <span class="mandatory tt font-bold w-9" data-names="ind,asst,dep"
                      data-toggle="tooltip" title="Toileting">
                    <span class="highlight" data-field-name="toileting">Toileting</span>
                    </span>

                <label class="font-normal flex items-center"><input type="radio" name="toileting" class="m-0"
                                                                    autocomplete="off" value="ind"
                                                                    data-score="0">
                    <span class="pl-1">IND</span></label>

                <label class="font-normal flex items-center pl-6"><input type="radio" name="toileting" class="m-0"
                                                                         autocomplete="off" value="asst"
                                                                         data-score="0.5">
                    <span class="pl-1">ASST</span></label>

                <label class="font-normal flex items-center pl-6"><input type="radio" name="toileting" class="m-0"
                                                                         autocomplete="off" value="dep"
                                                                         data-score="1">
                    <span class="pl-1">DEP</span></label>
            </div>
        </div>
    </div>
    <div>

    </div>

</div>