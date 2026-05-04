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
<div class="flex immunization-row items-center">
    <div>
        <svg height="10" width="10">
            <circle cx="5" cy="5" r="4" stroke="black" stroke-width="1" fill="white"/>
        </svg>
        <input type="checkbox" class="action-required" name="action_required_immunizations" data-score="1">
        <strong>Immunizations</strong>
    </div>
    <div class="mandatory ml-4 font-bold highlight tt" data-field-name="zoster" data-names="y,n" data-bs-toggle="tooltip"
         title="Immunizations: Zoster">
        Zoster
    </div>
    <div class="pl-1 flex items-center">
        <label class="flex items-center font-normal">
            <input type="radio" name="zoster" value="1" class="m-0" autocomplete="off">
            <span class="pl-1">Y</span>
        </label>

        <label class="flex items-center font-normal ml-1">
            <input type="radio" name="zoster" value="0" class="m-0" autocomplete="off">
            <span class="pl-1">N</span>
        </label>
    </div>
    <div class="mandatory font-bold ml-2 influenza highlight tt" data-field-name="influenza" data-names="y,n"
         data-bs-toggle="tooltip"
         title="Immunizations: Influenza">
        Influenza
    </div>
    <div class="flex items-center pl-1">
        <label class="font-normal flex items-center">
            <input type="radio" name="influenza" value="1" class="m-0" autocomplete="off">
            <span class="pl-1">Y</span>
        </label>
        <label class="font-normal ml-1 flex items-center">
            <input type="radio" name="influenza" value="0" class="m-0" autocomplete="off">
            <span class="pl-1">N</span>
        </label>
    </div>
    <div class="mandatory font-bold ml-2 tt highlight" data-field-name="pneumococcal" data-names="y,n"
         data-bs-toggle="tooltip"
         title="Immunizations: Pneumococca">
        Pneumococcal
    </div>
    <div class="pl-1 flex items-center">
        <label class="font-normal flex items-center">
            <input type="radio" name="pneumococcal" value="1" class="m-0" autocomplete="off">
            <span class="pl-1">Y</span>
        </label>
        <label class="font-normal ml-1 flex items-center">
            <input type="radio" name="pneumococcal" value="0" class="m-0" autocomplete="off">
            <span class="pl-1">N</span>
        </label>
    </div>
    <div class="mandatory font-bold ml-2 tt highlight" data-field-name="tetanus_and_diphtheria" data-names="y,n"
         data-bs-toggle="tooltip"
         title="Immunizations: Tetanus & Diphtheria">
        Tetanus and Diphtheria
    </div>
    <div class="pl-1 flex items-center">
        <label class="font-normal flex items-center">
            <input type="radio" name="tetanus_and_diphtheria" value="1" class="m-0" autocomplete="off">
            <span class="pl-1">Y</span>
        </label>
        <label class="font-normal ml-1 flex items-center">
            <input type="radio" name="tetanus_and_diphtheria" value="0" class="m-0" autocomplete="off">
            <span class="pl-1">N</span>
        </label>
    </div>
    <div class="mandatory font-bold ml-4 highlight tt" data-field-name="hep_a" data-names="y,n" data-bs-toggle="tooltip"
         title="Immunizations: Hepatitis A">
        Hep A
    </div>
    <div class="pl-1 flex items-center">
        <label class="font-normal flex items-center">
            <input type="radio" name="hep_a" value="1" class="m-0" autocomplete="off">
            <span class="pl-1">Y</span>
        </label>
        <label class="font-normal ml-1 flex items-center">
            <input type="radio" name="hep_a" value="0" class="m-0" autocomplete="off">
            <span class="pl-1">N</span>
        </label>
    </div>
    <div class="mandatory font-bold ml-4 tt highlight" data-field-name="hep_b" data-names="y,n" data-bs-toggle="tooltip"
         title="Immunizations: Hepatitis B">
        Hep B
    </div>
    <div class="pl-1 flex items-center">
        <label class="font-normal flex items-center">
            <input type="radio" name="hep_b" value="1" class="m-0" autocomplete="off">
            <span class="pl-1">Y</span>
        </label>
        <label class="font-normal ml-1 flex items-center">
            <input type="radio" name="hep_b" value="0" class="m-0" autocomplete="off">
            <span class="pl-1">N</span>
        </label>
    </div>
</div>