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
<div class="flex items-center w-full">
    <div>
        <svg height="10" width="10">
            <circle cx="5" cy="5" r="4" stroke="black" stroke-width="1" fill="white"/>
        </svg>
        <input type="checkbox" class="action-required" name="action_required_supports" data-score="1">
        <strong class="tt" data-bs-toggle="tooltip" title="HCNS: Home Care Nursing Support">Supports</strong>
    </div>

    <div class="pl-4 flex items-center">
        <label class="font-normal flex items-center"><input type="radio" name="supports" class="m-0" autocomplete="off"
                                                            value="none needed">
            <span class="pl-1">None needed</span></label>

        <label class="font-normal flex items-center ml-2"><input type="radio" name="supports" class="m-0"
                                                                 autocomplete="off" value="informal">
            <span class="pl-1">Informal</span></label>

        <label class="font-normal flex items-center ml-2"><input type="radio" name="supports" class="m-0"
                                                                 autocomplete="off" value="hcns">
            <span class="pl-1">HCNS</span></label>

        <label class="font-normal flex items-center ml-2"><input type="radio" name="supports" class="m-0"
                                                                 autocomplete="off" value="other">
            <span class="pl-1">Other</span></label>
    </div>
    <div class="pl-4 flex items-center">
        <span class="tt font-bold flex items-center form-label" data-bs-toggle="tooltip" title="For ADL or IADL">
        Requires more support
        </span>

        <label class="font-normal flex items-center ml-2"><input type="radio" name="requires_more_support" class="m-0"
                                                                 autocomplete="off" value="y">
            <span class="pl-1">Y</span></label>
        <label class="font-normal flex items-center ml-2"><input type="radio" name="requires_more_support" class="m-0"
                                                                 autocomplete="off" value="n">
            <span class="pl-1">N</span></label>
    </div>
</div>